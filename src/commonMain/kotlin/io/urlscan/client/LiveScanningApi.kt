package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.toByteArray
import io.urlscan.client.model.LiveScanRequest
import io.urlscan.client.model.LiveScanResponse
import io.urlscan.client.model.LiveScanStoreRequest
import io.urlscan.client.model.LiveScannerInfo
import io.urlscan.client.model.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveScanningApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {

    /**
     * Enum defining the types of resources available from a live scan.
     */
    enum class LiveScanResourceType(
        val path: String,
        val contentType: ContentType,
        val isBinary: Boolean
    ) {
        RESULT("result", ContentType.Application.Json, false),
        SCREENSHOT("screenshot", ContentType.Image.PNG, true),
        DOM("dom", ContentType.Text.Html, false),
        RESPONSE("response", ContentType.Application.OctetStream, true),
        DOWNLOAD("download", ContentType.Application.OctetStream, true);
    }

    /**
     * Get list of available Live Scanning nodes along with their current metadata.
     *
     * @return List of available scanner information
     */
    suspend fun getLiveScanners(): List<LiveScannerInfo> {
        return httpClient.get("${config.apiHost}/api/v1/livescan/scanners/").body()
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
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Get live scan resource as a String (for JSON/HTML/text content).
     */
    suspend fun getLiveScanResourceAsString(
        scannerId: String,
        resourceType: LiveScanResourceType,
        resourceId: String
    ): String {
        require(!resourceType.isBinary) {
            "Resource type ${resourceType.name} is binary, use getLiveScanResourceAsBinary method instead"
        }

        return httpClient.get(
            "${config.apiHost}/api/v1/livescan/$scannerId/${resourceType.path}/$resourceId"
        ) {
            accept(resourceType.contentType)
        }.body()
    }

    /**
     * Get live scan resource as ByteArray (for binary content).
     */
    suspend fun getLiveScanResourceAsBinary(
        scannerId: String,
        resourceType: LiveScanResourceType,
        resourceId: String
    ): ByteArray {
        require(resourceType.isBinary) {
            "Resource type ${resourceType.name} is not binary, use getLiveScanResourceAsString method instead"
        }

        return withContext(Dispatchers.Default) {
            val channel = httpClient.get(
                "${config.apiHost}/api/v1/livescan/$scannerId/${resourceType.path}/$resourceId"
            ) {
                accept(resourceType.contentType)
            }.bodyAsChannel()

            channel.toByteArray()
        }
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
        ).body()
    }
}