package io.github.bovinemagnet.antoraconfluence.fingerprint

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentFingerprintStoreTest {

    @TempDir
    lateinit var tempDir: File

    private fun storeFile() = File(tempDir, "fingerprints.json")

    // -------------------------------------------------------------------------
    // isChanged()
    // -------------------------------------------------------------------------

    @Test
    fun `isChanged returns true for new page`() {
        val store = ContentFingerprintStore(storeFile())
        assertThat(store.isChanged("my-docs/ROOT/index", "some content")).isTrue()
    }

    @Test
    fun `isChanged returns false when content is unchanged`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("my-docs/ROOT/index", "some content", confluencePageId = "123")
        assertThat(store.isChanged("my-docs/ROOT/index", "some content")).isFalse()
    }

    @Test
    fun `isChanged returns true when content changes`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("my-docs/ROOT/index", "original content")
        assertThat(store.isChanged("my-docs/ROOT/index", "modified content")).isTrue()
    }

    // -------------------------------------------------------------------------
    // put() and get()
    // -------------------------------------------------------------------------

    @Test
    fun `put stores entry and get retrieves it`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("page1", "content", confluencePageId = "456", confluenceTitle = "My Page")
        val entry = store.get("page1")
        assertThat(entry).isNotNull
        assertThat(entry!!.confluencePageId).isEqualTo("456")
        assertThat(entry.confluenceTitle).isEqualTo("My Page")
        assertThat(entry.contentHash).isNotBlank()
    }

    @Test
    fun `put overwrites existing entry`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("page1", "v1", confluencePageId = "100")
        store.put("page1", "v2", confluencePageId = "100")
        assertThat(store.isChanged("page1", "v2")).isFalse()
        assertThat(store.isChanged("page1", "v1")).isTrue()
    }

    // -------------------------------------------------------------------------
    // remove()
    // -------------------------------------------------------------------------

    @Test
    fun `remove deletes entry and isChanged returns true afterwards`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("page1", "content")
        store.remove("page1")
        assertThat(store.get("page1")).isNull()
        assertThat(store.isChanged("page1", "content")).isTrue()
    }

    // -------------------------------------------------------------------------
    // save() and reload
    // -------------------------------------------------------------------------

    @Test
    fun `save persists store and reload restores entries`() {
        val file = storeFile()
        val store = ContentFingerprintStore(file)
        store.put("page1", "hello world", confluencePageId = "111", confluenceTitle = "Page One")
        store.put("page2", "another page", confluencePageId = "222")
        store.save()

        assertThat(file.exists()).isTrue()

        val reloaded = ContentFingerprintStore(file)
        assertThat(reloaded.allPageIds()).containsExactlyInAnyOrder("page1", "page2")
        assertThat(reloaded.isChanged("page1", "hello world")).isFalse()
        assertThat(reloaded.get("page1")?.confluencePageId).isEqualTo("111")
    }

    @Test
    fun `corrupt store file is handled gracefully`() {
        val file = storeFile()
        file.writeText("not valid json {{{{")
        // Should not throw
        val store = ContentFingerprintStore(file)
        assertThat(store.allEntries()).isEmpty()
    }

    @Test
    fun `missing store file is handled gracefully`() {
        val store = ContentFingerprintStore(File(tempDir, "missing.json"))
        assertThat(store.allEntries()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // sha256()
    // -------------------------------------------------------------------------

    @Test
    fun `sha256 produces consistent hex digest`() {
        val store = ContentFingerprintStore(storeFile())
        val h1 = store.sha256("hello")
        val h2 = store.sha256("hello")
        assertThat(h1).isEqualTo(h2)
        assertThat(h1).hasSize(64) // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    fun `sha256 differs for different inputs`() {
        val store = ContentFingerprintStore(storeFile())
        assertThat(store.sha256("aaa")).isNotEqualTo(store.sha256("bbb"))
    }

    // -------------------------------------------------------------------------
    // allPageIds / allEntries
    // -------------------------------------------------------------------------

    @Test
    fun `allPageIds returns all tracked ids`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("a", "1")
        store.put("b", "2")
        store.put("c", "3")
        assertThat(store.allPageIds()).containsExactlyInAnyOrder("a", "b", "c")
    }

    @Test
    fun `allEntries returns all tracked entries`() {
        val store = ContentFingerprintStore(storeFile())
        store.put("x", "content")
        assertThat(store.allEntries()).hasSize(1)
        assertThat(store.allEntries().first().pageId).isEqualTo("x")
    }

    // -------------------------------------------------------------------------
    // putComposite() and isCompositeChanged()
    // -------------------------------------------------------------------------

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

    @Test
    fun `putComposite persists and reloads correctly`() {
        val file = storeFile()
        val store1 = ContentFingerprintStore(file)
        store1.putComposite("page", "raw", "comp", confluencePageId = "456", parentKey = "parent")
        store1.save()

        val store2 = ContentFingerprintStore(file)
        val entry = store2.get("page")
        assertThat(entry).isNotNull
        assertThat(entry!!.compositeHash).isEqualTo("comp")
        assertThat(entry.confluencePageId).isEqualTo("456")
        assertThat(entry.parentKey).isEqualTo("parent")
    }
}
