package io.github.bovinemagnet.antoraconfluence.confluence

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory

/**
 * Converts HTML5 output from Asciidoctorj into Confluence storage format.
 *
 * Handles the following transformations:
 * - Code blocks → `<ac:structured-macro ac:name="code">` with language parameter
 * - Admonition blocks → appropriate Confluence info/tip/warning/note macros
 * - Local images → `<ac:image><ri:attachment>` references
 * - Internal xref links → `<ac:link><ri:page>` references
 * - External links and anchor links are left as-is
 * - Tables and normal paragraphs are passed through unchanged
 */
class ConfluenceStorageFormatConverter {

    private val log = LoggerFactory.getLogger(ConfluenceStorageFormatConverter::class.java)

    companion object {
        private val ADMONITION_TYPE_MAP = mapOf(
            "note" to "info",
            "tip" to "tip",
            "warning" to "warning",
            "caution" to "note",
            "important" to "warning"
        )
    }

    /**
     * Converts an HTML fragment to Confluence storage format.
     *
     * @param html    HTML fragment as produced by Asciidoctorj (without html/body wrappers).
     * @param context Page context containing resolved xrefs and image manifest.
     * @return        Confluence storage format XML fragment.
     */
    fun convert(html: String, context: PageContext): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)

        transformCodeBlocks(doc)
        transformAdmonitions(doc)
        transformImages(doc, context)
        transformLinks(doc, context)

        return doc.body().html()
    }

    // -------------------------------------------------------------------------
    // Code block transformation
    // -------------------------------------------------------------------------

    private fun transformCodeBlocks(doc: Document) {
        // Asciidoctorj wraps code in <div class="listingblock"><div class="content"><pre class="highlight"><code class="language-xxx">
        // Also handles plain <pre><code> without a language class
        doc.select("pre:has(code)").forEach { pre ->
            val code = pre.selectFirst("code") ?: return@forEach
            val language = extractLanguage(code)
            val codeText = code.wholeText()

            val macro = buildCodeMacro(language, codeText)
            pre.replaceWith(macro)
        }
    }

    private fun extractLanguage(code: Element): String? {
        return code.classNames()
            .firstOrNull { it.startsWith("language-") }
            ?.removePrefix("language-")
    }

    private fun buildCodeMacro(language: String?, codeText: String): Element {
        // Build as a raw XML string then parse — Jsoup handles ac: prefixes as tag names
        val langParam = if (language != null) {
            """<ac:parameter ac:name="language">$language</ac:parameter>"""
        } else {
            ""
        }
        val escaped = codeText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val xml = """<ac:structured-macro ac:name="code">$langParam<ac:plain-text-body><![CDATA[$escaped]]></ac:plain-text-body></ac:structured-macro>"""
        // Parse as a body fragment and return the first element
        val fragment = Jsoup.parseBodyFragment(xml)
        fragment.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        return fragment.body().children().first()
            ?: Element("ac:structured-macro").attr("ac:name", "code")
    }

    // -------------------------------------------------------------------------
    // Admonition transformation
    // -------------------------------------------------------------------------

    private fun transformAdmonitions(doc: Document) {
        doc.select("div.admonitionblock").forEach { admonition ->
            val type = admonition.classNames()
                .firstOrNull { it != "admonitionblock" }
                ?: "note"
            val confluenceMacroName = ADMONITION_TYPE_MAP[type] ?: "info"

            // Content is in td.content
            val contentTd = admonition.selectFirst("td.content")
            val innerHtml = contentTd?.html() ?: admonition.html()

            val macro = buildAdmonitionMacro(confluenceMacroName, innerHtml)
            admonition.replaceWith(macro)
        }
    }

    private fun buildAdmonitionMacro(macroName: String, bodyHtml: String): Element {
        val xml = """<ac:structured-macro ac:name="$macroName"><ac:rich-text-body>$bodyHtml</ac:rich-text-body></ac:structured-macro>"""
        val fragment = Jsoup.parseBodyFragment(xml)
        fragment.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        return fragment.body().children().first()
            ?: Element("ac:structured-macro").attr("ac:name", macroName)
    }

    // -------------------------------------------------------------------------
    // Image transformation
    // -------------------------------------------------------------------------

    private fun transformImages(doc: Document, context: PageContext) {
        doc.select("img").forEach { img ->
            val src = img.attr("src")

            // Leave external images as-is
            if (src.startsWith("http://") || src.startsWith("https://")) {
                return@forEach
            }

            // Check image manifest for local images
            val filename = src.substringAfterLast("/")
            if (context.imageManifest.containsKey(filename)) {
                val macro = buildImageMacro(filename)
                img.replaceWith(macro)
            } else if (context.imageManifest.containsKey(src)) {
                val macro = buildImageMacro(src)
                img.replaceWith(macro)
            } else {
                val message = "Unresolved local image: $src"
                if (context.strict) {
                    throw IllegalStateException(message)
                } else {
                    log.warn(message)
                }
            }
        }
    }

    private fun buildImageMacro(filename: String): Element {
        val xml = """<ac:image><ri:attachment ri:filename="$filename"/></ac:image>"""
        val fragment = Jsoup.parseBodyFragment(xml)
        fragment.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        return fragment.body().children().first()
            ?: Element("ac:image")
    }

    // -------------------------------------------------------------------------
    // Link transformation
    // -------------------------------------------------------------------------

    private fun transformLinks(doc: Document, context: PageContext) {
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")

            // Leave external links as-is
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return@forEach
            }

            // Leave anchor links as-is
            if (href.startsWith("#")) {
                return@forEach
            }

            // Check resolvedXrefs for internal links
            val pageTitle = context.resolvedXrefs[href]
                ?: context.resolvedXrefs[href.removeSuffix(".html")]
                ?: context.resolvedXrefs[href.removeSuffix(".adoc")]

            if (pageTitle != null) {
                val linkText = link.text()
                val macro = buildPageLink(pageTitle, linkText)
                link.replaceWith(macro)
            } else {
                val message = "Unresolved internal link: $href"
                if (context.strict) {
                    throw IllegalStateException(message)
                } else {
                    log.warn(message)
                }
            }
        }
    }

    private fun buildPageLink(pageTitle: String, linkText: String): Element {
        val body = if (linkText.isNotBlank()) {
            """<ac:plain-text-link-body><![CDATA[$linkText]]></ac:plain-text-link-body>"""
        } else {
            ""
        }
        val xml = """<ac:link><ri:page ri:content-title="$pageTitle"/>$body</ac:link>"""
        val fragment = Jsoup.parseBodyFragment(xml)
        fragment.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)
        return fragment.body().children().first()
            ?: Element("ac:link")
    }
}
