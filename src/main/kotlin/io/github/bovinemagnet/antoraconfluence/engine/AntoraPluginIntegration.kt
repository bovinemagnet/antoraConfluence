package io.github.bovinemagnet.antoraconfluence.engine

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Optional integration with the Gradle Antora plugin (org.antora).
 * Detects the plugin, parses the playbook, and wires task dependencies.
 */
class AntoraPluginIntegration {

    private val logger = Logging.getLogger(AntoraPluginIntegration::class.java)

    fun configure(project: Project) {
        project.plugins.withId("org.antora") {
            logger.info("Detected org.antora plugin — enabling integration")
            tryWireTaskDependency(project)
        }
    }

    private fun tryWireTaskDependency(project: Project) {
        // Check if dependsOnAntoraTask is enabled
        val ext = project.extensions.findByName("antoraConfluence") ?: return
        try {
            val sourceSpec = ext.javaClass.getMethod("getSource").invoke(ext)
            val dependsOnProp = sourceSpec.javaClass.getMethod("getDependsOnAntoraTask").invoke(sourceSpec)
            val getOrElse = dependsOnProp.javaClass.getMethod("getOrElse", Any::class.java)
            val shouldDepend = getOrElse.invoke(dependsOnProp, false) as Boolean

            if (shouldDepend) {
                project.tasks.named("antoraConfluenceValidate", object : Action<Task> {
                    override fun execute(task: Task) {
                        task.dependsOn("antora")
                    }
                })
                logger.info("Wired antoraConfluenceValidate to depend on antora task")
            }
        } catch (e: Exception) {
            logger.info("Could not wire Antora task dependency: {}", e.message)
        }
    }

    /**
     * Parses content sources from an Antora playbook YAML file.
     * Returns a list of content source URLs/paths.
     */
    internal fun parsePlaybookContentSources(playbookFile: File): List<String> {
        return try {
            val mapper = YAMLMapper()
            val tree: Map<String, Any?> = mapper.readValue(
                playbookFile,
                object : TypeReference<Map<String, Any?>>() {}
            )
            @Suppress("UNCHECKED_CAST")
            val content = tree["content"] as? Map<String, Any?> ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            val sources = content["sources"] as? List<Map<String, Any?>> ?: return emptyList()
            sources.mapNotNull { it["url"]?.toString() }
        } catch (e: Exception) {
            logger.info("Could not parse playbook YAML: {}", e.message)
            emptyList()
        }
    }
}
