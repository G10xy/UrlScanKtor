package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.urlscan.client.exception.ApiException
import io.urlscan.client.exception.AuthenticationException
import io.urlscan.client.exception.NotFoundException
import io.urlscan.client.exception.RateLimitException
import io.urlscan.client.exception.UrlScanException
import io.urlscan.client.exception.handleClientException
import io.urlscan.client.internal.createPlatformHttpClient
import io.urlscan.client.model.SavedSearchResponse
import io.urlscan.client.model.ScanRequest
import io.urlscan.client.model.ScanResponse
import io.urlscan.client.model.ScanResult

/**
 * Main client for interacting with the urlscan.io API.
 * @property config Configuration for the client including API key and base URL
 * @property httpClient Ktor HTTP client used for making requests
 */
class UrlScanClient(
    private val config: UrlScanConfig = UrlScanConfig(""),
    private val httpClient: HttpClient = createPlatformHttpClient(config)
) {

    val generic: GenericApi by lazy {
        GenericApi(httpClient, config)
    }

    /**
     * Submit a URL for scanning.
     *
     * @param request The scan request containing the URL and optional parameters
     * @return ScanResponse containing the UUID and result URL
     * @throws AuthenticationException if API key is invalid
     * @throws RateLimitException if rate limit is exceeded
     * @throws UrlScanException for other API errors
     */
    suspend fun submitScan(request: ScanRequest): ScanResponse {
        return try {
            httpClient.post("${config.baseUrl}/scan/") {
                contentType(ContentType.Application.Json)
                header("API-Key", config.apiKey)
                setBody(request)
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error during scan submission: ${e.message}", e)
        }
    }

    /**
     * Retrieve the results of a completed scan.
     *
     * @param scanId The scanId of the scan (returned from submitScan)
     * @return ScanResult containing all scan data
     * @throws NotFoundException if the scan scanId doesn't exist
     * @throws UrlScanException for other API errors
     */
    suspend fun getResult(scanId: String): ScanResult {
        return try {
            httpClient.get("${config.baseUrl}/result/$scanId/") {
                header("API-Key", config.apiKey)
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error retrieving result: ${e.message}", e)
        }
    }

    /**
     * Search for scans matching a query.
     *
     * @param query The search query (e.g., "page.domain:example.com")
     * @param size Number of results to return (default: 100, max: 10000)
     * @return SearchResponse containing matching scans
     * @throws UrlScanException for API errors
     */
    suspend fun search(query: String, size: Int = 100): SavedSearchResponse {
        return try {
            httpClient.get("${config.baseUrl}/search/") {
                header("API-Key", config.apiKey)
                parameter("q", query)
                parameter("size", size)
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error during search: ${e.message}", e)
        }
    }

    /**
     * Get the screenshot of a scan result.
     *
     * @param uuid The UUID of the scan
     * @return ByteArray containing the PNG image data
     * @throws NotFoundException if the scan UUID doesn't exist
     * @throws UrlScanException for other API errors
     */
    suspend fun getScreenshot(uuid: String): ByteArray {
        return try {
            httpClient.get("${config.baseUrl}/screenshots/$uuid.png") {
                header("API-Key", config.apiKey)
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error retrieving screenshot: ${e.message}", e)
        }
    }

    /**
     * Get the DOM content of a scan result.
     *
     * @param uuid The UUID of the scan
     * @return String containing the DOM HTML
     * @throws NotFoundException if the scan UUID doesn't exist
     * @throws UrlScanException for other API errors
     */
    suspend fun getDom(uuid: String): String {
        return try {
            httpClient.get("${config.baseUrl}/dom/$uuid/") {
                header("API-Key", config.apiKey)
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error retrieving DOM: ${e.message}", e)
        }
    }



    /**
     * Close the HTTP client and release resources.
     * Should be called when the client is no longer needed.
     */
    fun close() {
        httpClient.close()
    }
}