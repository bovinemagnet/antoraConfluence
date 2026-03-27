package io.github.bovinemagnet.antoraconfluence.tasks

import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Rebuilds (reconciles) the local state file by querying Confluence page properties.
 *
 * This task is useful when:
 * - The local state file (`state.json`) has been deleted or is missing (e.g. after a fresh clone).
 * - The local state has drifted from Confluence reality (e.g. after a manual page move).
 *
 * The task queries Confluence for all pages in the configured space that carry the
 * `antora-confluence-key` page property, rebuilds the local fingerprint store entries from
 * that remote metadata, and saves the updated store file.
 *
 * **This task makes only read-only Confluence API calls.**
 */
@DisableCachingByDefault(because = "Interacts with remote Confluence API")
abstract class AntoraConfluenceReconcileStateTask : DefaultTask() {

    @get:Input
    abstract val confluenceUrl: Property<String>

    @get:Internal
    abstract val username: Property<String>

    @get:Internal
    abstract val apiToken: Property<String>

    @get:Input
    abstract val credentialsPresent: Property<Boolean>

    @get:Input
    abstract val spaceKey: Property<String>

    /** Local state file to be rebuilt. */
    @get:Internal
    abstract val fingerprintFile: RegularFileProperty

    @TaskAction
    fun reconcileState() {
        validateRequiredInputs()

        val storeFile = fingerprintFile.get().asFile
        logger.lifecycle("Reconciling local state from Confluence remote metadata…")
        logger.lifecycle("  Space       : ${spaceKey.get()}")
        logger.lifecycle("  State file  : ${storeFile.absolutePath}")

        val client = ConfluenceClient(
            baseUrl = confluenceUrl.get(),
            username = username.get(),
            apiToken = apiToken.get()
        )

        // Verify space is accessible
        val space = client.getSpace(spaceKey.get())
            ?: throw GradleException("Confluence space '${spaceKey.get()}' not found or not accessible.")

        logger.lifecycle("  Connected to space '${space.key}' (id=${space.id})")

        // Load or create the local state store
        val store = ContentFingerprintStore(storeFile)
        val existingCount = store.allEntries().size

        logger.lifecycle("  Local state has $existingCount tracked page(s).")
        logger.lifecycle("")

        // Query Confluence for managed pages
        val managedPages = client.listManagedPages(space.id, "managed-by-antora-confluence")
        logger.lifecycle("  Found ${managedPages.size} managed page(s) in Confluence")

        var reconciledCount = 0
        for (page in managedPages) {
            val canonicalKey = client.getPageProperty(page.id, "antora-confluence-key") ?: continue
            val fingerprint = client.getPageProperty(page.id, "antora-confluence-fingerprint") ?: ""
            val sourcePath = client.getPageProperty(page.id, "antora-confluence-source")

            store.putComposite(
                pageId = canonicalKey,
                contentHash = fingerprint,
                compositeHash = fingerprint,
                confluencePageId = page.id,
                confluenceTitle = page.title,
                sourcePath = sourcePath
            )
            reconciledCount++
            logger.lifecycle("  Reconciled: $canonicalKey -> Confluence page ${page.id} (${page.title})")
        }

        logger.lifecycle("")
        logger.lifecycle("Reconciliation complete. $reconciledCount page(s) reconciled.")

        // Save (persist) any updates
        store.save()
        logger.lifecycle("State file saved: ${storeFile.absolutePath}")
    }

    private fun validateRequiredInputs() {
        val missing = mutableListOf<String>()
        if (!confluenceUrl.isPresent || confluenceUrl.get().isBlank()) missing += "confluence.baseUrl"
        if (!username.isPresent || username.get().isBlank()) missing += "confluence.username"
        if (!apiToken.isPresent || apiToken.get().isBlank()) missing += "confluence.apiToken"
        if (!spaceKey.isPresent || spaceKey.get().isBlank()) missing += "confluence.spaceKey"
        if (missing.isNotEmpty()) {
            throw GradleException(
                "antoraConfluenceReconcileState requires the following extension properties to be set: ${missing.joinToString()}"
            )
        }
    }
}
