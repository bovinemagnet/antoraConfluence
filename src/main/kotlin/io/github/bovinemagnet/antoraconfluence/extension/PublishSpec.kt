package io.github.bovinemagnet.antoraconfluence.extension

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.VersionMode
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Publish behavior settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     publish {
 *         hierarchy.set(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)
 *         versionMode.set(VersionMode.HIERARCHY)
 *         createIndexPages.set(true)
 *         strict.set(false)
 *         orphanStrategy.set(OrphanStrategy.REPORT)
 *         publishStrategy.set(PublishStrategy.CREATE_AND_UPDATE)
 *         applyLabels.add("managed-by-antora-confluence")
 *         dryRun.set(false)
 *     }
 * }
 * ```
 */
abstract class PublishSpec @Inject constructor() {

    /**
     * How the Antora component/version/module/page structure is mapped to a Confluence
     * page hierarchy. Defaults to [HierarchyMode.COMPONENT_VERSION_MODULE_PAGE].
     */
    abstract val hierarchy: Property<HierarchyMode>

    /**
     * How component versions are represented in the hierarchy.
     * Defaults to [VersionMode.HIERARCHY].
     */
    abstract val versionMode: Property<VersionMode>

    /**
     * When `true`, the plugin automatically creates index (landing) pages for
     * components, versions, and modules that do not have an explicit `index.adoc`.
     * Defaults to `false`.
     */
    abstract val createIndexPages: Property<Boolean>

    /**
     * When `true`, warnings are promoted to build failures. For example, unresolved xrefs
     * or orphaned pages will abort the build rather than being logged as warnings.
     * Defaults to `false`.
     */
    abstract val strict: Property<Boolean>

    /**
     * Controls what happens to managed Confluence pages whose Antora source no longer exists.
     * Defaults to [OrphanStrategy.REPORT]: no destructive action.
     */
    abstract val orphanStrategy: Property<OrphanStrategy>

    /**
     * Controls whether pages are created, updated, or both.
     * Defaults to [PublishStrategy.CREATE_AND_UPDATE].
     */
    abstract val publishStrategy: Property<PublishStrategy>

    /**
     * Confluence labels applied to every managed page on create or update.
     * Useful for identifying managed pages in Confluence.
     */
    abstract val applyLabels: ListProperty<String>

    /**
     * When `true`, no changes are written to Confluence. The plugin logs what would happen
     * without making any mutating API calls. Defaults to `false`.
     */
    abstract val dryRun: Property<Boolean>
}
