package io.github.bovinemagnet.antoraconfluence.antora

import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceStorageFormatConverter
import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import java.io.File

/**
 * Converts AsciiDoc source files to HTML using Asciidoctorj.
 *
 * A single [AsciiDocConverter] instance caches the underlying [Asciidoctor] runtime for reuse
 * across multiple conversions.  Call [close] when the instance is no longer needed to free
 * native resources.
 */
class AsciiDocConverter : AutoCloseable {

    private var _asciidoctor: Asciidoctor? = null

    private val asciidoctor: Asciidoctor
        get() {
            if (_asciidoctor == null) {
                _asciidoctor = Asciidoctor.Factory.create()
            }
            return _asciidoctor!!
        }

    /**
     * Renders the given [sourceFile] to an HTML string.
     *
     * @param sourceFile  Absolute path to the `.adoc` source file.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Rendered HTML fragment (does not include html/body wrappers).
     */
    fun renderToHtml(sourceFile: File, attributes: Map<String, String> = emptyMap()): String {
        val baseDir = sourceFile.parentFile
        val extraAttributes = attributes.toMutableMap()
        val options = Options.builder()
            .safe(SafeMode.UNSAFE)
            .standalone(false)
            .baseDir(baseDir)
            .attributes(buildAttributes(extraAttributes))
            .build()
        return asciidoctor.convertFile(sourceFile, options)
            ?: asciidoctor.convert(sourceFile.readText(), options)
    }

    /**
     * Renders the given [content] string (AsciiDoc markup) to an HTML string.
     *
     * @param content     AsciiDoc markup as a string.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Rendered HTML fragment.
     */
    fun renderStringToHtml(content: String, attributes: Map<String, String> = emptyMap()): String {
        val options = Options.builder()
            .safe(SafeMode.UNSAFE)
            .standalone(false)
            .attributes(buildAttributes(attributes))
            .build()
        return asciidoctor.convert(content, options)
    }

    /**
     * Renders the given [sourceFile] to Confluence storage format.
     *
     * Converts AsciiDoc to HTML first, then post-processes the HTML into
     * Confluence storage format using [ConfluenceStorageFormatConverter].
     *
     * @param sourceFile  Absolute path to the `.adoc` source file.
     * @param context     Page context for resolving xrefs and images.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Confluence storage format XML fragment.
     */
    fun renderToConfluenceStorage(
        sourceFile: File,
        context: PageContext,
        attributes: Map<String, String> = emptyMap()
    ): String {
        val html = renderToHtml(sourceFile, attributes)
        return ConfluenceStorageFormatConverter().convert(html, context)
    }

    /**
     * Renders the given AsciiDoc [content] string to Confluence storage format.
     *
     * @param content     AsciiDoc markup as a string.
     * @param context     Page context for resolving xrefs and images.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Confluence storage format XML fragment.
     */
    fun renderStringToConfluenceStorage(
        content: String,
        context: PageContext,
        attributes: Map<String, String> = emptyMap()
    ): String {
        val html = renderStringToHtml(content, attributes)
        return ConfluenceStorageFormatConverter().convert(html, context)
    }

    override fun close() {
        _asciidoctor?.close()
        _asciidoctor = null
    }

    private fun buildAttributes(extra: Map<String, String>): Attributes {
        val builder = Attributes.builder()
            .attribute("source-highlighter", "coderay")
            .attribute("icons", "font")
            .attribute("toc", "auto")
            .attribute("idprefix", "")
            .attribute("idseparator", "-")
        extra.forEach { (key, value) -> builder.attribute(key, value) }
        return builder.build()
    }
}
