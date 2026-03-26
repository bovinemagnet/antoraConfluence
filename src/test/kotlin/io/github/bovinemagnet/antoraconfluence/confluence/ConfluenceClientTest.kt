package io.github.bovinemagnet.antoraconfluence.confluence

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ConfluenceClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ConfluenceClient

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

    // -------------------------------------------------------------------------
    // addLabels
    // -------------------------------------------------------------------------

    @Test
    fun `addLabels sends POST with label body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        client.addLabels("42", listOf("managed-by-antora", "docs"))

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/labels")
        val body = request.body.readUtf8()
        assertThat(body).contains("managed-by-antora")
        assertThat(body).contains("docs")
    }

    // -------------------------------------------------------------------------
    // setPageProperty
    // -------------------------------------------------------------------------

    @Test
    fun `setPageProperty sends POST with property body`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.setPageProperty("42", "antora-hash", "abc123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/properties")
        val body = request.body.readUtf8()
        assertThat(body).contains("antora-hash")
        assertThat(body).contains("abc123")
    }

    // -------------------------------------------------------------------------
    // getPageProperty
    // -------------------------------------------------------------------------

    @Test
    fun `getPageProperty returns value when found`() {
        val responseJson = """{"id":"1","key":"antora-hash","value":"abc123"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val result = client.getPageProperty("42", "antora-hash")

        assertThat(result).isEqualTo("abc123")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).contains("/properties/antora-hash")
    }

    @Test
    fun `getPageProperty returns null when not found`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val result = client.getPageProperty("42", "antora-hash")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // uploadAttachment
    // -------------------------------------------------------------------------

    @Test
    fun `uploadAttachment sends multipart POST and returns attachment ID`() {
        val responseJson = """{"id":"attach-99","title":"diagram.png"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val tempFile = File.createTempFile("test-upload", ".png")
        tempFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        try {
            val attachmentId = client.uploadAttachment("42", "diagram.png", tempFile, "image/png")

            assertThat(attachmentId).isEqualTo("attach-99")
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).contains("/attachments")
            assertThat(request.getHeader("Content-Type")).contains("multipart")
        } finally {
            tempFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // getAttachments
    // -------------------------------------------------------------------------

    @Test
    fun `getAttachments returns list of attachments`() {
        val responseJson = """
            {
              "results": [
                {"id":"attach-1","title":"image.png","mediaType":"image/png"},
                {"id":"attach-2","title":"data.csv","mediaType":"text/csv"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val attachments = client.getAttachments("42")

        assertThat(attachments).hasSize(2)
        assertThat(attachments[0].id).isEqualTo("attach-1")
        assertThat(attachments[0].title).isEqualTo("image.png")
        assertThat(attachments[1].id).isEqualTo("attach-2")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).contains("/attachments")
    }

    // -------------------------------------------------------------------------
    // listManagedPages
    // -------------------------------------------------------------------------

    @Test
    fun `listManagedPages returns pages with given label`() {
        val responseJson = """
            {
              "results": [
                {"id":"100","title":"Page One","spaceId":"SPACE1"},
                {"id":"101","title":"Page Two","spaceId":"SPACE1"}
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        val pages = client.listManagedPages("SPACE1", "managed-by-antora")

        assertThat(pages).hasSize(2)
        assertThat(pages[0].id).isEqualTo("100")
        assertThat(pages[1].title).isEqualTo("Page Two")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).contains("space-id=SPACE1")
        assertThat(request.path).contains("label=managed-by-antora")
    }
}
