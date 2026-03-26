# Product Requirements Document

## Product: Gradle Plugin for Publishing Antora AsciiDoc Content to Atlassian Confluence

**Document status:** Draft
**Product name (working):** `com.example.antora-confluence`
**Form factor:** Gradle plugin
**Primary target:** JVM-based build environments using Gradle
**Primary publishing target:** Atlassian Confluence Cloud
**Inspiration:** Similar developer experience to the Gradle Antora plugin, with task-oriented configuration and CI-friendly execution

---

## 1. Summary

Build a **Gradle plugin** that takes an **Antora-structured AsciiDoc documentation project** and publishes it to **Atlassian Confluence** as a managed page hierarchy.

The plugin should feel natural to teams already using Gradle and Antora-related Gradle workflows. It must support:

* Antora-aware content discovery
* Gradle task-based execution
* incremental detection of changed pages
* creation of new Confluence pages for new source files
* updates of existing Confluence pages as new Confluence versions
* safe, repeatable publishing from local builds and CI/CD pipelines
* configuration patterns consistent with established Gradle plugin conventions

This plugin is intended for teams that already use Gradle to orchestrate documentation builds and want Confluence publication to become a first-class build task rather than a separate standalone CLI.

---

## 2. Problem Statement

Teams that use Antora and AsciiDoc often already rely on Gradle to manage docs workflows, including:

* documentation assembly
* AsciiDoc processing
* Antora site generation
* CI/CD task orchestration
* validation and quality gates

When those same teams need to publish documentation to Confluence, they often have to introduce a separate script or external CLI. That causes several problems:

* duplicated configuration between Gradle and external tooling
* awkward CI integration
* no native Gradle task inputs/outputs and up-to-date checking
* poor alignment with existing Antora build conventions
* inconsistent local developer workflow
* weak dependency modeling between docs generation and Confluence publication

A Gradle plugin solves this by making Confluence publishing part of the existing build lifecycle.

---

## 3. Product Vision

Make Confluence publishing for Antora documentation feel like a normal Gradle docs task: declarative, cache-aware where possible, incremental, composable, and easy to run in CI.

---

## 4. Goals

### Primary Goals

1. Provide a **Gradle-native way** to publish Antora documentation to Confluence.
2. Offer task semantics similar in spirit to the Gradle Antora plugin.
3. Automatically detect and publish changed content only.
4. Publish new source pages automatically into the correct Confluence hierarchy.
5. Update existing Confluence pages by creating new Confluence revisions.
6. Support dry-run, validation, and full publish modes through Gradle tasks.
7. Be easy to integrate into existing Gradle docs builds.

### Secondary Goals

1. Reuse Gradle conventions for task configuration, lazy properties, and build lifecycle wiring.
2. Support incremental execution and clear task inputs.
3. Support multi-project and convention-plugin based rollouts in enterprise builds.
4. Provide good local developer ergonomics and CI observability.

---

## 5. Non-Goals

For the initial release, the following are out of scope:

* bidirectional sync from Confluence back into source files
* full emulation of the complete Antora site UI in Confluence
* support for every custom AsciiDoc extension
* GUI-based publishing
* automatic Confluence space provisioning
* broad content migration from arbitrary Confluence spaces into Antora
* perfect Gradle build cache support for remote publishing tasks
* deleting Confluence pages by default

One important opinionated boundary: a remote publishing task is not the same as a local artifact-producing task. We can support Gradle incrementality and good input tracking, but pretending this is a perfect build-cache candidate would be misleading. The plugin should behave like a well-designed publishing plugin, not like a pure compiler task.

---

## 6. Target Users

### Primary Users

* engineers maintaining docs-as-code pipelines
* platform teams using Gradle for documentation builds
* developer experience teams
* technical writers embedded in engineering orgs
* teams already using Antora via Gradle

### Secondary Users

* build engineers
* solution architects
* internal docs platform maintainers

---

## 7. User Stories

### Documentation Maintainer

* As a maintainer, I want to configure Confluence publication in `build.gradle.kts` so docs publishing is part of the build.
* As a maintainer, I want plugin tasks that resemble other Antora-related Gradle tasks so the workflow is familiar.
* As a maintainer, I want to run a dry run before publishing to see which pages will be created or updated.
* As a maintainer, I want only changed pages to be published so routine updates are fast.

### Build Engineer

