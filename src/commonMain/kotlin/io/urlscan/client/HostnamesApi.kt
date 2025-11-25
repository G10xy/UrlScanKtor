package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.urlscan.client.model.HostnameHistorySchema

class HostnamesApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get the historical observations for a specific hostname.
     * Returns data from the "Hostnames" data source including DNS records, certificates, and scan history.
     *
     * @param hostname The hostname to query (e.g., "example.com")
     * @param limit Maximum number of results to return (10-10000, default 1000)
     * @param pageState Optional page state from previous call for pagination
     * @return HostnameHistorySchema containing historical observations
     */
    suspend fun getHostnameHistory(
        hostname: String,
        limit: Int = 1000,
        pageState: String? = null
    ): HostnameHistorySchema {
        require(hostname.isNotBlank()) { "Hostname cannot be blank" }
        require(limit in 10..10000) { "Limit must be between 10 and 10000" }

        return httpClient.get("${config.baseUrl}/api/v1/hostname/$hostname") {
            parameter("limit", limit)
            pageState?.let { parameter("pageState", it) }
        }.body()
    }
}