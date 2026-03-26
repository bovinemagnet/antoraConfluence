package io.github.bovinemagnet.antoraconfluence.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.Instant

/**
 * Dry-run task that shows which Antora pages would be created or updated in Confluence
 * without making any mutating API calls.
 *
 * For each page the plan reports one of:
 * - **CREATE** – page does not yet exist in Confluence
 * - **UPDATE** – page exists but content has changed since the last publish
 * - **SKIP**   – page exists and content is unchanged
 *
 * The plan is also written to [planReportFile] in JSON format.
 */
abstract class AntoraConfluencePlanTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val siteKey: Property<String>

    @get:Input
    @get:Optional
    abstract val confluenceUrl: Property<String>

    @get:Internal
    abstract val username: Property<String>

    @get:Internal
    abstract val apiToken: Property<String>

    @get:Input
    @get:Optional
    abstract val credentialsPresent: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val spaceKey: Property<String>

    @get:Input
    @get:Optional
    abstract val parentPageId: Property<String>

    /** Local state file (read-only; may not exist on first run). */
    @get:Internal
    abstract val fingerprintFile: RegularFileProperty

    /** JSON plan report output. */
    @get:OutputFile
    abstract val planReportFile: RegularFileProperty

    @TaskAction
    fun plan() {
        val contentDirFile = contentDir.get().asFile
        val site = siteKey.orNull?.trim() ?: ""
        val scanner = AntoraContentScanner()
        val pages = scanner.scan(contentDirFile, site)

        val storeFile = fingerprintFile.asFile.orNull
        val store = if (storeFile != null && storeFile.exists()) ContentFingerprintStore(storeFile) else null

        logger.lifecycle("")
        logger.lifecycle("=== Antora Confluence Publish Plan ===")
        logger.lifecycle("Content dir : ${contentDirFile.absolutePath}")
        logger.lifecycle("Site key    : ${site.ifBlank { "(not set)" }}")
        logger.lifecycle("Total pages : ${pages.size}")
        logger.lifecycle("")

        if (pages.isEmpty()) {
            logger.lifecycle("No AsciiDoc pages found. Nothing to publish.")
            writePlanReport(emptyList())
            return
        }

        // Query Confluence for existing pages when credentials are available
        val confluencePageIds: Map<String, String?> = if (canConnectToConfluence()) {
            resolveConfluencePageIds(pages.map { it.pageId }, pages.map { it.suggestedTitle })
        } else {
            emptyMap()
        }

        var createCount = 0
        var updateCount = 0
        var skipCount = 0

        val planActions = mutableListOf<Map<String, Any?>>()

        pages.sortedBy { it.pageId }.forEach { page ->
            val content = page.sourceFile.readText()
            val changed = store?.isChanged(page.pageId, content) ?: true
            val existsInConfluence = confluencePageIds[page.pageId] != null
                || store?.get(page.pageId)?.confluencePageId != null

            val action = when {
                !existsInConfluence -> { createCount++; "CREATE" }
                changed             -> { updateCount++; "UPDATE" }
                else                -> { skipCount++;   "SKIP" }
            }
            logger.lifecycle("  [${action.padEnd(6)}]  ${page.pageId}")
            planActions += mapOf(
                "pageId" to page.pageId,
                "title" to page.suggestedTitle,
                "action" to action,
                "sourceFile" to page.sourceFile.path
            )
        }

        logger.lifecycle("")
        logger.lifecycle("Plan summary: $createCount to create, $updateCount to update, $skipCount to skip.")
        logger.lifecycle("")

        writePlanReport(planActions)
    }

    private fun canConnectToConfluence(): Boolean =
        confluenceUrl.isPresent && confluenceUrl.get().isNotBlank() &&
            username.isPresent && username.get().isNotBlank() &&
            apiToken.isPresent && apiToken.get().isNotBlank() &&
            spaceKey.isPresent && spaceKey.get().isNotBlank()

    private fun resolveConfluencePageIds(
        pageIds: List<String>,
        titles: List<String>
    ): Map<String, String?> {
        return try {
            val client = ConfluenceClient(
                baseUrl = confluenceUrl.get(),
                username = username.get(),
                apiToken = apiToken.get()
            )
            val space = client.getSpace(spaceKey.get()) ?: return emptyMap()
            pageIds.zip(titles).associate { (pageId, title) ->
                pageId to client.findPageByTitle(space.id, title)?.id
            }
        } catch (e: Exception) {
            logger.warn("Could not query Confluence for existing pages: ${e.message}")
            emptyMap()
        }
    }

    private fun writePlanReport(actions: List<Map<String, Any?>>) {
        val report = mapOf(
            "timestamp" to Instant.now().toString(),
            "siteKey" to (siteKey.orNull ?: ""),
            "spaceKey" to (spaceKey.orNull ?: ""),
            "actions" to actions
        )
        val reportFile = planReportFile.get().asFile
        reportFile.parentFile?.mkdirs()
        jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportFile, report)
        logger.lifecycle("Plan report written to ${reportFile.absolutePath}")
    }
}