* As a build engineer, I want the plugin to expose proper Gradle tasks, extensions, and lazy properties so it works cleanly in multi-project builds.
* As a build engineer, I want task dependencies to wire naturally with Antora generation and validation tasks.
* As a build engineer, I want failure behavior and logging to be suitable for CI.

### Platform Team

* As a platform team, I want to standardize this plugin via convention plugins across many repositories.
* As a platform team, I want managed pages in Confluence to be clearly identifiable and traceable back to source.

---

## 8. Why a Gradle Plugin Instead of a Standalone CLI

A standalone CLI is useful, but the Gradle plugin version has distinct advantages:

* fits existing Gradle-based docs pipelines
* leverages Gradle task graph and lifecycle
* allows configuration in one place
* aligns with Antora plugin workflows
* supports preconfigured enterprise convention plugins
* enables dependency wiring such as:

  * validate docs before publish
  * generate Antora inventory before publish
  * publish only for release builds
* reduces duplicated configuration between CI scripts and local dev

The plugin should still encapsulate a reusable internal engine, but the primary user interface in this product is Gradle.

---

## 9. Product Principles

### 9.1 Gradle-native

Use Gradle idioms, not a CLI awkwardly wrapped in a plugin.

### 9.2 Antora-aware

Understand Antora structures rather than treating documentation as a random folder of `.adoc` files.

### 9.3 Publish safely

Default behavior must minimize accidental damage in Confluence.

### 9.4 Incremental and traceable

Track what changed, why it changed, and what was published.

### 9.5 Convention over ceremony

Reasonable defaults matter. Teams should not need fifty lines of config to get basic publishing working.

---

## 10. Product Scope

The plugin will:

1. add a Gradle extension for configuration
2. register Antora-aware Confluence publishing tasks
3. scan Antora content structure
4. resolve and render AsciiDoc content for Confluence
5. create and update Confluence pages
6. detect changed pages and publish incrementally
7. create missing pages for new source content
8. maintain source-to-page mapping metadata
9. emit build-friendly reports

---

## 11. Plugin UX and Gradle Integration Model

The plugin should behave similarly to a well-designed Gradle publishing plugin.

### 11.1 Plugin application

Example:

```kotlin id="az58pv"
plugins {
    id("org.antora") version "..."
    id("com.example.antora-confluence") version "1.0.0"
}
```

### 11.2 Extension-based configuration

Example:

```kotlin id="gkcx19"
antoraConfluence {
    confluence {
        baseUrl.set("https://company.atlassian.net/wiki")
        spaceKey.set("ENG")
        parentPageId.set("123456789")
        username.set(providers.environmentVariable("CONFLUENCE_USER"))
        apiToken.set(providers.environmentVariable("CONFLUENCE_TOKEN"))
    }

    source {
        antoraRoot.set(layout.projectDirectory.dir("docs"))
        siteKey.set("platform-docs")
    }

    publish {
        hierarchy.set(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)
        versionMode.set(VersionMode.HIERARCHY)
        createIndexPages.set(true)
        orphanStrategy.set(OrphanStrategy.REPORT)
        strict.set(false)
        dryRun.convention(false)
    }
}
```

### 11.3 Tasks

The plugin should register tasks such as:

* `antoraConfluenceValidate`
* `antoraConfluencePlan`
* `antoraConfluencePublish`
* `antoraConfluenceFullPublish`
* `antoraConfluenceReconcileState`

Optionally:

* `antoraConfluencePublish<Component>`
* `antoraConfluencePublish<Version>`

These tasks should be discoverable and consistent.

---

## 12. Functional Requirements

## 12.1 Plugin Application and Task Registration

The plugin must:

* apply cleanly to Gradle projects
* register a top-level extension named `antoraConfluence`
* register tasks lazily
* expose task groups and descriptions appropriately
* support Kotlin DSL and Groovy DSL
* work in single-project and multi-project builds

### Acceptance Criteria

* Applying the plugin adds the expected extension and tasks.
* Running `./gradlew tasks` shows Confluence tasks under a clear group such as `documentation` or `publishing`.

---

## 12.2 Antora Source Discovery

The plugin must discover Antora content from configured directories and optionally integrate with outputs or metadata from an Antora Gradle workflow.

### Requirements

* support local Antora content roots
* detect components, versions, modules, pages, nav files, images, and attachments
* understand standard Antora directory structure
* optionally consume Antora-generated metadata if available from related tasks

