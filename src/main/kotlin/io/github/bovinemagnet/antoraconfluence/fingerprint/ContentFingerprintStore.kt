package io.github.bovinemagnet.antoraconfluence.fingerprint

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Tracks per-page content fingerprints (SHA-256 hashes) so that publish operations can be
 * performed incrementally: only pages whose content has changed since the last publish need to
 * be sent to Confluence.
 *
 * The store is persisted as a JSON file in the Gradle build directory so that it survives between
 * Gradle invocations without requiring a network round-trip.
 *
 * Thread-safety: this class is **not** thread-safe.  It is intended to be used from a single
 * Gradle task worker thread.
 */
class ContentFingerprintStore(private val storeFile: File) {

    private val mapper = jacksonObjectMapper()
    private val entries: MutableMap<String, FingerprintEntry> = mutableMapOf()

    init {
        load()
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the [FingerprintEntry] for [pageId], or `null` if no record exists yet.
     */
    fun get(pageId: String): FingerprintEntry? = entries[pageId]

    /**
     * Returns `true` if the content fingerprint for [pageId] differs from the stored value,
     * meaning the page needs to be published (created or updated).
     *
     * A page that has never been published is considered changed.
     *
     * @param pageId      Stable identity key (see [io.github.bovinemagnet.antoraconfluence.antora.AntoraPage.pageId]).
     * @param content     Current source content to compare against the stored fingerprint.
     */
    fun isChanged(pageId: String, content: String): Boolean {
        val stored = entries[pageId] ?: return true
        return stored.contentHash != sha256(content)
    }

    /**
     * Records or updates the fingerprint for [pageId].
     *
     * @param pageId          Stable identity key.
     * @param content         Source content whose hash will be stored.
     * @param confluencePageId Confluence numeric page ID, if the page exists in Confluence.
     * @param confluenceTitle  The title used when the page was last published.
     */
    fun put(
        pageId: String,
        content: String,
        confluencePageId: String? = null,
        confluenceTitle: String? = null
    ) {
        entries[pageId] = FingerprintEntry(
            pageId = pageId,
            contentHash = sha256(content),
            confluencePageId = confluencePageId,
            confluenceTitle = confluenceTitle,
            lastPublishedAt = Instant.now().toString()
        )
    }

    /**
     * Records or updates the fingerprint for [pageId] with a composite hash that covers
     * the page content plus all its dependencies (images, includes).
     *
     * @param pageId           Stable identity key.
     * @param contentHash      SHA-256 hash of the raw page content.
     * @param compositeHash    Hash covering content and all dependency hashes.
     * @param confluencePageId Confluence numeric page ID, if the page exists in Confluence.
     * @param confluenceTitle  The title used when the page was last published.
     * @param parentKey        The Antora component/module key of the parent.
     * @param sourcePath       Relative path to the source AsciiDoc file.
     * @param imageHashes      Map of image path to SHA-256 hash.
     * @param includeHashes    Map of include file path to SHA-256 hash.
     * @param pluginVersion    Version of the plugin that last published this page.
     */
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

    /**
     * Returns `true` if the composite hash for [pageId] differs from the stored value,
     * meaning the page or one of its dependencies has changed.
     *
     * A page that has never been published is considered changed.
     *
     * @param pageId        Stable identity key.
     * @param compositeHash Current composite hash to compare against the stored value.
     */
    fun isCompositeChanged(pageId: String, compositeHash: String): Boolean {
        val entry = entries[pageId] ?: return true
        return entry.compositeHash != compositeHash
    }

    /**
     * Removes the fingerprint record for [pageId].
     * Subsequent calls to [isChanged] will return `true` for this page.
     */
    fun remove(pageId: String) {
        entries.remove(pageId)
    }

    /** Returns all stored page IDs. */
    fun allPageIds(): Set<String> = entries.keys.toSet()

    /** Returns all stored entries. */
    fun allEntries(): Collection<FingerprintEntry> = entries.values.toList()

    /**
     * Persists the in-memory store to [storeFile].
     * Parent directories are created automatically.
     */
    fun save() {
        storeFile.parentFile?.mkdirs()
        val data = StorageFormat(entries = entries.values.toList())
        mapper.writerWithDefaultPrettyPrinter().writeValue(storeFile, data)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun load() {
        if (!storeFile.exists()) return
        try {
            val data: StorageFormat = mapper.readValue(storeFile)
            entries.clear()
            data.entries.forEach { entries[it.pageId] = it }
        } catch (e: Exception) {
            // Corrupt / incompatible store file – start fresh.
            entries.clear()
        }
    }

    internal fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // -------------------------------------------------------------------------
    // Data models
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StorageFormat(val entries: List<FingerprintEntry> = emptyList())
}

/**
 * A single record in the fingerprint store.
 *
 * @property pageId           Stable Antora page identity key.
 * @property contentHash      SHA-256 hex digest of the source AsciiDoc content.
 * @property compositeHash    Hash covering content and all dependency hashes (images, includes).
 * @property confluencePageId Confluence numeric page ID assigned when the page was first created.
 * @property confluenceTitle  Title used when the page was last published to Confluence.
 * @property parentKey        The Antora component/module key of the parent.
 * @property sourcePath       Relative path to the source AsciiDoc file.
 * @property imageHashes      Map of image path to SHA-256 hash.
 * @property includeHashes    Map of include file path to SHA-256 hash.
 * @property pluginVersion    Version of the plugin that last published this page.
 * @property lastPublishedAt  ISO-8601 timestamp of the last successful publish.
 */
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
