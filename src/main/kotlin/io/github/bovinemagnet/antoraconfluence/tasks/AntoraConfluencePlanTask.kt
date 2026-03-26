package io.github.bovinemagnet.antoraconfluence.tasks

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

/**
 * Dry-run task that shows which Antora pages would be created or updated in Confluence
 * without making any mutating API calls.
 *
 * For each page the plan reports one of:
 * - **CREATE** – page does not yet exist in Confluence
 * - **UPDATE** – page exists but content has changed since the last publish
 * - **SKIP**   – page exists and content is unchanged
 */
abstract class AntoraConfluencePlanTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val confluenceUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val username: Property<String>

    @get:Input
    @get:Optional
    abstract val apiToken: Property<String>

    @get:Input
    @get:Optional
    abstract val spaceKey: Property<String>

    @get:Input
    @get:Optional
    abstract val parentPageTitle: Property<String>

    @get:Internal
    abstract val fingerprintFile: RegularFileProperty

    @TaskAction
    fun plan() {
        val contentDirFile = contentDir.get().asFile
        val scanner = AntoraContentScanner()
        val pages = scanner.scan(contentDirFile)

        val storeFile = fingerprintFile.asFile.orNull
        val store = if (storeFile != null && storeFile.exists()) ContentFingerprintStore(storeFile) else null

        logger.lifecycle("")
        logger.lifecycle("=== Antora Confluence Publish Plan ===")
        logger.lifecycle("Content dir : ${contentDirFile.absolutePath}")
        logger.lifecycle("Total pages : ${pages.size}")
        logger.lifecycle("")

        if (pages.isEmpty()) {
            logger.lifecycle("No AsciiDoc pages found. Nothing to publish.")
            return
        }

        // When Confluence credentials are available, query current page existence.
        val confluencePageIds: Map<String, String?> = if (canConnectToConfluence()) {
            resolveConfluencePageIds(pages.map { it.pageId }, pages.map { it.suggestedTitle })
        } else {
            emptyMap()
        }

        var createCount = 0
        var updateCount = 0
        var skipCount = 0

        pages.sortedBy { it.pageId }.forEach { page ->
            val content = page.sourceFile.readText()
            val changed = store?.isChanged(page.pageId, content) ?: true
            val existsInConfluence = confluencePageIds[page.pageId] != null || store?.get(page.pageId)?.confluencePageId != null

            val action = when {
                !existsInConfluence -> { createCount++; "CREATE" }
                changed -> { updateCount++; "UPDATE" }
                else -> { skipCount++; "SKIP  " }
            }
            logger.lifecycle("  [$action]  ${page.pageId}")
        }

        logger.lifecycle("")
        logger.lifecycle("Plan summary: $createCount to create, $updateCount to update, $skipCount to skip.")
        logger.lifecycle("")
    }

    private fun canConnectToConfluence(): Boolean =
        confluenceUrl.isPresent && confluenceUrl.get().isNotBlank() &&
                username.isPresent && username.get().isNotBlank() &&
                apiToken.isPresent && apiToken.get().isNotBlank() &&
                spaceKey.isPresent && spaceKey.get().isNotBlank()

    private fun resolveConfluencePageIds(pageIds: List<String>, titles: List<String>): Map<String, String?> {
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
}
