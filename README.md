# antoraConfluence

A Gradle plugin that publishes [Antora](https://antora.org/)-structured AsciiDoc documentation into Atlassian Confluence as a managed hierarchy of pages.

## Features

- **Gradle-native ergonomics** – integrates with Gradle via an extension and four lifecycle tasks, modelled after the [Gradle Antora plugin](https://github.com/gradle/gradle-antora-plugin) workflow
- **Incremental publish** – SHA-256 content fingerprints avoid redundant API calls; only changed pages are sent to Confluence
- **Traceable page identity** – each page has a stable `<component>/<version>/<module>/<path>` key stored in a local fingerprint store, enabling reliable create/update detection across runs
- **Safe publishing** – supports `CREATE_AND_UPDATE`, `CREATE_ONLY`, and `UPDATE_ONLY` strategies, plus a `dryRun` mode that logs all planned actions without touching Confluence
- **Validation and planning** – dedicated tasks for local development and CI/CD that validate content structure and show a publish plan before any changes are made
- **AsciiDoc conversion** – converts AsciiDoc source files to HTML using [Asciidoctorj](https://asciidoctor.org/docs/asciidoctorj/)

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
    confluenceUrl = "https://mycompany.atlassian.net/wiki"
    username      = "user@example.com"
    apiToken      = System.getenv("CONFLUENCE_API_TOKEN")
    spaceKey      = "DOCS"
    parentPageTitle = "My Documentation"          // must already exist in the space
    contentDir    = layout.projectDirectory.dir("docs")
    publishStrategy = PublishStrategy.CREATE_AND_UPDATE  // default
    dryRun        = false                          // default
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
| `antoraConfluenceValidate` | Validates the Antora content structure and checks that required configuration properties are set. No network calls. |
| `antoraConfluencePlan` | Dry-run that shows which pages would be created or updated. Queries Confluence for existing pages if credentials are provided. |
| `antoraConfluencePublish` | Publishes content to Confluence. Uses fingerprints for incremental updates. |
| `antoraConfluenceReport` | Reports on the current publish state from the local fingerprint store and latest publish report. |

### Task dependency graph

```
antoraConfluenceReport
  └── antoraConfluencePlan
        └── antoraConfluenceValidate

antoraConfluencePublish
  └── antoraConfluenceValidate
```

## Publish strategies

| Strategy | Behaviour |
|----------|-----------|
| `CREATE_AND_UPDATE` | Creates new pages and updates existing ones (default). |
| `CREATE_ONLY` | Only creates new pages; never updates existing Confluence pages. |
| `UPDATE_ONLY` | Only updates pages that already exist in Confluence; never creates new ones. |

## Incremental publish

After a successful publish, the plugin writes a fingerprint store to
`build/antora-confluence/fingerprints.json`. On subsequent runs, only pages whose
AsciiDoc content has changed since the last publish are sent to Confluence.

The fingerprint entry for each page contains:
- A SHA-256 hash of the AsciiDoc source
- The Confluence numeric page ID (for efficient updates)
- The timestamp of the last successful publish

## Dry run

Set `dryRun = true` (or pass `-PdryRun=true`) to log all planned create/update
actions without making any mutating API calls. This is useful for verifying the
publish plan in CI/CD before a live run.

## Build & test

```bash
./gradlew build
./gradlew test
```

## License

Apache 2.0
