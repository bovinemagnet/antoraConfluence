package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Confluence connection settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     confluence {
 *         baseUrl.set("https://mycompany.atlassian.net/wiki")
 *         spaceKey.set("DOCS")
 *         parentPageId.set("123456789")
 *         username.set(providers.environmentVariable("CONFLUENCE_USER"))
 *         apiToken.set(providers.environmentVariable("CONFLUENCE_TOKEN"))
 *     }
 * }
 * ```
 */
abstract class ConfluenceSpec @Inject constructor() {

    /** Base URL of the Confluence instance, e.g. `https://mycompany.atlassian.net/wiki`. */
    abstract val baseUrl: Property<String>

    /** Key of the Confluence space where pages will be published, e.g. `DOCS`. */
    abstract val spaceKey: Property<String>

    /**
     * Numeric Confluence page ID of the parent page under which all published pages will be
     * organized. The parent page must already exist in the target space.
     *
     * Using a numeric ID (rather than a title) avoids fragile title-based lookups and is the
     * recommended approach for production pipelines.
     */
    abstract val parentPageId: Property<String>

    /** Confluence username — typically an email address for Confluence Cloud. */
    abstract val username: Property<String>

    /**
     * Confluence API token.
     * For Cloud: generate at https://id.atlassian.com/manage/api-tokens.
     * For Data Center: personal access token or password.
     */
    abstract val apiToken: Property<String>
}
