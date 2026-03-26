package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Top-level extension for the Antora Confluence plugin.
 *
 * Configuration is organized into six nested blocks:
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
 *     source {
 *         antoraRoot.set(layout.projectDirectory.dir("docs"))
 *         siteKey.set("my-site")
 *     }
 *     publish {
 *         hierarchy.set(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)
 *         orphanStrategy.set(OrphanStrategy.REPORT)
 *         dryRun.set(false)
 *     }
 *     render {
 *         uploadImages.set(true)
 *         normalizeWhitespaceForDiff.set(true)
 *     }
 *     state {
 *         rebuildFromRemoteOnMissing.set(true)
 *     }
 *     reports {
 *         jsonReportFile.set(layout.buildDirectory.file("reports/antora-confluence/publish.json"))
 *     }
 * }
 * ```
 */
abstract class AntoraConfluenceExtension @Inject constructor(objects: ObjectFactory) {

    /** Confluence connection settings (URL, credentials, space, parent page). */
    val confluence: ConfluenceSpec = objects.newInstance(ConfluenceSpec::class.java)

    /** Antora content source settings (root directory, site key). */
    val source: SourceSpec = objects.newInstance(SourceSpec::class.java)

    /** Publish behavior settings (hierarchy, strategy, orphan handling, labels, dry-run). */
    val publish: PublishSpec = objects.newInstance(PublishSpec::class.java)

    /** AsciiDoc rendering settings (xref handling, image upload, whitespace normalization). */
    val render: RenderSpec = objects.newInstance(RenderSpec::class.java)

    /** State file persistence settings. */
    val state: StateSpec = objects.newInstance(StateSpec::class.java)

    /** Report file path settings. */
    val reports: ReportsSpec = objects.newInstance(ReportsSpec::class.java)

    // -------------------------------------------------------------------------
    // DSL convenience methods for nested block configuration
    // -------------------------------------------------------------------------

    fun confluence(action: Action<ConfluenceSpec>) = action.execute(confluence)
    fun source(action: Action<SourceSpec>) = action.execute(source)
    fun publish(action: Action<PublishSpec>) = action.execute(publish)
    fun render(action: Action<RenderSpec>) = action.execute(render)
    fun state(action: Action<StateSpec>) = action.execute(state)
    fun reports(action: Action<ReportsSpec>) = action.execute(reports)
}
