package io.github.bovinemagnet.antoraconfluence

/**
 * Controls how the Antora component/version/module/page hierarchy is represented as a
 * Confluence page tree.
 */
enum class HierarchyMode {
    /**
     * Full hierarchy: root → component → version → module → page (default).
     *
     * ```
     * <root page>
     *   └── <component>
     *         └── <version>
     *               └── <module>
     *                     └── <page>
     * ```
     */
    COMPONENT_VERSION_MODULE_PAGE,

    /**
     * Component and version are merged into a single level.
     *
     * ```
     * <root page>
     *   └── <component> <version>
     *         └── <module>
     *               └── <page>
     * ```
     */
    COMPONENT_VERSION_PAGE,

    /**
     * Flatten the module level; pages appear directly under component/version.
     *
     * ```
     * <root page>
     *   └── <component>
     *         └── <version>
     *               └── <page>
     * ```
     */
    COMPONENT_PAGE
}
