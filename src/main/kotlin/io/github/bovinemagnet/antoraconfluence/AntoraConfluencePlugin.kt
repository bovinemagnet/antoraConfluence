package io.github.bovinemagnet.antoraconfluence

import io.github.bovinemagnet.antoraconfluence.extension.AntoraConfluenceExtension
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluencePlanTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluencePublishTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceReportTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceValidateTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that publishes Antora-structured AsciiDoc documentation to Atlassian Confluence.
 *
 * Registers the `antoraConfluence` extension and four lifecycle tasks:
 * - `antoraConfluenceValidate` – validates configuration and Antora content structure
 * - `antoraConfluencePlan`     – dry-run showing pages that would be created or updated
 * - `antoraConfluencePublish`  – publishes content to Confluence with incremental fingerprinting
 * - `antoraConfluenceReport`   – reports on the current publish state from the fingerprint store
 */
class AntoraConfluencePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME,
            AntoraConfluenceExtension::class.java
        )

        extension.contentDir.convention(project.layout.projectDirectory.dir("docs"))
        extension.publishStrategy.convention(PublishStrategy.CREATE_AND_UPDATE)
        extension.dryRun.convention(false)

        val validateTask = project.tasks.register(TASK_VALIDATE, AntoraConfluenceValidateTask::class.java)
        validateTask.configure {
            group = TASK_GROUP
            description = "Validates the Antora content structure and Confluence configuration."
            contentDir.set(extension.contentDir)
            confluenceUrl.set(extension.confluenceUrl)
            spaceKey.set(extension.spaceKey)
        }

        val planTask = project.tasks.register(TASK_PLAN, AntoraConfluencePlanTask::class.java)
        planTask.configure {
            group = TASK_GROUP
            description = "Shows pages that would be created or updated without making changes."
            contentDir.set(extension.contentDir)
            confluenceUrl.set(extension.confluenceUrl)
            username.set(extension.username)
            apiToken.set(extension.apiToken)
            spaceKey.set(extension.spaceKey)
            parentPageTitle.set(extension.parentPageTitle)
            fingerprintFile.set(
                project.layout.buildDirectory.file("antora-confluence/fingerprints.json")
            )
            dependsOn(validateTask)
        }

        val publishTask = project.tasks.register(TASK_PUBLISH, AntoraConfluencePublishTask::class.java)
        publishTask.configure {
            group = TASK_GROUP
            description = "Publishes Antora AsciiDoc content to Confluence."
            contentDir.set(extension.contentDir)
            confluenceUrl.set(extension.confluenceUrl)
            username.set(extension.username)
            apiToken.set(extension.apiToken)
            spaceKey.set(extension.spaceKey)
            parentPageTitle.set(extension.parentPageTitle)
            publishStrategy.set(extension.publishStrategy)
            dryRun.set(extension.dryRun)
            fingerprintFile.set(
                project.layout.buildDirectory.file("antora-confluence/fingerprints.json")
            )
            reportFile.set(
                project.layout.buildDirectory.file("antora-confluence/publish-report.json")
            )
            dependsOn(validateTask)
        }

        project.tasks.register(TASK_REPORT, AntoraConfluenceReportTask::class.java).configure {
            group = TASK_GROUP
            description = "Reports on the current Confluence publish state."
            fingerprintFile.set(
                project.layout.buildDirectory.file("antora-confluence/fingerprints.json")
            )
            reportFile.set(
                project.layout.buildDirectory.file("antora-confluence/publish-report.json")
            )
            dependsOn(planTask)
        }
    }

    companion object {
        const val EXTENSION_NAME = "antoraConfluence"
        const val TASK_GROUP = "Antora Confluence"
        const val TASK_VALIDATE = "antoraConfluenceValidate"
        const val TASK_PLAN = "antoraConfluencePlan"
        const val TASK_PUBLISH = "antoraConfluencePublish"
        const val TASK_REPORT = "antoraConfluenceReport"
    }
}
