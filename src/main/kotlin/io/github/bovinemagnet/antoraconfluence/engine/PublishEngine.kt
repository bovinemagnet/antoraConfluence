package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceApiException
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePage
import io.github.bovinemagnet.antoraconfluence.antora.AsciiDocConverter
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishAction
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishRequest
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishResult
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishSummary
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import org.slf4j.LoggerFactory

/**
 * Core publish engine that scans Antora content and publishes it to Confluence.
 *
 * This class is a pure Kotlin class with no Gradle API dependencies; it is designed to be
 * called from Gradle task action methods but can equally be used in any JVM context.
 *
 * In dry-run mode the engine computes the publish plan (CREATE / UPDATE / SKIP) without making
 * any mutating calls to the Confluence API.
 */
class PublishEngine {

    private val log = LoggerFactory.getLogger(PublishEngine::class.java)

    /**
     * Executes a publish operation described by [request] and returns a [PublishSummary].
     *
     * @param request All parameters required to perform the publish.
     * @return Summary of actions taken (or planned, when [PublishRequest.dryRun] is `true`).
     * @throws IllegalStateException if strict mode is enabled and failures or orphans are detected.
     */
    fun publish(request: PublishRequest): PublishSummary {
        val scanner = AntoraContentScanner()
        val pages = scanner.scan(request.contentDir, request.siteKey)

        if (pages.isEmpty()) {
            log.info("No Antora pages found in ${request.contentDir.absolutePath}. Nothing to publish.")
            return buildSummary(emptyList(), 0, request)
        }

        log.info(
            "${if (request.dryRun) "[DRY RUN] " else ""}${if (request.forceAll) "Full publish" else "Publishing"}: " +
                "${pages.size} page(s) to Confluence space '${request.spaceKey}'"
        )

        val store = ContentFingerprintStore(request.stateFile)

        val results = if (request.dryRun) {
            computeDryRunResults(pages, store, request)
        } else {
            executeLivePublish(pages, store, request)
        }

        val sourcePageIds = pages.map { it.pageId }.toSet()
        val orphanedPageIds = store.allPageIds() - sourcePageIds
        val orphanCount = handleOrphans(orphanedPageIds, store, request)

        if (!request.dryRun) {
            store.save()
        }

        val summary = buildSummary(results, orphanCount, request)

        if (request.strict) {
            if (results.any { it.action == PublishAction.FAILED }) {
                val failCount = results.count { it.action == PublishAction.FAILED }
                throw IllegalStateException(
                    "$failCount page(s) failed to publish and strict mode is enabled."
                )
            }
            if (orphanCount > 0) {
                throw IllegalStateException(
                    "$orphanCount orphaned managed page(s) detected and strict mode is enabled. " +
                        "Check the log for details."
                )
            }
        }

        return summary
    }

    // -------------------------------------------------------------------------
    // Dry-run plan computation
    // -------------------------------------------------------------------------

