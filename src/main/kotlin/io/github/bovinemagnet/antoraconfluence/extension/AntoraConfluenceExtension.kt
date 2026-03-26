package io.github.bovinemagnet.antoraconfluence.extension

import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration extension for the Antora Confluence plugin.
 *
 * Example usage in `build.gradle.kts`:
 * ```kotlin
 * antoraConfluence {
 *     confluenceUrl = "https://mycompany.atlassian.net/wiki"
 *     username = "user@example.com"
 *     apiToken = System.getenv("CONFLUENCE_API_TOKEN")
 *     spaceKey = "DOCS"
 *     parentPageTitle = "My Documentation"
 *     contentDir = layout.projectDirectory.dir("docs")
 *     publishStrategy = PublishStrategy.CREATE_AND_UPDATE
 *     dryRun = false
 * }
 * ```
 */
abstract class AntoraConfluenceExtension @Inject constructor() {

    /** Base URL of the Confluence instance (e.g. `https://mycompany.atlassian.net/wiki`). */
    abstract val confluenceUrl: Property<String>

    /** Confluence username (typically an email address for Confluence Cloud). */
    abstract val username: Property<String>

    /**
     * Confluence API token.
     * For Confluence Cloud, generate a token at https://id.atlassian.com/manage/api-tokens.
     * For Confluence Data Center, this is the user's password or a personal access token.
     */
    abstract val apiToken: Property<String>

    /** Key of the Confluence space where pages will be published (e.g. `DOCS`). */
    abstract val spaceKey: Property<String>

    /**
     * Title of the parent page under which all published pages will be organized.
     * The parent page must already exist in the target space.
     */
    abstract val parentPageTitle: Property<String>

    /**
     * Root directory of the Antora content source tree.
     * Defaults to `<projectDir>/docs`.
     */
    abstract val contentDir: DirectoryProperty

    /**
     * Controls how pages are managed during publish.
     * Defaults to [PublishStrategy.CREATE_AND_UPDATE].
     */
    abstract val publishStrategy: Property<PublishStrategy>

    /**
     * When `true`, no changes are written to Confluence.
     * The plugin will report what *would* happen without making any API calls that mutate state.
     * Defaults to `false`.
     */
    abstract val dryRun: Property<Boolean>
}
