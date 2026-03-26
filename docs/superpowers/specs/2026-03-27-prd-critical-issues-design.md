# Design: PRD Critical Issues Implementation

**Date:** 2026-03-27
**Scope:** All 10 critical issues from PRD compliance review, plus engine extraction refactor and Jackson YAML upgrade
**Approach:** Horizontal layers — build shared infrastructure first, then wire features

---

## 1. Publishing Engine Extraction

### Problem

`AntoraConfluencePublishTask` and `AntoraConfluenceFullPublishTask` duplicate page-finding, validation, publishing, logging, reporting, and result-tracking logic. The PRD (section 20.4) explicitly warns against embedding logic in task classes.

### Design

**New class: `PublishEngine`** in `io.github.bovinemagnet.antoraconfluence.engine`

Responsibilities:
- Accept a `PublishRequest` data class containing all inputs (pages, config, credentials, strategy flags)
- Orchestrate the full publish pipeline: scan, render, fingerprint, diff, plan, execute, report
- Return a `PublishResult`

Both task classes become thin shells: gather inputs from Gradle properties, build `PublishRequest`, call `engine.publish()`, write outputs.

**Model classes** in `engine/model/`:
- `PublishRequest` — all inputs bundled
- `PublishResult` — summary of actions taken
- `PublishAction` (enum: CREATE, UPDATE, SKIP, ORPHAN) — moved from inside task
- `PagePlan` — per-page plan entry with action, reason, page identity

`FullPublishTask` differs only in that it skips fingerprint comparison. This becomes a boolean flag (`forceAll`) on `PublishRequest` rather than a separate code path.

---

## 2. Enhanced Content Scanner & Jackson YAML

### Jackson YAML Upgrade

Replace the naive line-by-line `antora.yml` parser with `jackson-dataformat-yaml`. The `parseAntoraYml` method in `AntoraContentScanner` becomes a `ObjectMapper(YAMLFactory()).readValue<Map<String, Any>>()` call. Handles multi-line values, comments, and nested structures correctly.

### Enhanced `AntoraPage` Model

Add fields:
- `title` — parsed from the AsciiDoc document title (`= Title` line), falling back to the current `suggestedTitle` logic
- `images` — list of image references found in the source
- `includes` — list of include file references found in the source
- `xrefs` — list of Antora xref targets found in the source

### New Class: `AsciiDocReferenceExtractor`

Package: `antora/`

A lightweight pre-parse step that scans `.adoc` source text with regex to extract:
- `image::` and `image:` references
- `include::` references
- `xref:` references
- Document title (`= Title` on first content line)

Intentionally separate from the full Asciidoctorj render — runs during scanning so the content model knows about dependencies before any conversion happens.

### `AntoraContentScanner` Changes

- Uses `AsciiDocReferenceExtractor` on each discovered page
- Discovers `images/` directories alongside `pages/` directories
- Returns an `AntoraContentModel` wrapper containing both the page list and an image manifest

---

## 3. Confluence Storage Format Post-Processor

### New Class: `ConfluenceStorageFormatConverter`

Package: `confluence/`

Takes HTML5 output from Asciidoctorj and transforms it to Confluence storage format using Jsoup (new dependency) for DOM-based transformation.

### Transformations

| HTML5 input | Confluence storage format output |
|---|---|
| `<pre><code class="language-java">` | `<ac:structured-macro ac:name="code"><ac:parameter ac:name="language">java</ac:parameter><ac:plain-text-body>...</ac:plain-text-body></ac:structured-macro>` |
| `<div class="admonitionblock note">` | `<ac:structured-macro ac:name="info"><ac:rich-text-body>...</ac:rich-text-body></ac:structured-macro>` |
| `<div class="admonitionblock warning">` | `<ac:structured-macro ac:name="warning"><ac:rich-text-body>...</ac:rich-text-body></ac:structured-macro>` |
| `<div class="admonitionblock tip">` | `<ac:structured-macro ac:name="tip"><ac:rich-text-body>...</ac:rich-text-body></ac:structured-macro>` |
| `<div class="admonitionblock caution">` | `<ac:structured-macro ac:name="note"><ac:rich-text-body>...</ac:rich-text-body></ac:structured-macro>` |
| `<img src="...">` (local asset) | `<ac:image><ri:attachment ri:filename="..."/></ac:image>` |
| `<a href="...">` (internal xref) | `<ac:link><ri:page ri:content-title="..."/></ac:link>` |
| `<a href="...">` (external link) | Left as-is |
| `<table>` | Left as-is |

### Design Decisions

