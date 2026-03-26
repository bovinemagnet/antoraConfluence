package io.github.bovinemagnet.antoraconfluence.antora

import java.io.File

/**
 * Represents a single AsciiDoc page discovered inside an Antora content tree.
 *
 * @property componentName  Value of the `name` field in the nearest `antora.yml`.
 * @property componentVersion Value of the `version` field in the nearest `antora.yml` (may be empty).
 * @property moduleName     Name of the Antora module (folder under `modules/`).
 * @property relativePath   Path of the `.adoc` file relative to the module's `pages/` directory.
 * @property sourceFile     Absolute file reference to the `.adoc` source.
 */
data class AntoraPage(
    val componentName: String,
    val componentVersion: String,
    val moduleName: String,
    val relativePath: String,
    val sourceFile: File
) {
    /**
     * A stable, human-readable identifier that uniquely identifies this page within a
     * Confluence space.  It is used as the Confluence page title and as the key in the
     * [io.github.bovinemagnet.antoraconfluence.fingerprint.ContentFingerprintStore].
     */
    val pageId: String
        get() = buildString {
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

    /** Suggested Confluence page title derived from the AsciiDoc file name. */
    val suggestedTitle: String
        get() {
            val baseName = sourceFile.nameWithoutExtension
            return baseName
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

    /**
     * Scans [contentDir] recursively for all Antora components and their pages.
     *
     * @param contentDir Root of the Antora content source tree.
     * @return List of [AntoraPage] instances, one per `.adoc` file found.
     * @throws AntoraStructureException if [contentDir] does not exist or is not a directory.
     */
    fun scan(contentDir: File): List<AntoraPage> {
        require(contentDir.exists()) { "Content directory does not exist: ${contentDir.absolutePath}" }
        require(contentDir.isDirectory) { "Content path is not a directory: ${contentDir.absolutePath}" }

        return findAntoraYmlFiles(contentDir).flatMap { antoraYml ->
            val descriptor = parseAntoraYml(antoraYml)
            scanComponent(antoraYml.parentFile, descriptor)
        }
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
        val lines = antoraYml.readLines()
        var name = ""
        var version = ""
        var title = ""
        for (line in lines) {
            when {
                line.startsWith("name:") -> name = line.removePrefix("name:").trim().removeSurrounding("'").removeSurrounding("\"")
                line.startsWith("version:") -> version = line.removePrefix("version:").trim().removeSurrounding("'").removeSurrounding("\"")
                line.startsWith("title:") -> title = line.removePrefix("title:").trim().removeSurrounding("'").removeSurrounding("\"")
            }
        }
        if (name.isBlank()) {
            throw AntoraStructureException("Missing 'name' field in ${antoraYml.absolutePath}")
        }
        return AntoraComponentDescriptor(name = name, version = version, title = title)
    }

    private fun scanComponent(componentDir: File, descriptor: AntoraComponentDescriptor): List<AntoraPage> {
        val modulesDir = File(componentDir, "modules")
        if (!modulesDir.isDirectory) return emptyList()

        return modulesDir.listFiles { f -> f.isDirectory }
            .orEmpty()
            .flatMap { moduleDir ->
                val pagesDir = File(moduleDir, "pages")
                if (!pagesDir.isDirectory) return@flatMap emptyList()
                scanPages(pagesDir, descriptor, moduleDir.name, pagesDir)
            }
    }

    private fun scanPages(
        pagesDir: File,
        descriptor: AntoraComponentDescriptor,
        moduleName: String,
        baseDir: File
    ): List<AntoraPage> =
        pagesDir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .map { adocFile ->
                AntoraPage(
                    componentName = descriptor.name,
                    componentVersion = descriptor.version,
                    moduleName = moduleName,
                    relativePath = adocFile.relativeTo(baseDir).path,
                    sourceFile = adocFile
                )
            }
            .toList()
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
