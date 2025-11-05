package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SavedSearchDatasource {
    @SerialName("hostnames") HOSTNAMES,
    @SerialName("scans") SCANS
}

@Serializable
enum class Tlp {
    @SerialName("red") RED,
    @SerialName("amber+strict") AMBER_STRICT,
    @SerialName("amber") AMBER,
    @SerialName("green") GREEN,
    @SerialName("clear") CLEAR
}

@Serializable
data class SavedSearch(
    val datasource: SavedSearchDatasource? = null,
    val createdAt: String? = null, // readOnly
    val description: String? = null,
    val longDescription: String? = null,
    val name: String? = null,
    val ownerDescription: String? = null, // readOnly
    val permissions: List<TeamPermission>? = null,
    val query: String? = null,
    val tlp: Tlp? = null,
    @SerialName("usertags")
    val userTags: List<String>? = null
)

@Serializable
data class SavedSearchRequest(
    val datasource: SavedSearchDatasource,
    val name: String,
    val query: String,
    val description: String? = null,
    val longDescription: String? = null,
    val tlp: Tlp? = null,
    @SerialName("usertags")
    val userTags: List<String>? = null,
    val permissions: List<TeamPermission>? = null
)

@Serializable
data class SavedSearchRequestWrapper(
    val search: SavedSearchRequest
)

@Serializable
data class SavedSearchResponse(
    @SerialName("_id")
    val id: String,
    val datasource: SavedSearchDatasource,
    val createdAt: String,
    val description: String?,
    val longDescription: String?,
    val name: String,
    val ownerDescription: String,
    val permissions: List<TeamPermission>?,
    val query: String,
    val tlp: Tlp?,
    @SerialName("usertags")
    val userTags: List<String>?
)