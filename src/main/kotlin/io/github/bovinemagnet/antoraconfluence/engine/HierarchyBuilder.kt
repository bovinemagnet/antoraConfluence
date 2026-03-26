package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.engine.model.HierarchyNode
import io.github.bovinemagnet.antoraconfluence.engine.model.NodeType

/**
 * Builds a [HierarchyNode] tree from a flat list of [AntoraPage] entries, applying the
 * configured [HierarchyMode] and [VersionMode] rules.
 *
 * @author Paul Snow
 * @since 0.0.0
 */
class HierarchyBuilder {

    /**
     * Maps [pages] into a list of root [HierarchyNode] trees.
     *
     * @param pages            Flat list of Antora pages to organise.
     * @param hierarchyMode    Controls how deeply the component/version/module/page hierarchy is
     *                         expressed in the Confluence page tree.
     * @param versionMode      Controls whether versions appear as separate hierarchy nodes or are
     *                         prepended to page titles instead.
     * @param createIndexPages When `true`, intermediate (non-page) nodes receive a simple HTML
     *                         listing as their [HierarchyNode.htmlContent].
     * @return List of root component nodes.
     */
    fun build(
        pages: List<AntoraPage>,
        hierarchyMode: HierarchyMode,
        versionMode: VersionMode,
        createIndexPages: Boolean
    ): List<HierarchyNode> {
        val roots = when (hierarchyMode) {
            HierarchyMode.COMPONENT_VERSION_MODULE_PAGE ->
                buildComponentVersionModulePage(pages, versionMode)

            HierarchyMode.COMPONENT_VERSION_PAGE ->
                buildComponentVersionPage(pages, versionMode)

            HierarchyMode.COMPONENT_PAGE ->
                buildComponentPage(pages, versionMode)
        }

        if (createIndexPages) {
            roots.forEach { applyIndexPages(it) }
        }

        return roots
    }

    // -------------------------------------------------------------------------
    // Mode: COMPONENT_VERSION_MODULE_PAGE
    // -------------------------------------------------------------------------

    private fun buildComponentVersionModulePage(
        pages: List<AntoraPage>,
        versionMode: VersionMode
    ): List<HierarchyNode> {
        val componentGroups = pages.groupBy { it.componentName }

        return componentGroups.map { (componentName, componentPages) ->
            val componentKey = buildKey(componentPages.first(), level = "component")
            val componentNode = HierarchyNode(
                canonicalKey = componentKey,
                title = componentName,
                nodeType = NodeType.COMPONENT
            )

            if (versionMode == VersionMode.TITLE_PREFIX) {
                // Skip version level; group directly by module, prefix titles
                val moduleGroups = componentPages.groupBy { it.moduleName }
                moduleGroups.forEach { (moduleName, modulePages) ->
                    val moduleNode = createModuleNode(componentKey, componentPages.first(), moduleName)
                    modulePages.forEach { page ->
                        moduleNode.children.add(createPageNode(page, versionPrefix = page.componentVersion))
                    }
                    componentNode.children.add(moduleNode)
                }
            } else {
                // HIERARCHY: group by version, then by module
                val versionGroups = componentPages.groupBy { it.componentVersion }
                versionGroups.forEach { (componentVersion, versionPages) ->
                    if (componentVersion.isBlank()) {
                        // Omit version level entirely when version is blank
                        val moduleGroups = versionPages.groupBy { it.moduleName }
                        moduleGroups.forEach { (moduleName, modulePages) ->
                            val moduleNode = createModuleNode(componentKey, versionPages.first(), moduleName)
                            modulePages.forEach { page -> moduleNode.children.add(createPageNode(page)) }
                            componentNode.children.add(moduleNode)
                        }
                    } else {
                        val versionKey = "$componentKey/$componentVersion"
                        val versionNode = HierarchyNode(
                            canonicalKey = versionKey,
                            title = componentVersion,
                            nodeType = NodeType.VERSION
                        )
                        val moduleGroups = versionPages.groupBy { it.moduleName }
                        moduleGroups.forEach { (moduleName, modulePages) ->
                            val moduleNode = createModuleNode(versionKey, versionPages.first(), moduleName)
                            modulePages.forEach { page -> moduleNode.children.add(createPageNode(page)) }
                            versionNode.children.add(moduleNode)
                        }
                        componentNode.children.add(versionNode)
                    }
                }
            }

            componentNode
        }
    }