### Acceptance Criteria

* The plugin can scan a standard Antora repo layout and build an internal content model.
* Invalid source structure produces actionable Gradle task failures.

---

## 12.3 Relationship to Gradle Antora Plugin

Because the user explicitly wants similarity to the Gradle Antora plugin, this product should align with that mental model.

### Requirements

* configuration should be declarative and nested
* tasks should fit the documentation lifecycle
* it should support task dependency wiring with Antora tasks where present
* it should not require users to duplicate core source config unnecessarily if those values can be inferred or shared

### Preferred behavior

If Antora plugin tasks or extensions are present, the plugin should be able to:

* discover relevant source directories
* depend on validation or preparation tasks when appropriate
* optionally consume a prepared content model rather than rescanning everything from scratch

But this should be additive, not a hard dependency.

### Acceptance Criteria

* The plugin works standalone with Antora-structured content.
* The plugin works more smoothly when the Gradle Antora plugin is also applied.

---

## 12.4 Confluence Hierarchy Mapping

The plugin must map Antora structure into Confluence page hierarchy.

### Default hierarchy

Recommended default:

* configured root page

  * component

    * version

      * module

        * page

### Requirements

* configurable hierarchy strategy
* optional flattening of module nodes
* optional version-as-title-prefix mode
* optional component landing pages and version landing pages

### Acceptance Criteria

* Hierarchy is deterministic across runs.
* Users can configure parent structure without custom code.

---

## 12.5 AsciiDoc Rendering for Confluence

The plugin must render AsciiDoc into Confluence-compatible content.

### Initial required support

* headings
* paragraphs
* lists
* code blocks
* tables
* blockquotes
* inline styles
* links
* anchors
* images
* common admonitions
* include directives
* internal xrefs where resolvable

### Requirements

* resolve includes before diffing and publishing
* normalize rendered content for stable comparison
* support warnings for unsupported constructs
* strict mode should fail on unresolved critical issues

### Acceptance Criteria

* Common engineering docs render cleanly in Confluence.
* Unsupported constructs are reported in task output.

---

## 12.6 Change Detection and Incremental Publish

This is a core feature.

The plugin must detect which pages need publishing.

### Changes that must trigger update

* source content changes
* include dependency changes
* image/attachment changes that affect a page
* title changes
* parent hierarchy changes
* relevant rendering config changes
* xref resolution changes that alter output

### Detection model

The plugin should compute a **canonical rendered fingerprint** rather than only raw file hashes.

This fingerprint should include:

* normalized rendered body
* title
* parent mapping
* relevant metadata
* referenced assets
* render-affecting configuration

### Dependency tracking

Maintain dependency relationships:

* page -> includes
* page -> images
* page -> attachments
* page -> xrefs where needed

### Gradle-specific expectation

Task inputs should be modeled clearly, but remote publish decisions should still use internal state reconciliation rather than trusting only Gradle’s local up-to-date checks.

This is important. Gradle’s incremental model helps, but Confluence state is remote. The plugin must do its own publish diffing logic.

### Acceptance Criteria

* Unchanged runs result in no Confluence updates.
* Changing one included file republishes all impacted pages.
* Adding a new page causes creation only for that page and any impacted index nodes.

---

## 12.7 Confluence Page Creation

The plugin must create missing pages in Confluence.

### Requirements

* authenticate to Confluence
* create parent nodes if missing and configured
* create leaf pages with title, content, labels, and metadata
* store page mapping metadata remotely and locally

### Stored metadata should include

* managed marker
* canonical page key
* source file path
* Antora component/module/version
* content fingerprint
* tool/plugin version
* publish timestamp

### Acceptance Criteria

* New source pages result in corresponding new Confluence pages.
* Confluence pages created by the plugin are identifiable as managed pages.

---

## 12.8 Updating Existing Pages as New Confluence Versions

When content changes, the plugin must update the existing Confluence page and let Confluence create a new page version.

### Requirements

* resolve managed page identity
* compare fingerprint
* update only when changed
* preserve page mapping
* record new metadata after update

### Acceptance Criteria

* Changed pages receive new Confluence revisions.
* Unchanged pages are skipped.

---

## 12.9 New Source Page Detection

The plugin must discover source pages that do not yet exist in Confluence.

### Requirements

* reconcile source inventory with known state and remote metadata
* create missing pages under correct parent
* update hierarchy/index structures if configured

