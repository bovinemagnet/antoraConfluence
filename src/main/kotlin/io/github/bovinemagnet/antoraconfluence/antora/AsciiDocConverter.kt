package io.github.bovinemagnet.antoraconfluence.antora

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
     * Converts the given [sourceFile] to an HTML string suitable for embedding in a Confluence
     * storage-format page body.
     *
     * @param sourceFile  Absolute path to the `.adoc` source file.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Rendered HTML fragment (does not include html/body wrappers).
     */
    fun convert(sourceFile: File, attributes: Map<String, String> = emptyMap()): String {
        val options = Options.builder()
            .safe(SafeMode.UNSAFE)
            .standalone(false)
            .attributes(buildAttributes(attributes))
            .build()
        return asciidoctor.convertFile(sourceFile, options)
    }

    /**
     * Converts the given [content] string (AsciiDoc markup) to an HTML string.
     *
     * @param content     AsciiDoc markup as a string.
     * @param attributes  Optional map of additional Asciidoctor document attributes.
     * @return            Rendered HTML fragment.
     */
    fun convertString(content: String, attributes: Map<String, String> = emptyMap()): String {
        val options = Options.builder()
            .safe(SafeMode.UNSAFE)
            .standalone(false)
            .attributes(buildAttributes(attributes))
            .build()
        return asciidoctor.convert(content, options)
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
