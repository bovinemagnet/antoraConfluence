package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.file.RegularFileProperty
import javax.inject.Inject

/**
 * Report file settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     reports {
 *         jsonReportFile.set(layout.buildDirectory.file("reports/antora-confluence/publish.json"))
 *         planReportFile.set(layout.buildDirectory.file("reports/antora-confluence/plan.json"))
 *     }
 * }
 * ```
 */
abstract class ReportsSpec @Inject constructor() {

    /**
     * Output file for the JSON publish report produced by `antoraConfluencePublish`.
     * Defaults to `<buildDir>/antora-confluence/publish-report.json`.
     */
    abstract val jsonReportFile: RegularFileProperty

    /**
     * Output file for the JSON plan report produced by `antoraConfluencePlan`.
     * Defaults to `<buildDir>/antora-confluence/plan-report.json`.
     */
    abstract val planReportFile: RegularFileProperty
}
