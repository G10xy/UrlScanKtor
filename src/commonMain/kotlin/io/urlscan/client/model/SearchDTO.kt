package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val results: List<SearchResult>,
    val total: Int,
    val took: Int,
    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
data class SearchResult(
    val task: Task,
    val page: Page,
    val stats: Stats? = null,
    val verdicts: Verdicts? = null,
    val submitter: Submitter? = null,
    val dom: Dom? = null,
    val links: Links? = null,
    val scanner: Scanner? = null,
    val result: String,
    val screenshot: String,
    val id: String,
    val score: Int? = null,
    val sort: List<Long>
)

@Serializable
data class Task(
    val visibility: String,
    val method: String,
    val domain: String,
    val apexDomain: String,
    val time: String,
    val uuid: String,
    val url: String
)

@Serializable
data class Page(
    val country: String? = null,
    val server: String? = null,
    val ip: String? = null,
    val mimeType: String? = null,
    val title: String? = null,
    val url: String,
    val domain: String? = null,
    val apexDomain: String? = null,
    val asnname: String? = null,
    val asn: String? = null,
    val status: Int? = null
)

@Serializable
data class Stats(
    val uniqIPs: Int,
    val uniqCountries: Int,
    val dataLength: Int,
    val encodedDataLength: Int,
    val requests: Int
)

@Serializable
data class Verdicts(
    val score: Int,
    val malicious: Boolean,
    val urlscan: UrlscanVerdict? = null
)

@Serializable
data class UrlscanVerdict(
    val malicious: Boolean
)

@Serializable
data class Dom(
    val size: Int,
    val hash: String
)

@Serializable
data class Links(
    val length: Int
)

@Serializable
data class Scanner(
    val country: String
)
