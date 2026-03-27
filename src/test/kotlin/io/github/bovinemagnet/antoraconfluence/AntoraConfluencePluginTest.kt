package io.github.bovinemagnet.antoraconfluence

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests for [AntoraConfluencePlugin] using Gradle TestKit.
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
        assertThat(result.output).containsIgnoringCase("documentation")
    }

    @Test
    fun `plugin registers all six tasks`() {
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
            .contains("antoraConfluenceFullPublish")
            .contains("antoraConfluenceReconcileState")
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

    @Test
    fun `plugin tasks appear in documentation group`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
        """)
        val result = runner("tasks").build()
        assertThat(result.output).containsIgnoringCase("documentation")
    }

    // -------------------------------------------------------------------------
    // Nested extension DSL
    // -------------------------------------------------------------------------

    @Test
    fun `nested confluence block is accessible`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                confluence {
                    baseUrl.set("https://example.atlassian.net/wiki")
                    spaceKey.set("DOCS")
                }
                source {
                    siteKey.set("my-site")
                }
            }
            tasks.register("checkNestedDsl") {
                doLast {
                    val ext = project.extensions.getByType(
                        io.github.bovinemagnet.antoraconfluence.extension.AntoraConfluenceExtension::class.java
                    )
                    println("url=" + ext.confluence.baseUrl.get())
                    println("site=" + ext.source.siteKey.get())
                }
            }
        """)
        val result = runner("checkNestedDsl").build()
        assertThat(result.output)
            .contains("url=https://example.atlassian.net/wiki")
            .contains("site=my-site")
    }

    @Test
    fun `publish block defaults are applied`() {
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            tasks.register("checkDefaults") {
                doLast {
                    val ext = project.extensions.getByType(
                        io.github.bovinemagnet.antoraconfluence.extension.AntoraConfluenceExtension::class.java
                    )
                    println("dryRun=" + ext.publish.dryRun.get())
                    println("strict=" + ext.publish.strict.get())
                    println("hierarchy=" + ext.publish.hierarchy.get())
                    println("orphan=" + ext.publish.orphanStrategy.get())
                }
            }
        """)
        val result = runner("checkDefaults").build()
        assertThat(result.output)
            .contains("dryRun=false")
            .contains("strict=false")
            .contains("hierarchy=COMPONENT_VERSION_MODULE_PAGE")
            .contains("orphan=REPORT")
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
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                }
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
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                }
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
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                }
            }
        """)
        val result = runner("antoraConfluenceValidate").buildAndFail()
        assertThat(result.task(":antoraConfluenceValidate")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @Test
    fun `validate task emits warning when baseUrl is not set`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                    // confluence.baseUrl intentionally omitted
                }
            }
        """)
        val result = runner("antoraConfluenceValidate").build()
        assertThat(result.output).contains("baseUrl")
    }

    @Test
    fun `validate task warns when credentials are missing with confluenceUrl set`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                }
                confluence {
                    baseUrl.set("https://example.atlassian.net/wiki")
                    spaceKey.set("DOCS")
                }
            }
        """)
        val result = runner("antoraConfluenceValidate").build()
        assertThat(result.output).contains("username")
        assertThat(result.output).contains("apiToken")
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
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                    siteKey.set("my-site")
                }
            }
        """)
        val result = runner("antoraConfluencePlan").build()
        assertThat(result.task(":antoraConfluencePlan")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).containsAnyOf("CREATE", "Plan summary")
    }

    @Test
    fun `plan task page ids include siteKey`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                    siteKey.set("acme-docs")
                }
            }
        """)
        val result = runner("antoraConfluencePlan").build()
        // page IDs in plan output should be prefixed with siteKey
        assertThat(result.output).contains("acme-docs/")
    }

    // -------------------------------------------------------------------------
    // Antora plugin integration
    // -------------------------------------------------------------------------

    @Test
    fun `plan task shows hierarchy-aware page structure`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                    siteKey.set("test-site")
                }
            }
        """)
        val result = runner("antoraConfluencePlan").build()
        assertThat(result.task(":antoraConfluencePlan")?.outcome)
            .isEqualTo(org.gradle.testkit.runner.TaskOutcome.SUCCESS)
        assertThat(result.output).contains("test-site/test-docs")
    }

    @Test
    fun `plugin works standalone without org antora plugin`() {
        createValidAntoraContent()
        writeBuildFile("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                source {
                    antoraRoot.set(layout.projectDirectory.dir("docs"))
                }
            }
        """)
        val result = runner("antoraConfluenceValidate").build()
        assertThat(result.task(":antoraConfluenceValidate")?.outcome)
            .isEqualTo(org.gradle.testkit.runner.TaskOutcome.SUCCESS)
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
