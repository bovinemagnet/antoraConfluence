package io.github.bovinemagnet.antoraconfluence.confluence.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** Minimal representation of a Confluence page returned by the REST API v2. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluencePage(
    val id: String,
    val title: String,
    val spaceId: String? = null,
    val parentId: String? = null,
    val version: ConfluenceVersion? = null,
    val status: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceVersion(val number: Int)

/** Response wrapper for paginated list endpoints. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluencePageList(
    val results: List<ConfluencePage> = emptyList()
)

/** Minimal representation of a Confluence space. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceSpace(
    val id: String,
    val key: String,
    val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConfluenceSpaceList(
    val results: List<ConfluenceSpace> = emptyList()
)

/** Request body for creating a page (REST API v2). */
data class CreatePageRequest(
    val spaceId: String,
    val parentId: String?,
    val title: String,
    val body: PageBody,
    val status: String = "current"
)

/** Request body for updating a page (REST API v2). */
data class UpdatePageRequest(
    val id: String,
    val title: String,
    val body: PageBody,
    val version: VersionRequest,
    val status: String = "current"
)

data class VersionRequest(val number: Int, val message: String = "Published by antora-confluence")

data class PageBody(
    val representation: String = "storage",
    val value: String
)
