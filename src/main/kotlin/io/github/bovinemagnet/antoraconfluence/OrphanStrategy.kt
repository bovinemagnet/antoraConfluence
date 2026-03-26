package io.github.bovinemagnet.antoraconfluence

/**
 * Defines what happens to managed Confluence pages whose Antora source file no longer exists.
 *
 * The default is [REPORT]: no destructive action is taken automatically.
 */
enum class OrphanStrategy {
    /**
     * List orphaned pages in the publish report only. No changes are made to Confluence (default).
     */
    REPORT,

    /**
     * Add a label (e.g. `antora-confluence-orphan`) to the Confluence page to make it
     * identifiable for manual review. The page itself is not deleted or archived.
     */
    LABEL,

    /**
     * Archive orphaned pages by moving them to a configurable archive location in Confluence.
     * The pages are not deleted.
     */
    ARCHIVE
}
