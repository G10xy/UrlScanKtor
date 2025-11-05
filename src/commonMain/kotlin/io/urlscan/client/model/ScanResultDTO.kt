package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// DTO of ScanResult API
typealias Options = Map<String, String>
typealias DataTiming = Map<String, Double>
typealias Global = Map<String, String>

@Serializable
data class ScanResult(
    val data: Data,
    val stats: Stats,
    val meta: Meta,
    val task: Task,
    val page: Page,
    val lists: Lists,
    val verdicts: Verdicts,
    val submitter: Submitter? = null
)

@Serializable
data class Data(
    val requests: List<RequestItem>,
    val cookies: List<Cookie>,
    val console: List<ConsoleMessage>,
    val links: List<Link>,
    val timing: DataTiming,
    val globals: List<Global>
)

@Serializable
data class RequestItem(
    val request: RequestDetails,
    val response: ResponseDetails,
    val initiatorInfo: InitiatorInfo? = null
)

@Serializable
data class RequestDetails(
    val requestId: String,
    val loaderId: String,
    val documentURL: String,
    val request: InnerRequest,
    val timestamp: Double,
    val wallTime: Double,
    val initiator: Initiator,
    val redirectHasExtraInfo: Boolean,
    val type: String,
    val frameId: String,
    val hasUserGesture: Boolean,
    val primaryRequest: Boolean? = null
)

@Serializable
data class InnerRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val mixedContentType: String,
    val initialPriority: String,
    val referrerPolicy: String,
    val isSameSite: Boolean,
    val isLinkPreload: Boolean
)

@Serializable
data class ResponseDetails(
    val encodedDataLength: Int,
    val dataLength: Int,
    val requestId: String,
    val type: String,
    val hasExtraInfo: Boolean,
    val hash: String,
    val size: Int,
    val asn: Asn,
    val geoip: GeoIp,
    val rdns: Rdns? = null,
    val response: InnerResponse,
    val securityHeaders: List<SecurityHeader> = emptyList()
)

@Serializable
data class SecurityHeader(
    val name: String,
    val value: String
)


@Serializable
data class InnerResponse(
    val url: String,
    val status: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val remoteIPAddress: String,
    val remotePort: Int,
    val encodedDataLength: Int,
    val timing: ResponseTiming,
    val responseTime: Double,
    val protocol: String,
    val alternateProtocolUsage: String,
    val securityState: String,
    val securityDetails: SecurityDetails? = null
)

@Serializable
data class SecurityDetails(
    val protocol: String? = null,
    val keyExchange: String? = null,
    val keyExchangeGroup: String? = null,
    val cipher: String? = null,
    val certificateId: Int? = null,
    val subjectName: String,
    val sanList: List<String>,
    val issuer: String,
    val validFrom: Long,
    val validTo: Long,
    val signedCertificateTimestampList: List<String>,
    val certificateTransparencyCompliance: String,
    val serverSignatureAlgorithm: Int? = null,
    val encryptedClientHello: Boolean? = null
)

@Serializable
data class ResponseTiming(
    val requestTime: Double,
    val proxyStart: Double,
    val proxyEnd: Double,
    val dnsStart: Double,
    val dnsEnd: Double,
    val connectStart: Double,
    val connectEnd: Double,
    val sslStart: Double,
    val sslEnd: Double,
    val workerStart: Double,
    val workerReady: Double,
    val workerFetchStart: Double,
    val workerRespondWithSettled: Double,
    val sendStart: Double,
    val sendEnd: Double,
    val pushStart: Double,
    val pushEnd: Double,
    @SerialName("receiveHeadersStart") val receiveHeadersStart: Double? = null,
    val receiveHeadersEnd: Double
)

@Serializable
data class Cookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expires: Double,
    val size: Int,
    val httpOnly: Boolean,
    val secure: Boolean,
    val session: Boolean,
    val sameSite: String? = null
)

@Serializable
data class ConsoleMessage(
    val message: Message
)

@Serializable
data class Message(
    val source: String,
    val level: String,
    val text: String,
    val timestamp: Double,
    val url: String
)



@Serializable
data class Task(
    val uuid: String,
    val time: String,
    val url: String,
    val visibility: String,
    val options: Options,
    val method: String,
    val source: String? = null,
    val userAgent: String? = null,
    val reportURL: String,
    val screenshotURL: String,
    val domURL: String,
    val tags: List<String> = emptyList()
)

