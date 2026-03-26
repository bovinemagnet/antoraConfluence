package io.github.bovinemagnet.antoraconfluence

import io.github.bovinemagnet.antoraconfluence.engine.AntoraPluginIntegration
import io.github.bovinemagnet.antoraconfluence.extension.AntoraConfluenceExtension
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceFullPublishTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluencePlanTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluencePublishTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceReconcileStateTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceReportTask
import io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceValidateTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that publishes Antora-structured AsciiDoc documentation to Atlassian Confluence.
 *
 * Registers the `antoraConfluence` extension and the following lifecycle tasks:
 *
 * | Task | Purpose |
 * |------|---------|
 * | `antoraConfluenceValidate`       | Validates config and Antora content structure |
 * | `antoraConfluencePlan`           | Dry-run showing pages that would be created or updated |
 * | `antoraConfluencePublish`        | Incremental publish (only changed pages) |
 * | `antoraConfluenceFullPublish`    | Full publish (all pages regardless of fingerprint) |
 * | `antoraConfluenceReconcileState` | Rebuilds local state from remote Confluence metadata |
 * | `antoraConfluenceReport`         | Reports on current publish state |
 */
class AntoraConfluencePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME,
            AntoraConfluenceExtension::class.java
        )

        // Apply defaults
        extension.source.antoraRoot.convention(project.layout.projectDirectory.dir("docs"))
        extension.publish.hierarchy.convention(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)
        extension.publish.versionMode.convention(VersionMode.HIERARCHY)
        extension.publish.createIndexPages.convention(false)
        extension.publish.strict.convention(false)
        extension.publish.orphanStrategy.convention(OrphanStrategy.REPORT)
        extension.publish.publishStrategy.convention(PublishStrategy.CREATE_AND_UPDATE)
        extension.publish.dryRun.convention(false)
        extension.render.uploadImages.convention(true)
        extension.render.normalizeWhitespaceForDiff.convention(true)
        extension.render.failOnUnresolvedXref.convention(false)
        extension.state.rebuildFromRemoteOnMissing.convention(true)
        extension.state.file.convention(
            project.layout.buildDirectory.file("antora-confluence/state.json")
        )
        extension.reports.jsonReportFile.convention(
            project.layout.buildDirectory.file("antora-confluence/publish-report.json")
        )
        extension.reports.planReportFile.convention(
            project.layout.buildDirectory.file("antora-confluence/plan-report.json")
        )

        // Set default for dependsOnAntoraTask
        extension.source.dependsOnAntoraTask.convention(false)

        // Optional Antora plugin integration
        AntoraPluginIntegration().configure(project)

        // Register tasks (lazily)
        val credentialsPresentProvider = extension.confluence.username.zip(extension.confluence.apiToken) { u, t ->
            u.isNotBlank() && t.isNotBlank()
        }.orElse(false)

        val validateTask = project.tasks.register(TASK_VALIDATE, AntoraConfluenceValidateTask::class.java)
        validateTask.configure {
            group = TASK_GROUP
            description = "Validates the Antora content structure and Confluence configuration."
            contentDir.set(extension.source.antoraRoot)
            confluenceUrl.set(extension.confluence.baseUrl)
            spaceKey.set(extension.confluence.spaceKey)
            credentialsPresent.set(credentialsPresentProvider)
        }

        val planTask = project.tasks.register(TASK_PLAN, AntoraConfluencePlanTask::class.java)
        planTask.configure {
            group = TASK_GROUP
            description = "Shows pages that would be created or updated without making changes."
            contentDir.set(extension.source.antoraRoot)
            siteKey.set(extension.source.siteKey)
            confluenceUrl.set(extension.confluence.baseUrl)
            username.set(extension.confluence.username)
            apiToken.set(extension.confluence.apiToken)
            spaceKey.set(extension.confluence.spaceKey)
            parentPageId.set(extension.confluence.parentPageId)
            credentialsPresent.set(credentialsPresentProvider)
            fingerprintFile.set(extension.state.file)
            planReportFile.set(extension.reports.planReportFile)
            dependsOn(validateTask)
        }

        val publishTask = project.tasks.register(TASK_PUBLISH, AntoraConfluencePublishTask::class.java)
        publishTask.configure {
            group = TASK_GROUP
            description = "Publishes changed Antora AsciiDoc pages to Confluence (incremental)."
            contentDir.set(extension.source.antoraRoot)
            siteKey.set(extension.source.siteKey)
            confluenceUrl.set(extension.confluence.baseUrl)
            username.set(extension.confluence.username)
            apiToken.set(extension.confluence.apiToken)
            spaceKey.set(extension.confluence.spaceKey)
            parentPageId.set(extension.confluence.parentPageId)
            publishStrategy.set(extension.publish.publishStrategy)
            orphanStrategy.set(extension.publish.orphanStrategy)
            strict.set(extension.publish.strict)
            applyLabels.set(extension.publish.applyLabels)
            dryRun.set(extension.publish.dryRun)
            credentialsPresent.set(credentialsPresentProvider)
            fingerprintFile.set(extension.state.file)
            reportFile.set(extension.reports.jsonReportFile)
            dependsOn(validateTask)
        }

        val fullPublishTask = project.tasks.register(TASK_FULL_PUBLISH, AntoraConfluenceFullPublishTask::class.java)
        fullPublishTask.configure {
            group = TASK_GROUP
            description = "Republishes all Antora pages to Confluence regardless of content fingerprint."
            contentDir.set(extension.source.antoraRoot)
            siteKey.set(extension.source.siteKey)
            confluenceUrl.set(extension.confluence.baseUrl)
            username.set(extension.confluence.username)
            apiToken.set(extension.confluence.apiToken)
            spaceKey.set(extension.confluence.spaceKey)
            parentPageId.set(extension.confluence.parentPageId)
            strict.set(extension.publish.strict)
            applyLabels.set(extension.publish.applyLabels)
            dryRun.set(extension.publish.dryRun)
            credentialsPresent.set(credentialsPresentProvider)
            fingerprintFile.set(extension.state.file)
            reportFile.set(extension.reports.jsonReportFile)
            dependsOn(validateTask)
        }

        val reconcileTask = project.tasks.register(TASK_RECONCILE_STATE, AntoraConfluenceReconcileStateTask::class.java)
        reconcileTask.configure {
            group = TASK_GROUP
            description = "Rebuilds the local state file from Confluence page properties metadata."
            confluenceUrl.set(extension.confluence.baseUrl)
            username.set(extension.confluence.username)
            apiToken.set(extension.confluence.apiToken)
            spaceKey.set(extension.confluence.spaceKey)
            credentialsPresent.set(credentialsPresentProvider)
            fingerprintFile.set(extension.state.file)
        }

        project.tasks.register(TASK_REPORT, AntoraConfluenceReportTask::class.java).configure {
            group = TASK_GROUP
            description = "Reports on the current Confluence publish state."
            fingerprintFile.set(extension.state.file)
            reportFile.set(extension.reports.jsonReportFile)
        }
    }

    companion object {
        const val EXTENSION_NAME = "antoraConfluence"
        const val TASK_GROUP = "documentation"
        const val TASK_VALIDATE = "antoraConfluenceValidate"
        const val TASK_PLAN = "antoraConfluencePlan"
        const val TASK_PUBLISH = "antoraConfluencePublish"
        const val TASK_FULL_PUBLISH = "antoraConfluenceFullPublish"
        const val TASK_RECONCILE_STATE = "antoraConfluenceReconcileState"
        const val TASK_REPORT = "antoraConfluenceReport"
    }
}
