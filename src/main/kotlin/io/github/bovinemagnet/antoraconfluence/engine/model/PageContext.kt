package io.github.bovinemagnet.antoraconfluence.engine.model

import java.io.File

data class PageContext(
    val resolvedXrefs: Map<String, String> = emptyMap(),
    val imageManifest: Map<String, File> = emptyMap(),
    val strict: Boolean = false
)
