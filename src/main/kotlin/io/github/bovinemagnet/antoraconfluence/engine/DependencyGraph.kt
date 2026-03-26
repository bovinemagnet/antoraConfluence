package io.github.bovinemagnet.antoraconfluence.engine

class DependencyGraph {
    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val dependents = mutableMapOf<String, MutableSet<String>>()

    fun addDependency(pageId: String, resourceId: String) {
        dependencies.getOrPut(pageId) { mutableSetOf() }.add(resourceId)
        dependents.getOrPut(resourceId) { mutableSetOf() }.add(pageId)
    }

    fun getDependencies(pageId: String): Set<String> = dependencies[pageId] ?: emptySet()
    fun getDependents(resourceId: String): Set<String> = dependents[resourceId] ?: emptySet()
    fun getAffectedPages(changedResources: Set<String>): Set<String> =
        changedResources.flatMap { getDependents(it) }.toSet()
}
