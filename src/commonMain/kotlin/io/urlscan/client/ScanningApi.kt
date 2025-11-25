package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.toByteArray
import io.urlscan.client.model.ScanRequest
import io.urlscan.client.model.ScanResponse
import io.urlscan.client.model.ScanResult
import io.urlscan.client.model.UserAgentsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanningApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Submit a URL to be scanned.
     *
     * @param request The scan request containing the URL and optional parameters
     * @return ScanResponse containing the scan UUID and metadata
     */
    suspend fun submitScan(request: ScanRequest): ScanResponse {
        return httpClient.post("${config.baseUrl}/api/v1/scan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Get the result of a completed scan.
     *
     * @param scanId UUID of the scan result
     * @return ScanResult containing comprehensive scan data
     */
    suspend fun getResult(scanId: String): ScanResult {
        return httpClient.get("${config.baseUrl}/api/v1/result/$scanId/").body()
    }

    /**
     * Download the screenshot for a completed scan.
     *
     * @param scanId UUID of the scan
     * @return ByteArray containing the PNG image data
     */
    suspend fun getScreenshot(scanId: String): ByteArray {
        return withContext(Dispatchers.Default) {
            httpClient.prepareGet("${config.baseUrl}/screenshots/$scanId.png") {
                accept(ContentType.Image.PNG)
            }.execute { response ->
                response.bodyAsChannel().toByteArray()
            }
        }
    }

    /**
     * Get the DOM snapshot for a completed scan.
     *
     * @param scanId UUID of the scan
     * @return String containing the HTML DOM snapshot
     */
    suspend fun getDom(scanId: String): String {
        return httpClient.get("${config.baseUrl}/dom/$scanId/") {
            accept(ContentType.Text.Html)
        }.body()
    }

    /**
     * Get list of available countries for scanning.
     *
     * @return AvailableCountriesResponse containing list of country codes
     */
    suspend fun getAvailableCountries(): List<String> {
        return httpClient.get("${config.baseUrl}/api/v1/availableCountries").body()
    }

    /**
     * Get grouped user agents available for scanning.
     *
     * @return UserAgentsResponse containing grouped user agent strings
     */
    suspend fun getUserAgents(): UserAgentsResponse {
        return httpClient.get("${config.baseUrl}/api/v1/userAgents").body()
    }
}