### Acceptance Criteria

* Newly added Antora pages are published without manual page registration.

---

## 12.10 Page Identity and Rename Safety

This is one of the most important design areas.

### Requirements

* assign each managed page a canonical page key
* persist that key in local state and Confluence page properties
* support explicit stable page key override in source or config
* surface ambiguous renames rather than blindly duplicating pages

### Recommended canonical key

```text id="7od4sw"
<siteKey>/<component>/<version>/<module>/<relative-page-path>
```

### Optional explicit override

Allow users to define a persistent identity attribute, for example:

```asciidoc id="glzy2u"
:confluence-page-key: billing-auth
```

### Product decision

Do not rely heavily on fuzzy rename detection in v1. It sounds smart, but it causes bad surprises. Explicit stable identity is more reliable.

### Acceptance Criteria

* Managed page identity survives ordinary repeated publishes.
* Explicit page keys prevent accidental duplication during moves.

---

## 12.11 Asset Handling

The plugin should support images in v1 and optionally attachments later.

### Requirements

* detect locally referenced images
* upload changed images to Confluence
* associate images with correct target pages
* rewrite references appropriately for Confluence rendering

### Acceptance Criteria

* Local images display correctly in published Confluence pages.
* Changed images are republished when necessary.

---

## 12.12 Internal Cross References

The plugin must resolve Antora internal links where possible.

### Requirements

* map xrefs to managed Confluence target pages
* preserve external links unchanged
* warn on unresolved internal links
* fail on unresolved critical links in strict mode

### Acceptance Criteria

* Common internal documentation links work after publish.

---

## 12.13 Orphan Detection

The plugin should detect managed Confluence pages whose source content no longer exists.

### v1 Requirements

* identify orphaned managed pages
* report them
* optionally archive or relabel them
* do not hard delete by default

### Acceptance Criteria

* Orphaned managed pages appear in publish reports.
* Default behavior is safe and non-destructive.

---

## 12.14 Validation and Dry Run

The plugin must provide Gradle tasks for non-mutating analysis.

### Required tasks

* `antoraConfluenceValidate`
* `antoraConfluencePlan`

### `validate`

Should:

* scan source
* resolve includes/xrefs
* validate config and auth
* identify unsupported constructs

### `plan`

Should:

* compute create/update/skip/orphan actions
* avoid performing writes
* emit a human-readable and machine-readable report

### Acceptance Criteria

* Teams can preview publication changes before publishing.

---

## 13. Gradle Task Model

## 13.1 Required Tasks

### `antoraConfluenceValidate`

Purpose:

* validate source structure, configuration, credentials, rendering feasibility, and references

### `antoraConfluencePlan`

Purpose:

* produce a publication plan without writes

### `antoraConfluencePublish`

Purpose:

* perform incremental publish

### `antoraConfluenceFullPublish`

Purpose:

* republish all managed pages regardless of diff

### `antoraConfluenceReconcileState`

Purpose:

* rebuild or verify local state from remote page metadata

---

## 13.2 Task Inputs and Outputs

The plugin should model inputs clearly using Gradle lazy types.

### Inputs should include

* Antora source directories
* nav files
* included files
* plugin config
* render-affecting options
* credentials presence markers, not secret values directly
* local state file where appropriate

### Outputs may include

* report files
* state file
* plan file

### Important constraint

The publish task should not pretend to be fully cacheable across environments. It interacts with remote mutable state. It can still participate in up-to-date checks in a limited and honest way.

### Recommended annotation posture

* validation and planning tasks: more build-cache friendly
* publish tasks: likely not cacheable, but incrementally smart internally

This is the right tradeoff.

---

## 13.3 Task Dependency Wiring

The plugin should support common dependency patterns.

### Examples

* `antoraConfluencePublish` depends on `antoraConfluenceValidate`
* if Antora site generation tasks exist, publish may depend on them or consume their outputs
* project teams can wire `check.dependsOn(antoraConfluenceValidate)`

### Acceptance Criteria

* Tasks compose naturally with existing Gradle lifecycle.

---

## 14. Configuration Model

The plugin must use an extension-based configuration model with nested blocks.

## 14.1 Example Kotlin DSL