@Serializable
data class Page(
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

@Serializable
data class Verdicts(
    val overall: Verdict,
    val urlscan: Verdict,
    val engines: EnginesVerdict,
    val community: CommunityVerdict
)

@Serializable
data class Verdict(
    val score: Int,
    val categories: List<String>,
    val brands: List<String>,
    val tags: List<String>,
    val malicious: Boolean,
    val hasVerdicts: Boolean
)

@Serializable
data class EnginesVerdict(
    val score: Int,
    val malicious: Boolean,
    val enginesTotal: Int,
    val maliciousTotal: Int,
    val benignTotal: Int,
    val hasVerdicts: Boolean
)

@Serializable
data class CommunityVerdict(
    val score: Int,
    val votesTotal: Int,
    val votesMalicious: Int,
    val votesBenign: Int,
    val malicious: Boolean,
    val hasVerdicts: Boolean
)

@Serializable
data class Submitter(
    val country: String? = null
)

@Serializable
data class Stats(
    val resourceStats: List<ResourceStats>,
    val protocolStats: List<ProtocolStats>,
    val tlsStats: List<TlsStats>,
    val serverStats: List<ServerStats>,
    val domainStats: List<DomainStats>,
    val regDomainStats: List<RegDomainStats>,
    val ipStats: List<IpStats>,
    val secureRequests: Int,
    val securePercentage: Int,
    val IPv6Percentage: Int,
    val uniqCountries: Int,
    val totalLinks: Int,
    val maliciousRequests: Int,
    val adBlocked: Int,
    val malicious: Int
)

@Serializable
data class ResourceStats(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val latency: Double,
    val countries: List<String>,
    val ips: List<String>,
    val type: String,
    val compression: String,
    val percentage: Int
)

@Serializable
data class ProtocolStats(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val latency: Double,
    val countries: List<String>,
    val ips: List<String>,
    val percentage: Int,
    val protocol: String,
    val securityState: Map<String, Int>
)

@Serializable
data class TlsStats(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val latency: Double,
    val countries: List<String>,
    val ips: List<String>,
    val percentage: Int,
    val protocols: Map<String, Int>,
    val securityState: String
)

@Serializable
data class ServerStats(
    val count: Int,
    val size: Int,
    val encodedSize: Int,
    val latency: Double,
    val countries: List<String>,
    val ips: List<String>,
    val percentage: Int,
    val server: String
)

@Serializable
data class DomainStats(
    val count: Int,
    val ips: List<String>,
    val redirects: Int,
    val size: Int,
    val encodedSize: Int,
    val countries: List<String>,
    val index: Int,
    val initiators: List<String>,
    val requests: Int,
    val domain: String
)

@Serializable
data class RegDomainStats(
    val count: Int,
    val ips: List<String>,
    val redirects: Int,
    val size: Int,
    val encodedSize: Int,
    val countries: List<String>,
    val index: Int,
    val initiators: List<String>,
    val requests: Int,
    val regDomain: String,
    val subDomains: List<SubDomain>
)

@Serializable
data class SubDomain(
    val domain: String,
    val country: String
)

@Serializable
data class IpStats(
    val requests: Int,
    val domains: List<String>,
    val ips: List<String>,
    val countries: List<String>,
    val asns: List<AsnInfo>,
    val encodedSize: Int,
    val size: Int,
    val redirects: Int,
    val ip: String,
    val asn: Asn,
    val geoip: GeoIp,
    val index: Int,
    val ipv6: Boolean,
    val count: Int? = null,
    val rdns: Rdns? = null
)

@Serializable
data class AsnInfo(
    val asn: String,
    val country: String,
    val organisation: String
)


@Serializable
data class Meta(
    val processors: Processors
)

@Serializable
data class Processors(
    val umbrella: Umbrella,
    val geoip: GeoipProcessor,
    val rdns: RdnsProcessor,
    val asn: AsnProcessor,
    val wappa: Wappa
)

@Serializable
data class Umbrella(
    val data: List<UmbrellaData>
)

@Serializable
data class UmbrellaData(
    val hostname: String,
    val rank: Int
)

@Serializable
data class GeoipProcessor(
    val data: List<GeoipData>
)

@Serializable
data class GeoipData(
    val ip: String,
    val geoip: GeoIp
)

@Serializable
data class RdnsProcessor(
    val data: List<RdnsData>
)

@Serializable
data class RdnsData(
    val ip: String,
    val ptr: String
)

@Serializable
data class AsnProcessor(
    val data: List<AsnData>
)

@Serializable
data class AsnData(
    val ip: String,
    val asn: String,
    val country: String,
    val organisation: String,
    val registrar: String,
    val date: String,
    val description: String,
    val route: String,
    val name: String
)

@Serializable
data class Wappa(
    val data: List<WappaData>
)

@Serializable
data class WappaData(
    val confidence: List<Confidence>,
    val confidenceTotal: Int,
    val app: String,
    val icon: String,
    val website: String,
    val categories: List<Category>
)

@Serializable
data class Confidence(
    val confidence: Int,
    val pattern: String
)

@Serializable
data class Category(
    val name: String,
    val id: String,
    val priority: Int
)

@Serializable
data class Lists(
    val ips: List<String>,
    val countries: List<String>,
    val asns: List<String>,
    val domains: List<String>,
    val servers: List<String>,
    val urls: List<String>,
    val linkDomains: List<String>,
    val certificates: List<Certificate>,
    val hashes: List<String>
)

@Serializable
data class Certificate(
    val subjectName: String,
    val issuer: String,
    val validFrom: Long,
    val validTo: Long
)

@Serializable
data class GeoIp(
    val country: String? = null,
    val region: String? = null,
    val timezone: String? = null,
    val city: String? = null,
    val ll: List<Double>,
    val country_name: String? = null,
    val metro: Int? = null,
    val area: Int? = null
)

@Serializable
data class Asn(
    val ip: String,
    val asn: String,
    val country: String,
    val registrar: String,
    val date: String,
    val description: String,
    val route: String,
    val name: String
)

@Serializable
data class Rdns(
    val ip: String,
    val ptr: String
)

@Serializable
data class Initiator(
    val type: String,
    val url: String? = null,
    val lineNumber: Int? = null,
    val columnNumber: Int? = null
)

@Serializable
data class Link(
    val href: String,
    val text: String
)

@Serializable
data class InitiatorInfo(
    val url: String,
    val host: String,
    val type: String
)
