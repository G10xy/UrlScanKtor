package io.urlscan.client.model

import kotlinx.serialization.Serializable

@Serializable
data class ScanRequest(
    val url: String,
    val visibility: Visibility,
    val country: String,
    val tags: List<String>? = null,
)

@Serializable
data class ScanResponse(
    val uuid: String,
    val visibility: Visibility,
    val url: String,
    val country: String
)