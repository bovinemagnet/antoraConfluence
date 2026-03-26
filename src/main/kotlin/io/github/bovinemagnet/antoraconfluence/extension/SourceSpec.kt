package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Antora content source settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     source {
 *         antoraRoot.set(layout.projectDirectory.dir("docs"))
 *         siteKey.set("platform-docs")
 *     }
 * }
 * ```
 */
abstract class SourceSpec @Inject constructor() {

    /**
     * Root directory of the Antora content source tree. Defaults to `<projectDir>/docs`.
     * This directory must contain at least one `antora.yml` component descriptor.
     */
    abstract val antoraRoot: DirectoryProperty

    /**
     * A stable site-level identifier used to namespace the canonical page key.
     *
     * The full canonical key for a page is: `<siteKey>/<component>/<version>/<module>/<path>`
     *
     * This is important for multi-site or multi-project builds where the same component name
     * might appear in different Confluence spaces.
     */
    abstract val siteKey: Property<String>

    /**
     * Additional include directories scanned for AsciiDoc source files.
     * Use this when shared includes or partials live outside the main [antoraRoot].
     */
    abstract val includes: ConfigurableFileCollection

    /** When true, antoraConfluenceValidate will depend on the `antora` task if the org.antora plugin is applied. */
    abstract val dependsOnAntoraTask: Property<Boolean>
}
