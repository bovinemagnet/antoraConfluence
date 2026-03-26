package io.github.bovinemagnet.antoraconfluence.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DependencyGraphTest {

    @Test
    fun `adding dependency creates edge`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "include-1.adoc")
        assertThat(graph.getDependencies("page-a")).containsExactly("include-1.adoc")
    }

    @Test
    fun `getDependents returns pages depending on a resource`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "shared-header.adoc")
        graph.addDependency("page-b", "shared-header.adoc")
        assertThat(graph.getDependents("shared-header.adoc")).containsExactlyInAnyOrder("page-a", "page-b")
    }

    @Test
    fun `page with no dependencies returns empty set`() {
        val graph = DependencyGraph()
        assertThat(graph.getDependencies("page-a")).isEmpty()
    }

    @Test
    fun `getAffectedPages returns all pages affected by resource changes`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "partial.adoc")
        graph.addDependency("page-b", "partial.adoc")
        graph.addDependency("page-c", "other.adoc")
        val affected = graph.getAffectedPages(setOf("partial.adoc"))
        assertThat(affected).containsExactlyInAnyOrder("page-a", "page-b")
    }
}