```kotlin id="ggjlwm"
antoraConfluence {
    confluence {
        baseUrl.set("https://company.atlassian.net/wiki")
        spaceKey.set("ENG")
        parentPageId.set("123456")
        username.set(providers.environmentVariable("CONFLUENCE_USER"))
        apiToken.set(providers.environmentVariable("CONFLUENCE_TOKEN"))
    }

    source {
        antoraRoot.set(layout.projectDirectory.dir("docs"))
        siteKey.set("platform-docs")
        includes.from("docs/modules")
    }

    publish {
        hierarchy.set(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)
        versionMode.set(VersionMode.HIERARCHY)
        createIndexPages.set(true)
        strict.set(false)
        orphanStrategy.set(OrphanStrategy.REPORT)
        applyLabels.add("managed-by-antora-confluence")
        applyLabels.add("docs-as-code")
    }

    render {
        failOnUnresolvedXref.set(true)
        uploadImages.set(true)
        normalizeWhitespaceForDiff.set(true)
    }

    state {
        file.set(layout.buildDirectory.file("antora-confluence/state.json"))
        rebuildFromRemoteOnMissing.set(true)
    }

    reports {
        jsonReportFile.set(layout.buildDirectory.file("reports/antora-confluence/publish.json"))
        planReportFile.set(layout.buildDirectory.file("reports/antora-confluence/plan.json"))
    }
}
```

## 14.2 Example Groovy DSL

```groovy id="2rzhxw"
antoraConfluence {
    confluence {
        baseUrl = 'https://company.atlassian.net/wiki'
        spaceKey = 'ENG'
        parentPageId = '123456'
        username = providers.environmentVariable('CONFLUENCE_USER')
        apiToken = providers.environmentVariable('CONFLUENCE_TOKEN')
    }

    source {
        antoraRoot = layout.projectDirectory.dir('docs')
        siteKey = 'platform-docs'
    }

    publish {
        hierarchy = HierarchyMode.COMPONENT_VERSION_MODULE_PAGE
        versionMode = VersionMode.HIERARCHY
        createIndexPages = true
        orphanStrategy = OrphanStrategy.REPORT
    }
}
```

---

## 15. Extension Model

The plugin should expose a clean domain model using Gradle managed properties.

### Recommended nested extensions

* `confluence`
* `source`
* `publish`
* `render`
* `state`
* `reports`

### Property types

Use:

* `Property<T>`
* `ListProperty<T>`
* `SetProperty<T>`
* `DirectoryProperty`
* `RegularFileProperty`
* `ConfigurableFileCollection`
* `Provider<T>`

### Why

This makes the plugin compatible with lazy configuration, configuration cache goals, and enterprise convention plugin usage.

---

## 16. Multi-Project Support

The plugin should work in multi-project Gradle builds.

### Requirements

* allow application at root or subproject level
* allow each project to publish its own docs
* optionally support an aggregate publish task at root
* avoid configuration collisions across projects

### Example scenarios

* each service publishes its own Antora component
* root project aggregates docs from multiple modules
* enterprise convention plugin configures shared Confluence defaults

### Acceptance Criteria

* Multi-project builds can publish docs from one or several subprojects.

---

## 17. State Management

The plugin must persist publication state.

### Required state fields

* canonical page key
* source path
* Confluence page ID
* title
* parent ID or parent key
* last fingerprint
* dependency fingerprint info
* last publish timestamp
* plugin version

### Storage approach

Use both:

1. **local state file** under `build/` or configured location
2. **Confluence page properties/labels** for remote recovery

### Why

* local state improves speed
* remote metadata allows recovery and reconciliation
* relying on only one of them is brittle

### Acceptance Criteria

* Missing local state can be rebuilt from remote metadata when configured.
* State remains deterministic across repeated runs.

---

## 18. Authentication and Security

### Requirements

* support secure authentication via Gradle providers and environment variables
* do not print secrets in logs
* validate credentials before expensive work begins
* support token-based auth for Confluence Cloud first

### Acceptance Criteria

* Invalid credentials fail early.
* Secrets remain redacted.

---

## 19. Reporting and Logging

The plugin must produce both developer-friendly and CI-friendly output.

### Console output should summarize

* pages scanned
* pages created
* pages updated
* pages skipped
* pages failed
* orphaned pages detected
* images uploaded
* warnings

### File outputs

* JSON plan report
* JSON publish report
* optional detailed diagnostics

### Logging modes

* quiet
* lifecycle/default
* info
* debug

### Acceptance Criteria

* Build logs clearly explain what happened without requiring debug mode.
* CI can archive structured reports.