- Jsoup parses the HTML fragment, walks the DOM, replaces nodes in place
- The converter receives a `PageContext` object containing resolved xref targets (Confluence page titles) and image manifest (filenames uploaded as attachments)
- Unknown/unsupported constructs pass through as-is with a warning logged
- Strict mode (`failOnUnresolvedXref`) causes unresolved xrefs to throw

### `AsciiDocConverter` Changes

- Rename current method to `renderToHtml()`
- Add `renderToConfluenceStorage()` that calls `renderToHtml()` then `ConfluenceStorageFormatConverter.convert()`

---

## 4. Hierarchy Mapping

### New Class: `HierarchyBuilder`

Package: `engine/`

Takes a flat list of `AntoraPage` entries and `HierarchyMode` + `VersionMode` configuration, produces an ordered tree of `HierarchyNode` entries.

### New Data Class: `HierarchyNode`

Package: `engine/model/`

Fields:
- `canonicalKey` — stable identity (e.g. `my-site/my-component` for an intermediate node)
- `title` — Confluence page title
- `nodeType` — enum: `COMPONENT`, `VERSION`, `MODULE`, `PAGE`
- `children` — ordered list of child `HierarchyNode`
- `sourcePage` — nullable `AntoraPage` (null for intermediate structural nodes)
- `htmlContent` — nullable (null for structural nodes unless `createIndexPages` is true)

### Hierarchy Modes

**`COMPONENT_VERSION_MODULE_PAGE`** (default):
```
parentPage
  +-- my-component
        +-- 1.0
              +-- ROOT
                    +-- index
                    +-- getting-started
```

**`COMPONENT_VERSION_PAGE`** — combines component and version into a single node, module remains:
```
parentPage
  +-- my-component/1.0
        +-- ROOT
              +-- index
              +-- getting-started
```

**`COMPONENT_PAGE`** — flattens version and module:
```
parentPage
  +-- my-component
        +-- index
        +-- getting-started
```

**`VersionMode.TITLE_PREFIX`** — instead of creating a version node, prepends version to page titles: `1.0 - Getting Started`.

### Index Pages

When `createIndexPages` is true, intermediate structural nodes get auto-generated HTML listing their children as links. When false, they get a minimal placeholder body.

### Engine Integration

1. `PublishEngine` calls `HierarchyBuilder.build(pages, config)` to get the tree
2. Engine walks the tree top-down, creating/updating pages in order (parents before children)
3. Each node's Confluence page ID is captured and passed as `parentId` to its children
4. `ContentFingerprintStore` tracks intermediate nodes keyed by their `canonicalKey`

---

## 5. Labels & Page Properties

### Labels

**`ConfluenceClient` addition:** `addLabels(pageId: String, labels: List<String>)` — calls `POST /api/v2/pages/{id}/labels`.

Called after every create and update. The engine applies labels from `PublishRequest.applyLabels` plus an implicit `managed-by-antora-confluence` label.

### Page Properties

**`ConfluenceClient` additions:**
- `setPageProperty(pageId: String, key: String, value: String)` — `POST /api/v2/pages/{id}/properties`
- `getPageProperty(pageId: String, key: String): String?` — `GET /api/v2/pages/{id}/properties/{key}`
- `listManagedPages(spaceId: String, label: String): List<ConfluencePage>` — finds pages by label

**Stored properties per managed page:**

| Property key | Value | Purpose |
|---|---|---|
| `antora-confluence-key` | Canonical page key | Remote identity for reconciliation |
| `antora-confluence-source` | Relative source file path | Traceability |
| `antora-confluence-component` | `component/version/module` | Antora coordinates |
| `antora-confluence-fingerprint` | SHA-256 hash | Remote change detection |
| `antora-confluence-plugin-version` | Plugin version string | Diagnostics |
| `antora-confluence-published-at` | ISO-8601 timestamp | Audit trail |

Properties are written after every successful create or update.

---

## 6. Image Handling

### Upload Flow

1. During scanning, `AsciiDocReferenceExtractor` collects image references per page
2. `AntoraContentScanner` resolves references against the module's `images/` directory, producing an `ImageManifest` (map of `imageName` to `File`)
3. Before publishing a page, the engine checks which images are new or changed (by file hash)
4. Changed images are uploaded as Confluence page attachments

### `ConfluenceClient` Additions

- `uploadAttachment(pageId: String, fileName: String, file: File, mimeType: String): String` — `POST /api/v2/pages/{id}/attachments` with multipart form data
- `getAttachments(pageId: String): List<ConfluenceAttachment>` — lists existing attachments

