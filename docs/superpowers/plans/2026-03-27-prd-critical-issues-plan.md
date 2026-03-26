# PRD Critical Issues Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all 10 critical issues identified in the PRD compliance review, plus the engine extraction refactor, Jackson YAML upgrade, and optional Gradle Antora plugin integration.

**Architecture:** Horizontal layers — build shared infrastructure (engine, scanner, converter) first, then wire features (hierarchy, labels, images, xrefs, fingerprinting, reconciliation) into it. Each layer builds on the previous.

**Tech Stack:** Kotlin 2.0, Gradle 8.x/9.x, JUnit 5, AssertJ, Gradle TestKit, OkHttp 4.12, Jackson 2.17, Asciidoctorj 3.0, Jsoup 1.18, jackson-dataformat-yaml 2.17

**Spec:** `docs/superpowers/specs/2026-03-27-prd-critical-issues-design.md`

**Build command:** `gradle21w build` (use `gradle21w` instead of `./gradlew`)

---

## File Structure

### New files to create

```
src/main/kotlin/io/github/bovinemagnet/antoraconfluence/
  engine/
    PublishEngine.kt              — Orchestrates the full publish pipeline
    HierarchyBuilder.kt           — Maps flat pages into Confluence page tree
    XrefResolver.kt               — Resolves Antora xrefs to Confluence page titles
    DependencyGraph.kt            — Tracks page→include/image/xref dependencies
    AntoraPluginIntegration.kt    — Optional org.antora plugin detection
    model/
      PublishModels.kt            — PublishRequest, PublishResult, PublishAction, PagePlan
      HierarchyNode.kt           — Tree node for Confluence page hierarchy
      PageContext.kt              — Per-page rendering context (xrefs, images)
  antora/
    AsciiDocReferenceExtractor.kt — Regex extraction of images, includes, xrefs, title
    AntoraContentModel.kt         — Wrapper for pages + image manifest
  confluence/
    ConfluenceStorageFormatConverter.kt — HTML5 → Confluence storage format via Jsoup

src/test/kotlin/io/github/bovinemagnet/antoraconfluence/
  engine/
    PublishEngineTest.kt
    HierarchyBuilderTest.kt
    XrefResolverTest.kt
    DependencyGraphTest.kt
  antora/
    AsciiDocReferenceExtractorTest.kt
    AsciiDocConverterTest.kt
  confluence/
    ConfluenceClientTest.kt
    ConfluenceStorageFormatConverterTest.kt
```

### Existing files to modify

```
build.gradle.kts                                          — Add jsoup, jackson-dataformat-yaml deps
src/main/kotlin/.../AntoraConfluencePlugin.kt             — Wire credentialsPresent, Antora plugin detection
src/main/kotlin/.../antora/AntoraContentScanner.kt        — Jackson YAML, reference extraction, image discovery
src/main/kotlin/.../antora/AsciiDocConverter.kt           — Rename method, add storage format pass
src/main/kotlin/.../confluence/ConfluenceClient.kt        — Add labels, properties, attachments, managed page listing
src/main/kotlin/.../confluence/model/ConfluenceModels.kt  — Add attachment, label, property models
src/main/kotlin/.../extension/SourceSpec.kt               — Add dependsOnAntoraTask property
src/main/kotlin/.../fingerprint/ContentFingerprintStore.kt — Enhanced state model with composite hash
src/main/kotlin/.../tasks/AntoraConfluencePublishTask.kt  — Thin shell delegating to PublishEngine
src/main/kotlin/.../tasks/AntoraConfluenceFullPublishTask.kt — Thin shell delegating to PublishEngine
src/main/kotlin/.../tasks/AntoraConfluencePlanTask.kt     — Delegate to engine for plan generation
src/main/kotlin/.../tasks/AntoraConfluenceReconcileStateTask.kt — Full implementation using page properties
src/main/kotlin/.../tasks/AntoraConfluenceValidateTask.kt — Add credential presence check
src/test/kotlin/.../AntoraConfluencePluginTest.kt         — Extend for new behaviours
src/test/kotlin/.../fingerprint/ContentFingerprintStoreTest.kt — Extend for enhanced state model
```

---

## Task 1: Add New Dependencies

**Files:**
- Modify: `build.gradle.kts:15-30`

- [ ] **Step 1: Add Jsoup and Jackson YAML dependencies to build.gradle.kts**

Add after the existing `asciidoctorjVersion` line (line 17):

```kotlin
val jsoupVersion = "1.18.1"
```

Add to the `dependencies` block after the existing `implementation` lines (after line 23):

```kotlin
implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
implementation("org.jsoup:jsoup:$jsoupVersion")
```

- [ ] **Step 2: Verify the build compiles with new dependencies**

Run: `gradle21w build`
Expected: BUILD SUCCESSFUL (compilation and existing tests pass)

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "Add Jsoup and Jackson YAML dependencies"
```

---

## Task 2: Create Engine Model Classes

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/model/PublishModels.kt`
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/model/HierarchyNode.kt`
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/model/PageContext.kt`

- [ ] **Step 1: Create PublishModels.kt**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine.model

/**
 * Bundles all inputs for a publish operation.
 */
data class PublishRequest(
    val contentDir: java.io.File,
    val siteKey: String,
    val confluenceUrl: String,
    val username: String,
    val apiToken: String,
    val spaceKey: String,
    val parentPageId: String?,
    val publishStrategy: io.github.bovinemagnet.antoraconfluence.PublishStrategy,
    val orphanStrategy: io.github.bovinemagnet.antoraconfluence.OrphanStrategy,
    val hierarchyMode: io.github.bovinemagnet.antoraconfluence.HierarchyMode,
    val versionMode: io.github.bovinemagnet.antoraconfluence.VersionMode,
    val createIndexPages: Boolean,
    val strict: Boolean,
    val applyLabels: List<String>,
    val dryRun: Boolean,
    val forceAll: Boolean,
    val uploadImages: Boolean,
    val normalizeWhitespaceForDiff: Boolean,
    val failOnUnresolvedXref: Boolean,
    val stateFile: java.io.File,
    val reportFile: java.io.File
)

/**
 * Action determined for a page during planning.
 */
enum class PublishAction {
    CREATE, UPDATE, SKIP, ORPHAN, FAILED
}

/**
 * Per-page entry in the publish plan.
 */
data class PagePlan(
    val pageId: String,
    val title: String,
    val action: PublishAction,
    val reason: String,
    val sourceFile: String?
)

/**
 * Result of publishing a single page.
 */
data class PublishResult(
    val pageId: String,
    val title: String,
    val action: PublishAction,
    val confluencePageId: String?,
    val error: String?
)

/**
 * Summary of a complete publish operation.
 */
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
```

- [ ] **Step 2: Create HierarchyNode.kt**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine.model

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage

/**
 * Type of node in the Confluence page hierarchy.
 */
enum class NodeType {
    COMPONENT, VERSION, MODULE, PAGE
}

/**
 * A node in the Confluence page tree. Intermediate nodes (COMPONENT, VERSION, MODULE)
 * are structural pages; leaf nodes (PAGE) correspond to actual AsciiDoc source pages.
 */
data class HierarchyNode(
    val canonicalKey: String,
    val title: String,
    val nodeType: NodeType,
    val children: MutableList<HierarchyNode> = mutableListOf(),
    val sourcePage: AntoraPage? = null,
    var htmlContent: String? = null,
    var confluencePageId: String? = null
)
```

- [ ] **Step 3: Create PageContext.kt**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine.model

/**
 * Per-page context passed to the Confluence storage format converter.
 * Contains resolved xref targets and image manifest for reference rewriting.
 */
data class PageContext(
    val resolvedXrefs: Map<String, String> = emptyMap(),
    val imageManifest: Map<String, java.io.File> = emptyMap(),
    val strict: Boolean = false
)
```

- [ ] **Step 4: Verify compilation**

Run: `gradle21w build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/
git commit -m "Add engine model classes for publish pipeline"
```

---

## Task 3: Extract Publishing Engine

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngineTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluencePublishTask.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceFullPublishTask.kt`

