package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class HostnameHistorySource {
    @SerialName("ct") CT,
    @SerialName("scan") SCAN,
    @SerialName("pdns") PDNS,
    @SerialName("zonefile") ZONEFILE,
    @SerialName("scan-link") SCAN_LINK,
    @SerialName("scan-cert-subject") SCAN_CERT_SUBJECT
}

@Serializable
data class HostnameHistoryResult(
    @SerialName("seen_on")
    val seenOn: String,
    val source: HostnameHistorySource,
    @SerialName("sub_id")
    val subId: String,
    @SerialName("first_seen")
    val firstSeen: String,
    @SerialName("last_seen")
    val lastSeen: String,
    @SerialName("data_type")
    val dataType: String?,
    val data: HostnameData?
)

@Serializable
data class HostnameData (
    val ttl: Int?,
    val rdata: String?,
    val geoip: GeoIpInfo,
    val asn: AsnInfo
)

@Serializable
data class HostnameHistorySchema(
    val pageState: String?,
    val results: List<HostnameHistoryResult>
)