### New Model

`ConfluenceAttachment` in `confluence/model/` — `id`, `title`, `fileSize`, `mediaType`.

### Image Reference Rewriting

`ConfluenceStorageFormatConverter` receives the image manifest and rewrites local image references to `<ac:image><ri:attachment ri:filename="..."/></ac:image>`. Images not in the manifest produce a warning (or error in strict mode).

### Fingerprint Integration

Image file hashes are included in the page's composite fingerprint. Changed images cause all referencing pages to be republished.

### State Tracking

`ContentFingerprintStore` gains `imageHashes` per page entry — `Map<String, String>` of filename to SHA-256.

---

## 7. Xref Resolution

### New Class: `XrefResolver`

Package: `engine/`

Takes the full list of `AntoraPage` entries and the `HierarchyBuilder` output, builds a lookup map from Antora xref targets to resolved Confluence page titles.

### Antora Xref Formats

- Same module: `xref:page-name.adoc[]`
- Cross-module: `xref:module:page-name.adoc[]`
- Cross-component: `xref:component:module:page-name.adoc[]`
- With version: `xref:version@component:module:page-name.adoc[]`

### Resolution Process

1. Build an index of all known pages keyed by Antora resource ID (`component:module:page-name`)
2. For each page's xref list, look up the target in the index
3. Produce a `ResolvedXrefMap` — `Map<String, String>` of original xref target to Confluence page title
4. Unresolved xrefs collected into a warnings list

### Integration

- `PublishEngine` calls `XrefResolver.resolve()` after scanning, before rendering
- Resolved map passed to `ConfluenceStorageFormatConverter` as part of `PageContext`
- Strict mode: unresolved xref throws, failing the build
- Non-strict mode: unresolved xrefs left as plain text links with warning

### Scope Boundary

Only xrefs pointing to pages within the current publish set are resolved. External URLs and xrefs to unpublished components pass through unchanged.

---

## 8. Dependency Tracking & Rendered Fingerprinting

### New Class: `DependencyGraph`

Package: `engine/`

A directed graph tracking what each page depends on. Built during scanning from `AsciiDocReferenceExtractor` output.

### Dependency Edges

- `page` to `included file` (include directives)
- `page` to `image file` (image references)
- `page` to `xref target page` (if target's title changes, linking page's rendered output changes)

### Composite Fingerprint

Replaces the current raw-source-only hash. New fingerprint per page is SHA-256 of:
- Rendered HTML body (post-conversion to Confluence storage format)
- Page title
- Parent canonical key (hierarchy position)
- Sorted image file hashes
- Sorted include file hashes
- Render-affecting configuration values

### Change Propagation

1. Hash all include files and image files
2. For each page, compute composite fingerprint
3. Compare against stored fingerprint
4. Include file changed: all pages including it get new fingerprint, marked for update
5. Image file changed: all pages referencing it get new fingerprint, marked for update

### Enhanced State Model

```json
{
  "pageId": "my-site/comp/1.0/ROOT/index",
  "compositeHash": "sha256:...",
  "confluencePageId": "123456",
  "confluenceTitle": "Index",
  "parentKey": "my-site/comp/1.0/ROOT",
  "sourcePath": "docs/modules/ROOT/pages/index.adoc",
  "imageHashes": {"logo.png": "sha256:..."},
  "includeHashes": {"_partials/header.adoc": "sha256:..."},
  "pluginVersion": "0.1.0",
  "lastPublishedAt": "2026-03-27T10:00:00Z"
}
```

---

## 9. Credentials Safety & ReconcileState

### Credentials Safety

On all task classes, change `username` and `apiToken` annotations from `@Input` to `@Internal`.

Add `@Input` property `credentialsPresent` of type `Property<Boolean>`, wired in `AntoraConfluencePlugin.apply()`:

```kotlin
credentialsPresent.set(
    extension.confluence.username.map { it.isNotBlank() }
        .zip(extension.confluence.apiToken.map { it.isNotBlank() }) { a, b -> a && b }
)
```

### ReconcileState Implementation

1. Call `listManagedPages(spaceId, "managed-by-antora-confluence")` to find managed pages
2. For each page, read `antora-confluence-key` property to get canonical key
3. Read `antora-confluence-fingerprint`, `antora-confluence-source`, and other properties
4. Rebuild the local `ContentFingerprintStore` from remote values
5. Write the reconstructed state file

### Validation Enhancement

`ValidateTask` gains a credential check: when `confluenceUrl` is set, verify that `username` and `apiToken` are also present. Warning in non-strict mode, failure in strict mode. No API calls, presence check only.

