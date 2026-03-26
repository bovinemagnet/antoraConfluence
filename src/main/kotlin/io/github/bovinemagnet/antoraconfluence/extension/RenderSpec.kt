package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * AsciiDoc rendering settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     render {
 *         failOnUnresolvedXref.set(true)
 *         uploadImages.set(true)
 *         normalizeWhitespaceForDiff.set(true)
 *     }
 * }
 * ```
 */
abstract class RenderSpec @Inject constructor() {

    /**
     * When `true`, build fails if any Antora internal xref cannot be resolved to a
     * known managed Confluence page. Defaults to `false`.
     */
    abstract val failOnUnresolvedXref: Property<Boolean>

    /**
     * When `true`, locally referenced images are uploaded to the Confluence page as
     * attachments and references are rewritten appropriately. Defaults to `true`.
     */
    abstract val uploadImages: Property<Boolean>

    /**
     * When `true`, whitespace differences in the rendered HTML are ignored when computing
     * content fingerprints. This prevents spurious updates caused by whitespace-only
     * rendering changes. Defaults to `true`.
     */
    abstract val normalizeWhitespaceForDiff: Property<Boolean>
}
