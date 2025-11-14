package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.toByteArray
import io.urlscan.client.model.LiveScanRequest
import io.urlscan.client.model.LiveScanResponse
import io.urlscan.client.model.LiveScannerInfo
import io.urlscan.client.model.LiveScanStoreRequest
import io.urlscan.client.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveScanningApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get list of available Live Scanning nodes along with their current metadata.
     *
     * @return List of available scanner information
     */
    suspend fun getLiveScanners(): List<LiveScannerInfo> {
        return httpClient.get("${config.apiHost}/api/v1/livescan/scanners/") {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body()
    }

    /**
     * Non-blocking trigger for live scan. Returns immediately with scan UUID.
     * The client is responsible for polling the result endpoint until scan completes.
     *
     * @param scannerId ID of the scanner to use for this scan
     * @param request The scan request containing URL and scanner options
     * @return LiveScanResponse containing the scan UUID
     */
    suspend fun triggerLiveScanNonBlocking(
        scannerId: String,
        request: LiveScanRequest
    ): LiveScanResponse {
        return httpClient.post("${config.apiHost}/api/v1/livescan/$scannerId/task/") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Blocking trigger for live scan. The HTTP request will block until the scan completes.
     *
     * @param scannerId ID of the scanner to use for this scan
     * @param request The scan request containing URL and scanner options
     * @return LiveScanResponse containing the scan UUID upon completion
     */
    suspend fun triggerLiveScan(
        scannerId: String,
        request: LiveScanRequest
    ): LiveScanResponse {
        return httpClient.post("${config.apiHost}/api/v1/livescan/$scannerId/scan/") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Get a resource from a completed live scan.
     * Resources can be: result, screenshot, dom, response, or download.
     *
     * @param scannerId ID of the scanner
     * @param resourceType Type of resource: result, screenshot, dom, response, or download
     * @param resourceId UUID for result/screenshot/dom, or SHA256 hash for response/download
     * @return ByteArray containing the resource data
     */
    suspend fun getLiveScanResource(
        scannerId: String,
        resourceType: String,
        resourceId: String
    ): ByteArray {
        return withContext(Dispatchers.Default) {
            val channel = httpClient.get(
                "${config.apiHost}/api/v1/livescan/$scannerId/$resourceType/$resourceId"
            ) {
                headers {
                    append("API-Key", config.apiKey)
                }
            }.bodyAsChannel()

            channel.toByteArray()
        }
    }

    /**
     * Get a resource as a string (for JSON results and DOM content).
     *
     * @param scannerId ID of the scanner
     * @param resourceType Type of resource: result, dom
     * @param resourceId UUID of the scan
     * @return String containing the resource data
     */
    suspend fun getLiveScanResourceAsString(
        scannerId: String,
        resourceType: String,
        resourceId: String
    ): String {
        return httpClient.get(
            "${config.apiHost}/api/v1/livescan/$scannerId/$resourceType/$resourceId"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body()
    }

    /**
     * Store a temporary live scan as a permanent snapshot on urlscan.io.
     * After storing, the scan will be available through the regular API.
     *
     * @param scannerId ID of the scanner
     * @param scanId UUID of the temporary live scan to store
     * @param visibility Visibility level for the stored scan: public, unlisted, or private
     * @return Response from the store operation
     */
    suspend fun storeLiveScan(
        scannerId: String,
        scanId: String,
        visibility: Visibility
    ): String {
        return httpClient.put(
            "${config.apiHost}/api/v1/livescan/$scannerId/$scanId/"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(LiveScanStoreRequest(visibility))
        }.body()
    }

    /**
     * Purge a temporary live scan from the scanner immediately.
     * Scans are automatically purged after 60 minutes if not explicitly deleted.
     *
     * @param scannerId ID of the scanner
     * @param scanId UUID of the temporary live scan to purge
     * @return Response from the purge operation
     */
    suspend fun purgeLiveScan(
        scannerId: String,
        scanId: String
    ): String {
        return httpClient.delete(
            "${config.apiHost}/api/v1/livescan/$scannerId/$scanId/"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body()
    }

    /**
     * Get screenshot from a live scan as ByteArray.
     *
     * @param scannerId ID of the scanner
     * @param scanId UUID of the scan
     * @return ByteArray containing the PNG image
     */
    suspend fun getLiveScanScreenshot(
        scannerId: String,
        scanId: String
    ): ByteArray {
        return getLiveScanResource(scannerId, "screenshot", scanId)
    }

    /**
     * Get DOM from a live scan as String.
     *
     * @param scannerId ID of the scanner
     * @param scanId UUID of the scan
     * @return String containing the HTML DOM
     */
    suspend fun getLiveScanDom(
        scannerId: String,
        scanId: String
    ): String {
        return getLiveScanResourceAsString(scannerId, "dom", scanId)
    }

    /**
     * Get result from a live scan as String (JSON).
     *
     * @param scannerId ID of the scanner
     * @param scanId UUID of the scan
     * @return String containing the JSON result
     */
    suspend fun getLiveScanResult(
        scannerId: String,
        scanId: String
    ): String {
        return getLiveScanResourceAsString(scannerId, "result", scanId)
    }

    /**
     * Download a file from a live scan by SHA256 hash.
     *
     * @param scannerId ID of the scanner
     * @param fileHash SHA256 hash of the file
     * @return ByteArray containing the file data
     */
    suspend fun downloadLiveScanFile(
        scannerId: String,
        fileHash: String
    ): ByteArray {
        return getLiveScanResource(scannerId, "download", fileHash)
    }
}