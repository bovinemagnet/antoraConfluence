package io.github.bovinemagnet.antoraconfluence

/**
 * Defines how pages are managed during a publish operation.
 */
enum class PublishStrategy {
    /** Create new pages and update existing ones (default). */
    CREATE_AND_UPDATE,

    /** Only create new pages; never update existing Confluence pages. */
    CREATE_ONLY,

    /** Only update pages that already exist in Confluence; never create new ones. */
    UPDATE_ONLY
}
