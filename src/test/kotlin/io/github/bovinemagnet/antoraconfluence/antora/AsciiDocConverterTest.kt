package io.github.bovinemagnet.antoraconfluence.antora

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [AsciiDocConverter].
 */
class AsciiDocConverterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `renderToHtml produces HTML output`() {
        val adoc = tempDir.resolve("test.adoc")
        adoc.writeText("= Test Page\n\nThis is some content.\n")

        AsciiDocConverter().use { converter ->
            val html = converter.renderToHtml(adoc)

            assertThat(html).isNotBlank()
            assertThat(html).contains("This is some content")
        }
    }

    @Test
    fun `renderToConfluenceStorage produces Confluence format for NOTE block`() {
        val adoc = tempDir.resolve("note-page.adoc")
        adoc.writeText(
            """= Note Page

NOTE: This is an important note.
"""
        )
        val context = PageContext()

        AsciiDocConverter().use { converter ->
            val result = converter.renderToConfluenceStorage(adoc, context)

            assertThat(result).contains("""ac:name="info"""")
            assertThat(result).contains("This is an important note.")
        }
    }
}
