package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// BASIC SCAN MODELS (Request/Response)

@Serializable
data class ScanRequest(
    val url: String,
    val visibility: Visibility = Visibility.PUBLIC,
    val country: String? = null,
    val tags: List<String>? = null,
    val overrideSafety: Boolean? = null,
    val referer: String? = null,
    val customagent: String? = null
)

@Serializable
data class ScanResponse(
    val uuid: String,
    val visibility: String,
    val url: String,
    val country: String
)

@Serializable
data class AvailableCountriesResponse(
    val countries: List<String>
)

@Serializable
data class UserAgentGroup(
    val group: String,
    val useragents: List<String>
)

@Serializable
data class UserAgentsResponse(
    val userAgents: List<UserAgentGroup>
)

// SCAN RESULT MODELS

@Serializable
data class ScanResult(
    val data: ScanData,
    val stats: ScanStats,
    val meta: ScanMeta,
    val task: ScanTask,
    val page: ScanPage,
    val lists: ScanLists,
    val verdicts: ScanVerdicts,
    val submitter: Submitter? = null
)

@Serializable
data class ScanData(
    val requests: List<ScanRequest_> = emptyList(),
    val cookies: List<JsonObject> = emptyList(),
    val console: List<ConsoleMessage> = emptyList(),
    val links: List<Link> = emptyList(),
    val timing: JsonObject? = null,
    val globals: List<JsonObject> = emptyList()
)

@Serializable
data class ScanRequest_(
    val request: RequestDetails,
    val response: ResponseDetails? = null,
    val initiatorInfo: InitiatorInfo? = null
)

@Serializable
data class RequestDetails(
    val requestId: String,
    val loaderId: String,
    val documentURL: String,
    val request: HttpRequest,
    val timestamp: Double,
    val wallTime: Double,
    val initiator: JsonObject,
    val redirectHasExtraInfo: Boolean,
    val type: String,
    val frameId: String,
    val hasUserGesture: Boolean,
    val primaryRequest: Boolean? = null
)

@Serializable
data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val mixedContentType: String? = null,
    val initialPriority: String? = null,
    val referrerPolicy: String? = null,
    val isSameSite: Boolean? = null,
    val isLinkPreload: Boolean? = null
)

@Serializable
data class ResponseDetails(
    val encodedDataLength: Int,
    val dataLength: Int,
    val requestId: String,
    val type: String,
    val hasExtraInfo: Boolean? = null,
    val hash: String? = null,
    val size: Int? = null,
    val asn: AsnInfo? = null,
    val geoip: GeoIpInfo? = null,
    val rdns: RdnsInfo? = null,
    val response: HttpResponse
)

@Serializable
data class HttpResponse(
    val url: String,
    val status: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val remoteIPAddress: String,
    val remotePort: Int,
    val encodedDataLength: Int,
    val timing: JsonObject? = null,
    val responseTime: Double? = null,
    val protocol: String,
    val alternateProtocolUsage: String? = null,
    val securityState: String,
    val securityDetails: SecurityDetails? = null,
    val securityHeaders: List<SecurityHeader>? = null
)

@Serializable
data class SecurityDetails(
    val protocol: String,
    val keyExchange: String? = null,
    val keyExchangeGroup: String? = null,
    val cipher: String,
    val certificateId: Int,
    val subjectName: String,
    val sanList: List<String>,
    val issuer: String,
    val validFrom: Long,
    val validTo: Long,
    val signedCertificateTimestampList: List<JsonElement> = emptyList(),
    val certificateTransparencyCompliance: String,
    val serverSignatureAlgorithm: Int? = null,
    val encryptedClientHello: Boolean? = null
)

@Serializable
data class SecurityHeader(
    val name: String,
    val value: String
)

@Serializable
data class InitiatorInfo(
    val url: String,
    val host: String,
    val type: String
)

@Serializable
data class ConsoleMessage(
    val message: JsonObject
)

@Serializable
data class Link(
    val href: String,
    val text: String
)

// STATISTICS MODELS

@Serializable
data class ScanStats(
    val resourceStats: List<ResourceStat> = emptyList(),
    val protocolStats: List<ProtocolStat> = emptyList(),
    val tlsStats: List<TlsStat> = emptyList(),
    val serverStats: List<ServerStat> = emptyList(),
    val domainStats: List<DomainStat> = emptyList(),
    val regDomainStats: List<RegDomainStat> = emptyList(),
    val secureRequests: Int = 0,
    val securePercentage: Int = 0,
    @SerialName("IPv6Percentage")
    val ipv6Percentage: Int = 0,
    val uniqCountries: Int = 0,
    val totalLinks: Int = 0,
    val maliciousRequests: Int = 0,
    val adBlocked: Int = 0,
    val malicious: Int = 0,
    val ipStats: List<IpStat> = emptyList()
)

@Serializable
data class ResourceStat(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val latency: Int,
    val countries: List<String>,
    val ips: List<String>,
    val type: String,
    val compression: String? = null,
    val percentage: Int
)

@Serializable
data class ProtocolStat(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val ips: List<String>,
    val countries: List<String>,
    val securityState: JsonObject? = null,
    val protocol: String,
    val percentage: Int? = null
)

@Serializable
data class TlsStat(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val ips: List<String>,
    val countries: List<String>,
    val protocols: Map<String, Int>,
    val securityState: String,
    val percentage: Int? = null
)