    private fun computeDryRunResults(
        pages: List<AntoraPage>,
        store: ContentFingerprintStore,
        request: PublishRequest
    ): List<PublishResult> =
        pages.sortedBy { it.pageId }.map { page ->
            val content = page.sourceFile.readText()
            when {
                !request.forceAll && !store.isChanged(page.pageId, content) ->
                    PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, store.get(page.pageId)?.confluencePageId, "Content unchanged")
                store.get(page.pageId)?.confluencePageId != null -> {
                    // Known page — would be updated
                    planSkipOrAction(page, store, request, PublishAction.UPDATE)
                }
                else -> {
                    // New page — would be created
                    planSkipOrAction(page, store, request, PublishAction.CREATE)
                }
            }
        }

    /**
     * Applies [PublishStrategy] constraints to determine whether a planned action should be
     * changed to SKIP.
     */
    private fun planSkipOrAction(
        page: AntoraPage,
        store: ContentFingerprintStore,
        request: PublishRequest,
        intendedAction: PublishAction
    ): PublishResult {
        val existingId = store.get(page.pageId)?.confluencePageId
        return when {
            intendedAction == PublishAction.CREATE && request.publishStrategy == PublishStrategy.UPDATE_ONLY ->
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, null, "UPDATE_ONLY: page not found")
            intendedAction == PublishAction.UPDATE && request.publishStrategy == PublishStrategy.CREATE_ONLY ->
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, existingId, "CREATE_ONLY: page already exists")
            else ->
                PublishResult(page.pageId, page.suggestedTitle, intendedAction, existingId, "Dry run")
        }
    }

    // -------------------------------------------------------------------------
    // Live publish
    // -------------------------------------------------------------------------

    private fun executeLivePublish(
        pages: List<AntoraPage>,
        store: ContentFingerprintStore,
        request: PublishRequest
    ): List<PublishResult> {
        val client = ConfluenceClient(
            baseUrl = request.confluenceUrl,
            username = request.username,
            apiToken = request.apiToken
        )

        val space = client.getSpace(request.spaceKey)
            ?: throw IllegalStateException("Confluence space '${request.spaceKey}' not found or not accessible.")

        val resolvedParentId: String? = request.parentPageId?.takeIf { it.isNotBlank() }

        val results = mutableListOf<PublishResult>()
        AsciiDocConverter().use { converter ->
            pages.sortedBy { it.pageId }.forEach { page ->
                val result = publishPage(page, converter, client, store, space.id, resolvedParentId, request)
                results.add(result)
                logResult(result, request.dryRun)
            }
        }
        return results
    }

    private fun publishPage(
        page: AntoraPage,
        converter: AsciiDocConverter,
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        spaceId: String,
        parentPageId: String?,
        request: PublishRequest
    ): PublishResult {
        val content = page.sourceFile.readText()

        if (!request.forceAll && !store.isChanged(page.pageId, content)) {
            return PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, null, "Content unchanged")
        }

        val existingPage: ConfluencePage? = findExistingPage(client, store, page, spaceId)

        return when {
            existingPage == null && request.publishStrategy == PublishStrategy.UPDATE_ONLY ->
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, null, "UPDATE_ONLY: page not found")
            existingPage != null && request.publishStrategy == PublishStrategy.CREATE_ONLY ->
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, existingPage.id, "CREATE_ONLY: page already exists")
            existingPage == null ->
                createPage(page, content, converter, client, store, spaceId, parentPageId)
            else ->
                updatePage(page, content, converter, client, store, existingPage)
        }
    }

    private fun createPage(
        page: AntoraPage,
        content: String,
        converter: AsciiDocConverter,
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        spaceId: String,
        parentPageId: String?
    ): PublishResult =
        try {
            val html = converter.renderToHtml(page.sourceFile)
            val created = client.createPage(spaceId, parentPageId, page.suggestedTitle, html)
            store.put(page.pageId, content, created.id, page.suggestedTitle)
            PublishResult(page.pageId, page.suggestedTitle, PublishAction.CREATE, created.id, null)
        } catch (e: ConfluenceApiException) {
            log.error("Failed to create page '${page.suggestedTitle}': ${e.message}")
            PublishResult(page.pageId, page.suggestedTitle, PublishAction.FAILED, null, e.message)
        }

    private fun updatePage(
        page: AntoraPage,
        content: String,
        converter: AsciiDocConverter,
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        existing: ConfluencePage
    ): PublishResult =
        try {
            val html = converter.renderToHtml(page.sourceFile)
            val currentVersion = existing.version?.number ?: 1
            val updated = client.updatePage(existing.id, page.suggestedTitle, html, currentVersion)
            store.put(page.pageId, content, updated.id, page.suggestedTitle)
            PublishResult(page.pageId, page.suggestedTitle, PublishAction.UPDATE, updated.id, null)
        } catch (e: ConfluenceApiException) {
            log.error("Failed to update page '${page.suggestedTitle}': ${e.message}")
            PublishResult(page.pageId, page.suggestedTitle, PublishAction.FAILED, existing.id, e.message)
        }

    private fun findExistingPage(
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        page: AntoraPage,
        spaceId: String
    ): ConfluencePage? {
        val storedId = store.get(page.pageId)?.confluencePageId
        if (storedId != null) {
            val existing = client.getPage(storedId)
            if (existing != null) return existing
        }
        return client.findPageByTitle(spaceId, page.suggestedTitle)
    }

    // -------------------------------------------------------------------------
    // Orphan handling
    // -------------------------------------------------------------------------

    /**
     * Logs and handles orphaned pages according to [PublishRequest.orphanStrategy].
     *
     * @return The number of orphaned pages detected.
     */
    private fun handleOrphans(
        orphanedPageIds: Set<String>,
        store: ContentFingerprintStore,
        request: PublishRequest
    ): Int {
        if (orphanedPageIds.isEmpty()) return 0

        orphanedPageIds.forEach { pageId ->
            val entry = store.get(pageId)
            log.warn("Orphaned managed page: $pageId (Confluence ID: ${entry?.confluencePageId ?: "unknown"})")
        }

        when (request.orphanStrategy) {
            OrphanStrategy.REPORT ->
                log.info("${orphanedPageIds.size} orphaned page(s) detected. See warnings above.")
            OrphanStrategy.LABEL ->
                log.info("${orphanedPageIds.size} orphaned page(s) detected. Label-based archival not yet implemented.")
            OrphanStrategy.ARCHIVE ->
                log.info("${orphanedPageIds.size} orphaned page(s) detected. Page archival not yet implemented.")
        }

        return orphanedPageIds.size
    }

    // -------------------------------------------------------------------------
    // Logging helpers
    // -------------------------------------------------------------------------

    private fun logResult(result: PublishResult, dryRun: Boolean) {
        val prefix = if (dryRun) "[DRY RUN] " else ""
        val suffix = if (result.error != null) " (${result.error})" else ""
        log.info("  ${prefix}[${result.action.name.padEnd(6)}]  ${result.pageId}$suffix")
    }

    // -------------------------------------------------------------------------
    // Summary builder
    // -------------------------------------------------------------------------

    private fun buildSummary(
        results: List<PublishResult>,
        orphanCount: Int,
        request: PublishRequest
    ): PublishSummary {
        val created = results.count { it.action == PublishAction.CREATE }
        val updated = results.count { it.action == PublishAction.UPDATE }
        val skipped = results.count { it.action == PublishAction.SKIP }
        val failed = results.count { it.action == PublishAction.FAILED }
        return PublishSummary(
            results = results,
            created = created,
            updated = updated,
            skipped = skipped,
            failed = failed,
            orphaned = orphanCount,
            dryRun = request.dryRun,
            forceAll = request.forceAll
        )
    }
}
