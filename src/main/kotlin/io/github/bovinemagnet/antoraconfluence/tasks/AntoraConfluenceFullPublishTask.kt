package io.github.bovinemagnet.antoraconfluence.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.engine.PublishEngine
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishRequest
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishSummary
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

        val request = PublishRequest(
            contentDir = contentDir.get().asFile,
            siteKey = siteKey.orNull?.trim() ?: "",
            confluenceUrl = confluenceUrl.get(),
            username = username.get(),
            apiToken = apiToken.get(),
            spaceKey = spaceKey.get(),
            parentPageId = parentPageId.orNull,
            publishStrategy = PublishStrategy.CREATE_AND_UPDATE,
            orphanStrategy = OrphanStrategy.REPORT,
            hierarchyMode = HierarchyMode.COMPONENT_VERSION_MODULE_PAGE,
            versionMode = VersionMode.HIERARCHY,
            createIndexPages = false,
            strict = strict.get(),
            applyLabels = applyLabels.getOrElse(emptyList()),
            dryRun = dryRun.get(),
            forceAll = true,
            uploadImages = true,
            normalizeWhitespaceForDiff = true,
            failOnUnresolvedXref = false,
            stateFile = fingerprintFile.get().asFile,
            reportFile = reportFile.get().asFile
        )

        val summary = try {
            PublishEngine().publish(request)
        } catch (e: IllegalStateException) {
            throw GradleException(e.message ?: "Full publish failed", e)
        }

        writeReport(summary)
        logSummary(summary)

        if (strict.get() && summary.failed > 0) {
            throw GradleException(
                "${summary.failed} page(s) failed to publish and strict mode is enabled."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Reporting and logging
    // -------------------------------------------------------------------------

    private fun logSummary(summary: PublishSummary) {
        val prefix = if (summary.dryRun) "[DRY RUN] " else ""
        logger.lifecycle("")
        logger.lifecycle(
            "${prefix}Full publish complete: ${summary.created} created, ${summary.updated} updated, ${summary.failed} failed."
        )
        if (summary.failed > 0) {
            logger.error("${summary.failed} page(s) failed to publish. Check the report at ${reportFile.get().asFile.absolutePath}")
        }
    }

    private fun writeReport(summary: PublishSummary) {
        val report = mapOf(
            "timestamp" to Instant.now().toString(),
            "dryRun" to summary.dryRun,
            "type" to "FULL_PUBLISH",
            "spaceKey" to spaceKey.get(),
            "results" to summary.results.map { r ->
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
