package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DTO for triggering a live scan request.
 * Allows fine-grained control over scanner configuration and behavior.
 */
@Serializable
data class LiveScanRequest(
    val task: LiveScanTask? = null,
    val scanner: LiveScannerConfig? = null
)

/**
 * Configuration for the live scan task.
 *
 * @property url The URL to scan
 * @property visibility Visibility level for the scan result: public, unlisted, or private
 */
@Serializable
data class LiveScanTask(
    val url: String,
    val visibility: Visibility = Visibility.PUBLIC
)

/**
 * Scanner-specific configuration for live scans.
 * All properties are optional and have intelligent defaults.
 *
 * @property pageTimeout Time to wait for the whole scan process in milliseconds
 * @property captureDelay Delay after page has finished loading before capturing page content (ms)
 * @property extraHeaders Extra HTTP headers to send with requests
 * @property enableFeatures List of features to enable during the scan
 * @property disableFeatures List of features to disable during the scan
 */
@Serializable
data class LiveScannerConfig(
    val pageTimeout: Int? = null,
    val captureDelay: Int? = null,
    val extraHeaders: Map<String, String>? = null,
    val enableFeatures: List<String>? = null,
    val disableFeatures: List<String>? = null
)

/**
 * Response from a live scan trigger operation.
 * Contains the UUID needed to poll for results.
 *
 * @property uuid Unique identifier for the scan (used to fetch results)
 */
@Serializable
data class LiveScanResponse(
    val uuid: String
)

/**
 * Metadata about an available live scanner node.
 * Returned by the scanners endpoint.
 *
 * @property id Unique identifier for this scanner
 * @property location Geographic location of the scanner
 * @property status Current operational status (active, busy, etc.)
 * @property loadPercentage Current load as a percentage (0-100)
 * @property supportedCountries List of ISO country codes this scanner can scan from
 * @property features List of features supported by this scanner
 * @property lastHealthCheck Timestamp of last health check
 */
@Serializable
data class LiveScannerInfo(
    @SerialName("_id")
    val id: String,
    val location: String? = null,
    val status: String? = null,
    val loadPercentage: Int? = null,
    val supportedCountries: List<String>? = null,
    val features: List<String>? = null,
    val lastHealthCheck: String? = null
)

/**
 * Request body for storing a temporary live scan as a permanent snapshot.
 *
 * @property visibility Visibility level for the stored scan
 */
@Serializable
data class LiveScanStoreRequest(
    val visibility: Visibility
)

/**
 * Response wrapper for scan resource operations.
 * Used for generic responses from resource endpoints.
 */
@Serializable
data class LiveScanResourceResponse(
    val success: Boolean,
    val message: String? = null,
    val data: JsonObject? = null
)