---

## 20. Non-Functional Requirements

## 20.1 Performance

* handle hundreds to thousands of pages
* incremental publish should avoid unnecessary remote calls
* use concurrency carefully for remote reads/uploads while preserving safe page-tree operations

## 20.2 Reliability

* tolerate partial page-level failures when possible
* retry transient API failures with backoff
* fail fast on configuration or auth errors

## 20.3 Idempotency

* repeated publish with unchanged source does nothing remotely
* pages are not duplicated in common repeat-run scenarios

## 20.4 Maintainability

The implementation should separate:

* Gradle plugin layer
* content discovery engine
* renderer
* diff engine
* Confluence client
* state manager

This is important because a lot of plugin projects become unmaintainable when all logic is stuffed directly into task classes.

## 20.5 Gradle Compatibility

* support modern Gradle versions
* support configuration avoidance
* aim for configuration-cache friendliness where practical
* avoid eager task realization

---

## 21. Proposed Internal Architecture

## 21.1 Modules / Major Components

1. **Plugin Layer**

   * registers extension and tasks
   * wires dependencies
   * adapts Gradle properties to internal engine

2. **Antora Content Model Builder**

   * scans Antora structure
   * resolves components/modules/versions/pages

3. **Renderer**

   * parses AsciiDoc
   * resolves includes/xrefs
   * produces normalized output

4. **Dependency Graph Builder**

   * tracks page dependencies

5. **Diff Engine**

   * computes fingerprints
   * determines create/update/skip/orphan

6. **Confluence Client**

   * page lookup
   * create/update
   * upload asset
   * apply labels/properties

7. **State Store**

   * local manifest IO
   * remote metadata reconciliation

8. **Reporter**

   * console and JSON output

## 21.2 Data Flow

1. Gradle config is evaluated lazily
2. task executes
3. auth validated
4. source content discovered
5. pages rendered and normalized
6. fingerprints calculated
7. local and remote state reconciled
8. plan built
9. publish actions executed if task is mutating
10. state and reports written

---

## 22. Recommended Gradle API Design

The plugin should be pleasant to configure and easy to standardize.

### Recommended DSL characteristics

* nested extension blocks
* enums for hierarchy and strategy options
* strongly typed properties
* minimal required configuration for common cases

### Recommended task types

* abstract task classes using injected services
* input annotations on local inputs
* shared build services for API clients or throttling
* worker API where useful for CPU-bound rendering or planning phases

### Why this matters

A plugin like this will be adopted across teams only if it feels like a real Gradle plugin, not a shell command wrapper hidden behind `Exec`.

---

## 23. Example Gradle Usage Scenarios

## 23.1 Simple Project

A single docs project wants to publish one Antora component into one Confluence space.

Tasks:

* `./gradlew antoraConfluencePlan`
* `./gradlew antoraConfluencePublish`

## 23.2 CI Release Workflow

Pipeline runs:

* `./gradlew antoraConfluenceValidate`
* `./gradlew antoraConfluencePublish -PdocsRelease=true`

Only release branches execute the publish task.

## 23.3 Multi-Service Monorepo

Each subproject has docs and applies the plugin.
Root task aggregates:

* `publishAllDocsToConfluence`

## 23.4 Convention Plugin

An enterprise plugin preconfigures:

* Confluence base URL
* auth convention
* labels
* reporting location
* default hierarchy strategy

Each team only sets `spaceKey`, `parentPageId`, and `antoraRoot`.

---

## 24. Failure Handling

### Error categories

* invalid Gradle configuration
* missing required Confluence settings
* invalid Antora structure
* unresolved include/xref
* duplicate page key conflict
* Confluence API permission issue
* throttling or transient network failure
* unsupported AsciiDoc construct

### Behavior

* fail fast on config/auth issues
* continue page-by-page when safe for publish
* strict mode upgrades warnings into failures
* produce source-path-aware diagnostics

### Acceptance Criteria

* One broken page does not necessarily abort an entire large publish unless strict mode is enabled.

---

## 25. Risks and Mitigations

## Risk 1: Plugin becomes just a wrapper around a hidden CLI

**Mitigation:** design native Gradle extension, task types, and lifecycle integration.

## Risk 2: Remote publishing semantics do not align with Gradle up-to-date checks

**Mitigation:** clearly separate Gradle task inputs from internal remote reconciliation logic.

