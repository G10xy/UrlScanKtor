package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionFrequency {
    @SerialName("live") LIVE,
    @SerialName("hourly") HOURLY,
    @SerialName("daily") DAILY
}

@Serializable
enum class IncidentVisibility {
    @SerialName("unlisted") UNLISTED,
    @SerialName("private") PRIVATE
}

@Serializable
enum class IncidentCreationMode {
    @SerialName("none") NONE,
    @SerialName("default") DEFAULT,
    @SerialName("always") ALWAYS,
    @SerialName("ignore-if-exists") IGNORE_IF_EXISTS
}

@Serializable
enum class IncidentWatchKey {
    @SerialName("scans/page.url") SCANS_PAGE_URL,
    @SerialName("scans/page.domain") SCANS_PAGE_DOMAIN,
    @SerialName("scans/page.ip") SCANS_PAGE_IP,
    @SerialName("scans/page.apexDomain") SCANS_PAGE_APEX_DOMAIN,
    @SerialName("hostnames/hostname") HOSTNAMES_HOSTNAME,
    @SerialName("hostnames/ip") HOSTNAMES_IP,
    @SerialName("hostnames/domain") HOSTNAMES_DOMAIN
}

@Serializable
data class Subscription(
    val searchIds: List<String>,
    val frequency: SubscriptionFrequency,
    val emailAddresses: List<String>,
    val name: String,
    val isActive: Boolean,
    val ignoreTime: Boolean,
    val description: String? = null,
    val weekDays: List<WeekDay>? = null,
    val permissions: List<TeamPermission>? = null,
    val channelIds: List<String>? = null,
    val incidentChannelIds: List<String>? = null,
    val incidentProfileId: String? = null,
    val incidentVisibility: IncidentVisibility? = null,
    val incidentCreationMode: IncidentCreationMode? = null,
    val incidentWatchKeys: IncidentWatchKey? = null
)

@Serializable
data class SubscriptionResponse(
    @SerialName("_id")
    val id: String,
    val searchIds: List<String>,
    val frequency: SubscriptionFrequency,
    val emailAddresses: List<String>,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    val ignoreTime: Boolean,
    val weekDays: List<WeekDay>?,
    val permissions: List<TeamPermission>?,
    val channelIds: List<String>?,
    val incidentChannelIds: List<String>?,
    val incidentProfileId: String?,
    val incidentVisibility: IncidentVisibility?,
    val incidentCreationMode: IncidentCreationMode?,
    val incidentWatchKeys: IncidentWatchKey?
)

@Serializable
data class SubscriptionResponseWrapper(
    val subscription: SubscriptionResponse
)
