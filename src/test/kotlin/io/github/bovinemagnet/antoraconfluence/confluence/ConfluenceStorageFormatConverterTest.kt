package io.github.bovinemagnet.antoraconfluence.confluence

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for [ConfluenceStorageFormatConverter].
 */
class ConfluenceStorageFormatConverterTest {

    private val converter = ConfluenceStorageFormatConverter()
    private val emptyContext = PageContext()

    // -------------------------------------------------------------------------
    // Code blocks
    // -------------------------------------------------------------------------

    @Test
    fun `code block with language is converted to structured macro`() {
        val html = """<pre><code class="language-java">System.out.println("hello");</code></pre>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="code"""")
        assertThat(result).contains("""ac:name="language"""")
        assertThat(result).contains("java")
        assertThat(result).contains("ac:plain-text-body")
        assertThat(result).contains("""System.out.println("hello");""")
    }

    @Test
    fun `code block without language class is still converted`() {
        val html = """<pre><code>plain code here</code></pre>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="code"""")
        assertThat(result).contains("plain code here")
        assertThat(result).doesNotContain("""ac:name="language"""")
    }

    @Test
    fun `code block with kotlin language is converted correctly`() {
        val html = """<pre><code class="language-kotlin">val x = 42</code></pre>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="code"""")
        assertThat(result).contains("kotlin")
        assertThat(result).contains("val x = 42")
    }

    // -------------------------------------------------------------------------
    // Admonition blocks
    // -------------------------------------------------------------------------

    @Test
    fun `note admonition is converted to info macro`() {
        val html = admonitionHtml("note", "This is a note.")
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="info"""")
        assertThat(result).contains("ac:rich-text-body")
        assertThat(result).contains("This is a note.")
    }

    @Test
    fun `warning admonition is converted to warning macro`() {
        val html = admonitionHtml("warning", "This is a warning.")
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="warning"""")
        assertThat(result).contains("This is a warning.")
    }

    @Test
    fun `tip admonition is converted to tip macro`() {
        val html = admonitionHtml("tip", "This is a tip.")
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="tip"""")
        assertThat(result).contains("This is a tip.")
    }

    @Test
    fun `caution admonition is converted to note macro`() {
        // Confluence has no caution type — maps to note
        val html = admonitionHtml("caution", "This is a caution.")
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="note"""")
        assertThat(result).contains("This is a caution.")
    }

    @Test
    fun `important admonition is converted to warning macro`() {
        val html = admonitionHtml("important", "This is important.")
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("""ac:name="warning"""")
        assertThat(result).contains("This is important.")
    }

    // -------------------------------------------------------------------------
    // Local images
    // -------------------------------------------------------------------------

    @Test
    fun `local image in manifest is converted to ac-image attachment`() {
        val imageFile = File("/tmp/diagram.png")
        val context = PageContext(imageManifest = mapOf("diagram.png" to imageFile))
        val html = """<img src="diagram.png" alt="A diagram"/>"""
        val result = converter.convert(html, context)

        assertThat(result).contains("ac:image")
        assertThat(result).contains("ri:attachment")
        assertThat(result).contains("""ri:filename="diagram.png"""")
    }

    @Test
    fun `external image is left as-is`() {
        val html = """<img src="https://example.com/image.png" alt="external"/>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("https://example.com/image.png")
        assertThat(result).doesNotContain("ac:image")
    }

    @Test
    fun `unresolved local image logs warning in non-strict mode`() {
        val context = PageContext(strict = false)
        val html = """<img src="missing.png" alt="missing"/>"""
        // Should not throw in non-strict mode
        val result = converter.convert(html, context)
        // Image remains (not converted) — no exception
        assertThat(result).isNotNull()
    }

    @Test
    fun `unresolved local image throws in strict mode`() {
        val context = PageContext(strict = true)
        val html = """<img src="missing.png" alt="missing"/>"""

        assertThatThrownBy { converter.convert(html, context) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("missing.png")
    }

    // -------------------------------------------------------------------------
    // Links
    // -------------------------------------------------------------------------

    @Test
    fun `internal xref link in resolvedXrefs is converted to ac-link`() {
        val context = PageContext(resolvedXrefs = mapOf("getting-started.html" to "Getting Started"))
        val html = """<a href="getting-started.html">Getting Started</a>"""
        val result = converter.convert(html, context)

        assertThat(result).contains("ac:link")
        assertThat(result).contains("ri:page")
        assertThat(result).contains("""ri:content-title="Getting Started"""")
    }

    @Test
    fun `external link is left as-is`() {
        val html = """<a href="https://example.com">Example</a>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("https://example.com")
        assertThat(result).doesNotContain("ac:link")
    }

    @Test
    fun `anchor link is left as-is`() {
        val html = """<a href="#section-heading">Section</a>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("#section-heading")
        assertThat(result).doesNotContain("ac:link")
    }

    @Test
    fun `unresolved internal link logs warning in non-strict mode`() {
        val context = PageContext(strict = false)
        val html = """<a href="unknown-page.html">Unknown Page</a>"""
        // Should not throw
        val result = converter.convert(html, context)
        assertThat(result).isNotNull()
    }

    @Test
    fun `unresolved internal link throws in strict mode`() {
        val context = PageContext(strict = true)
        val html = """<a href="unknown-page.html">Unknown Page</a>"""

        assertThatThrownBy { converter.convert(html, context) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("unknown-page.html")
    }

    // -------------------------------------------------------------------------
    // Pass-through elements
    // -------------------------------------------------------------------------

    @Test
    fun `tables are passed through unchanged`() {
        val html = """<table><tbody><tr><td>Cell 1</td><td>Cell 2</td></tr></tbody></table>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("<table>")
        assertThat(result).contains("<td>Cell 1</td>")
    }

    @Test
    fun `normal paragraphs are passed through unchanged`() {
        val html = """<p>This is a normal paragraph with <strong>bold</strong> text.</p>"""
        val result = converter.convert(html, emptyContext)

        assertThat(result).contains("<p>")
        assertThat(result).contains("normal paragraph")
        assertThat(result).contains("<strong>bold</strong>")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds an Asciidoctorj-style admonition HTML fragment.
     * Asciidoctorj wraps content in `div.admonitionblock TYPE > table > tbody > tr > td.content`.
     */
    private fun admonitionHtml(type: String, content: String): String =
        """<div class="admonitionblock $type">
            <table><tbody><tr>
              <td class="icon"><div class="title">${type.replaceFirstChar { it.uppercaseChar() }}</div></td>
              <td class="content">$content</td>
            </tr></tbody></table>
          </div>"""
}
