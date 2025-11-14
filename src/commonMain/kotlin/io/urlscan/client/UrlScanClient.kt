package io.urlscan.client

import io.ktor.client.HttpClient
import io.urlscan.client.internal.createPlatformHttpClient

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

    val scanning: ScanningApi by lazy {
        ScanningApi(httpClient, config)
    }

    val search: SearchApi by lazy {
        SearchApi(httpClient, config)
    }

    val liveScanning: LiveScanningApi by lazy {
        LiveScanningApi(httpClient, config)
    }



/*
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
*/



    /**
     * Close the HTTP client and release resources.
     * Should be called when the client is no longer needed.
     */
    fun close() {
        httpClient.close()
    }
}