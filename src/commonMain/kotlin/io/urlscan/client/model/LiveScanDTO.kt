package io.urlscan.client.model

import kotlinx.serialization.Serializable

@Serializable
data class LiveScanTask(
    val url: String,
    val visibility: Visibility? = null
)

@Serializable
data class LiveScanScanner(
    val pageTimeout: Int? = null,
    val captureDelay: Int? = null,
    val extraHeaders: Map<String, String>? = null,
    val enableFeatures: List<String>? = null,
    val disableFeatures: List<String>? = null
)

@Serializable
data class LivescanRequest(
    val task: LiveScanTask,
    val scanner: LiveScanScanner? = null
)