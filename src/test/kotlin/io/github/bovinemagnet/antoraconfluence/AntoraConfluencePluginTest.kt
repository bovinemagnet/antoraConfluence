package io.github.bovinemagnet.antoraconfluence

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests for [AntoraConfluencePlugin] using Gradle TestKit.
 *
 * These tests verify that the plugin applies cleanly, registers the expected tasks and extension,
 * and that the validate task behaves correctly with a valid Antora content tree.
 */
class AntoraConfluencePluginTest {

    @TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { File(projectDir, "build.gradle.kts") }
    private val settingsFile by lazy { File(projectDir, "settings.gradle.kts") }

    // -------------------------------------------------------------------------
    // Plugin application
    // -------------------------------------------------------------------------

    @Test
    fun `plugin applies without error`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
        """)
        val result = runner("tasks").build()
        assertThat(result.output).contains("Antora Confluence")
    }

    @Test
    fun `plugin registers all four tasks`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
        """)
        val result = runner("tasks", "--all").build()
        assertThat(result.output)
            .contains("antoraConfluenceValidate")
            .contains("antoraConfluencePlan")
            .contains("antoraConfluencePublish")
            .contains("antoraConfluenceReport")
    }

    @Test
    fun `plugin registers antoraConfluence extension`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            tasks.register("checkExtension") {
                doLast {
                    println("extension ok")
                    println(project.extensions.getByName("antoraConfluence"))
                }
            }
        """)
        val result = runner("checkExtension").build()
        assertThat(result.output).contains("extension ok")
    }

    // -------------------------------------------------------------------------
    // antoraConfluenceValidate task
    // -------------------------------------------------------------------------

    @Test
    fun `validate task passes with valid Antora structure`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                contentDir = layout.projectDirectory.dir("docs")
            }
        """)
        val result = runner("antoraConfluenceValidate").build()
        assertThat(result.task(":antoraConfluenceValidate")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Validation passed")
    }

    @Test
    fun `validate task fails when docs directory is missing`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                contentDir = layout.projectDirectory.dir("docs")
            }
        """)
        val result = runner("antoraConfluenceValidate").buildAndFail()
        assertThat(result.task(":antoraConfluenceValidate")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `validate task fails when no antora yml present`() {
        File(projectDir, "docs").mkdirs()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                contentDir = layout.projectDirectory.dir("docs")
            }
        """)
        val result = runner("antoraConfluenceValidate").buildAndFail()
        assertThat(result.task(":antoraConfluenceValidate")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `validate task emits warning when confluenceUrl is not set`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                contentDir = layout.projectDirectory.dir("docs")
                // confluenceUrl intentionally omitted
            }
        """)
        val result = runner("antoraConfluenceValidate").build()
        assertThat(result.output).contains("confluenceUrl")
    }

    // -------------------------------------------------------------------------
    // antoraConfluencePlan task
    // -------------------------------------------------------------------------

    @Test
    fun `plan task runs and shows page plan without Confluence credentials`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                contentDir = layout.projectDirectory.dir("docs")
            }
        """)
        val result = runner("antoraConfluencePlan").build()
        assertThat(result.task(":antoraConfluencePlan")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).containsAnyOf("CREATE", "Plan summary")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun writeBuildFile(content: String) {
        settingsFile.writeText("rootProject.name = \"test-project\"\n")
        buildFile.writeText(content.trimIndent())
    }

    private fun createValidAntoraContent() {
        val docsDir = File(projectDir, "docs").also { it.mkdirs() }
        File(docsDir, "antora.yml").writeText("name: test-docs\nversion: '1.0'\n")
        val pagesDir = File(docsDir, "modules/ROOT/pages").also { it.mkdirs() }
        File(pagesDir, "index.adoc").writeText("= Index\n\nWelcome to the docs.\n")
        File(pagesDir, "getting-started.adoc").writeText("= Getting Started\n\nGet started here.\n")
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(*args, "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
}
