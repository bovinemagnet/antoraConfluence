package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishAction
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [PublishEngine].
 *
 * All tests use [dryRun = true] so no Confluence API calls are made.
 */
class PublishEngineTest {

    @TempDir
    lateinit var tempDir: File

    private val engine = PublishEngine()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createAntoraContent(
        root: File,
        componentName: String = "test-docs",
        version: String = "1.0",
        pages: List<String> = listOf("index.adoc", "getting-started.adoc")
    ) {
        val ymlContent = buildString {
            appendLine("name: $componentName")
            if (version.isNotBlank()) appendLine("version: '$version'")
        }
        File(root, "antora.yml").writeText(ymlContent)
        val pagesDir = File(root, "modules/ROOT/pages").also { it.mkdirs() }
        pages.forEach { page ->
            File(pagesDir, page).writeText("= ${page.removeSuffix(".adoc").replace('-', ' ')}\n\nContent of $page.\n")
        }
    }

    private fun buildDryRunRequest(
        contentDir: File,
        siteKey: String = "test-site",
        forceAll: Boolean = false,
        stateFile: File = File(tempDir, "state.json"),
        reportFile: File = File(tempDir, "report.json")
    ) = PublishRequest(
        contentDir = contentDir,
        siteKey = siteKey,
        confluenceUrl = "https://example.atlassian.net/wiki",
        username = "user@example.com",
        apiToken = "fake-token",
        spaceKey = "DOCS",
        parentPageId = null,
        publishStrategy = PublishStrategy.CREATE_AND_UPDATE,
        orphanStrategy = OrphanStrategy.REPORT,
        hierarchyMode = HierarchyMode.COMPONENT_VERSION_MODULE_PAGE,
        versionMode = VersionMode.HIERARCHY,
        createIndexPages = false,
        strict = false,
        applyLabels = emptyList(),
        dryRun = true,
        forceAll = forceAll,
        uploadImages = true,
        normalizeWhitespaceForDiff = true,
        failOnUnresolvedXref = false,
        stateFile = stateFile,
        reportFile = reportFile
    )

    // -------------------------------------------------------------------------
    // Dry-run: plan generation
    // -------------------------------------------------------------------------

    @Test
    fun `dry run produces CREATE actions for new pages`() {
        createAntoraContent(tempDir)
        val request = buildDryRunRequest(contentDir = tempDir)

        val summary = engine.publish(request)

        assertThat(summary.results).isNotEmpty
        assertThat(summary.results.map { it.action }).containsOnly(PublishAction.CREATE)
        assertThat(summary.dryRun).isTrue()
    }

    @Test
    fun `dry run counts created pages correctly`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc", "getting-started.adoc", "advanced.adoc"))
        val request = buildDryRunRequest(contentDir = tempDir)

        val summary = engine.publish(request)

        assertThat(summary.created).isEqualTo(3)
        assertThat(summary.updated).isEqualTo(0)
        assertThat(summary.skipped).isEqualTo(0)
        assertThat(summary.failed).isEqualTo(0)
    }

    @Test
    fun `dry run returns results for each discovered page`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc", "overview.adoc"))
        val request = buildDryRunRequest(contentDir = tempDir)

        val summary = engine.publish(request)

        assertThat(summary.results).hasSize(2)
    }

    @Test
    fun `dry run page results contain expected pageId format with siteKey`() {
        createAntoraContent(tempDir, componentName = "my-docs", version = "2.0", pages = listOf("index.adoc"))
        val request = buildDryRunRequest(contentDir = tempDir, siteKey = "acme")

        val summary = engine.publish(request)

        assertThat(summary.results).hasSize(1)
        assertThat(summary.results[0].pageId).contains("acme/my-docs/2.0/ROOT/index")
    }

    @Test
    fun `dry run with no pages returns empty summary`() {
        // Create antora.yml but no pages
        File(tempDir, "antora.yml").writeText("name: empty-docs\n")
        File(tempDir, "modules/ROOT/pages").mkdirs()
        val request = buildDryRunRequest(contentDir = tempDir)

        val summary = engine.publish(request)

        assertThat(summary.results).isEmpty()
        assertThat(summary.created).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // forceAll flag
    // -------------------------------------------------------------------------

    @Test
    fun `forceAll flag is reflected in summary`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc"))
        val request = buildDryRunRequest(contentDir = tempDir, forceAll = true)

        val summary = engine.publish(request)

        assertThat(summary.forceAll).isTrue()
    }

    @Test
    fun `forceAll false is reflected in summary`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc"))
        val request = buildDryRunRequest(contentDir = tempDir, forceAll = false)

        val summary = engine.publish(request)

        assertThat(summary.forceAll).isFalse()
    }

    // -------------------------------------------------------------------------
    // Incremental publish (fingerprint-based skip)
    // -------------------------------------------------------------------------

    @Test
    fun `unchanged page is SKIP after first publish when not forceAll`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc"))
        val stateFile = File(tempDir, "state.json")
        val reportFile = File(tempDir, "report.json")
        val request = buildDryRunRequest(contentDir = tempDir, stateFile = stateFile, reportFile = reportFile)

        // Pre-populate fingerprint store as if the page was previously published
        val store = io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore(stateFile)
        val scanner = io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner()
        val pages = scanner.scan(tempDir, siteKey = "test-site")
        pages.forEach { page ->
            store.put(page.pageId, page.sourceFile.readText(), "conf-123", page.suggestedTitle)
        }
        store.save()

        val summary = engine.publish(request)

        // With an existing fingerprint that matches, pages should be SKIP
        assertThat(summary.results.map { it.action }).containsOnly(PublishAction.SKIP)
        assertThat(summary.skipped).isEqualTo(1)
    }

    @Test
    fun `forceAll true republishes even unchanged pages as CREATE or UPDATE in dry run`() {
        createAntoraContent(tempDir, pages = listOf("index.adoc"))
        val stateFile = File(tempDir, "state.json")
        val reportFile = File(tempDir, "report.json")

        // Pre-populate fingerprint store
        val store = io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore(stateFile)
        val scanner = io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner()
        val pages = scanner.scan(tempDir, siteKey = "test-site")
        pages.forEach { page ->
            store.put(page.pageId, page.sourceFile.readText(), "conf-123", page.suggestedTitle)
        }
        store.save()

        val request = buildDryRunRequest(contentDir = tempDir, forceAll = true, stateFile = stateFile, reportFile = reportFile)
        val summary = engine.publish(request)

        // forceAll bypasses fingerprint — should be UPDATE (existing fingerprint found)
        assertThat(summary.results.map { it.action }).doesNotContain(PublishAction.SKIP)
    }

    // -------------------------------------------------------------------------
    // Image handling
    // -------------------------------------------------------------------------

    @Test
    fun `dry run handles content with image references`() {
        val docsDir = File(tempDir, "docs").also { it.mkdirs() }
        File(docsDir, "antora.yml").writeText("name: test-comp\nversion: '1.0'\n")
        val pagesDir = File(docsDir, "modules/ROOT/pages").also { it.mkdirs() }
        File(pagesDir, "index.adoc").writeText("= Index\n\nimage::logo.png[Logo]\n")
        val imagesDir = File(docsDir, "modules/ROOT/images").also { it.mkdirs() }
        File(imagesDir, "logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val request = buildDryRunRequest(contentDir = docsDir)
        val summary = engine.publish(request)
        assertThat(summary.results).isNotEmpty
        assertThat(summary.results.first().action).isEqualTo(PublishAction.CREATE)
    }
}
