package io.github.bovinemagnet.antoraconfluence.engine.model

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.VersionMode
import java.io.File

data class PublishRequest(
    val contentDir: File,
    val siteKey: String,
    val confluenceUrl: String,
    val username: String,
    val apiToken: String,
    val spaceKey: String,
    val parentPageId: String?,
    val publishStrategy: PublishStrategy,
    val orphanStrategy: OrphanStrategy,
    val hierarchyMode: HierarchyMode,
    val versionMode: VersionMode,
    val createIndexPages: Boolean,
    val strict: Boolean,
    val applyLabels: List<String>,
    val dryRun: Boolean,
    val forceAll: Boolean,
    val uploadImages: Boolean,
    val normalizeWhitespaceForDiff: Boolean,
    val failOnUnresolvedXref: Boolean,
    val stateFile: File,
    val reportFile: File
)

enum class PublishAction {
    CREATE, UPDATE, SKIP, ORPHAN, FAILED
}

data class PagePlan(
    val pageId: String,
    val title: String,
    val action: PublishAction,
    val reason: String,
    val sourceFile: String?
)

data class PublishResult(
    val pageId: String,
    val title: String,
    val action: PublishAction,
    val confluencePageId: String?,
    val error: String?
)

data class PublishSummary(
    val results: List<PublishResult>,
    val created: Int,
    val updated: Int,
    val skipped: Int,
    val failed: Int,
    val orphaned: Int,
    val dryRun: Boolean,
    val forceAll: Boolean
)
