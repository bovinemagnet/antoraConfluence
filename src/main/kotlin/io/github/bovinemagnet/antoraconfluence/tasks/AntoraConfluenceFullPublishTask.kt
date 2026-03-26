package io.github.bovinemagnet.antoraconfluence.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.bovinemagnet.antoraconfluence.antora.AsciiDocConverter
import io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceApiException
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePage
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.Instant

/**
 * Full publish task — republishes **all** managed Antora pages to Confluence regardless of
 * whether the content fingerprint has changed.
 *
 * Use this task to force a complete resync of all pages, for example after a Confluence space
 * migration, or when the state file has been deleted and `antoraConfluenceReconcileState` is
 * not appropriate.
 *
 * When [dryRun] is `true` the task logs all planned actions but makes no mutating API calls.
 */
abstract class AntoraConfluenceFullPublishTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val siteKey: Property<String>

    @get:Input
    abstract val confluenceUrl: Property<String>

    @get:Input
    abstract val username: Property<String>

    @get:Input
    abstract val apiToken: Property<String>

    @get:Input
    abstract val spaceKey: Property<String>

    @get:Input
    @get:Optional
    abstract val parentPageId: Property<String>

    @get:Input
    abstract val strict: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val applyLabels: ListProperty<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:OutputFile
    abstract val fingerprintFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun fullPublish() {
        validateRequiredInputs()

        val contentDirFile = contentDir.get().asFile
        val isDryRun = dryRun.get()
        val site = siteKey.orNull?.trim() ?: ""

        val storeFile = fingerprintFile.get().asFile
        val store = ContentFingerprintStore(storeFile)

        val client = ConfluenceClient(
            baseUrl = confluenceUrl.get(),
            username = username.get(),
            apiToken = apiToken.get()
        )

        val space = client.getSpace(spaceKey.get())
            ?: throw GradleException("Confluence space '${spaceKey.get()}' not found or not accessible.")

        val resolvedParentId: String? = parentPageId.orNull?.takeIf { it.isNotBlank() }

        val scanner = AntoraContentScanner()
        val pages = scanner.scan(contentDirFile, site)

        if (pages.isEmpty()) {
            logger.lifecycle("No Antora pages found in ${contentDirFile.absolutePath}. Nothing to publish.")
            writeReport(emptyList(), isDryRun)
            return
        }

        logger.lifecycle(
            "${if (isDryRun) "[DRY RUN] " else ""}Full publish: ${pages.size} page(s) to " +
                "Confluence space '${spaceKey.get()}' (ignoring fingerprints)"
        )

        val results = mutableListOf<PublishResult>()

        AsciiDocConverter().use { converter ->
            pages.sortedBy { it.pageId }.forEach { page ->
                val result = publishPage(page, converter, client, store, space.id, resolvedParentId, isDryRun)
                results.add(result)
                logResult(result, isDryRun)
            }
        }

        if (!isDryRun) {
            store.save()
        }

        writeReport(results, isDryRun)
        logSummary(results, isDryRun)

        if (strict.get() && results.any { it.action == PublishAction.FAILED }) {
            throw GradleException(
                "${results.count { it.action == PublishAction.FAILED }} page(s) failed to publish " +
                    "and strict mode is enabled."
            )
        }
    }

    private fun publishPage(
        page: AntoraPage,
        converter: AsciiDocConverter,
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        spaceId: String,
        parentPageId: String?,
        dryRun: Boolean
    ): PublishResult {
        val content = page.sourceFile.readText()
        val existingPage: ConfluencePage? = findExistingPage(client, store, page, spaceId)

        return if (existingPage == null) {
            if (dryRun) PublishResult(page.pageId, page.suggestedTitle, PublishAction.CREATED, null, "Dry run")
            else try {
                val html = converter.convert(page.sourceFile)
                val created = client.createPage(spaceId, parentPageId, page.suggestedTitle, html)
                store.put(page.pageId, content, created.id, page.suggestedTitle)
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.CREATED, created.id, null)
            } catch (e: ConfluenceApiException) {
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.FAILED, null, e.message)
            }
        } else {
            if (dryRun) PublishResult(page.pageId, page.suggestedTitle, PublishAction.UPDATED, existingPage.id, "Dry run")
            else try {
                val html = converter.convert(page.sourceFile)
                val currentVersion = existingPage.version?.number ?: 1
                val updated = client.updatePage(existingPage.id, page.suggestedTitle, html, currentVersion)
                store.put(page.pageId, content, updated.id, page.suggestedTitle)
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.UPDATED, updated.id, null)
            } catch (e: ConfluenceApiException) {
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.FAILED, existingPage.id, e.message)
            }
        }
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

    private fun validateRequiredInputs() {
        val missing = mutableListOf<String>()
        if (!confluenceUrl.isPresent || confluenceUrl.get().isBlank()) missing += "confluence.baseUrl"
        if (!username.isPresent || username.get().isBlank()) missing += "confluence.username"
        if (!apiToken.isPresent || apiToken.get().isBlank()) missing += "confluence.apiToken"
        if (!spaceKey.isPresent || spaceKey.get().isBlank()) missing += "confluence.spaceKey"
        if (missing.isNotEmpty()) {
            throw GradleException(
                "antoraConfluenceFullPublish requires the following extension properties to be set: ${missing.joinToString()}"
            )
        }
    }

    private fun logResult(result: PublishResult, dryRun: Boolean) {
        val prefix = if (dryRun) "[DRY RUN] " else ""
        val suffix = if (result.error != null) " (${result.error})" else ""
        logger.lifecycle("  ${prefix}[${result.action.name.padEnd(7)}]  ${result.pageId}$suffix")
    }

    private fun logSummary(results: List<PublishResult>, dryRun: Boolean) {
        val prefix = if (dryRun) "[DRY RUN] " else ""
        val created = results.count { it.action == PublishAction.CREATED }
        val updated = results.count { it.action == PublishAction.UPDATED }
        val failed = results.count { it.action == PublishAction.FAILED }
        logger.lifecycle("")
        logger.lifecycle("${prefix}Full publish complete: $created created, $updated updated, $failed failed.")
    }

    private fun writeReport(results: List<PublishResult>, dryRun: Boolean) {
        val report = mapOf(
            "timestamp" to Instant.now().toString(),
            "dryRun" to dryRun,
            "type" to "FULL_PUBLISH",
            "spaceKey" to spaceKey.get(),
            "results" to results.map { r ->
                mapOf(
                    "pageId" to r.pageId,
                    "title" to r.title,
                    "action" to r.action.name,
                    "confluencePageId" to r.confluencePageId,
                    "error" to r.error
                )
            }
        )
        val reportFileObj = reportFile.get().asFile
        reportFileObj.parentFile?.mkdirs()
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFileObj, report)
        logger.lifecycle("Report written to ${reportFileObj.absolutePath}")
    }
}
