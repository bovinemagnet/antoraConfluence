package io.github.bovinemagnet.antoraconfluence.antora

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AntoraContentScannerTest {

    private val scanner = AntoraContentScanner()

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // validate()
    // -------------------------------------------------------------------------

    @Test
    fun `validate throws when contentDir does not exist`() {
        val missing = File(tempDir, "nonexistent")
        assertThatThrownBy { scanner.validate(missing) }
            .isInstanceOf(AntoraStructureException::class.java)
            .hasMessageContaining("does not exist")
    }

    @Test
    fun `validate throws when contentDir has no antora yml`() {
        assertThatThrownBy { scanner.validate(tempDir) }
            .isInstanceOf(AntoraStructureException::class.java)
            .hasMessageContaining("antora.yml")
    }

    @Test
    fun `validate passes for valid Antora structure`() {
        createValidAntoraComponent(tempDir, componentName = "my-docs", moduleName = "ROOT", pages = listOf("index.adoc"))
        // Should not throw
        scanner.validate(tempDir)
    }

    @Test
    fun `validate throws when antora yml is missing name field`() {
        val componentDir = File(tempDir, "component").also { it.mkdirs() }
        File(componentDir, "antora.yml").writeText("version: '1.0'\n")
        assertThatThrownBy { scanner.validate(tempDir) }
            .isInstanceOf(AntoraStructureException::class.java)
            .hasMessageContainingAll("antora.yml", "name")
    }

    // -------------------------------------------------------------------------
    // scan() — without siteKey
    // -------------------------------------------------------------------------

    @Test
    fun `scan returns empty list when no adoc files are present`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", emptyList())
        val pages = scanner.scan(tempDir)
        assertThat(pages).isEmpty()
    }

    @Test
    fun `scan finds pages in ROOT module`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc", "overview.adoc"))
        val pages = scanner.scan(tempDir)
        assertThat(pages).hasSize(2)
        assertThat(pages.map { it.moduleName }).containsOnly("ROOT")
        assertThat(pages.map { it.componentName }).containsOnly("my-docs")
    }

    @Test
    fun `scan finds pages across multiple modules`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc"))
        addModule(tempDir, "my-docs", "admin", listOf("config.adoc", "setup.adoc"))
        val pages = scanner.scan(tempDir)
        assertThat(pages).hasSize(3)
        assertThat(pages.map { it.moduleName }).containsExactlyInAnyOrder("ROOT", "admin", "admin")
    }

    @Test
    fun `scan finds pages across multiple components`() {
        val comp1 = File(tempDir, "comp1").also { it.mkdirs() }
        val comp2 = File(tempDir, "comp2").also { it.mkdirs() }
        createValidAntoraComponent(comp1, "docs-a", "ROOT", listOf("page-a.adoc"))
        createValidAntoraComponent(comp2, "docs-b", "ROOT", listOf("page-b.adoc"))
        val pages = scanner.scan(tempDir)
        assertThat(pages).hasSize(2)
        assertThat(pages.map { it.componentName }).containsExactlyInAnyOrder("docs-a", "docs-b")
    }

    @Test
    fun `scan ignores non adoc files`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc"))
        val pagesDir = File(tempDir, "modules/ROOT/pages")
        File(pagesDir, "README.md").writeText("# readme")
        File(pagesDir, "image.png").writeBytes(byteArrayOf())
        val pages = scanner.scan(tempDir)
        assertThat(pages).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // scan() — page identity with siteKey
    // -------------------------------------------------------------------------

    @Test
    fun `scan populates correct pageId with siteKey and version`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("getting-started.adoc"), version = "1.0")
        val pages = scanner.scan(tempDir, siteKey = "acme-site")
        assertThat(pages).hasSize(1)
        assertThat(pages[0].pageId).isEqualTo("acme-site/my-docs/1.0/ROOT/getting-started")
    }

    @Test
    fun `scan pageId omits version when blank`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc"), version = "")
        val pages = scanner.scan(tempDir, siteKey = "my-site")
        assertThat(pages[0].pageId).isEqualTo("my-site/my-docs/ROOT/index")
    }

    @Test
    fun `scan pageId omits siteKey when blank`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc"), version = "1.0")
        val pages = scanner.scan(tempDir, siteKey = "")
        assertThat(pages[0].pageId).isEqualTo("my-docs/1.0/ROOT/index")
    }

    @Test
    fun `scan siteKey is stored on AntoraPage`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("index.adoc"))
        val pages = scanner.scan(tempDir, siteKey = "test-site")
        assertThat(pages[0].siteKey).isEqualTo("test-site")
    }

    @Test
    fun `suggestedTitle converts hyphens and capitalises words`() {
        createValidAntoraComponent(tempDir, "my-docs", "ROOT", listOf("getting-started.adoc"))
        val pages = scanner.scan(tempDir)
        assertThat(pages[0].suggestedTitle).isEqualTo("Getting Started")
    }

    // -------------------------------------------------------------------------
    // parseAntoraYml()
    // -------------------------------------------------------------------------

    @Test
    fun `parseAntoraYml reads name version and title`() {
        val ymlFile = File(tempDir, "antora.yml")
        ymlFile.writeText("""
            name: my-component
            version: '2.3'
            title: My Component
        """.trimIndent())
        val descriptor = scanner.parseAntoraYml(ymlFile)
        assertThat(descriptor.name).isEqualTo("my-component")
        assertThat(descriptor.version).isEqualTo("2.3")
        assertThat(descriptor.title).isEqualTo("My Component")
    }

    @Test
    fun `parseAntoraYml handles missing optional fields gracefully`() {
        val ymlFile = File(tempDir, "antora.yml")
        ymlFile.writeText("name: minimal\n")
        val descriptor = scanner.parseAntoraYml(ymlFile)
        assertThat(descriptor.name).isEqualTo("minimal")
        assertThat(descriptor.version).isBlank()
        assertThat(descriptor.title).isBlank()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createValidAntoraComponent(
        root: File,
        componentName: String,
        moduleName: String,
        pages: List<String>,
        version: String = ""
    ) {
        val ymlContent = buildString {
            appendLine("name: $componentName")
            if (version.isNotBlank()) appendLine("version: '$version'")
        }
        File(root, "antora.yml").writeText(ymlContent)
        addModule(root, componentName, moduleName, pages)
    }

    private fun addModule(root: File, componentName: String, moduleName: String, pages: List<String>) {
        val pagesDir = File(root, "modules/$moduleName/pages").also { it.mkdirs() }
        pages.forEach { page ->
            File(pagesDir, page).writeText("= ${page.removeSuffix(".adoc")}\n\nContent of $page.\n")
        }
    }
}
