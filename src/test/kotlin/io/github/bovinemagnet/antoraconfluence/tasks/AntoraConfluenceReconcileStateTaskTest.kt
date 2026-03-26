package io.github.bovinemagnet.antoraconfluence.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AntoraConfluenceReconcileStateTaskTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `reconcileState task queries Confluence and writes state file`() {
        // Enqueue mock responses before writing build file (URL must be known first)
        val baseUrl = mockWebServer.url("/wiki").toString()

        // 1. GET /wiki/api/v2/spaces?keys=DOCS&limit=1
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[{"id":"space-1","key":"DOCS","name":"Documentation"}]}""")
        )

        // 2. GET /wiki/api/v2/pages?space-id=space-1&label=managed-by-antora-confluence&limit=250
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[{"id":"page-1","title":"Index","spaceId":"space-1","status":"current"}]}""")
        )

        // 3. GET /wiki/api/v2/pages/page-1/properties/antora-confluence-key
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"p1","key":"antora-confluence-key","value":"site/comp/1.0/ROOT/index"}""")
        )

        // 4. GET /wiki/api/v2/pages/page-1/properties/antora-confluence-fingerprint
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"p2","key":"antora-confluence-fingerprint","value":"abc123"}""")
        )

        // 5. GET /wiki/api/v2/pages/page-1/properties/antora-confluence-source
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"p3","key":"antora-confluence-source","value":"docs/pages/index.adoc"}""")
        )

        // Write build file using the mock server URL
        val stateFilePath = projectDir.resolve("build/antora-confluence/state.json").absolutePath
            .replace("\\", "\\\\")

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }

            antoraConfluence {
                confluence {
                    baseUrl.set("$baseUrl")
                    username.set("test-user")
                    apiToken.set("test-token")
                    spaceKey.set("DOCS")
                }
                state {
                    file.set(layout.buildDirectory.file("antora-confluence/state.json"))
                }
            }
            """.trimIndent()
        )

        projectDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "test-reconcile-project""""
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("antoraConfluenceReconcileState", "--stacktrace")
            .withPluginClasspath()
            .build()

        // Task must succeed
        assertThat(result.task(":antoraConfluenceReconcileState")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)

        // State file must exist
        val stateFile = projectDir.resolve("build/antora-confluence/state.json")
        assertThat(stateFile).exists()

        // State file must contain the reconciled page
        val stateContent = stateFile.readText()
        assertThat(stateContent).contains("site/comp/1.0/ROOT/index")
        assertThat(stateContent).contains("page-1")
        assertThat(stateContent).contains("abc123")
        assertThat(stateContent).contains("docs/pages/index.adoc")
        assertThat(stateContent).contains("Index")

        // Verify reconciliation output log
        assertThat(result.output).contains("Found 1 managed page(s) in Confluence")
        assertThat(result.output).contains("Reconciled: site/comp/1.0/ROOT/index -> Confluence page page-1")
        assertThat(result.output).contains("Reconciliation complete. 1 page(s) reconciled.")
    }

    @Test
    fun `reconcileState task reports zero pages when no managed pages found`() {
        val baseUrl = mockWebServer.url("/wiki").toString()

        // 1. GET space
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[{"id":"space-1","key":"DOCS","name":"Documentation"}]}""")
        )

        // 2. GET pages with label — empty result
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"results":[]}""")
        )

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }

            antoraConfluence {
                confluence {
                    baseUrl.set("$baseUrl")
                    username.set("test-user")
                    apiToken.set("test-token")
                    spaceKey.set("DOCS")
                }
                state {
                    file.set(layout.buildDirectory.file("antora-confluence/state.json"))
                }
            }
            """.trimIndent()
        )

        projectDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "test-reconcile-empty-project""""
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("antoraConfluenceReconcileState", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertThat(result.task(":antoraConfluenceReconcileState")?.outcome)
            .isEqualTo(TaskOutcome.SUCCESS)

        assertThat(result.output).contains("Found 0 managed page(s) in Confluence")
        assertThat(result.output).contains("Reconciliation complete. 0 page(s) reconciled.")
    }
}
