package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IncidentType {
    @SerialName("hostname") HOSTNAME,
    @SerialName("ip") IP,
    @SerialName("url") URL
}

@Serializable
enum class IncidentState {
    @SerialName("active") ACTIVE,
    @SerialName("closed") CLOSED
}

@Serializable
enum class CloseMethod {
    @SerialName("manual") MANUAL,
    @SerialName("automatic") AUTOMATIC
}

@Serializable
enum class ScanIntervalMode {
    @SerialName("automatic") AUTOMATIC,
    @SerialName("manual") MANUAL
}

@Serializable
data class Incident(
    @SerialName("_id")
    val id: String,
    val type: IncidentType,
    val state: IncidentState,
    val observable: String,
    val visibility: IncidentVisibility,
    val createdAt: String,
    val expireAt: String,
    val watchedAttributes: List<String>,
    val countries: List<String>,
    val countriesPerInterval: Int,
    val userAgents: List<String>,
    val userAgentsPerInterval: Int,
    val stopDelaySuspended: Int,
    val stopDelayInactive: Int,
    val stopDelayMalicious: Int,
    val scanIntervalAfterSuspended: Int,
    val scanIntervalAfterMalicious: Int,
    val closeMethod: CloseMethod,
    val channels: List<String>,
    val sourceType: String,
    val sourceId: String?,
    val incidentProfile: String?,
    val stateSize: Int,
    val stateCount: Int,
    val scanIntervalMode: ScanIntervalMode,
    val labels: List<String>
)

@Serializable
data class IncidentSchema(
    val incident: Incident
)
