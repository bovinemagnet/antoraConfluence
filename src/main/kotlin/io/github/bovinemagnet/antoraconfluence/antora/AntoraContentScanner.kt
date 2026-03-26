package io.github.bovinemagnet.antoraconfluence.antora

import java.io.File

/**
 * Represents a single AsciiDoc page discovered inside an Antora content tree.
 *
 * @property siteKey          Optional site-level identifier that namespaces the canonical page key.
 * @property componentName    Value of the `name` field in the nearest `antora.yml`.
 * @property componentVersion Value of the `version` field in the nearest `antora.yml` (may be empty).
 * @property moduleName       Name of the Antora module (folder under `modules/`).
 * @property relativePath     Path of the `.adoc` file relative to the module's `pages/` directory.
 * @property sourceFile       Absolute file reference to the `.adoc` source.
 * @property title            Document title parsed from the AsciiDoc `= Title` heading; falls back
 *                            to [suggestedTitle] when blank.
 * @property images           Image targets extracted from block and inline image macros.
 * @property includes         Targets of all `include::` directives found in the source.
 * @property xrefs            Targets of all `xref:` macros found in the source.
 */
data class AntoraPage(
    val siteKey: String,
    val componentName: String,
    val componentVersion: String,
    val moduleName: String,
    val relativePath: String,
    val sourceFile: File,
    val title: String = "",
    val images: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val xrefs: List<String> = emptyList()
) {
    /**
     * Stable canonical identity key for this page.
     *
     * Format: `<siteKey>/<component>/<version>/<module>/<path-without-extension>`
     *
     * The `siteKey` segment is omitted when blank.  This key is used:
     * - as the key in [io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore]
     * - as the identity stored in Confluence page properties
     */
    val pageId: String
        get() = buildString {
            if (siteKey.isNotBlank()) {
                append(siteKey)
                append("/")
            }
            append(componentName)
            if (componentVersion.isNotBlank()) {
                append("/")
                append(componentVersion)
            }
            append("/")
            append(moduleName)
            append("/")
            append(relativePath.removeSuffix(".adoc"))
        }

    /**
     * Suggested Confluence page title.
     *
     * When [title] is non-blank it is used as the base; otherwise the AsciiDoc filename is
     * used. In both cases hyphens and underscores are replaced with spaces and each word is
     * title-cased, so that raw filenames and plain document titles are both normalised into a
     * human-readable form suitable for a Confluence page title.
     */
    val suggestedTitle: String
        get() {
            val base = if (title.isNotBlank()) title else sourceFile.nameWithoutExtension
            return base
                .replace('-', ' ')
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
        }
}

/**
 * Scans an Antora content directory tree and returns all discoverable [AntoraPage] entries.
 *
 * The scanner expects the standard Antora layout:
 * ```
 * <contentDir>/
 * └── <component>/
 *     ├── antora.yml
 *     └── modules/
 *         └── <module>/
 *             └── pages/
 *                 ├── index.adoc
 *                 └── ...
 * ```
 *
 * The scanner is intentionally stateless and side-effect free – it only reads the filesystem.
 */
class AntoraContentScanner {

    private val referenceExtractor = AsciiDocReferenceExtractor()

    /**
     * Scans [contentDir] recursively for all Antora components and their pages.
     *
     * @param contentDir Root of the Antora content source tree.
     * @param siteKey    Optional site-level identifier prepended to each page's canonical key.
     * @return List of [AntoraPage] instances, one per `.adoc` file found.
     * @throws AntoraStructureException if [contentDir] does not exist or is not a directory.
     */
    fun scan(contentDir: File, siteKey: String = ""): List<AntoraPage> {
        require(contentDir.exists()) { "Content directory does not exist: ${contentDir.absolutePath}" }
        require(contentDir.isDirectory) { "Content path is not a directory: ${contentDir.absolutePath}" }

        return findAntoraYmlFiles(contentDir).flatMap { antoraYml ->
            val descriptor = parseAntoraYml(antoraYml)
            scanComponent(antoraYml.parentFile, descriptor, siteKey)
        }
    }