- [ ] **Step 1: Write failing test for PublishEngine**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.OrphanStrategy
import io.github.bovinemagnet.antoraconfluence.PublishStrategy
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishAction
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PublishEngineTest {

    @TempDir
    lateinit var tempDir: File

    private fun createAntoraContent(root: File): File {
        val docsDir = File(root, "docs").also { it.mkdirs() }
        File(docsDir, "antora.yml").writeText("name: test-comp\nversion: '1.0'\n")
        val pagesDir = File(docsDir, "modules/ROOT/pages").also { it.mkdirs() }
        File(pagesDir, "index.adoc").writeText("= Index\n\nHello world.\n")
        File(pagesDir, "guide.adoc").writeText("= Guide\n\nA guide.\n")
        return docsDir
    }

    private fun buildRequest(
        contentDir: File,
        dryRun: Boolean = true,
        forceAll: Boolean = false
    ): PublishRequest {
        val stateFile = File(tempDir, "state.json")
        val reportFile = File(tempDir, "report.json")
        return PublishRequest(
            contentDir = contentDir,
            siteKey = "test-site",
            confluenceUrl = "https://example.atlassian.net/wiki",
            username = "user@example.com",
            apiToken = "token",
            spaceKey = "DOCS",
            parentPageId = "123",
            publishStrategy = PublishStrategy.CREATE_AND_UPDATE,
            orphanStrategy = OrphanStrategy.REPORT,
            hierarchyMode = HierarchyMode.COMPONENT_VERSION_MODULE_PAGE,
            versionMode = VersionMode.HIERARCHY,
            createIndexPages = false,
            strict = false,
            applyLabels = listOf("docs-as-code"),
            dryRun = dryRun,
            forceAll = forceAll,
            uploadImages = true,
            normalizeWhitespaceForDiff = true,
            failOnUnresolvedXref = false,
            stateFile = stateFile,
            reportFile = reportFile
        )
    }

    @Test
    fun `dry run produces plan without making API calls`() {
        val docsDir = createAntoraContent(tempDir)
        val request = buildRequest(docsDir, dryRun = true)
        val engine = PublishEngine()
        val summary = engine.publish(request)
        assertThat(summary.dryRun).isTrue()
        assertThat(summary.results).isNotEmpty
        assertThat(summary.results).allMatch { it.action == PublishAction.CREATE }
    }

    @Test
    fun `forceAll flag is passed through to summary`() {
        val docsDir = createAntoraContent(tempDir)
        val request = buildRequest(docsDir, dryRun = true, forceAll = true)
        val engine = PublishEngine()
        val summary = engine.publish(request)
        assertThat(summary.forceAll).isTrue()
    }

    @Test
    fun `publish scans content and returns results for each page`() {
        val docsDir = createAntoraContent(tempDir)
        val request = buildRequest(docsDir, dryRun = true)
        val engine = PublishEngine()
        val summary = engine.publish(request)
        val pageIds = summary.results.map { it.pageId }
        assertThat(pageIds).anyMatch { it.contains("index") }
        assertThat(pageIds).anyMatch { it.contains("guide") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle21w test --tests "*.PublishEngineTest"`
Expected: FAIL — `PublishEngine` class does not exist

- [ ] **Step 3: Create PublishEngine with minimal implementation**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.antora.AntoraContentScanner
import io.github.bovinemagnet.antoraconfluence.antora.AsciiDocConverter
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishAction
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishRequest
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishResult
import io.github.bovinemagnet.antoraconfluence.engine.model.PublishSummary
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
import org.slf4j.LoggerFactory

/**
 * Orchestrates the full publish pipeline: scan → render → fingerprint → diff → plan → execute → report.
 *
 * Both [io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluencePublishTask] and
 * [io.github.bovinemagnet.antoraconfluence.tasks.AntoraConfluenceFullPublishTask] delegate to this engine.
 * The [PublishRequest.forceAll] flag controls whether fingerprint comparison is skipped.
 */
class PublishEngine {

    private val logger = LoggerFactory.getLogger(PublishEngine::class.java)

    fun publish(request: PublishRequest): PublishSummary {
        val scanner = AntoraContentScanner()
        val pages = scanner.scan(request.contentDir, request.siteKey)
        val store = ContentFingerprintStore(request.stateFile)

        val results = mutableListOf<PublishResult>()

        if (request.dryRun) {
            // Dry run: plan only, no API calls
            for (page in pages) {
                val content = page.sourceFile.readText()
                val action = if (request.forceAll || store.isChanged(page.pageId, content)) {
                    val existingEntry = store.get(page.pageId)
                    if (existingEntry?.confluencePageId != null) PublishAction.UPDATE else PublishAction.CREATE
                } else {
                    PublishAction.SKIP
                }
                results.add(
                    PublishResult(
                        pageId = page.pageId,
                        title = page.suggestedTitle,
                        action = action,
                        confluencePageId = store.get(page.pageId)?.confluencePageId,
                        error = null
                    )
                )
            }
        } else {
            // Live publish
            val client = ConfluenceClient(
                baseUrl = request.confluenceUrl,
                username = request.username,
                apiToken = request.apiToken
            )
            val space = client.getSpace(request.spaceKey)
                ?: throw IllegalStateException("Confluence space '${request.spaceKey}' not found")
            val spaceId = space.id

            AsciiDocConverter().use { converter ->
                for (page in pages) {
                    val result = publishPage(page, converter, client, store, spaceId, request)
                    results.add(result)
                }
            }

            // Orphan detection
            val sourcePageIds = pages.map { it.pageId }.toSet()
            val storedPageIds = store.allPageIds()
            val orphanedIds = storedPageIds - sourcePageIds
            handleOrphans(orphanedIds, store, request, results)

            store.save()
        }

        return buildSummary(results, request)
    }

    private fun publishPage(
        page: io.github.bovinemagnet.antoraconfluence.antora.AntoraPage,
        converter: AsciiDocConverter,
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        spaceId: String,
        request: PublishRequest
    ): PublishResult {
        val content = page.sourceFile.readText()

        // Check fingerprint unless forceAll
        if (!request.forceAll && !store.isChanged(page.pageId, content)) {
            return PublishResult(page.pageId, page.suggestedTitle, PublishAction.SKIP, store.get(page.pageId)?.confluencePageId, null)
        }

        return try {
            val html = converter.convert(page.sourceFile)
            val existingEntry = store.get(page.pageId)
            val existingPage = findExistingPage(client, store, page, spaceId)

            if (existingPage != null) {
                val versionNumber = existingPage.version?.number ?: 1
                val updated = client.updatePage(existingPage.id, page.suggestedTitle, html, versionNumber)
                store.put(page.pageId, content, updated.id, updated.title)
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.UPDATE, updated.id, null)
            } else {
                val created = client.createPage(spaceId, request.parentPageId, page.suggestedTitle, html)
                store.put(page.pageId, content, created.id, created.title)
                PublishResult(page.pageId, page.suggestedTitle, PublishAction.CREATE, created.id, null)
            }
        } catch (e: Exception) {
            logger.error("Failed to publish page {}: {}", page.pageId, e.message)
            if (request.strict) throw e
            PublishResult(page.pageId, page.suggestedTitle, PublishAction.FAILED, null, e.message)
        }
    }

    private fun findExistingPage(
        client: ConfluenceClient,
        store: ContentFingerprintStore,
        page: io.github.bovinemagnet.antoraconfluence.antora.AntoraPage,
        spaceId: String
    ): io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePage? {
        val entry = store.get(page.pageId)
        if (entry?.confluencePageId != null) {
            val existing = client.getPage(entry.confluencePageId)
            if (existing != null) return existing
        }
        return client.findPageByTitle(spaceId, page.suggestedTitle)
    }

    private fun handleOrphans(
        orphanedIds: Set<String>,
        store: ContentFingerprintStore,
        request: PublishRequest,
        results: MutableList<PublishResult>
    ) {
        if (orphanedIds.isEmpty()) return

        for (id in orphanedIds) {
            val entry = store.get(id)
            logger.warn("Orphaned page detected: {} (Confluence page: {})", id, entry?.confluencePageId)
            results.add(PublishResult(id, entry?.confluenceTitle ?: id, PublishAction.ORPHAN, entry?.confluencePageId, null))
        }

        when (request.orphanStrategy) {
            io.github.bovinemagnet.antoraconfluence.OrphanStrategy.REPORT -> {
                logger.info("Orphaned pages reported: {}", orphanedIds.size)
            }
            io.github.bovinemagnet.antoraconfluence.OrphanStrategy.LABEL -> {
                logger.warn("OrphanStrategy.LABEL not yet implemented — orphans reported only")
            }
            io.github.bovinemagnet.antoraconfluence.OrphanStrategy.ARCHIVE -> {
                logger.warn("OrphanStrategy.ARCHIVE not yet implemented — orphans reported only")
            }
        }

        if (request.strict && orphanedIds.isNotEmpty()) {
            throw IllegalStateException("Strict mode: ${orphanedIds.size} orphaned page(s) detected")
        }
    }

    private fun buildSummary(results: List<PublishResult>, request: PublishRequest): PublishSummary {
        return PublishSummary(
            results = results,
            created = results.count { it.action == PublishAction.CREATE },
            updated = results.count { it.action == PublishAction.UPDATE },
            skipped = results.count { it.action == PublishAction.SKIP },
            failed = results.count { it.action == PublishAction.FAILED },
            orphaned = results.count { it.action == PublishAction.ORPHAN },
            dryRun = request.dryRun,
            forceAll = request.forceAll
        )
    }

}
```

Note: The engine uses SLF4J directly (not Gradle's logger). All logging uses `logger.info` or `logger.warn`. The `lifecycle` log level is Gradle-only and must not be used in engine classes.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.PublishEngineTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Refactor PublishTask to delegate to engine**

Replace the `@TaskAction` method and private methods in `AntoraConfluencePublishTask.kt` (lines 83-317). Keep all the `@Input`/`@Output` property declarations (lines 36-80). The task becomes:

```kotlin
@TaskAction
fun publish() {
    val engine = PublishEngine()
    val request = PublishRequest(
        contentDir = contentDir.get().asFile,
        siteKey = siteKey.getOrElse(""),
        confluenceUrl = confluenceUrl.get(),
        username = username.get(),
        apiToken = apiToken.get(),
        spaceKey = spaceKey.get(),
        parentPageId = parentPageId.orNull,
        publishStrategy = publishStrategy.get(),
        orphanStrategy = orphanStrategy.get(),
        hierarchyMode = io.github.bovinemagnet.antoraconfluence.HierarchyMode.COMPONENT_VERSION_MODULE_PAGE,
        versionMode = io.github.bovinemagnet.antoraconfluence.VersionMode.HIERARCHY,
        createIndexPages = false,
        strict = strict.get(),
        applyLabels = applyLabels.getOrElse(emptyList()),
        dryRun = dryRun.get(),
        forceAll = false,
        uploadImages = true,
        normalizeWhitespaceForDiff = true,
        failOnUnresolvedXref = false,
        stateFile = fingerprintFile.get().asFile,
        reportFile = reportFile.get().asFile
    )
    val summary = engine.publish(request)
    logSummary(summary)
    writeReport(summary)
}

private fun logSummary(summary: PublishSummary) {
    val prefix = if (summary.dryRun) "[DRY RUN] " else ""
    for (result in summary.results) {
        logger.lifecycle("${prefix}${result.action}: ${result.pageId} (${result.title})")
    }
    logger.lifecycle("${prefix}Summary: ${summary.created} created, ${summary.updated} updated, ${summary.skipped} skipped, ${summary.failed} failed, ${summary.orphaned} orphaned")
}

private fun writeReport(summary: PublishSummary) {
    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    val report = mapOf(
        "timestamp" to java.time.Instant.now().toString(),
        "dryRun" to summary.dryRun,
        "type" to if (summary.forceAll) "FULL_PUBLISH" else "INCREMENTAL_PUBLISH",
        "results" to summary.results.map { mapOf(
            "pageId" to it.pageId,
            "title" to it.title,
            "action" to it.action.name,
            "confluencePageId" to it.confluencePageId,
            "error" to it.error
        )}
    )
    reportFile.get().asFile.parentFile.mkdirs()
    mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.get().asFile, report)
}
```

Remove all the old private methods: `publishPage`, `createPage`, `updatePage`, `findExistingPage`, `handleOrphans`, `validateRequiredInputs`, `logResult`, `logSummary`, `writeReport`. Also remove the `PublishAction` enum and `PublishResult` data class from the bottom of this file (they now live in `engine/model/PublishModels.kt`).

- [ ] **Step 6: Refactor FullPublishTask to delegate to engine**

Same approach as step 5 for `AntoraConfluenceFullPublishTask.kt`. The `@TaskAction` method becomes:

```kotlin
@TaskAction
fun fullPublish() {
    val engine = PublishEngine()
    val request = PublishRequest(
        contentDir = contentDir.get().asFile,
        siteKey = siteKey.getOrElse(""),
        confluenceUrl = confluenceUrl.get(),
        username = username.get(),
        apiToken = apiToken.get(),
        spaceKey = spaceKey.get(),
        parentPageId = parentPageId.orNull,
        publishStrategy = io.github.bovinemagnet.antoraconfluence.PublishStrategy.CREATE_AND_UPDATE,
        orphanStrategy = io.github.bovinemagnet.antoraconfluence.OrphanStrategy.REPORT,
        hierarchyMode = io.github.bovinemagnet.antoraconfluence.HierarchyMode.COMPONENT_VERSION_MODULE_PAGE,
        versionMode = io.github.bovinemagnet.antoraconfluence.VersionMode.HIERARCHY,
        createIndexPages = false,
        strict = strict.get(),
        applyLabels = applyLabels.getOrElse(emptyList()),
        dryRun = dryRun.get(),
        forceAll = true,
        uploadImages = true,
        normalizeWhitespaceForDiff = true,
        failOnUnresolvedXref = false,
        stateFile = fingerprintFile.get().asFile,
        reportFile = reportFile.get().asFile
    )
    val summary = engine.publish(request)
    logSummary(summary)
    writeReport(summary)
}
```

Add the same `logSummary` and `writeReport` private methods as in step 5. Remove all old private methods.

- [ ] **Step 7: Run all tests to verify nothing broke**

Run: `gradle21w test`
Expected: All existing tests pass, plus 3 new PublishEngine tests

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngineTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluencePublishTask.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceFullPublishTask.kt
git commit -m "Extract PublishEngine from task classes"
```

---

## Task 4: Credentials Safety

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluencePublishTask.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceFullPublishTask.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluencePlanTask.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceReconcileStateTask.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePlugin.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceValidateTask.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePluginTest.kt`

- [ ] **Step 1: Write failing test for credential safety**

Add to `AntoraConfluencePluginTest.kt`:

```kotlin
@Test
fun `validate task warns when credentials are missing with confluenceUrl set`() {
    createValidAntoraContent()
    writeBuildFile("""
        plugins {
            id("io.github.bovinemagnet.antora-confluence")
        }
        antoraConfluence {
            source {
                antoraRoot.set(layout.projectDirectory.dir("docs"))
            }
            confluence {
                baseUrl.set("https://example.atlassian.net/wiki")
                spaceKey.set("DOCS")
            }
        }
    """)
    val result = runner("antoraConfluenceValidate").build()
    assertThat(result.output).contains("username")
    assertThat(result.output).contains("apiToken")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle21w test --tests "*.AntoraConfluencePluginTest.validate task warns when credentials are missing with confluenceUrl set"`
Expected: FAIL — validate task does not check credentials

- [ ] **Step 3: Change @Input to @Internal on credential properties**

In `AntoraConfluencePublishTask.kt`, change the annotations on `username` and `apiToken`:

From:
```kotlin
@get:Input
abstract val username: Property<String>

@get:Input
abstract val apiToken: Property<String>
```

To:
```kotlin
@get:Internal
abstract val username: Property<String>

@get:Internal
abstract val apiToken: Property<String>
```

Add a new property:
```kotlin
@get:Input
abstract val credentialsPresent: Property<Boolean>
```

Apply the same changes to `AntoraConfluenceFullPublishTask.kt`, `AntoraConfluencePlanTask.kt`, and `AntoraConfluenceReconcileStateTask.kt`.

- [ ] **Step 4: Wire credentialsPresent in AntoraConfluencePlugin.kt**

After each task's existing `.configure { }` block that has `username` and `apiToken`, add:

```kotlin
credentialsPresent.set(
    extension.confluence.username.zip(extension.confluence.apiToken) { u, t ->
        u.isNotBlank() && t.isNotBlank()
    }.orElse(false)
)
```

- [ ] **Step 5: Add credential presence check to ValidateTask**

In `AntoraConfluenceValidateTask.kt`, add after the existing `spaceKey` property:

```kotlin
@get:Input
@get:Optional
abstract val credentialsPresent: Property<Boolean>
```

Add to the `validate()` method, after the existing warning checks (after line 63):

```kotlin
if (confluenceUrl.isPresent) {
    if (!credentialsPresent.getOrElse(false)) {
        logger.warn("WARNING: confluence.baseUrl is set but username and/or apiToken are missing.")
    }
}
```

Wire in `AntoraConfluencePlugin.kt` for the validate task:

```kotlin
credentialsPresent.set(
    extension.confluence.username.zip(extension.confluence.apiToken) { u, t ->
        u.isNotBlank() && t.isNotBlank()
    }.orElse(false)
)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `gradle21w test`
Expected: All tests pass including the new credential warning test

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePlugin.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePluginTest.kt
git commit -m "Fix credential safety: change @Input to @Internal on secrets"
```

---

## Task 5: Jackson YAML Upgrade & AsciiDoc Reference Extractor

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AntoraContentScanner.kt`
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocReferenceExtractor.kt`
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AntoraContentModel.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocReferenceExtractorTest.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AntoraContentScannerTest.kt`

- [ ] **Step 1: Write failing tests for AsciiDocReferenceExtractor**

```kotlin
package io.github.bovinemagnet.antoraconfluence.antora

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AsciiDocReferenceExtractorTest {

    private val extractor = AsciiDocReferenceExtractor()

    @Test
    fun `extracts document title from first heading`() {
        val content = "= My Page Title\n\nSome content."
        val refs = extractor.extract(content)
        assertThat(refs.title).isEqualTo("My Page Title")
    }

    @Test
    fun `returns null title when no heading present`() {
        val content = "Some content without a title."
        val refs = extractor.extract(content)
        assertThat(refs.title).isNull()
    }

    @Test
    fun `extracts block image references`() {
        val content = "= Title\n\nimage::diagram.png[A diagram]\n\nMore text."
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactly("diagram.png")
    }

    @Test
    fun `extracts inline image references`() {
        val content = "See image:icon.svg[icon] for details."
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactly("icon.svg")
    }

    @Test
    fun `extracts include directives`() {
        val content = "= Title\n\ninclude::partial\$header.adoc[]\n\ninclude::_partials/footer.adoc[]"
        val refs = extractor.extract(content)
        assertThat(refs.includes).containsExactlyInAnyOrder("partial\$header.adoc", "_partials/footer.adoc")
    }

    @Test
    fun `extracts xref targets`() {
        val content = "See xref:getting-started.adoc[] and xref:admin:setup.adoc[setup guide]."
        val refs = extractor.extract(content)
        assertThat(refs.xrefs).containsExactlyInAnyOrder("getting-started.adoc", "admin:setup.adoc")
    }

    @Test
    fun `handles content with no references`() {
        val content = "= Simple Page\n\nJust plain text."
        val refs = extractor.extract(content)
        assertThat(refs.images).isEmpty()
        assertThat(refs.includes).isEmpty()
        assertThat(refs.xrefs).isEmpty()
    }

    @Test
    fun `ignores commented-out references`() {
        val content = "= Title\n\n// image::hidden.png[]\n\nimage::visible.png[]"
        val refs = extractor.extract(content)
        assertThat(refs.images).containsExactly("visible.png")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.AsciiDocReferenceExtractorTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create AsciiDocReferenceExtractor**

```kotlin
package io.github.bovinemagnet.antoraconfluence.antora

/**
 * Extracted references from an AsciiDoc source file.
 */
data class ExtractedReferences(
    val title: String?,
    val images: List<String>,
    val includes: List<String>,
    val xrefs: List<String>
)

/**
 * Lightweight regex-based extractor for AsciiDoc references.
 * Runs during scanning (before Asciidoctorj rendering) to build
 * the dependency graph and content model.
 */
class AsciiDocReferenceExtractor {

    private val titlePattern = Regex("""^= (.+)$""", RegexOption.MULTILINE)
    private val blockImagePattern = Regex("""^image::([^\[]+)\[""", RegexOption.MULTILINE)
    private val inlineImagePattern = Regex("""(?<!\n)(?<!^)image:([^\[:\n]+)\[""", RegexOption.MULTILINE)
    private val includePattern = Regex("""^include::([^\[]+)\[""", RegexOption.MULTILINE)
    private val xrefPattern = Regex("""xref:([^\[]+)\[""")
    private val commentPattern = Regex("""^//.*$""", RegexOption.MULTILINE)

    fun extract(content: String): ExtractedReferences {
        // Strip single-line comments to avoid extracting commented-out references
        val uncommented = content.replace(commentPattern, "")

        val title = titlePattern.find(uncommented)?.groupValues?.get(1)?.trim()

        val images = (blockImagePattern.findAll(uncommented).map { it.groupValues[1].trim() } +
            inlineImagePattern.findAll(uncommented).map { it.groupValues[1].trim() })
            .toList()

        val includes = includePattern.findAll(uncommented)
            .map { it.groupValues[1].trim() }
            .toList()

        val xrefs = xrefPattern.findAll(uncommented)
            .map { it.groupValues[1].trim() }
            .toList()

        return ExtractedReferences(
            title = title,
            images = images,
            includes = includes,
            xrefs = xrefs
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.AsciiDocReferenceExtractorTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Create AntoraContentModel**

```kotlin
package io.github.bovinemagnet.antoraconfluence.antora

import java.io.File

/**
 * Complete content model returned by [AntoraContentScanner].
 * Contains discovered pages, their extracted references, and the image manifest.
 */
data class AntoraContentModel(
    val pages: List<AntoraPage>,
    val imageManifest: Map<String, File>
)
```

- [ ] **Step 6: Upgrade AntoraContentScanner to use Jackson YAML**

In `AntoraContentScanner.kt`, replace the `parseAntoraYml` method (lines 137-153) with:

```kotlin
internal fun parseAntoraYml(antoraYml: File): AntoraComponentDescriptor {
    val mapper = com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
    val tree: Map<String, Any?> = mapper.readValue(
        antoraYml,
        com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {}
    )
    val name = tree["name"]?.toString()?.trim() ?: ""
    val version = tree["version"]?.toString()?.trim() ?: ""
    val title = tree["title"]?.toString()?.trim() ?: ""
    if (name.isBlank()) {
        throw AntoraStructureException("Missing 'name' field in ${antoraYml.absolutePath}")
    }
    return AntoraComponentDescriptor(name = name, version = version, title = title)
}
```

Add imports at the top:
```kotlin
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
```

- [ ] **Step 7: Add reference extraction and image discovery to AntoraContentScanner**

Add a `private val referenceExtractor = AsciiDocReferenceExtractor()` field.

Modify the `scanPages` method to extract references and populate the `title` field. Update `AntoraPage` to include the new fields:

In `AntoraPage` data class, add:
```kotlin
val title: String,
val images: List<String> = emptyList(),
val includes: List<String> = emptyList(),
val xrefs: List<String> = emptyList()
```

Update `scanPages` to populate these from `referenceExtractor.extract()`.

Add a new method to the scanner:
```kotlin
fun scanFull(contentDir: File, siteKey: String = ""): AntoraContentModel {
    val pages = scan(contentDir, siteKey)
    val imageManifest = discoverImages(contentDir)
    return AntoraContentModel(pages, imageManifest)
}

private fun discoverImages(contentDir: File): Map<String, File> {
    val manifest = mutableMapOf<String, File>()
    contentDir.walkTopDown()
        .filter { it.isFile && it.parentFile.name == "images" }
        .forEach { manifest[it.name] = it }
    return manifest
}
```

- [ ] **Step 8: Update existing scanner tests for YAML upgrade**

Run: `gradle21w test --tests "*.AntoraContentScannerTest"`
Expected: PASS — the YAML upgrade should be backwards compatible with existing test fixtures

- [ ] **Step 9: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/antora/
git commit -m "Upgrade YAML parsing to Jackson, add reference extractor and content model"
```

---

## Task 6: Confluence Storage Format Converter

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceStorageFormatConverter.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceStorageFormatConverterTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocConverter.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocConverterTest.kt`

- [ ] **Step 1: Write failing tests for storage format converter**

```kotlin
package io.github.bovinemagnet.antoraconfluence.confluence

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfluenceStorageFormatConverterTest {

    private val converter = ConfluenceStorageFormatConverter()

    @Test
    fun `converts code block to structured macro`() {
        val html = """<pre><code class="language-java">System.out.println("hello");</code></pre>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("""ac:name="code"""")
        assertThat(result).contains("""ac:name="language">java""")
        assertThat(result).contains("System.out.println")
    }

    @Test
    fun `converts note admonition to info macro`() {
        val html = """<div class="admonitionblock note"><table><tbody><tr><td class="content">Important info here.</td></tr></tbody></table></div>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("""ac:name="info"""")
        assertThat(result).contains("Important info here.")
    }

    @Test
    fun `converts warning admonition to warning macro`() {
        val html = """<div class="admonitionblock warning"><table><tbody><tr><td class="content">Be careful.</td></tr></tbody></table></div>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("""ac:name="warning"""")
    }

    @Test
    fun `converts tip admonition to tip macro`() {
        val html = """<div class="admonitionblock tip"><table><tbody><tr><td class="content">A tip.</td></tr></tbody></table></div>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("""ac:name="tip"""")
    }

    @Test
    fun `converts caution admonition to note macro`() {
        val html = """<div class="admonitionblock caution"><table><tbody><tr><td class="content">Caution.</td></tr></tbody></table></div>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("""ac:name="note"""")
    }

    @Test
    fun `rewrites local image to attachment macro`() {
        val html = """<img src="diagram.png" alt="diagram">"""
        val context = PageContext(imageManifest = mapOf("diagram.png" to java.io.File("diagram.png")))
        val result = converter.convert(html, context)
        assertThat(result).contains("""<ac:image""")
        assertThat(result).contains("""ri:filename="diagram.png"""")
    }

    @Test
    fun `leaves external image as-is`() {
        val html = """<img src="https://example.com/logo.png" alt="logo">"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("https://example.com/logo.png")
    }

    @Test
    fun `rewrites internal xref link to confluence link`() {
        val html = """<a href="getting-started.adoc">Getting Started</a>"""
        val context = PageContext(resolvedXrefs = mapOf("getting-started.adoc" to "Getting Started"))
        val result = converter.convert(html, context)
        assertThat(result).contains("""<ac:link""")
        assertThat(result).contains("""ri:content-title="Getting Started"""")
    }

    @Test
    fun `leaves external link as-is`() {
        val html = """<a href="https://example.com">Example</a>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("https://example.com")
    }

    @Test
    fun `leaves tables as-is`() {
        val html = """<table><tr><td>Cell</td></tr></table>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("<table>")
        assertThat(result).contains("<td>Cell</td>")
    }

    @Test
    fun `passes through unknown elements unchanged`() {
        val html = """<p>Normal paragraph.</p>"""
        val result = converter.convert(html, PageContext())
        assertThat(result).contains("<p>Normal paragraph.</p>")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.ConfluenceStorageFormatConverterTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create ConfluenceStorageFormatConverter**

```kotlin
package io.github.bovinemagnet.antoraconfluence.confluence

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory

/**
 * Transforms HTML5 output from Asciidoctorj into Confluence storage format.
 * Uses Jsoup for DOM-based transformation.
 */
class ConfluenceStorageFormatConverter {

    private val logger = LoggerFactory.getLogger(ConfluenceStorageFormatConverter::class.java)

    private val admonitionMapping = mapOf(
        "note" to "info",
        "tip" to "tip",
        "warning" to "warning",
        "caution" to "note",
        "important" to "warning"
    )

    fun convert(html: String, context: PageContext): String {
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
            .prettyPrint(false)

        convertCodeBlocks(doc)
        convertAdmonitions(doc)
        convertImages(doc, context)
        convertLinks(doc, context)

        return doc.body().html()
    }

    private fun convertCodeBlocks(doc: Document) {
        for (pre in doc.select("pre:has(code)")) {
            val code = pre.selectFirst("code") ?: continue
            val language = code.classNames()
                .firstOrNull { it.startsWith("language-") }
                ?.removePrefix("language-")
                ?: ""

            val codeContent = code.wholeText()

            val macro = StringBuilder()
            macro.append("""<ac:structured-macro ac:name="code">""")
            if (language.isNotEmpty()) {
                macro.append("""<ac:parameter ac:name="language">$language</ac:parameter>""")
            }
            macro.append("""<ac:plain-text-body><![CDATA[$codeContent]]></ac:plain-text-body>""")
            macro.append("""</ac:structured-macro>""")

            pre.before(macro.toString())
            pre.remove()
        }
    }

    private fun convertAdmonitions(doc: Document) {
        for (div in doc.select("div.admonitionblock")) {
            val type = div.classNames()
                .firstOrNull { it != "admonitionblock" }
                ?: continue
            val macroName = admonitionMapping[type] ?: "info"
            val contentTd = div.selectFirst("td.content")
            val content = contentTd?.html() ?: div.html()

            val macro = """<ac:structured-macro ac:name="$macroName"><ac:rich-text-body>$content</ac:rich-text-body></ac:structured-macro>"""

            div.before(macro)
            div.remove()
        }
    }

    private fun convertImages(doc: Document, context: PageContext) {
        for (img in doc.select("img")) {
            val src = img.attr("src")
            if (src.startsWith("http://") || src.startsWith("https://")) {
                continue // External image — leave as-is
            }
            val fileName = src.substringAfterLast("/")
            if (context.imageManifest.containsKey(fileName)) {
                val macro = """<ac:image><ri:attachment ri:filename="$fileName"/></ac:image>"""
                img.before(macro)
                img.remove()
            } else {
                logger.warn("Image not found in manifest: {}", src)
                if (context.strict) {
                    throw IllegalStateException("Strict mode: image not found in manifest: $src")
                }
            }
        }
    }

    private fun convertLinks(doc: Document, context: PageContext) {
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("#")) {
                continue // External or anchor link — leave as-is
            }
            val resolvedTitle = context.resolvedXrefs[href]
            if (resolvedTitle != null) {
                val linkText = a.text()
                val macro = """<ac:link><ri:page ri:content-title="$resolvedTitle"/><ac:plain-text-link-body><![CDATA[$linkText]]></ac:plain-text-link-body></ac:link>"""
                a.before(macro)
                a.remove()
            } else {
                logger.warn("Unresolved internal link: {}", href)
                if (context.strict) {
                    throw IllegalStateException("Strict mode: unresolved internal link: $href")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.ConfluenceStorageFormatConverterTest"`
Expected: PASS (11 tests)

- [ ] **Step 5: Write test for AsciiDocConverter storage format method**

```kotlin
package io.github.bovinemagnet.antoraconfluence.antora

import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AsciiDocConverterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `renderToHtml produces HTML output`() {
        val adoc = File(tempDir, "test.adoc").apply { writeText("= Test\n\nHello world.") }
        AsciiDocConverter().use { converter ->
            val html = converter.renderToHtml(adoc)
            assertThat(html).contains("Hello world")
        }
    }

    @Test
    fun `renderToConfluenceStorage produces Confluence format`() {
        val adoc = File(tempDir, "test.adoc").apply {
            writeText("= Test\n\n[NOTE]\n====\nImportant info.\n====\n")
        }
        AsciiDocConverter().use { converter ->
            val storage = converter.renderToConfluenceStorage(adoc, PageContext())
            assertThat(storage).contains("ac:name=\"info\"")
            assertThat(storage).contains("Important info")
        }
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `gradle21w test --tests "*.AsciiDocConverterTest"`
Expected: FAIL — `renderToHtml` and `renderToConfluenceStorage` methods do not exist

- [ ] **Step 7: Update AsciiDocConverter with new methods**

In `AsciiDocConverter.kt`, rename `convert` to `renderToHtml` and `convertString` to `renderStringToHtml`. Add:

```kotlin
fun renderToConfluenceStorage(
    sourceFile: File,
    context: PageContext,
    attributes: Map<String, String> = emptyMap()
): String {
    val html = renderToHtml(sourceFile, attributes)
    return ConfluenceStorageFormatConverter().convert(html, context)
}

fun renderStringToConfluenceStorage(
    content: String,
    context: PageContext,
    attributes: Map<String, String> = emptyMap()
): String {
    val html = renderStringToHtml(content, attributes)
    return ConfluenceStorageFormatConverter().convert(html, context)
}
```

Add import:
```kotlin
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceStorageFormatConverter
import io.github.bovinemagnet.antoraconfluence.engine.model.PageContext
```

Also update `PublishEngine` to call `renderToConfluenceStorage` instead of `convert`.

- [ ] **Step 8: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceStorageFormatConverter.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceStorageFormatConverterTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocConverter.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/antora/AsciiDocConverterTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git commit -m "Add Confluence storage format converter and update renderer"
```

---

## Task 7: Hierarchy Builder

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/HierarchyBuilder.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/HierarchyBuilderTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`

- [ ] **Step 1: Write failing tests for HierarchyBuilder**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.engine.model.NodeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class HierarchyBuilderTest {

    private fun page(component: String, version: String, module: String, name: String, siteKey: String = "") =
        AntoraPage(
            siteKey = siteKey,
            componentName = component,
            componentVersion = version,
            moduleName = module,
            relativePath = "$name.adoc",
            sourceFile = File("/tmp/$name.adoc"),
            title = name.replaceFirstChar { it.uppercaseChar() },
            images = emptyList(),
            includes = emptyList(),
            xrefs = emptyList()
        )

    @Test
    fun `COMPONENT_VERSION_MODULE_PAGE creates full hierarchy`() {
        val pages = listOf(
            page("comp", "1.0", "ROOT", "index"),
            page("comp", "1.0", "ROOT", "guide")
        )
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, false)

        assertThat(roots).hasSize(1)
        val comp = roots[0]
        assertThat(comp.nodeType).isEqualTo(NodeType.COMPONENT)
        assertThat(comp.title).isEqualTo("comp")

        val version = comp.children[0]
        assertThat(version.nodeType).isEqualTo(NodeType.VERSION)
        assertThat(version.title).isEqualTo("1.0")

        val module = version.children[0]
        assertThat(module.nodeType).isEqualTo(NodeType.MODULE)

        assertThat(module.children).hasSize(2)
        assertThat(module.children.map { it.nodeType }).allMatch { it == NodeType.PAGE }
    }

    @Test
    fun `COMPONENT_VERSION_PAGE combines component and version`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"))
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_PAGE, VersionMode.HIERARCHY, false)

        assertThat(roots).hasSize(1)
        val combined = roots[0]
        assertThat(combined.nodeType).isEqualTo(NodeType.COMPONENT)
        assertThat(combined.title).isEqualTo("comp/1.0")

        val module = combined.children[0]
        assertThat(module.nodeType).isEqualTo(NodeType.MODULE)
    }

    @Test
    fun `COMPONENT_PAGE flattens version and module`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"))
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_PAGE, VersionMode.HIERARCHY, false)

        assertThat(roots).hasSize(1)
        val comp = roots[0]
        assertThat(comp.nodeType).isEqualTo(NodeType.COMPONENT)
        assertThat(comp.children[0].nodeType).isEqualTo(NodeType.PAGE)
    }

    @Test
    fun `TITLE_PREFIX prepends version to page title`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "guide"))
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_PAGE, VersionMode.TITLE_PREFIX, false)

        val leafPage = roots[0].children[0]
        assertThat(leafPage.title).isEqualTo("1.0 - Guide")
    }

    @Test
    fun `createIndexPages generates content for intermediate nodes`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"))
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, true)

        assertThat(roots[0].htmlContent).isNotNull()
        assertThat(roots[0].htmlContent).contains("comp")
    }

    @Test
    fun `multiple components produce multiple root nodes`() {
        val pages = listOf(
            page("comp-a", "1.0", "ROOT", "index"),
            page("comp-b", "2.0", "ROOT", "index")
        )
        val builder = HierarchyBuilder()
        val roots = builder.build(pages, HierarchyMode.COMPONENT_VERSION_MODULE_PAGE, VersionMode.HIERARCHY, false)

        assertThat(roots).hasSize(2)
        assertThat(roots.map { it.title }).containsExactlyInAnyOrder("comp-a", "comp-b")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.HierarchyBuilderTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create HierarchyBuilder**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.HierarchyMode
import io.github.bovinemagnet.antoraconfluence.VersionMode
import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import io.github.bovinemagnet.antoraconfluence.engine.model.HierarchyNode
import io.github.bovinemagnet.antoraconfluence.engine.model.NodeType

/**
 * Maps a flat list of [AntoraPage] entries into a Confluence page tree
 * based on [HierarchyMode] and [VersionMode] configuration.
 */
class HierarchyBuilder {

    fun build(
        pages: List<AntoraPage>,
        hierarchyMode: HierarchyMode,
        versionMode: VersionMode,
        createIndexPages: Boolean
    ): List<HierarchyNode> {
        return when (hierarchyMode) {
            HierarchyMode.COMPONENT_VERSION_MODULE_PAGE -> buildFullHierarchy(pages, versionMode, createIndexPages)
            HierarchyMode.COMPONENT_VERSION_PAGE -> buildComponentVersionPage(pages, versionMode, createIndexPages)
            HierarchyMode.COMPONENT_PAGE -> buildComponentPage(pages, versionMode, createIndexPages)
        }
    }

    private fun buildFullHierarchy(
        pages: List<AntoraPage>,
        versionMode: VersionMode,
        createIndexPages: Boolean
    ): List<HierarchyNode> {
        val byComponent = pages.groupBy { it.componentName }
        return byComponent.map { (compName, compPages) ->
            val compNode = HierarchyNode(
                canonicalKey = compKeyPrefix(compPages.first()) + compName,
                title = compName,
                nodeType = NodeType.COMPONENT,
                htmlContent = if (createIndexPages) generateIndexPage(compName, compPages.map { it.componentVersion }.distinct()) else null
            )
            val byVersion = compPages.groupBy { it.componentVersion }
            for ((version, versionPages) in byVersion) {
                val versionNode = HierarchyNode(
                    canonicalKey = "${compNode.canonicalKey}/$version",
                    title = version.ifBlank { "default" },
                    nodeType = NodeType.VERSION,
                    htmlContent = if (createIndexPages) generateIndexPage(version, versionPages.map { it.moduleName }.distinct()) else null
                )
                val byModule = versionPages.groupBy { it.moduleName }
                for ((moduleName, modulePages) in byModule) {
                    val moduleNode = HierarchyNode(
                        canonicalKey = "${versionNode.canonicalKey}/$moduleName",
                        title = moduleName,
                        nodeType = NodeType.MODULE,
                        htmlContent = if (createIndexPages) generateIndexPage(moduleName, modulePages.map { it.title }) else null
                    )
                    for (page in modulePages) {
                        moduleNode.children.add(pageToNode(page, moduleNode.canonicalKey, versionMode))
                    }
                    versionNode.children.add(moduleNode)
                }
                compNode.children.add(versionNode)
            }
            compNode
        }
    }

    private fun buildComponentVersionPage(
        pages: List<AntoraPage>,
        versionMode: VersionMode,
        createIndexPages: Boolean
    ): List<HierarchyNode> {
        val byComponentVersion = pages.groupBy { "${it.componentName}/${it.componentVersion}" }
        return byComponentVersion.map { (key, cvPages) ->
            val compNode = HierarchyNode(
                canonicalKey = compKeyPrefix(cvPages.first()) + key,
                title = key,
                nodeType = NodeType.COMPONENT,
                htmlContent = if (createIndexPages) generateIndexPage(key, cvPages.map { it.moduleName }.distinct()) else null
            )
            val byModule = cvPages.groupBy { it.moduleName }
            for ((moduleName, modulePages) in byModule) {
                val moduleNode = HierarchyNode(
                    canonicalKey = "${compNode.canonicalKey}/$moduleName",
                    title = moduleName,
                    nodeType = NodeType.MODULE,
                    htmlContent = if (createIndexPages) generateIndexPage(moduleName, modulePages.map { it.title }) else null
                )
                for (page in modulePages) {
                    moduleNode.children.add(pageToNode(page, moduleNode.canonicalKey, versionMode))
                }
                compNode.children.add(moduleNode)
            }
            compNode
        }
    }

    private fun buildComponentPage(
        pages: List<AntoraPage>,
        versionMode: VersionMode,
        createIndexPages: Boolean
    ): List<HierarchyNode> {
        val byComponent = pages.groupBy { it.componentName }
        return byComponent.map { (compName, compPages) ->
            val compNode = HierarchyNode(
                canonicalKey = compKeyPrefix(compPages.first()) + compName,
                title = compName,
                nodeType = NodeType.COMPONENT,
                htmlContent = if (createIndexPages) generateIndexPage(compName, compPages.map { it.title }) else null
            )
            for (page in compPages) {
                compNode.children.add(pageToNode(page, compNode.canonicalKey, versionMode))
            }
            compNode
        }
    }

    private fun pageToNode(page: AntoraPage, parentKey: String, versionMode: VersionMode): HierarchyNode {
        val title = when (versionMode) {
            VersionMode.HIERARCHY -> page.title
            VersionMode.TITLE_PREFIX -> if (page.componentVersion.isNotBlank()) "${page.componentVersion} - ${page.title}" else page.title
        }
        return HierarchyNode(
            canonicalKey = page.pageId,
            title = title,
            nodeType = NodeType.PAGE,
            sourcePage = page
        )
    }

    private fun compKeyPrefix(page: AntoraPage): String =
        if (page.siteKey.isNotBlank()) "${page.siteKey}/" else ""

    private fun generateIndexPage(title: String, items: List<String>): String {
        val listItems = items.joinToString("\n") { "<li>$it</li>" }
        return "<h1>$title</h1>\n<ul>\n$listItems\n</ul>"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.HierarchyBuilderTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Integrate HierarchyBuilder into PublishEngine**

Update `PublishEngine.publish()` to call `HierarchyBuilder.build()` and walk the tree top-down when publishing. The engine should:
1. Build the hierarchy tree
2. Walk top-down, creating/updating parent pages first
3. Pass each node's Confluence page ID as parentId to its children
4. Track intermediate nodes in the fingerprint store

This is a significant change to the engine's publish loop. Replace the flat page iteration with a recursive tree walk.

- [ ] **Step 6: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/HierarchyBuilder.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/HierarchyBuilderTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git commit -m "Add hierarchy builder for Confluence page tree mapping"
```

---

## Task 8: Labels & Page Properties

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceClient.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/model/ConfluenceModels.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/ConfluenceClientTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`

- [ ] **Step 1: Write failing tests for new ConfluenceClient methods**

```kotlin
package io.github.bovinemagnet.antoraconfluence.confluence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ConfluenceClient
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        client = ConfluenceClient(
            baseUrl = server.url("/wiki").toString(),
            username = "user",
            apiToken = "token"
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `addLabels sends POST with label body`() {
        server.enqueue(MockResponse().setBody("""{"results":[]}""").setResponseCode(200))
        client.addLabels("123", listOf("managed-by-antora-confluence", "docs"))
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/pages/123/labels")
        val body = request.body.readUtf8()
        assertThat(body).contains("managed-by-antora-confluence")
        assertThat(body).contains("docs")
    }

    @Test
    fun `setPageProperty sends POST with property body`() {
        server.enqueue(MockResponse().setBody("""{"id":"prop-1","key":"test-key","value":"test-value"}""").setResponseCode(200))
        client.setPageProperty("123", "test-key", "test-value")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/pages/123/properties")
        val body = request.body.readUtf8()
        assertThat(body).contains("test-key")
        assertThat(body).contains("test-value")
    }

    @Test
    fun `getPageProperty returns value when found`() {
        server.enqueue(MockResponse().setBody("""{"id":"prop-1","key":"my-key","value":"my-value"}""").setResponseCode(200))
        val value = client.getPageProperty("123", "my-key")
        assertThat(value).isEqualTo("my-value")
    }

    @Test
    fun `getPageProperty returns null when not found`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"errors":[]}"""))
        val value = client.getPageProperty("123", "missing-key")
        assertThat(value).isNull()
    }

    @Test
    fun `uploadAttachment sends multipart POST`() {
        server.enqueue(MockResponse().setBody("""{"results":[{"id":"att-1","title":"file.png"}]}""").setResponseCode(200))
        val tempFile = java.io.File.createTempFile("test", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val id = client.uploadAttachment("123", "file.png", tempFile, "image/png")
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).contains("/pages/123/attachments")
            assertThat(request.getHeader("Content-Type")).contains("multipart")
            assertThat(id).isEqualTo("att-1")
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `listManagedPages returns pages with given label`() {
        val responseBody = """{"results":[{"id":"456","title":"Managed Page","spaceId":"space-1","status":"current"}]}"""
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))
        val pages = client.listManagedPages("space-1", "managed-by-antora-confluence")
        assertThat(pages).hasSize(1)
        assertThat(pages[0].id).isEqualTo("456")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.ConfluenceClientTest"`
Expected: FAIL — methods do not exist

- [ ] **Step 3: Add new model classes to ConfluenceModels.kt**

Add to `ConfluenceModels.kt`:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceAttachment(
    val id: String,
    val title: String,
    val fileSize: Long? = null,
    val mediaType: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceAttachmentList(
    val results: List<ConfluenceAttachment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceProperty(
    val id: String? = null,
    val key: String,
    val value: String
)

data class LabelRequest(
    val name: String,
    val prefix: String = "global"
)
```

- [ ] **Step 4: Add new methods to ConfluenceClient**

Add after the existing `updatePage` method:

```kotlin
fun addLabels(pageId: String, labels: List<String>) {
    val body = json.writeValueAsString(labels.map { LabelRequest(name = it) })
        .toRequestBody(mediaTypeJson)
    val request = Request.Builder()
        .url("${apiBase()}/pages/$pageId/labels")
        .post(body)
        .header("Authorization", credentials)
        .header("Accept", "application/json")
        .build()
    val response = client.newCall(request).execute()
    response.use {
        if (!it.isSuccessful) {
            val errorBody = it.body?.string() ?: "(no body)"
            throw ConfluenceApiException("Failed to add labels to page $pageId: ${it.code} $errorBody")
        }
    }
}

fun setPageProperty(pageId: String, key: String, value: String) {
    val property = ConfluenceProperty(key = key, value = value)
    val body = json.writeValueAsString(property).toRequestBody(mediaTypeJson)
    val request = Request.Builder()
        .url("${apiBase()}/pages/$pageId/properties")
        .post(body)
        .header("Authorization", credentials)
        .header("Accept", "application/json")
        .build()
    executeAndParse<ConfluenceProperty>(request)
}

fun getPageProperty(pageId: String, key: String): String? {
    return try {
        val property: ConfluenceProperty = get("${apiBase()}/pages/$pageId/properties/$key")
        property.value
    } catch (e: ConfluenceNotFoundException) {
        null
    }
}

fun uploadAttachment(pageId: String, fileName: String, file: java.io.File, mimeType: String): String {
    val fileBody = okhttp3.RequestBody.create(mimeType.toMediaType(), file)
    val multipartBody = okhttp3.MultipartBody.Builder()
        .setType(okhttp3.MultipartBody.FORM)
        .addFormDataPart("file", fileName, fileBody)
        .build()
    val request = Request.Builder()
        .url("${apiBase()}/pages/$pageId/attachments")
        .post(multipartBody)
        .header("Authorization", credentials)
        .header("Accept", "application/json")
        .build()
    val result: ConfluenceAttachmentList = executeAndParse(request)
    return result.results.firstOrNull()?.id
        ?: throw ConfluenceApiException("No attachment returned after upload to page $pageId")
}

fun getAttachments(pageId: String): List<ConfluenceAttachment> {
    val result: ConfluenceAttachmentList = get("${apiBase()}/pages/$pageId/attachments")
    return result.results
}

fun listManagedPages(spaceId: String, label: String): List<ConfluencePage> {
    val result: ConfluencePageList = get("${apiBase()}/pages?space-id=$spaceId&label=$label&limit=250")
    return result.results
}
```

Add imports:
```kotlin
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceAttachmentList
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceProperty
import io.github.bovinemagnet.antoraconfluence.confluence.model.LabelRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradle21w test --tests "*.ConfluenceClientTest"`
Expected: PASS (6 tests)

- [ ] **Step 6: Integrate labels and properties into PublishEngine**

In the engine's `publishPage` method, after a successful create or update:

```kotlin
// Apply labels
val allLabels = request.applyLabels + "managed-by-antora-confluence"
if (!request.dryRun) {
    client.addLabels(confluencePageId, allLabels)

    // Set page properties
    client.setPageProperty(confluencePageId, "antora-confluence-key", page.pageId)
    client.setPageProperty(confluencePageId, "antora-confluence-source", page.sourceFile.path)
    client.setPageProperty(confluencePageId, "antora-confluence-component",
        "${page.componentName}/${page.componentVersion}/${page.moduleName}")
    client.setPageProperty(confluencePageId, "antora-confluence-fingerprint", store.sha256(content))
    client.setPageProperty(confluencePageId, "antora-confluence-plugin-version", "0.1.0")
    client.setPageProperty(confluencePageId, "antora-confluence-published-at",
        java.time.Instant.now().toString())
}
```

- [ ] **Step 7: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/confluence/
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git commit -m "Add labels, page properties, and attachment support to Confluence client"
```

---

## Task 9: Image Handling

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/model/PageContext.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngineTest.kt`

- [ ] **Step 1: Write failing test for image handling in dry run**

Add to `PublishEngineTest.kt`:

```kotlin
@Test
fun `dry run includes image references in page plan`() {
    val docsDir = File(tempDir, "docs").also { it.mkdirs() }
    File(docsDir, "antora.yml").writeText("name: test-comp\nversion: '1.0'\n")
    val pagesDir = File(docsDir, "modules/ROOT/pages").also { it.mkdirs() }
    File(pagesDir, "index.adoc").writeText("= Index\n\nimage::logo.png[Logo]\n")
    val imagesDir = File(docsDir, "modules/ROOT/images").also { it.mkdirs() }
    File(imagesDir, "logo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

    val request = buildRequest(docsDir, dryRun = true)
    val engine = PublishEngine()
    val summary = engine.publish(request)
    assertThat(summary.results).isNotEmpty
}
```

- [ ] **Step 2: Update PublishEngine to handle images**

In the engine's publish flow, after scanning with `scanFull()`:
1. Pass the `imageManifest` from `AntoraContentModel` to the `PageContext` for each page
2. Before publishing a page (in non-dry-run mode), upload images referenced by the page
3. Include image hashes in the state store

- [ ] **Step 3: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngineTest.kt
git commit -m "Add image handling to publish engine"
```

---

## Task 10: Xref Resolution

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/XrefResolver.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/XrefResolverTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`

- [ ] **Step 1: Write failing tests for XrefResolver**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class XrefResolverTest {

    private fun page(component: String, version: String, module: String, name: String) =
        AntoraPage(
            siteKey = "",
            componentName = component,
            componentVersion = version,
            moduleName = module,
            relativePath = "$name.adoc",
            sourceFile = File("/tmp/$name.adoc"),
            title = name.replaceFirstChar { it.uppercaseChar() },
            images = emptyList(),
            includes = emptyList(),
            xrefs = emptyList()
        )

    @Test
    fun `resolves same-module xref`() {
        val pages = listOf(
            page("comp", "1.0", "ROOT", "index"),
            page("comp", "1.0", "ROOT", "guide")
        )
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("guide.adoc", "comp", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Guide")
    }

    @Test
    fun `resolves cross-module xref`() {
        val pages = listOf(
            page("comp", "1.0", "ROOT", "index"),
            page("comp", "1.0", "admin", "setup")
        )
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("admin:setup.adoc", "comp", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Setup")
    }

    @Test
    fun `resolves cross-component xref`() {
        val pages = listOf(
            page("comp-a", "1.0", "ROOT", "index"),
            page("comp-b", "2.0", "ROOT", "api")
        )
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("comp-b:ROOT:api.adoc", "comp-a", "1.0", "ROOT")
        assertThat(resolved).isEqualTo("Api")
    }

    @Test
    fun `returns null for unresolved xref`() {
        val pages = listOf(page("comp", "1.0", "ROOT", "index"))
        val resolver = XrefResolver(pages)
        val resolved = resolver.resolve("nonexistent.adoc", "comp", "1.0", "ROOT")
        assertThat(resolved).isNull()
    }

    @Test
    fun `resolveAll returns map and collects warnings`() {
        val pages = listOf(
            page("comp", "1.0", "ROOT", "index"),
            page("comp", "1.0", "ROOT", "guide")
        )
        val xrefs = listOf("guide.adoc", "missing.adoc")
        val resolver = XrefResolver(pages)
        val result = resolver.resolveAll(xrefs, "comp", "1.0", "ROOT")
        assertThat(result.resolved).containsEntry("guide.adoc", "Guide")
        assertThat(result.unresolved).containsExactly("missing.adoc")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.XrefResolverTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create XrefResolver**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import io.github.bovinemagnet.antoraconfluence.antora.AntoraPage

/**
 * Result of resolving a set of xref targets.
 */
data class XrefResolution(
    val resolved: Map<String, String>,
    val unresolved: List<String>
)

/**
 * Resolves Antora xref targets to Confluence page titles using the known page inventory.
 *
 * Supported xref formats:
 * - Same module: `page-name.adoc`
 * - Cross-module: `module:page-name.adoc`
 * - Cross-component: `component:module:page-name.adoc`
 * - With version: `version@component:module:page-name.adoc`
 */
class XrefResolver(pages: List<AntoraPage>) {

    // Index by resource ID: "component:module:page-name"
    private val pageIndex: Map<String, AntoraPage> = pages.associateBy { page ->
        "${page.componentName}:${page.moduleName}:${page.relativePath.removeSuffix(".adoc")}"
    }

    // Also index by just "module:page-name" for within-component resolution
    private val byModulePage: Map<String, Map<String, AntoraPage>> = pages.groupBy { it.componentName }
        .mapValues { (_, compPages) ->
            compPages.associateBy { "${it.moduleName}:${it.relativePath.removeSuffix(".adoc")}" }
        }

    // Also index by just "page-name" for within-module resolution
    private val byPage: Map<String, Map<String, Map<String, AntoraPage>>> = pages.groupBy { it.componentName }
        .mapValues { (_, compPages) ->
            compPages.groupBy { it.moduleName }
                .mapValues { (_, modPages) -> modPages.associateBy { it.relativePath.removeSuffix(".adoc") } }
        }

    fun resolve(xref: String, fromComponent: String, fromVersion: String, fromModule: String): String? {
        val target = xref.removeSuffix(".adoc")
        val cleaned = target.removeSuffix(".adoc")

        // Check for version@ prefix
        val withoutVersion = if (cleaned.contains("@")) {
            cleaned.substringAfter("@")
        } else {
            cleaned
        }

        val parts = withoutVersion.split(":")
        val page = when (parts.size) {
            1 -> {
                // Same module: page-name
                byPage[fromComponent]?.get(fromModule)?.get(parts[0])
            }
            2 -> {
                // Cross-module: module:page-name
                byPage[fromComponent]?.get(parts[0])?.get(parts[1])
            }
            3 -> {
                // Cross-component: component:module:page-name
                byPage[parts[0]]?.get(parts[1])?.get(parts[2])
            }
            else -> null
        }

        return page?.title
    }

    fun resolveAll(xrefs: List<String>, fromComponent: String, fromVersion: String, fromModule: String): XrefResolution {
        val resolved = mutableMapOf<String, String>()
        val unresolved = mutableListOf<String>()
        for (xref in xrefs) {
            val title = resolve(xref, fromComponent, fromVersion, fromModule)
            if (title != null) {
                resolved[xref] = title
            } else {
                unresolved.add(xref)
            }
        }
        return XrefResolution(resolved, unresolved)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.XrefResolverTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Integrate XrefResolver into PublishEngine**

In `PublishEngine.publish()`, after scanning:

```kotlin
val xrefResolver = XrefResolver(pages)
```

When building the `PageContext` for each page:

```kotlin
val xrefResolution = xrefResolver.resolveAll(
    page.xrefs, page.componentName, page.componentVersion, page.moduleName
)
if (request.failOnUnresolvedXref && xrefResolution.unresolved.isNotEmpty()) {
    throw IllegalStateException("Unresolved xrefs in ${page.pageId}: ${xrefResolution.unresolved}")
}
val context = PageContext(
    resolvedXrefs = xrefResolution.resolved,
    imageManifest = imageManifest,
    strict = request.strict
)
```

- [ ] **Step 6: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/XrefResolver.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/XrefResolverTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git commit -m "Add xref resolution for Antora cross-references"
```

---

## Task 11: Dependency Tracking & Rendered Fingerprinting

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/DependencyGraph.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/DependencyGraphTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/fingerprint/ContentFingerprintStore.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/fingerprint/ContentFingerprintStoreTest.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt`

- [ ] **Step 1: Write failing tests for DependencyGraph**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DependencyGraphTest {

    @Test
    fun `adding dependency creates edge`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "include-1.adoc")
        assertThat(graph.getDependencies("page-a")).containsExactly("include-1.adoc")
    }

    @Test
    fun `getDependents returns pages depending on a resource`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "shared-header.adoc")
        graph.addDependency("page-b", "shared-header.adoc")
        assertThat(graph.getDependents("shared-header.adoc")).containsExactlyInAnyOrder("page-a", "page-b")
    }

    @Test
    fun `page with no dependencies returns empty set`() {
        val graph = DependencyGraph()
        assertThat(graph.getDependencies("page-a")).isEmpty()
    }

    @Test
    fun `getAffectedPages returns all pages affected by resource changes`() {
        val graph = DependencyGraph()
        graph.addDependency("page-a", "partial.adoc")
        graph.addDependency("page-b", "partial.adoc")
        graph.addDependency("page-c", "other.adoc")
        val affected = graph.getAffectedPages(setOf("partial.adoc"))
        assertThat(affected).containsExactlyInAnyOrder("page-a", "page-b")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradle21w test --tests "*.DependencyGraphTest"`
Expected: FAIL — class does not exist

- [ ] **Step 3: Create DependencyGraph**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

/**
 * Directed dependency graph tracking what each page depends on (includes, images, xrefs).
 * Used to determine which pages need republishing when a dependency changes.
 */
class DependencyGraph {

    // page → set of resources it depends on
    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    // resource → set of pages that depend on it
    private val dependents = mutableMapOf<String, MutableSet<String>>()

    fun addDependency(pageId: String, resourceId: String) {
        dependencies.getOrPut(pageId) { mutableSetOf() }.add(resourceId)
        dependents.getOrPut(resourceId) { mutableSetOf() }.add(pageId)
    }

    fun getDependencies(pageId: String): Set<String> =
        dependencies[pageId] ?: emptySet()

    fun getDependents(resourceId: String): Set<String> =
        dependents[resourceId] ?: emptySet()

    fun getAffectedPages(changedResources: Set<String>): Set<String> =
        changedResources.flatMap { getDependents(it) }.toSet()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.DependencyGraphTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Enhance ContentFingerprintStore with composite hash and new fields**

Update `FingerprintEntry` in `ContentFingerprintStore.kt`:

```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class FingerprintEntry(
    val pageId: String,
    val contentHash: String,
    val compositeHash: String? = null,
    val confluencePageId: String? = null,
    val confluenceTitle: String? = null,
    val parentKey: String? = null,
    val sourcePath: String? = null,
    val imageHashes: Map<String, String> = emptyMap(),
    val includeHashes: Map<String, String> = emptyMap(),
    val pluginVersion: String? = null,
    val lastPublishedAt: String? = null
)
```

Add a new method:

```kotlin
fun putComposite(
    pageId: String,
    contentHash: String,
    compositeHash: String,
    confluencePageId: String? = null,
    confluenceTitle: String? = null,
    parentKey: String? = null,
    sourcePath: String? = null,
    imageHashes: Map<String, String> = emptyMap(),
    includeHashes: Map<String, String> = emptyMap(),
    pluginVersion: String? = null
) {
    entries[pageId] = FingerprintEntry(
        pageId = pageId,
        contentHash = contentHash,
        compositeHash = compositeHash,
        confluencePageId = confluencePageId,
        confluenceTitle = confluenceTitle,
        parentKey = parentKey,
        sourcePath = sourcePath,
        imageHashes = imageHashes,
        includeHashes = includeHashes,
        pluginVersion = pluginVersion,
        lastPublishedAt = java.time.Instant.now().toString()
    )
}

fun isCompositeChanged(pageId: String, compositeHash: String): Boolean {
    val entry = entries[pageId] ?: return true
    return entry.compositeHash != compositeHash
}
```

- [ ] **Step 6: Add tests for enhanced fingerprint store**

Add to `ContentFingerprintStoreTest.kt`:

```kotlin
@Test
fun `putComposite stores all enhanced fields`() {
    val store = ContentFingerprintStore(storeFile())
    store.putComposite(
        pageId = "test/page",
        contentHash = "hash1",
        compositeHash = "composite1",
        confluencePageId = "123",
        confluenceTitle = "Test Page",
        parentKey = "test",
        sourcePath = "docs/pages/test.adoc",
        imageHashes = mapOf("logo.png" to "imghash"),
        includeHashes = mapOf("header.adoc" to "inchash"),
        pluginVersion = "0.1.0"
    )
    val entry = store.get("test/page")
    assertThat(entry).isNotNull
    assertThat(entry!!.compositeHash).isEqualTo("composite1")
    assertThat(entry.parentKey).isEqualTo("test")
    assertThat(entry.sourcePath).isEqualTo("docs/pages/test.adoc")
    assertThat(entry.imageHashes).containsEntry("logo.png", "imghash")
    assertThat(entry.includeHashes).containsEntry("header.adoc", "inchash")
    assertThat(entry.pluginVersion).isEqualTo("0.1.0")
}

@Test
fun `isCompositeChanged returns true for new page`() {
    val store = ContentFingerprintStore(storeFile())
    assertThat(store.isCompositeChanged("new/page", "hash")).isTrue()
}

@Test
fun `isCompositeChanged returns false when hash matches`() {
    val store = ContentFingerprintStore(storeFile())
    store.putComposite("page", "raw", "composite1")
    assertThat(store.isCompositeChanged("page", "composite1")).isFalse()
}
```

- [ ] **Step 7: Run tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 8: Integrate DependencyGraph into PublishEngine**

In `PublishEngine.publish()`, after scanning:

```kotlin
val depGraph = DependencyGraph()
for (page in pages) {
    for (inc in page.includes) depGraph.addDependency(page.pageId, inc)
    for (img in page.images) depGraph.addDependency(page.pageId, img)
}
```

When computing fingerprints, use the composite hash including rendered output, title, parent key, image hashes, and include hashes. Use `store.isCompositeChanged()` instead of `store.isChanged()`. Use `store.putComposite()` when recording state.

- [ ] **Step 9: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/DependencyGraph.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/engine/DependencyGraphTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/fingerprint/ContentFingerprintStore.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/fingerprint/ContentFingerprintStoreTest.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/PublishEngine.kt
git commit -m "Add dependency tracking and composite fingerprinting"
```

---

## Task 12: ReconcileState Implementation

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceReconcileStateTask.kt`
- Create: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceReconcileStateTaskTest.kt`

- [ ] **Step 1: Write failing test for ReconcileState**

```kotlin
package io.github.bovinemagnet.antoraconfluence.tasks

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AntoraConfluenceReconcileStateTaskTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reconcileState rebuilds state from remote page properties`() {
        // Mock: getSpace
        server.enqueue(MockResponse().setBody("""{"results":[{"id":"space-1","key":"DOCS","name":"Docs"}]}"""))
        // Mock: listManagedPages
        server.enqueue(MockResponse().setBody("""{"results":[{"id":"page-1","title":"Index","spaceId":"space-1","status":"current"}]}"""))
        // Mock: getPageProperty (antora-confluence-key)
        server.enqueue(MockResponse().setBody("""{"id":"p1","key":"antora-confluence-key","value":"site/comp/1.0/ROOT/index"}"""))
        // Mock: getPageProperty (antora-confluence-fingerprint)
        server.enqueue(MockResponse().setBody("""{"id":"p2","key":"antora-confluence-fingerprint","value":"abc123"}"""))
        // Mock: getPageProperty (antora-confluence-source)
        server.enqueue(MockResponse().setBody("""{"id":"p3","key":"antora-confluence-source","value":"docs/pages/index.adoc"}"""))

        val buildFile = File(projectDir, "build.gradle.kts")
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"test\"\n")
        buildFile.writeText("""
            plugins {
                id("io.github.bovinemagnet.antora-confluence")
            }
            antoraConfluence {
                confluence {
                    baseUrl.set("${server.url("/wiki")}")
                    spaceKey.set("DOCS")
                    username.set("user")
                    apiToken.set("token")
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("antoraConfluenceReconcileState", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        assertThat(result.task(":antoraConfluenceReconcileState")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val stateFile = File(projectDir, "build/antora-confluence/state.json")
        assertThat(stateFile).exists()
        val stateContent = stateFile.readText()
        assertThat(stateContent).contains("site/comp/1.0/ROOT/index")
        assertThat(stateContent).contains("page-1")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle21w test --tests "*.AntoraConfluenceReconcileStateTaskTest"`
Expected: FAIL — task does not rebuild state from remote

- [ ] **Step 3: Implement ReconcileState task**

Replace the `reconcileState()` method in `AntoraConfluenceReconcileStateTask.kt`:

```kotlin
@TaskAction
fun reconcileState() {
    validateRequiredInputs()

    val client = ConfluenceClient(
        baseUrl = confluenceUrl.get(),
        username = username.get(),
        apiToken = apiToken.get()
    )

    val space = client.getSpace(spaceKey.get())
        ?: throw GradleException("Confluence space '${spaceKey.get()}' not found or not accessible.")

    logger.lifecycle("Reconciling state from Confluence space: {} ({})", space.name, space.key)

    val store = ContentFingerprintStore(fingerprintFile.get().asFile)
    val managedPages = client.listManagedPages(space.id, "managed-by-antora-confluence")

    logger.lifecycle("Found {} managed pages in Confluence", managedPages.size)

    var reconciledCount = 0
    for (page in managedPages) {
        val canonicalKey = client.getPageProperty(page.id, "antora-confluence-key") ?: continue
        val fingerprint = client.getPageProperty(page.id, "antora-confluence-fingerprint") ?: ""
        val sourcePath = client.getPageProperty(page.id, "antora-confluence-source")

        store.putComposite(
            pageId = canonicalKey,
            contentHash = fingerprint,
            compositeHash = fingerprint,
            confluencePageId = page.id,
            confluenceTitle = page.title,
            sourcePath = sourcePath
        )
        reconciledCount++
        logger.lifecycle("  Reconciled: {} -> Confluence page {} ({})", canonicalKey, page.id, page.title)
    }

    store.save()
    logger.lifecycle("Reconciliation complete. {} pages reconciled. State saved to {}",
        reconciledCount, fingerprintFile.get().asFile.absolutePath)
}
```

Add imports:
```kotlin
import io.github.bovinemagnet.antoraconfluence.confluence.ConfluenceClient
import io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradle21w test --tests "*.AntoraConfluenceReconcileStateTaskTest"`
Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceReconcileStateTask.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/tasks/AntoraConfluenceReconcileStateTaskTest.kt
git commit -m "Implement ReconcileState task with remote page property recovery"
```

---

## Task 13: Gradle Antora Plugin Integration (Optional)

**Files:**
- Create: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/AntoraPluginIntegration.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePlugin.kt`
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/extension/SourceSpec.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePluginTest.kt`

- [ ] **Step 1: Add dependsOnAntoraTask property to SourceSpec**

In `SourceSpec.kt`, add:

```kotlin
/** When true, antoraConfluenceValidate will depend on the `antora` task if the org.antora plugin is applied. */
abstract val dependsOnAntoraTask: Property<Boolean>
```

- [ ] **Step 2: Create AntoraPluginIntegration**

```kotlin
package io.github.bovinemagnet.antoraconfluence.engine

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import java.io.File

/**
 * Optional integration with the Gradle Antora plugin (org.antora).
 * Detects the plugin, infers content root from the playbook, and wires task dependencies.
 */
class AntoraPluginIntegration {

    private val logger = Logging.getLogger(AntoraPluginIntegration::class.java)

    fun configure(project: Project) {
        project.plugins.withId("org.antora") {
            logger.info("Detected org.antora plugin — enabling Antora plugin integration")
            val antoraExtension = project.extensions.findByName("antora") ?: return@withId
            tryInferContentRoot(project, antoraExtension)
            tryWireTaskDependency(project)
        }
    }

    private fun tryInferContentRoot(project: Project, antoraExtension: Any) {
        try {
            val playbookProperty = antoraExtension.javaClass.getMethod("getPlaybook").invoke(antoraExtension)
            val getMethod = playbookProperty.javaClass.getMethod("getOrNull")
            val playbookFile = getMethod.invoke(playbookProperty) as? File ?: return

            if (!playbookFile.exists()) {
                logger.info("Antora playbook file not found: {}", playbookFile)
                return
            }

            val contentSources = parsePlaybookContentSources(playbookFile)
            if (contentSources.isNotEmpty()) {
                logger.info("Inferred Antora content sources from playbook: {}", contentSources)
            }
        } catch (e: Exception) {
            logger.info("Could not infer content root from Antora plugin: {}", e.message)
        }
    }

    private fun tryWireTaskDependency(project: Project) {
        val ext = project.extensions.findByName("antoraConfluence") ?: return
        val sourceSpec = ext.javaClass.getMethod("getSource").invoke(ext)
        val dependsOnProp = sourceSpec.javaClass.getMethod("getDependsOnAntoraTask").invoke(sourceSpec)
        val getOrElse = dependsOnProp.javaClass.getMethod("getOrElse", Any::class.java)
        val shouldDepend = getOrElse.invoke(dependsOnProp, false) as Boolean

        if (shouldDepend) {
            project.tasks.named("antoraConfluenceValidate") {
                it.dependsOn("antora")
            }
            logger.info("Wired antoraConfluenceValidate to depend on antora task")
        }
    }

    internal fun parsePlaybookContentSources(playbookFile: File): List<String> {
        return try {
            val mapper = YAMLMapper()
            val tree: Map<String, Any?> = mapper.readValue(
                playbookFile,
                object : TypeReference<Map<String, Any?>>() {}
            )
            @Suppress("UNCHECKED_CAST")
            val content = tree["content"] as? Map<String, Any?> ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            val sources = content["sources"] as? List<Map<String, Any?>> ?: return emptyList()
            sources.mapNotNull { it["url"]?.toString() }
        } catch (e: Exception) {
            logger.info("Could not parse playbook YAML: {}", e.message)
            emptyList()
        }
    }
}
```

- [ ] **Step 3: Wire integration in AntoraConfluencePlugin**

In `AntoraConfluencePlugin.apply()`, add after the extension defaults (after line 56):

```kotlin
// Set default for dependsOnAntoraTask
extension.source.dependsOnAntoraTask.convention(false)

// Optional Antora plugin integration
AntoraPluginIntegration().configure(project)
```

- [ ] **Step 4: Write test verifying standalone operation**

Add to `AntoraConfluencePluginTest.kt`:

```kotlin
@Test
fun `plugin works standalone without org antora plugin`() {
    createValidAntoraContent()
    writeBuildFile("""
        plugins {
            id("io.github.bovinemagnet.antora-confluence")
        }
        antoraConfluence {
            source {
                antoraRoot.set(layout.projectDirectory.dir("docs"))
            }
        }
    """)
    val result = runner("antoraConfluenceValidate").build()
    assertThat(result.task(":antoraConfluenceValidate")?.outcome)
        .isEqualTo(org.gradle.testkit.runner.TaskOutcome.SUCCESS)
}
```

- [ ] **Step 5: Run all tests**

Run: `gradle21w test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/engine/AntoraPluginIntegration.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePlugin.kt
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/extension/SourceSpec.kt
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePluginTest.kt
git commit -m "Add optional Gradle Antora plugin integration"
```

---

## Task 14: Final Integration & Verification

**Files:**
- Modify: `src/main/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePlugin.kt`
- Modify: `src/test/kotlin/io/github/bovinemagnet/antoraconfluence/AntoraConfluencePluginTest.kt`

- [ ] **Step 1: Wire remaining extension properties to publish tasks**

In `AntoraConfluencePlugin.kt`, update the publish task configuration to pass `hierarchyMode`, `versionMode`, `createIndexPages`, `uploadImages`, `normalizeWhitespaceForDiff`, and `failOnUnresolvedXref` from the extension to the task (and through to `PublishRequest`).

Add corresponding `@Input` properties to `AntoraConfluencePublishTask` and `AntoraConfluenceFullPublishTask` for any that are not yet wired.

- [ ] **Step 2: Write integration test verifying the full plan pipeline**

Add to `AntoraConfluencePluginTest.kt`:

```kotlin
@Test
fun `plan task shows hierarchy-aware page structure`() {
    createValidAntoraContent()
    writeBuildFile("""
        plugins {
            id("io.github.bovinemagnet.antora-confluence")
        }
        antoraConfluence {
            source {
                antoraRoot.set(layout.projectDirectory.dir("docs"))
                siteKey.set("test-site")
            }
        }
    """)
    val result = runner("antoraConfluencePlan").build()
    assertThat(result.task(":antoraConfluencePlan")?.outcome)
        .isEqualTo(org.gradle.testkit.runner.TaskOutcome.SUCCESS)
    assertThat(result.output).contains("test-site/test-docs")
}
```

- [ ] **Step 3: Run full test suite**

Run: `gradle21w test`
Expected: ALL tests pass

- [ ] **Step 4: Run build to confirm clean compilation**

Run: `gradle21w build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/github/bovinemagnet/antoraconfluence/
git add src/test/kotlin/io/github/bovinemagnet/antoraconfluence/
git commit -m "Wire remaining extension properties and add integration tests"
```
