package io.github.bovinemagnet.antoraconfluence.tasks

import io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner
import io.github.bovinemagnet.antoraconfluence.antora.AntoraStructureException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Validates the Antora content directory structure and required Confluence configuration values.
 *
 * This task is a prerequisite for [AntoraConfluencePlanTask] and [AntoraConfluencePublishTask].
 * It does **not** make any network calls; it only inspects the local filesystem and the extension
 * properties that have been set.
 */
abstract class AntoraConfluenceValidateTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val confluenceUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val spaceKey: Property<String>

    @get:Input
    @get:Optional
    abstract val credentialsPresent: Property<Boolean>

    @TaskAction
    fun validate() {
        val contentDirFile = contentDir.get().asFile

        logger.lifecycle("Validating Antora content directory: ${contentDirFile.absolutePath}")

        // 1. Validate Antora content structure
        val scanner = AntoraContentScanner()
        try {
            scanner.validate(contentDirFile)
        } catch (e: AntoraStructureException) {
            throw GradleException("Antora content validation failed: ${e.message}", e)
        }

        val pages = scanner.scan(contentDirFile)
        logger.lifecycle(
            "Found ${pages.size} Antora page(s) across " +
                "${pages.map { it.componentName }.distinct().size} component(s)."
        )

        // 2. Warn about missing Confluence settings (not fatal – may be provided at runtime)
        if (!confluenceUrl.isPresent || confluenceUrl.get().isBlank()) {
            logger.warn("antoraConfluence.confluence.baseUrl is not set. Tasks that call Confluence will fail.")
        }
        if (!spaceKey.isPresent || spaceKey.get().isBlank()) {
            logger.warn("antoraConfluence.confluence.spaceKey is not set. Tasks that call Confluence will fail.")
        }
        if (confluenceUrl.isPresent && confluenceUrl.get().isNotBlank()) {
            if (!credentialsPresent.getOrElse(false)) {
                logger.warn("WARNING: confluence.baseUrl is set but username and/or apiToken are missing.")
            }
        }

        logger.lifecycle("Validation passed.")
    }
}