---

## 10. New Dependencies & Test Strategy

### New Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | `$jacksonVersion` (2.17.2) | YAML parsing for `antora.yml` |
| `org.jsoup:jsoup` | `1.18.1` | HTML DOM manipulation for storage format conversion |

### Test Strategy

| Component | Approach |
|---|---|
| `PublishEngine` | Unit tests with mocked `ConfluenceClient`. Verify orchestration, ordering, plan generation, result aggregation |
| `ConfluenceStorageFormatConverter` | Pure input/output tests. Each transformation gets its own test: HTML input to expected storage format output |
| `HierarchyBuilder` | Pure unit tests. Given pages + config, assert tree structure. One test per `HierarchyMode`/`VersionMode` combination |
| `AsciiDocReferenceExtractor` | Unit tests with sample `.adoc` content, assert extracted images, includes, xrefs, title |
| `XrefResolver` | Unit tests. Given page inventory + xref list, assert resolved map and warnings |
| `DependencyGraph` | Unit tests. Assert propagation: include change marks dependent pages dirty |
| `ConfluenceClient` (labels, properties, attachments) | MockWebServer tests verifying correct API calls and response parsing |
| `ContentFingerprintStore` | Extend existing tests for composite hash and enhanced state model |
| Credentials safety | Gradle TestKit test verifying `@Internal` properties do not appear in task input fingerprint |
| ReconcileState | MockWebServer test: seed pages with properties, run task, verify rebuilt state file |

Existing `AntoraConfluencePluginTest` tests remain and are extended for hierarchy output in plan task and new validation behaviour.

---

## 11. Optional: Gradle Antora Plugin Integration (PRD section 12.3)

### Context

The Gradle Antora plugin (`org.antora`) is a CLI wrapper around `npx antora`. It registers an `antora` extension with a `playbook` property and a single `antora` task. It does **not** expose any structured content model, page catalogue, or site manifest — content sources are defined inside the playbook YAML, not in Gradle DSL.

This means integration is lightweight: detect the plugin, infer configuration, and wire task dependencies.

### New Class: `AntoraPluginIntegration`

Package: `engine/`

An optional helper invoked during `AntoraConfluencePlugin.apply()`. Only activates when the `org.antora` plugin is detected on the project.

### Behaviour When Detected

1. **Infer content root from playbook:** If `source.antoraRoot` is not explicitly set by the user, read the `playbook` property from the `antora` extension, parse the playbook YAML (using Jackson YAML from section 2), and extract content source directories from the `content.sources[].url` and `content.sources[].start_paths` fields. Use these to set a convention on `source.antoraRoot`.

2. **Wire task dependencies:** If the `antora` task exists and produces a generated site, optionally allow users to make `antoraConfluenceValidate` depend on it:
   ```kotlin
   antoraConfluence {
       source {
           dependsOnAntoraTask.set(true) // default: false
       }
   }
   ```
   When enabled, `antoraConfluenceValidate.dependsOn("antora")` is configured automatically.

3. **Log detection:** At `info` log level, report whether the Antora plugin was detected and what was inferred.

### Design Decisions

- **No hard dependency** on the Antora plugin classes. Detection uses `project.plugins.findPlugin("org.antora")` and reads the extension reflectively or by name (`project.extensions.findByName("antora")`). This avoids a compile-time dependency.
- **User config always wins.** If `source.antoraRoot` is explicitly set, the inferred value from the playbook is ignored.
- **Playbook parsing is best-effort.** If the playbook cannot be read or has an unexpected structure, a warning is logged and the plugin falls back to requiring explicit `source.antoraRoot` configuration.

### Test Strategy

- Gradle TestKit test: apply both `org.antora` and `io.github.bovinemagnet.antora-confluence`, verify that content root is inferred from playbook
- Gradle TestKit test: apply only our plugin, verify it works standalone without the Antora plugin
- Unit test for playbook YAML parsing of `content.sources`

---

## Implementation Order

1. Engine extraction (prerequisite refactor)
2. Credentials safety (quick fix, high impact)
3. Jackson YAML upgrade and enhanced content scanner
4. Confluence storage format post-processor
5. Hierarchy mapping
6. Labels and page properties
7. Image handling
8. Xref resolution
9. Dependency tracking and rendered fingerprinting
10. ReconcileState implementation
11. Gradle Antora plugin integration (optional, independent)

Each layer builds on the previous. Tests are written alongside each layer. Step 11 is independent and can be done at any point after step 3 (requires Jackson YAML for playbook parsing).
