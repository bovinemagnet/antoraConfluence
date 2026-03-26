package io.github.bovinemagnet.antoraconfluence.confluence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceAttachment
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceAttachmentList
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePage
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePageList
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceProperty
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceSpace
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceSpaceList
import io.github.bovinemagnet.antoraconfluence.confluence.model.CreatePageRequest
import io.github.bovinemagnet.antoraconfluence.confluence.model.LabelRequest
import io.github.bovinemagnet.antoraconfluence.confluence.model.PageBody
import io.github.bovinemagnet.antoraconfluence.confluence.model.UpdatePageRequest
import io.github.bovinemagnet.antoraconfluence.confluence.model.VersionRequest
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client for the Confluence REST API v2.
 *
 * Authentication is performed using HTTP Basic Auth with a username and API token.
 * For Confluence Cloud the username is an email address and the API token is generated at
 * https://id.atlassian.com/manage/api-tokens.
 *
 * @param baseUrl   Base URL of the Confluence instance, e.g. `https://example.atlassian.net/wiki`.
 * @param username  Confluence username (email for Cloud).
 * @param apiToken  API token or personal access token.
 * @param httpClient Optional custom [OkHttpClient]; a default client is created if omitted.
 */
class ConfluenceClient(
    private val baseUrl: String,
    private val username: String,
    private val apiToken: String,
    httpClient: OkHttpClient? = null
) {
    private val json = jacksonObjectMapper()
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
    private val credentials = Credentials.basic(username, apiToken)

    private val client: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // -------------------------------------------------------------------------
    // Space operations
    // -------------------------------------------------------------------------

    /**
     * Looks up a Confluence space by its key.
     *
     * @param spaceKey The space key (e.g. `DOCS`).
     * @return The [ConfluenceSpace], or `null` if it does not exist or is not accessible.
     */
    fun getSpace(spaceKey: String): ConfluenceSpace? {
        val url = "${apiBase()}/spaces?keys=$spaceKey&limit=1"
        val list: ConfluenceSpaceList = get(url)
        return list.results.firstOrNull()
    }

    // -------------------------------------------------------------------------
    // Page operations
    // -------------------------------------------------------------------------

    /**
     * Retrieves a page by its Confluence numeric ID.
     *
     * @param pageId  Confluence page ID.
     * @return The [ConfluencePage], or `null` if not found.
     */
    fun getPage(pageId: String): ConfluencePage? {
        val url = "${apiBase()}/pages/$pageId?body-format=storage"
        return try {
            get(url)
        } catch (e: ConfluenceNotFoundException) {
            null
        }
    }

    /**
     * Finds a page by its title within a given space.
     *
     * @param spaceId  Numeric Confluence space ID.
     * @param title    Exact page title to search for.
     * @return The first matching [ConfluencePage], or `null` if none found.
     */
    fun findPageByTitle(spaceId: String, title: String): ConfluencePage? {
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val url = "${apiBase()}/pages?spaceId=$spaceId&title=$encodedTitle&limit=1"
        val list: ConfluencePageList = get(url)
        return list.results.firstOrNull()
    }

    /**
     * Creates a new Confluence page.
     *
     * @param spaceId      Numeric Confluence space ID.
     * @param parentId     Numeric ID of the parent page (may be `null` for root-level pages).
     * @param title        Title for the new page.
     * @param htmlContent  HTML body content in Confluence storage format.
     * @return The created [ConfluencePage].
     */
    fun createPage(
        spaceId: String,
        parentId: String?,
        title: String,
        htmlContent: String
    ): ConfluencePage {
        val request = CreatePageRequest(
            spaceId = spaceId,
            parentId = parentId,
            title = title,
            body = PageBody(value = htmlContent)
        )
        val body = json.writeValueAsString(request).toRequestBody(mediaTypeJson)
        val httpRequest = Request.Builder()
            .url("${apiBase()}/pages")
            .post(body)
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        return executeAndParse(httpRequest)
    }

    /**
     * Updates an existing Confluence page with new content.
     *
     * @param pageId        Numeric Confluence page ID to update.
     * @param title         (Possibly new) page title.
     * @param htmlContent   Updated HTML body content in Confluence storage format.
     * @param versionNumber Current version number; the update will bump this by one.
     * @param versionMessage Optional comment attached to the new version.
     * @return The updated [ConfluencePage].
     */
    fun updatePage(
        pageId: String,
        title: String,
        htmlContent: String,
        versionNumber: Int,
        versionMessage: String = "Published by antora-confluence"
    ): ConfluencePage {
        val request = UpdatePageRequest(
            id = pageId,
            title = title,
            body = PageBody(value = htmlContent),
            version = VersionRequest(number = versionNumber + 1, message = versionMessage)
        )
        val body = json.writeValueAsString(request).toRequestBody(mediaTypeJson)
        val httpRequest = Request.Builder()
            .url("${apiBase()}/pages/$pageId")
            .put(body)
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        return executeAndParse(httpRequest)
    }

    // -------------------------------------------------------------------------
    // Label operations
    // -------------------------------------------------------------------------

    /**
     * Adds labels to a Confluence page.
     *
     * @param pageId Numeric Confluence page ID.
     * @param labels List of label names to add.
     */
    fun addLabels(pageId: String, labels: List<String>) {
        val labelRequests = labels.map { LabelRequest(name = it) }
        val body = json.writeValueAsString(labelRequests).toRequestBody(mediaTypeJson)
        val httpRequest = Request.Builder()
            .url("${apiBase()}/pages/$pageId/labels")
            .post(body)
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        val response: Response = client.newCall(httpRequest).execute()
        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body?.string() ?: "(no body)"
                throw ConfluenceApiException(
                    "Confluence API error ${it.code} for ${httpRequest.url}: $errorBody"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Page property operations
    // -------------------------------------------------------------------------

    /**
     * Sets a page property (creates or overwrites).
     *
     * @param pageId Numeric Confluence page ID.
     * @param key    Property key.
     * @param value  Property value (stored as a JSON string).
     */
    fun setPageProperty(pageId: String, key: String, value: String) {
        val property = ConfluenceProperty(key = key, value = value)
        val body = json.writeValueAsString(property).toRequestBody(mediaTypeJson)
        val httpRequest = Request.Builder()
            .url("${apiBase()}/pages/$pageId/properties")
            .post(body)
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        val response: Response = client.newCall(httpRequest).execute()
        response.use {
            if (!it.isSuccessful) {
                val errorBody = it.body?.string() ?: "(no body)"
                throw ConfluenceApiException(
                    "Confluence API error ${it.code} for ${httpRequest.url}: $errorBody"
                )
            }
        }
    }

    /**
     * Retrieves a page property value by key.
     *
     * @param pageId Numeric Confluence page ID.
     * @param key    Property key to look up.
     * @return The property value string, or `null` if not found.
     */
    fun getPageProperty(pageId: String, key: String): String? {
        val url = "${apiBase()}/pages/$pageId/properties/$key"
        return try {
            val property: ConfluenceProperty = get(url)
            property.value
        } catch (e: ConfluenceNotFoundException) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // Attachment operations
    // -------------------------------------------------------------------------

    /**
     * Uploads a file as an attachment to a Confluence page.
     *
     * @param pageId   Numeric Confluence page ID.
     * @param fileName Desired file name for the attachment.
     * @param file     The file to upload.
     * @param mimeType MIME type of the file.
     * @return The attachment ID.
     */
    fun uploadAttachment(pageId: String, fileName: String, file: File, mimeType: String): String {
        val fileBody = file.asRequestBody(mimeType.toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .build()
        val httpRequest = Request.Builder()
            .url("${apiBase()}/pages/$pageId/attachments")
            .post(multipartBody)
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        val attachment: ConfluenceAttachment = executeAndParse(httpRequest)
        return attachment.id
    }

    /**
     * Lists all attachments on a Confluence page.
     *
     * @param pageId Numeric Confluence page ID.
     * @return List of [ConfluenceAttachment] objects.
     */
    fun getAttachments(pageId: String): List<ConfluenceAttachment> {
        val url = "${apiBase()}/pages/$pageId/attachments"
        val list: ConfluenceAttachmentList = get(url)
        return list.results
    }

    // -------------------------------------------------------------------------
    // Managed page listing
    // -------------------------------------------------------------------------

    /**
     * Lists pages in a space that carry a specific label.
     *
     * @param spaceId Numeric Confluence space ID.
     * @param label   Label to filter by.
     * @return List of matching [ConfluencePage] objects (up to 250).
     */
    fun listManagedPages(spaceId: String, label: String): List<ConfluencePage> {
        val url = "${apiBase()}/pages?space-id=$spaceId&label=$label&limit=250"
        val list: ConfluencePageList = get(url)
        return list.results
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun apiBase() = "${baseUrl.trimEnd('/')}/api/v2"

    private inline fun <reified T> get(url: String): T {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", credentials)
            .header("Accept", "application/json")
            .build()
        return executeAndParse(request)
    }

    private inline fun <reified T> executeAndParse(request: Request): T {
        val response: Response = client.newCall(request).execute()
        response.use {
            when {
                it.code == 404 -> throw ConfluenceNotFoundException("Not found: ${request.url}")
                !it.isSuccessful -> {
                    val errorBody = it.body?.string() ?: "(no body)"
                    throw ConfluenceApiException(
                        "Confluence API error ${it.code} for ${request.url}: $errorBody"
                    )
                }
                else -> {
                    val bodyString = it.body?.string()
                        ?: throw ConfluenceApiException("Empty response body for ${request.url}")
                    return json.readValue(bodyString)
                }
            }
        }
    }
}

/** Thrown when the Confluence API returns an unexpected error response. */
open class ConfluenceApiException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Thrown when a requested Confluence resource is not found (HTTP 404). */
class ConfluenceNotFoundException(message: String) : ConfluenceApiException(message)
