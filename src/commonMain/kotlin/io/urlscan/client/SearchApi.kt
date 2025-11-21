package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.urlscan.client.model.SearchResponse

class SearchApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {

 /**
 * Search for historical scans, hostnames, domains, certificates, and incidents
 * @param q Search query using Elasticsearch Query String syntax
 * @param size Number of results to return (max 10000)
 * @param searchAfter For pagination - sort value from the last result of previous call
 * @param datasource Data source to search: scans, hostnames, incidents, notifications, certificates
 * @return SearchResponse containing search results
 */
suspend fun search(
    q: String,
    size: Int? = null,
    searchAfter: String? = null,
    datasource: String? = null
): SearchResponse {
    return httpClient.get("${config.apiHost}/api/v1/search") {
            parameter("q", q)
            size?.let { parameter("size", it) }
            searchAfter?.let { parameter("search_after", it) }
            datasource?.let { parameter("datasource", it) }
        }.body()
    }

    /**
     * Get structurally similar results to a specific scan (urlscan Pro feature)
     * @param scanId The original scan UUID to compare to
     * @param q Additional query filter
     * @param size Maximum results per call
     * @param searchAfter Parameter to iterate over older results
     * @return SearchResponse containing similar scan results
     */
    suspend fun getSimilarScans(
        scanId: String,
        q: String? = null,
        size: Int? = null,
        searchAfter: String? = null
    ): SearchResponse {
        return httpClient.get("${config.apiHost}/api/v1/pro/result/$scanId/similar/") {
                q?.let { parameter("q", it) }
                size?.let { parameter("size", it) }
                searchAfter?.let { parameter("search_after", it) }
            }.body()

    }
}