    /**
     * Performs a full scan of [contentDir], returning both the list of pages (with extracted
     * references) and a manifest of all images discovered in `images/` directories.
     *
     * @param contentDir Root of the Antora content source tree.
     * @param siteKey    Optional site-level identifier prepended to each page's canonical key.
     * @return [AntoraContentModel] containing pages and image manifest.
     */
    fun scanFull(contentDir: File, siteKey: String = ""): AntoraContentModel {
        val pages = scan(contentDir, siteKey)
        val imageManifest = discoverImages(contentDir)
        return AntoraContentModel(pages, imageManifest)
    }

    /**
     * Validates that [contentDir] contains at least one Antora component descriptor (`antora.yml`).
     *
     * @throws AntoraStructureException if no descriptor is found.
     */
    fun validate(contentDir: File) {
        if (!contentDir.exists()) {
            throw AntoraStructureException("Content directory does not exist: ${contentDir.absolutePath}")
        }
        if (!contentDir.isDirectory) {
            throw AntoraStructureException("Content path is not a directory: ${contentDir.absolutePath}")
        }
        val descriptors = findAntoraYmlFiles(contentDir)
        if (descriptors.isEmpty()) {
            throw AntoraStructureException(
                "No antora.yml component descriptor found under: ${contentDir.absolutePath}"
            )
        }
        descriptors.forEach { yml ->
            val descriptor = try {
                parseAntoraYml(yml)
            } catch (e: AntoraStructureException) {
                throw AntoraStructureException("Invalid antora.yml at ${yml.absolutePath}: ${e.message}", e)
            }
            if (descriptor.name.isBlank()) {
                throw AntoraStructureException("antora.yml at ${yml.absolutePath} is missing a 'name' field.")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun findAntoraYmlFiles(root: File): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.name == "antora.yml" }
            .toList()

    internal fun parseAntoraYml(antoraYml: File): AntoraComponentDescriptor {
        val mapper = com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
        val tree: Map<String, Any?> = mapper.readValue(
            antoraYml,
            object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any?>>() {}
        )
        val name = tree["name"]?.toString()?.trim() ?: ""
        val version = tree["version"]?.toString()?.trim() ?: ""
        val title = tree["title"]?.toString()?.trim() ?: ""
        if (name.isBlank()) {
            throw AntoraStructureException("Missing 'name' field in ${antoraYml.absolutePath}")
        }
        return AntoraComponentDescriptor(name = name, version = version, title = title)
    }

    private fun scanComponent(
        componentDir: File,
        descriptor: AntoraComponentDescriptor,
        siteKey: String
    ): List<AntoraPage> {
        val modulesDir = File(componentDir, "modules")
        if (!modulesDir.isDirectory) return emptyList()

        return modulesDir.listFiles { f -> f.isDirectory }
            .orEmpty()
            .flatMap { moduleDir ->
                val pagesDir = File(moduleDir, "pages")
                if (!pagesDir.isDirectory) return@flatMap emptyList()
                scanPages(pagesDir, descriptor, moduleDir.name, pagesDir, siteKey)
            }
    }

    private fun scanPages(
        pagesDir: File,
        descriptor: AntoraComponentDescriptor,
        moduleName: String,
        baseDir: File,
        siteKey: String
    ): List<AntoraPage> =
        pagesDir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .map { adocFile ->
                val content = adocFile.readText()
                val refs = referenceExtractor.extract(content)
                AntoraPage(
                    siteKey = siteKey,
                    componentName = descriptor.name,
                    componentVersion = descriptor.version,
                    moduleName = moduleName,
                    relativePath = adocFile.relativeTo(baseDir).path,
                    sourceFile = adocFile,
                    title = refs.title ?: "",
                    images = refs.images,
                    includes = refs.includes,
                    xrefs = refs.xrefs
                )
            }
            .toList()

    private fun discoverImages(contentDir: File): Map<String, File> {
        val manifest = mutableMapOf<String, File>()
        contentDir.walkTopDown()
            .filter { it.isFile && it.parentFile.name == "images" }
            .forEach { manifest[it.name] = it }
        return manifest
    }
}

/** Lightweight representation of a parsed `antora.yml` file. */
data class AntoraComponentDescriptor(
    val name: String,
    val version: String,
    val title: String
)

/** Thrown when the Antora content structure is invalid or cannot be parsed. */
class AntoraStructureException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
