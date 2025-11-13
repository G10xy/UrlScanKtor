package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement



@Serializable
data class QuotaPeriod(
    val limit: Int,
    val used: Int,
    val remaining: Int,
    val percent: Double
)

@Serializable
data class Submitter(
    val country: String? = null
)