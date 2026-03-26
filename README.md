# antoraConfluence

A Gradle plugin that publishes [Antora](https://antora.org/)-structured AsciiDoc documentation into Atlassian Confluence as a managed hierarchy of pages.

## Features

- **Gradle-native ergonomics** – declarative nested DSL, lazy properties, and task lifecycle integration
- **Antora-aware** – understands component/version/module/page structure rather than treating docs as a flat file tree
- **Incremental publish** – SHA-256 content fingerprints avoid redundant API calls; only changed pages are sent to Confluence
- **Traceable page identity** – stable `<siteKey>/<component>/<version>/<module>/<path>` key persisted locally and (in future) in Confluence page properties
- **Safe by default** – `CREATE_AND_UPDATE` strategy, `REPORT`-only orphan handling, `dryRun` mode
- **Strict mode** – promote warnings to build failures for CI quality gates
- **AsciiDoc conversion** – renders AsciiDoc to HTML using [Asciidoctorj](https://asciidoctor.org/docs/asciidoctorj/)

## Requirements

- Gradle 8.x or 9.x
- Java 17+
- Atlassian Confluence Cloud or Data Center (REST API v2)

## Quick Start

### 1. Apply the plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.bovinemagnet.antora-confluence") version "0.1.0"
}
```

### 2. Configure

```kotlin
antoraConfluence {
    confluence {
        baseUrl.set("https://mycompany.atlassian.net/wiki")
        spaceKey.set("DOCS")
        parentPageId.set("123456789")                                // numeric Confluence page ID
        username.set(providers.environmentVariable("CONFLUENCE_USER"))
        apiToken.set(providers.environmentVariable("CONFLUENCE_TOKEN"))
    }

    source {
        antoraRoot.set(layout.projectDirectory.dir("docs"))          // default
        siteKey.set("platform-docs")                                 // optional site namespace
    }

    publish {
        hierarchy.set(HierarchyMode.COMPONENT_VERSION_MODULE_PAGE)   // default
        versionMode.set(VersionMode.HIERARCHY)                       // default
        createIndexPages.set(false)                                  // default
        strict.set(false)                                            // default
        orphanStrategy.set(OrphanStrategy.REPORT)                    // default
        publishStrategy.set(PublishStrategy.CREATE_AND_UPDATE)       // default
        applyLabels.add("managed-by-antora-confluence")
        dryRun.set(false)                                            // default
    }

    render {
        uploadImages.set(true)                                       // default
        normalizeWhitespaceForDiff.set(true)                         // default
        failOnUnresolvedXref.set(false)                              // default
    }

    state {
        // file defaults to build/antora-confluence/state.json
        rebuildFromRemoteOnMissing.set(true)                         // default
    }

    reports {
        // jsonReportFile defaults to build/antora-confluence/publish-report.json
        // planReportFile  defaults to build/antora-confluence/plan-report.json
    }
}
```

### 3. Expected Antora content layout

```
docs/
├── antora.yml             ← component descriptor (must have a 'name' field)
└── modules/
    └── ROOT/
        └── pages/
            ├── index.adoc
            └── getting-started.adoc
```

## Tasks

| Task | Description |
|------|-------------|
| `antoraConfluenceValidate` | Validates the Antora content structure and required configuration. No network calls. |
| `antoraConfluencePlan` | Dry-run showing `CREATE` / `UPDATE` / `SKIP` per page. Writes a JSON plan report. |
| `antoraConfluencePublish` | Incremental publish — only pages whose content fingerprint has changed. |
| `antoraConfluenceFullPublish` | Full publish — republishes all pages regardless of fingerprint. |
| `antoraConfluenceReconcileState` | Rebuilds local state file from remote Confluence page metadata. |
| `antoraConfluenceReport` | Reads local state and last publish report; displays a summary. |

All tasks appear under the **Documentation** task group.

### Task dependency graph

```
antoraConfluencePublish     antoraConfluenceFullPublish
  └── antoraConfluenceValidate

antoraConfluencePlan
  └── antoraConfluenceValidate
```

## Page identity

Each managed page has a stable canonical key:

```
<siteKey>/<component>/<version>/<module>/<relative-path-without-extension>
```

For example: `platform-docs/my-component/1.0/ROOT/getting-started`

The `siteKey` segment is omitted when `source.siteKey` is not set. This key is stored in the
local state file and will be used as a Confluence page property for remote reconciliation.

## Enums

### `HierarchyMode`
| Value | Hierarchy produced |
|-------|--------------------|
| `COMPONENT_VERSION_MODULE_PAGE` | root → component → version → module → page *(default)* |
| `COMPONENT_VERSION_PAGE` | root → component+version → module → page |
| `COMPONENT_PAGE` | root → component → version → page *(module level flattened)* |

### `VersionMode`
| Value | Behaviour |
|-------|-----------|
| `HIERARCHY` | Each version is a separate hierarchy level *(default)* |
| `TITLE_PREFIX` | Version is prepended to the page title instead |

### `OrphanStrategy`
| Value | Behaviour |
|-------|-----------|
| `REPORT` | List orphaned pages in the publish report; no Confluence changes *(default)* |
| `LABEL` | Add an `antora-confluence-orphan` label to the page in Confluence |
| `ARCHIVE` | Move orphaned pages to a configured archive location |

### `PublishStrategy`
| Value | Behaviour |
|-------|-----------|
| `CREATE_AND_UPDATE` | Creates new pages and updates existing ones *(default)* |
| `CREATE_ONLY` | Only creates new pages; never updates existing Confluence pages |
| `UPDATE_ONLY` | Only updates pages that already exist in Confluence |

## Incremental publish

After a successful publish, the plugin writes a state file to
`build/antora-confluence/state.json` (configurable via `state.file`). On subsequent runs,
only pages whose AsciiDoc content has changed since the last publish are sent to Confluence.

If the state file is deleted or missing, run `antoraConfluenceReconcileState` to rebuild it
from remote Confluence page metadata (requires `state.rebuildFromRemoteOnMissing = true`).

## Build & test

```bash
./gradlew build
./gradlew test
```

## License

Apache 2.0
