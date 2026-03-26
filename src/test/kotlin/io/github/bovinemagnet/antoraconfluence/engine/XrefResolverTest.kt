package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class XrefResolverTest {

    private fun page(component: String, version: String, module: String, name: String) =
        AntoraPage(
            siteKey = "",
            componentName = component,
            componentVersion = version,
            moduleName = module,
            relativePath = "$name.adoc",
            sourceFile = File("/tmp/$name.adoc"),
            title = name.replaceFirstChar { it.uppercaseChar() }
        )

    @Test
    fun `resolves same-module xref`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"), page("comp", "1.0", "ROOT", "guide"))
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("guide.adoc", "comp", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Guide")
    }

    @Test
    fun `resolves cross-module xref`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"), page("comp", "1.0", "admin", "setup"))
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("admin:setup.adoc", "comp", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Setup")
    }

    @Test
    fun `resolves cross-component xref`() {
        val pages = listOf(page("comp-a", "1.0", "ROOT", "index"), page("comp-b", "2.0", "ROOT", "api"))
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("comp-b:ROOT:api.adoc", "comp-a", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Api")
    }

    @Test
    fun `returns null for unresolved xref`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"))
        val resolver = XrefResolver(pages)
        assertThat(resolver.resolve("nonexistent.adoc", "comp", "1.0", "ROOT")).isNull()
    }

    @Test
    fun `resolveAll returns map and collects warnings`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"), page("comp", "1.0", "ROOT", "guide"))
        val resolver = XrefResolver(pages)
        val result = resolver.resolveAll(listOf("guide.adoc", "missing.adoc"), "comp", "1.0", "ROOT")
        assertThat(result.resolved).containsEntry("guide.adoc", "Guide")
        assertThat(result.unresolved).containsExactly("missing.adoc")
    }

    @Test
    fun `handles version prefix in xref`() {
        val pages = listOf(page("comp", "2.0", "ROOT", "api"))
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("2.0@comp:ROOT:api.adoc", "other", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Api")
    }
}
