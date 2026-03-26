package io.github.bovinemagnet.antoraconfluence.antora

import java.io.File

/**
 * A full content model for an Antora source tree, combining the list of discovered pages
 * with a manifest of available images keyed by filename.
 *
 * @property pages         All [AntoraPage] entries discovered under the content root.
 * @property imageManifest Map of image filename to its [File] on disk, collected from all
 *                         `images/` directories within the content tree.
 */
data class AntoraContentModel(
    val pages: List<AntoraPage>,
    val imageManifest: Map<String, File>
)
