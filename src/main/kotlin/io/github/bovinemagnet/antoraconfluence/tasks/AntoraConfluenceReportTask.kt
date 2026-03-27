package io.github.bovinemagnet.antoraconfluence.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import io.github.bovinemagnet.antoraconfluence.fingerprint.FingerprintEntry
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Reports on the current Confluence publish state by reading the local fingerprint store and
 * the latest publish report produced by [AntoraConfluencePublishTask].
 *
 * This task is read-only and makes no API calls or filesystem writes.
 */
@DisableCachingByDefault(because = "Reads local state files")
abstract class AntoraConfluenceReportTask : DefaultTask() {

    @get:Internal
    abstract val fingerprintFile: RegularFileProperty

    @get:Internal
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun report() {
        logger.lifecycle("")
        logger.lifecycle("=== Antora Confluence Publish State Report ===")

        reportFingerprintStore()
        reportLastPublish()

        logger.lifecycle("")
    }

    private fun reportFingerprintStore() {
        val storeFileRef = fingerprintFile.asFile.orNull
        if (storeFileRef == null || !storeFileRef.exists()) {
            logger.lifecycle("No state file found. Run antoraConfluencePublish to populate it.")
            return
        }

        val store = ContentFingerprintStore(storeFileRef)
        val entries: Collection<FingerprintEntry> = store.allEntries()

        logger.lifecycle("")
        logger.lifecycle("State file   : ${storeFileRef.absolutePath}")
        logger.lifecycle("Tracked pages: ${entries.size}")

        if (entries.isEmpty()) {
            logger.lifecycle("(no pages tracked yet)")
            return
        }

        logger.lifecycle("")
        logger.lifecycle(buildString {
            appendLine("  ${"Page ID".padEnd(60)} ${"Confluence ID".padEnd(15)} Last Published")
            appendLine("  ${"─".repeat(60)} ${"─".repeat(15)} ${"─".repeat(30)}")
            entries.sortedBy { it.pageId }.forEach { e ->
                appendLine(
                    "  ${e.pageId.take(60).padEnd(60)} ${(e.confluencePageId ?: "—").padEnd(15)} ${e.lastPublishedAt ?: "—"}"
                )
            }
        }.trimEnd())
    }

    private fun reportLastPublish() {
        val reportFileRef = reportFile.asFile.orNull
        if (reportFileRef == null || !reportFileRef.exists()) {
            logger.lifecycle("")
            logger.lifecycle("No publish report found. Run antoraConfluencePublish to generate one.")
            return
        }

        try {
            val mapper = jacksonObjectMapper()
            @Suppress("UNCHECKED_CAST")
            val report = mapper.readValue(reportFileRef, Map::class.java) as Map<String, Any?>

            logger.lifecycle("")
            logger.lifecycle("Last publish report: ${reportFileRef.absolutePath}")
            logger.lifecycle("  Timestamp  : ${report["timestamp"]}")
            logger.lifecycle("  Space key  : ${report["spaceKey"]}")
            logger.lifecycle("  Strategy   : ${report["strategy"]}")
            logger.lifecycle("  Dry run    : ${report["dryRun"]}")

            @Suppress("UNCHECKED_CAST")
            val results = report["results"] as? List<Map<String, Any?>> ?: emptyList()
            val byAction = results.groupBy { it["action"] as? String ?: "UNKNOWN" }
            logger.lifecycle("  Results    : ${byAction.map { "${it.value.size} ${it.key}" }.joinToString(", ")}")
        } catch (e: Exception) {
            logger.warn("Could not read publish report: ${e.message}")
        }
    }
}
