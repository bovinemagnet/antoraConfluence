package io.github.bovinemagnet.antoraconfluence

/**
 * Controls how component versions are expressed in the Confluence page hierarchy.
 */
enum class VersionMode {
    /**
     * Each version is a separate level in the page hierarchy (default).
     * Produces: root → component → version → …
     */
    HIERARCHY,

    /**
     * Version is prepended to the page title rather than being a hierarchy level.
     * Produces: root → component → "[v1.0] Page Title"
     */
    TITLE_PREFIX
}