@Serializable
data class ServerStat(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val ips: List<String>,
    val countries: List<String>,
    val server: String,
    val percentage: Int? = null
)

@Serializable
data class DomainStat(
    val count: Int,
    val ips: List<String>,
    val domain: String,
    val size: Int,
    val encodedSize: Int,
    val countries: List<String>,
    val index: Int,
    val initiators: List<String>,
    val redirects: Int,
    val requests: Int? = null
)

@Serializable
data class RegDomainStat(
    val count: Int,
    val ips: List<String>,
    val regDomain: String,
    val size: Int,
    val encodedSize: Int,
    val countries: List<String>,
    val index: Int,
    val subDomains: List<SubDomain>,
    val redirects: Int,
    val requests: Int? = null
)

@Serializable
data class SubDomain(
    val domain: String,
    val country: String
)

@Serializable
data class IpStat(
    val requests: Int,
    val domains: List<String>,
    val ip: String,
    val asn: AsnInfo? = null,
    val dns: JsonObject? = null,
    val geoip: GeoIpInfo? = null,
    val size: Int,
    val encodedSize: Int,
    val countries: List<String>,
    val index: Int,
    val ipv6: Boolean,
    val redirects: Int,
    val count: Int? = null,
    val rdns: RdnsInfo? = null
)

// NETWORK INFO MODELS

@Serializable
data class RdnsInfo(
    val ip: String,
    val ptr: String
)

// METADATA MODELS

@Serializable
data class ScanMeta(
    val processors: Processors? = null
)

@Serializable
data class Processors(
    val umbrella: UmbrellaProcessor? = null,
    val geoip: GeoIpProcessor? = null,
    val rdns: RdnsProcessor? = null,
    val asn: AsnProcessor? = null,
    val wappa: WappaProcessor? = null
)

@Serializable
data class UmbrellaProcessor(
    val data: List<UmbrellaData> = emptyList()
)

@Serializable
data class UmbrellaData(
    val hostname: String,
    val rank: Int
)

@Serializable
data class GeoIpProcessor(
    val data: List<GeoIpProcessorData> = emptyList()
)

@Serializable
data class GeoIpProcessorData(
    val ip: String,
    val geoip: GeoIpInfo
)

@Serializable
data class RdnsProcessor(
    val data: List<RdnsInfo> = emptyList()
)

@Serializable
data class AsnProcessor(
    val data: List<AsnInfo> = emptyList()
)

@Serializable
data class WappaProcessor(
    val data: List<WappaData> = emptyList()
)

@Serializable
data class WappaData(
    val confidence: List<WappaConfidence> = emptyList(),
    val confidenceTotal: Int,
    val app: String,
    val icon: String,
    val website: String,
    val categories: List<WappaCategory> = emptyList()
)

@Serializable
data class WappaConfidence(
    val confidence: Int,
    val pattern: String
)

@Serializable
data class WappaCategory(
    val name: String,
    val id: String? = null,
    val priority: Int? = null
)

// TASK AND PAGE MODELS

@Serializable
data class ScanTask(
    val uuid: String,
    val time: String,
    val url: String,
    val visibility: String,
    val options: JsonObject? = null,
    val method: String,
    val source: String? = null,
    val userAgent: String? = null,
    val reportURL: String,
    val screenshotURL: String,
    val domURL: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class ScanPage(
    val country: String? = null,
    val server: String? = null,
    val city: String? = null,
    val domain: String? = null,
    val ip: String? = null,
    val asnname: String? = null,
    val asn: String? = null,
    val url: String,
    val ptr: String? = null
)

// LISTS AND CERTIFICATES

@Serializable
data class ScanLists(
    val ips: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val asns: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val servers: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val linkDomains: List<String> = emptyList(),
    val certificates: List<Certificate> = emptyList(),
    val hashes: List<String> = emptyList()
)

@Serializable
data class Certificate(
    val subjectName: String,
    val issuer: String,
    val validFrom: Long,
    val validTo: Long
)

// VERDICT MODELS

@Serializable
data class ScanVerdicts(
    val overall: VerdictDetails,
    val urlscan: VerdictDetails,
    val engines: EngineVerdicts,
    val community: CommunityVerdicts
)

@Serializable
data class VerdictDetails(
    val score: Int,
    val categories: List<String> = emptyList(),
    val brands: List<JsonObject> = emptyList(),
    val tags: List<String> = emptyList(),
    val malicious: Boolean,
    val hasVerdicts: Boolean
)

@Serializable
data class EngineVerdicts(
    val score: Int,
    val categories: List<String> = emptyList(),
    val brands: List<JsonObject> = emptyList(),
    val tags: List<String> = emptyList(),
    val malicious: Boolean,
    val enginesTotal: Int,
    val maliciousTotal: Int,
    val benignTotal: Int,
    val verdicts: List<EngineVerdict> = emptyList(),
    val maliciousVerdicts: List<JsonObject> = emptyList(),
    val benignVerdicts: List<JsonObject> = emptyList(),
    val hasVerdicts: Boolean
)

@Serializable
data class EngineVerdict(
    val engine: String,
    val classification: String
)

@Serializable
data class CommunityVerdicts(
    val score: Int,
    val categories: List<String> = emptyList(),
    val brands: List<JsonObject> = emptyList(),
    val tags: List<String> = emptyList(),
    val malicious: Boolean,
    val votesBenign: Int,
    val votesMalicious: Int,
    val votesTotal: Int,
    val hasVerdicts: Boolean
)