## Risk 3: Too much hidden coupling to a specific Antora plugin

**Mitigation:** support integration with Antora plugin when present, but keep standalone Antora-structure scanning available.

## Risk 4: Duplicate Confluence pages during moves/refactors

**Mitigation:** use stable canonical page keys and optional explicit page identity overrides.

## Risk 5: Rendering fidelity disappoints users

**Mitigation:** define supported subset clearly and emit warnings for unsupported constructs.

## Risk 6: Dangerous deletion behavior

**Mitigation:** orphan report or archive only, no default delete.

---

## 26. Success Metrics

### Adoption

* number of repositories applying the plugin
* number of successful publish executions per week

### Developer Experience

* median lines of plugin configuration per repo
* median time to first successful publish
* reduction in custom CI scripts

### Publish Quality

* duplicate page incidents
* unresolved xref counts
* publish failure rate
* unchanged-run zero-update rate

### Platform Quality

* number of teams standardizing through convention plugins
* number of multi-project builds using aggregate publish

---

## 27. MVP Definition

The MVP should include:

* Gradle plugin application and extension
* Antora content scanning
* task registration
* validate / plan / publish / full publish tasks
* Confluence Cloud authentication
* page creation and update
* dependency-aware incremental detection
* new page detection
* image support
* local state + remote metadata
* human-readable and JSON reports
* task wiring compatible with Antora-based docs builds

### MVP exclusions

* hard delete of orphan pages
* advanced rename heuristics
* Data Center support
* broad custom extension support
* perfect remote build-cache semantics
* attachment lifecycle beyond common images

---

## 28. Release Readiness Criteria

The plugin is ready for initial release when:

1. It can be applied to a Gradle project and configured via DSL.
2. It registers expected tasks and these run correctly in local and CI environments.
3. It can scan a valid Antora structure and publish to Confluence.
4. It creates the expected page hierarchy on first publish.
5. A second run with no changes results in no page updates.
6. Editing a single page updates only that page.
7. Editing a shared include updates all affected pages.
8. Adding a new page creates a new Confluence page.
9. Local images render correctly in Confluence.
10. Dry-run/plan output is accurate.
11. Managed pages are traceable through metadata.
12. The plugin integrates cleanly with builds already using Antora-related Gradle tasks.

---

## 29. Open Questions

1. Should the plugin depend directly on the Gradle Antora plugin APIs, or only integrate opportunistically if present?
2. Should render conversion use an HTML intermediary, AST intermediary, or direct Confluence format generation?
3. Should component/version landing pages be autogenerated by default?
4. How much configuration should be inferred from Antora plugin configuration versus explicitly declared?
5. Should aggregate multi-project publishing be in v1 or v1.1?
6. Should the plugin expose a separate reusable Java/Groovy API for convention plugins and custom tasks?
7. What is the minimum Gradle version target?
8. Should the plugin support both Kotlin DSL-first docs and Groovy DSL examples equally in documentation?

---

## 30. Recommended Product Decisions

These are the choices I would make.

### 30.1 Keep the engine separate from the plugin

The plugin should be the Gradle-facing shell over a reusable engine. That keeps testing cleaner and leaves room for a CLI later if needed.

### 30.2 Make Gradle integration first-class, not superficial

Use typed extensions, lazy properties, proper tasks, and shared services.

### 30.3 Integrate with Antora plugin when present, but do not require it

That is the right compatibility story.

### 30.4 Default to safe publication behavior

Incremental publish, report-only orphan handling, and strict metadata tracking should all be defaults.

### 30.5 Prefer explicit page identity over heuristic rename guessing

This will save real pain in long-lived docs repos.

### 30.6 Be honest about caching

Plan and validate can be more cache-friendly. Publish is remote and mutable. Design accordingly.

---

## 31. One-Paragraph Summary

The proposed product is a Gradle plugin, modeled after the workflow style of the Gradle Antora plugin, that publishes Antora-structured AsciiDoc documentation into Atlassian Confluence as a managed hierarchy of pages. It integrates naturally into Gradle builds using extensions and tasks, supports incremental publish based on dependency-aware content fingerprints, creates new Confluence pages for newly added source content, updates existing pages as new Confluence versions, and provides validation, planning, and reporting suitable for local development and CI/CD. The design prioritizes safe publishing, traceable page identity, Gradle-native ergonomics, and compatibility with existing Antora-based documentation pipelines.

