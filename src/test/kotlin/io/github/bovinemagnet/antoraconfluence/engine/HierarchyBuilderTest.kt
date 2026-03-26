package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.engine.model.NodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class HierarchyBuilderTest {

    private val builder = HierarchyBuilder()

    private fun page(
        component: String,
        version: String,
        module: String,
        name: String,
        siteKey: String = ""
    ) = AntoraPage(
        siteKey = siteKey,
        componentName = component,
        componentVersion = version,
        moduleName = module,
        relativePath = "$name.adoc",
        sourceFile = File("/tmp/$name.adoc"),
        title = name.replaceFirstChar { it.uppercaseChar() }
    )

    @Test
    fun `COMPONENT_VERSION_MODULE_PAGE creates full hierarchy`() {
        val pages = listOf(
            page("myapp", "1.0", "ROOT", "index"),
            page("myapp", "1.0", "ROOT", "guide")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, false)

        assertEquals(1, roots.size, "Expected one component root")
        val component = roots[0]
        assertEquals(NodeType.COMPONENT, component.nodeType)
        assertEquals("myapp", component.title)

        assertEquals(1, component.children.size, "Expected one version node")
        val version = component.children[0]
        assertEquals(NodeType.VERSION, version.nodeType)
        assertEquals("1.0", version.title)

        assertEquals(1, version.children.size, "Expected one module node")
        val module = version.children[0]
        assertEquals(NodeType.MODULE, module.nodeType)
        assertEquals("ROOT", module.title)

        assertEquals(2, module.children.size, "Expected two page nodes")
        assertTrue(module.children.all { it.nodeType == NodeType.PAGE })
    }

    @Test
    fun `COMPONENT_VERSION_PAGE combines component and version`() {
        val pages = listOf(
            page("myapp", "1.0", "ROOT", "index"),
            page("myapp", "1.0", "ROOT", "guide")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_PAGE, VersionMode.HIERARCHY, false)

        assertEquals(1, roots.size)
        val componentVersion = roots[0]
        assertEquals(NodeType.COMPONENT, componentVersion.nodeType)
        assertTrue(
            componentVersion.title.contains("myapp") && componentVersion.title.contains("1.0"),
            "Title should combine component and version, got: ${componentVersion.title}"
        )

        assertEquals(1, componentVersion.children.size, "Expected one module node")
        val module = componentVersion.children[0]
        assertEquals(NodeType.MODULE, module.nodeType)

        assertEquals(2, module.children.size, "Expected two page nodes")
        assertTrue(module.children.all { it.nodeType == NodeType.PAGE })
    }

    @Test
    fun `COMPONENT_PAGE flattens version and module`() {
        val pages = listOf(
            page("myapp", "1.0", "ROOT", "index"),
            page("myapp", "1.0", "guides", "guide")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_PAGE, VersionMode.HIERARCHY, false)

        assertEquals(1, roots.size)
        val component = roots[0]
        assertEquals(NodeType.COMPONENT, component.nodeType)
        assertEquals("myapp", component.title)

        assertEquals(2, component.children.size, "Pages should appear directly under component")
        assertTrue(component.children.all { it.nodeType == NodeType.PAGE })
    }

    @Test
    fun `TITLE_PREFIX prepends version to page title`() {
        val pages = listOf(
            page("myapp", "1.0", "ROOT", "guide")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.TITLE_PREFIX, false)

        // With TITLE_PREFIX there should be no VERSION node
        val component = roots[0]
        assertEquals(NodeType.COMPONENT, component.nodeType)

        // Navigate to find page nodes
        val allNodes = collectAllNodes(roots)
        val versionNodes = allNodes.filter { it.nodeType == NodeType.VERSION }
        assertTrue(versionNodes.isEmpty(), "No VERSION nodes should exist with TITLE_PREFIX")

        val pageNodes = allNodes.filter { it.nodeType == NodeType.PAGE }
        assertEquals(1, pageNodes.size)
        val pageTitle = pageNodes[0].title
        assertTrue(
            pageTitle.contains("1.0"),
            "Page title should contain version prefix, got: $pageTitle"
        )
        assertTrue(
            pageTitle.contains("Guide"),
            "Page title should contain original title, got: $pageTitle"
        )
    }

    @Test
    fun `createIndexPages generates content for intermediate nodes`() {
        val pages = listOf(
            page("myapp", "1.0", "ROOT", "index"),
            page("myapp", "1.0", "ROOT", "guide")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, true)

        val allNodes = collectAllNodes(roots)
        val intermediateNodes = allNodes.filter { it.nodeType != NodeType.PAGE }
        assertTrue(intermediateNodes.isNotEmpty(), "Should have intermediate nodes")
        intermediateNodes.forEach { node ->
            assertNotNull(node.htmlContent, "Intermediate node '${node.title}' should have htmlContent")
            assertTrue(node.htmlContent!!.contains("<h1>"), "htmlContent should contain <h1>")
            assertTrue(node.htmlContent!!.contains("<ul>"), "htmlContent should contain <ul>")
        }
    }

    @Test
    fun `multiple components produce multiple root nodes`() {
        val pages = listOf(
            page("app-a", "1.0", "ROOT", "index"),
            page("app-b", "2.0", "ROOT", "index")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, false)

        assertEquals(2, roots.size, "Expected two root component nodes")
        val titles = roots.map { it.title }.toSet()
        assertTrue(titles.contains("app-a"))
        assertTrue(titles.contains("app-b"))
    }

    @Test
    fun `pages without version omit version level`() {
        val pages = listOf(
            page("myapp", "", "ROOT", "index")
        )

        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, false)

        assertEquals(1, roots.size)
        val allNodes = collectAllNodes(roots)
        val versionNodes = allNodes.filter { it.nodeType == NodeType.VERSION }
        assertTrue(versionNodes.isEmpty(), "No VERSION nodes should exist when version is blank")
    }

    // ---- helpers ----

    private fun collectAllNodes(nodes: List<io.github.bovinemagnet.antoraconfluence.engine.model.HierarchyNode>): List<io.github.bovinemagnet.antoraconfluence.engine.model.HierarchyNode> {
        val result = mutableListOf<io.github.bovinemagnet.antoraconfluence.engine.model.HierarchyNode>()
        for (node in nodes) {
            result.add(node)
            result.addAll(collectAllNodes(node.children))
        }
        return result
    }
}
