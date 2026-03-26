package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage

/**
 * Holds the outcome of resolving a collection of xref targets.
 *
 * @property resolved   Map of xref target string to the resolved Confluence page title.
 * @property unresolved List of xref targets that could not be matched to any known page.
 */
data class XrefResolution(
    val resolved: Map<String, String>,
    val unresolved: List<String>
)

/**
 * Resolves Antora cross-reference (xref) targets to Confluence page titles.
 *
 * Supported xref formats:
 * - Same-module:      `page-name.adoc`
 * - Cross-module:     `module:page-name.adoc`
 * - Cross-component:  `component:module:page-name.adoc`
 * - Version-prefixed: `version@component:module:page-name.adoc`
 *
 * @param pages Full list of [AntoraPage] instances available for resolution.
 */
class XrefResolver(pages: List<AntoraPage>) {

    /**
     * Index keyed by `component/module/pageNameWithoutExtension` for efficient lookup.
     * Version information is intentionally omitted from the key so that version-qualified
     * xrefs (`2.0@comp:ROOT:api.adoc`) still resolve against the available pages.
     */
    private val index: Map<String, AntoraPage> = pages.associateBy { page ->
        indexKey(page.componentName, page.moduleName, page.relativePath.removeSuffix(".adoc"))
    }

    /**
     * Resolves a single xref target string to a Confluence page title.
     *
     * @param xref            The raw xref target (e.g. `"guide.adoc"`, `"admin:setup.adoc"`).
     * @param fromComponent   Component name of the page that contains the xref.
     * @param fromVersion     Version of the source page (currently unused in lookup key).
     * @param fromModule      Module name of the page that contains the xref.
     * @return The [AntoraPage.suggestedTitle] of the target page, or `null` if not found.
     */
    fun resolve(xref: String, fromComponent: String, fromVersion: String, fromModule: String): String? {
        // 1. Strip the .adoc suffix
        var target = xref.removeSuffix(".adoc")

        // 2. Strip the optional version@ prefix
        if (target.contains('@')) {
            target = target.substringAfter('@')
        }

        // 3. Split on ':' to determine the xref format
        val parts = target.split(':')
        val key = when (parts.size) {
            1 -> indexKey(fromComponent, fromModule, parts[0])
            2 -> indexKey(fromComponent, parts[0], parts[1])
            3 -> indexKey(parts[0], parts[1], parts[2])
            else -> return null
        }

        return index[key]?.suggestedTitle
    }

    /**
     * Resolves all xref targets in [xrefs], returning a [XrefResolution] that separates
     * successfully resolved targets from those that could not be matched.
     *
     * @param xrefs         Collection of raw xref target strings to resolve.
     * @param fromComponent Component name of the page containing these xrefs.
     * @param fromVersion   Version of the source page.
     * @param fromModule    Module name of the source page.
     * @return [XrefResolution] with resolved titles and a list of unresolved targets.
     */
    fun resolveAll(
        xrefs: List<String>,
        fromComponent: String,
        fromVersion: String,
        fromModule: String
    ): XrefResolution {
        val resolved = mutableMapOf<String, String>()
        val unresolved = mutableListOf<String>()

        for (xref in xrefs) {
            val title = resolve(xref, fromComponent, fromVersion, fromModule)
            if (title != null) {
                resolved[xref] = title
            } else {
                unresolved.add(xref)
            }
        }

        return XrefResolution(resolved = resolved, unresolved = unresolved)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun indexKey(component: String, module: String, pageNameWithoutExtension: String): String =
        "$component/$module/$pageNameWithoutExtension"
}
