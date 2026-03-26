package io.github.bovinemagnet.antoraconfluence.antora

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AsciiDocReferenceExtractorTest {

    private val extractor = AsciiDocReferenceExtractor()

    // -------------------------------------------------------------------------
    // Title
    // -------------------------------------------------------------------------

    @Test
    fun `extract returns title when document title is present`() {
        val content = """
            = My Document Title

            Some content here.
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.title).isEqualTo("My Document Title")
    }

    @Test
    fun `extract returns null title when no document title is present`() {
        val content = """
            Some content without a title.

            == Section Heading
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.title).isNull()
    }

    @Test
    fun `extract returns first title only when multiple level-1 headings exist`() {
        val content = """
            = First Title

            = Second Title
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.title).isEqualTo("First Title")
    }

    // -------------------------------------------------------------------------
    // Block images
    // -------------------------------------------------------------------------

    @Test
    fun `extract finds block image macros`() {
        val content = """
            = Page

            image::diagram.png[Diagram]
            image::screenshots/overview.png[Overview]
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactlyInAnyOrder("diagram.png", "screenshots/overview.png")
    }

    // -------------------------------------------------------------------------
    // Inline images
    // -------------------------------------------------------------------------

    @Test
    fun `extract finds inline image macros`() {
        val content = """
            = Page

            Click the image:button.png[Button] to continue.
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.images).contains("button.png")
    }

    @Test
    fun `extract does not double-count block images as inline`() {
        val content = """
            = Page

            image::block.png[Block image]
        """.trimIndent()
        val refs = extractor.extract(content)
        // block.png should appear exactly once
        assertThat(refs.images).containsExactly("block.png")
    }

    @Test
    fun `extract returns both block and inline images deduplicated`() {
        val content = """
            = Page

            image::shared.png[Block]

            See image:shared.png[Inline] for details.
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactly("shared.png")
    }

    // -------------------------------------------------------------------------
    // Includes
    // -------------------------------------------------------------------------

    @Test
    fun `extract finds include directives`() {
        val content = """
            = Page

            include::partial${'$'}_header.adoc[]
            include::modules/ROOT/pages/shared.adoc[leveloffset=+1]
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.includes).containsExactlyInAnyOrder(
            "partial\$_header.adoc[]".substringBefore("[]"),
            "modules/ROOT/pages/shared.adoc[leveloffset=+1]".substringBefore("[")
        )
    }

    @Test
    fun `extract returns empty includes when none present`() {
        val content = "= Page\n\nJust text."
        val refs = extractor.extract(content)
        assertThat(refs.includes).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Xrefs
    // -------------------------------------------------------------------------

    @Test
    fun `extract finds xref macros`() {
        val content = """
            = Page

            See xref:other-page.adoc[Other Page] and xref:component::module/page.adoc[Cross-component].
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.xrefs).containsExactlyInAnyOrder(
            "other-page.adoc",
            "component::module/page.adoc"
        )
    }

    @Test
    fun `extract returns empty xrefs when none present`() {
        val content = "= Page\n\nNo cross-references here."
        val refs = extractor.extract(content)
        assertThat(refs.xrefs).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Empty content
    // -------------------------------------------------------------------------

    @Test
    fun `extract returns empty references for empty content`() {
        val refs = extractor.extract("")
        assertThat(refs.title).isNull()
        assertThat(refs.images).isEmpty()
        assertThat(refs.includes).isEmpty()
        assertThat(refs.xrefs).isEmpty()
    }

    // -------------------------------------------------------------------------
    // Commented-out references
    // -------------------------------------------------------------------------

    @Test
    fun `extract ignores commented-out image macros`() {
        val content = """
            = Page

            // image::hidden.png[Hidden]
            image::visible.png[Visible]
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactly("visible.png")
        assertThat(refs.images).doesNotContain("hidden.png")
    }

    @Test
    fun `extract ignores commented-out include directives`() {
        val content = """
            = Page

            // include::commented-out.adoc[]
            include::active.adoc[]
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.includes).containsExactly("active.adoc")
        assertThat(refs.includes).doesNotContain("commented-out.adoc")
    }

    @Test
    fun `extract ignores commented-out xrefs`() {
        val content = """
            = Page

            // xref:hidden-page.adoc[Hidden]
            xref:visible-page.adoc[Visible]
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.xrefs).containsExactly("visible-page.adoc")
        assertThat(refs.xrefs).doesNotContain("hidden-page.adoc")
    }

    @Test
    fun `extract ignores commented-out title`() {
        val content = """
            // = Commented Title
            = Real Title

            Content.
        """.trimIndent()
        val refs = extractor.extract(content)
        assertThat(refs.title).isEqualTo("Real Title")
    }
}
