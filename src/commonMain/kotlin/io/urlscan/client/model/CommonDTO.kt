package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SearchResponse(
    val results: List<JsonElement>,
    val total: Int,
    val took: Int,
    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
data class QuotaPeriod(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val percent: Double
)