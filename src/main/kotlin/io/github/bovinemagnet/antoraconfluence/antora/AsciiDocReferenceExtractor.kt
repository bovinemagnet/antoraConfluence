package io.github.bovinemagnet.antoraconfluence.antora

/**
 * Holds all references extracted from a single AsciiDoc document.
 *
 * @property title    The document title (first `= ...` heading), or `null` if absent.
 * @property images   Image targets found via both block (`image::`) and inline (`image:`) macros.
 * @property includes Targets of all `include::` directives.
 * @property xrefs    Targets of all `xref:` macros.
 */
data class ExtractedReferences(
    val title: String?,
    val images: List<String>,
    val includes: List<String>,
    val xrefs: List<String>
)

/**
 * Scans raw AsciiDoc source text and extracts document metadata and cross-references using
 * regular expressions.
 *
 * Single-line comments (`// ...`) are stripped before extraction so that commented-out
 * directives are not included in the results.
 */
class AsciiDocReferenceExtractor {

    // Strip single-line comments (lines beginning with //)
    private val commentPattern = Regex("""^//.*$""", setOf(RegexOption.MULTILINE))

    // Document title: first line matching "= <title>"
    private val titlePattern = Regex("""^= (.+)$""", setOf(RegexOption.MULTILINE))

    // Block image macro: image::<target>[...]
    private val blockImagePattern = Regex("""^image::([^\[]+)\[""", setOf(RegexOption.MULTILINE))

    // Inline image macro: image:<target>[...] but NOT preceded by a colon (which would make it block)
    private val inlineImagePattern = Regex("""(?<!:)image:([^\[:\n]+)\[""")

    // Include directive
    private val includePattern = Regex("""^include::([^\[]+)\[""", setOf(RegexOption.MULTILINE))

    // Xref macro
    private val xrefPattern = Regex("""xref:([^\[]+)\[""")

    /**
     * Extracts references from the provided AsciiDoc [content] string.
     *
     * @param content Raw AsciiDoc source text.
     * @return [ExtractedReferences] populated from the content.
     */
    fun extract(content: String): ExtractedReferences {
        val stripped = commentPattern.replace(content, "")

        val title = titlePattern.find(stripped)?.groupValues?.get(1)?.trim()

        val blockImages = blockImagePattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .toList()

        val inlineImages = inlineImagePattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .toList()

        val images = (blockImages + inlineImages).distinct()

        val includes = includePattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .toList()

        val xrefs = xrefPattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .toList()

        return ExtractedReferences(
            title = title,
            images = images,
            includes = includes,
            xrefs = xrefs
        )
    }
}
