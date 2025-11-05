package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChannelType {
    @SerialName("webhook") WEBHOOK,
    @SerialName("email") EMAIL
}

@Serializable
data class Channel(
    @SerialName("_id")
    val id: String? = null,
    val type: ChannelType,
    val name: String,
    val webhookURL: String? = null,
    val frequency: String? = null,
    val emailAddresses: List<String>? = null,
    val utcTime: String? = null,
    val isActive: Boolean? = null,
    val isDefault: Boolean? = null,
    val ignoreTime: Boolean? = null,
    val weekDays: List<WeekDay>? = null,
    val permissions: List<TeamPermission>? = null
)

@Serializable
data class ChannelRequest(
    val channel: Channel
)

@Serializable
data class ChannelResponse(
    @SerialName("_id")
    val id: String,
    val type: String,
    val webhookURL: String?,
    val frequency: String?,
    val emailAddresses: List<String>?,
    val utcTime: String?,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean,
    val ignoreTime: Boolean,
    val weekDays: List<WeekDay>?,
    val permissions: List<TeamPermission>?
)
