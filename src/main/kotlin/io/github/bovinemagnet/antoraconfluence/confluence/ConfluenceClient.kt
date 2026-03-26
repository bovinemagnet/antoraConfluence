package io.github.bovinemagnet.antoraconfluence.confluence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePage
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluencePageList
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceSpace
import io.github.bovinemagnet.antoraconfluence.confluence.model.ConfluenceSpaceList
import io.github.bovinemagnet.antoraconfluence.confluence.model.CreatePageRequest
import io.github.bovinemagnet.antoraconfluence.confluence.model.PageBody
import io.github.bovinemagnet.antoraconfluence.confluence.model.UpdatePageRequest
import io.github.bovinemagnet.antoraconfluence.confluence.model.VersionRequest
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
