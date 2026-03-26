package io.github.bovinemagnet.antoraconfluence.extension

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * State persistence settings nested inside [AntoraConfluenceExtension].
 *
 * ```kotlin
 * antoraConfluence {
 *     state {
 *         file.set(layout.buildDirectory.file("antora-confluence/state.json"))
 *         rebuildFromRemoteOnMissing.set(true)
 *     }
 * }
 * ```
 */
abstract class StateSpec @Inject constructor() {

    /**
     * Path to the local state / fingerprint store file.
     * Defaults to `<buildDir>/antora-confluence/state.json`.
     */
    abstract val file: RegularFileProperty

    /**
     * When `true` and the local state file is missing, the plugin attempts to rebuild it by
     * querying Confluence page properties for managed pages. This is useful after cloning
     * a repository fresh or when the build directory is wiped. Defaults to `true`.
     */
    abstract val rebuildFromRemoteOnMissing: Property<Boolean>
}