    // -------------------------------------------------------------------------
    // Mode: COMPONENT_VERSION_PAGE
    // -------------------------------------------------------------------------

    private fun buildComponentVersionPage(
        pages: List<AntoraPage>,
        versionMode: VersionMode
    ): List<HierarchyNode> {
        // Group by component+version combination
        val cvGroups = pages.groupBy { "${it.componentName}/${it.componentVersion}" }

        return cvGroups.map { (_, cvPages) ->
            val sample = cvPages.first()
            val combinedTitle = if (sample.componentVersion.isBlank()) {
                sample.componentName
            } else {
                "${sample.componentName} ${sample.componentVersion}"
            }
            val componentKey = buildKey(sample, level = "component_version")
            val componentVersionNode = HierarchyNode(
                canonicalKey = componentKey,
                title = combinedTitle,
                nodeType = NodeType.COMPONENT
            )

            val moduleGroups = cvPages.groupBy { it.moduleName }
            moduleGroups.forEach { (moduleName, modulePages) ->
                val moduleNode = createModuleNode(componentKey, sample, moduleName)
                modulePages.forEach { page ->
                    val prefix = if (versionMode == VersionMode.TITLE_PREFIX) page.componentVersion else null
                    moduleNode.children.add(createPageNode(page, versionPrefix = prefix))
                }
                componentVersionNode.children.add(moduleNode)
            }

            componentVersionNode
        }
    }

    // -------------------------------------------------------------------------
    // Mode: COMPONENT_PAGE
    // -------------------------------------------------------------------------

    private fun buildComponentPage(
        pages: List<AntoraPage>,
        versionMode: VersionMode
    ): List<HierarchyNode> {
        val componentGroups = pages.groupBy { it.componentName }

        return componentGroups.map { (componentName, componentPages) ->
            val componentKey = buildKey(componentPages.first(), level = "component")
            val componentNode = HierarchyNode(
                canonicalKey = componentKey,
                title = componentName,
                nodeType = NodeType.COMPONENT
            )

            componentPages.forEach { page ->
                val prefix = if (versionMode == VersionMode.TITLE_PREFIX) page.componentVersion else null
                componentNode.children.add(createPageNode(page, versionPrefix = prefix))
            }

            componentNode
        }
    }

    // -------------------------------------------------------------------------
    // Node factories
    // -------------------------------------------------------------------------

    private fun createModuleNode(parentKey: String, samplePage: AntoraPage, moduleName: String): HierarchyNode {
        val key = "$parentKey/$moduleName"
        return HierarchyNode(
            canonicalKey = key,
            title = moduleName,
            nodeType = NodeType.MODULE
        )
    }

    private fun createPageNode(page: AntoraPage, versionPrefix: String? = null): HierarchyNode {
        val rawTitle = page.suggestedTitle
        val title = if (!versionPrefix.isNullOrBlank()) "$versionPrefix - $rawTitle" else rawTitle
        return HierarchyNode(
            canonicalKey = page.pageId,
            title = title,
            nodeType = NodeType.PAGE,
            sourcePage = page
        )
    }

    // -------------------------------------------------------------------------
    // Canonical key helpers
    // -------------------------------------------------------------------------

    private fun buildKey(page: AntoraPage, level: String): String {
        val sb = StringBuilder()
        if (page.siteKey.isNotBlank()) sb.append(page.siteKey).append("/")
        sb.append(page.componentName)
        if (level == "component_version" && page.componentVersion.isNotBlank()) {
            sb.append("/").append(page.componentVersion)
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Index page generation
    // -------------------------------------------------------------------------

    private fun applyIndexPages(node: HierarchyNode) {
        if (node.nodeType != NodeType.PAGE && node.children.isNotEmpty()) {
            node.htmlContent = buildIndexHtml(node)
        }
        node.children.forEach { applyIndexPages(it) }
    }

    private fun buildIndexHtml(node: HierarchyNode): String {
        val items = node.children.joinToString("\n") { "<li>${it.title}</li>" }
        return "<h1>${node.title}</h1>\n<ul>\n$items\n</ul>"
    }
}
