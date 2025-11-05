package io.urlscan.client.internal

import io.ktor.client.*
import io.urlscan.client.UrlScanConfig

/**
 * Creates a platform-specific HTTP client configured with the provided settings.
 *
 * This is an `expect` declaration that must be implemented by each platform:
 * - JVM/Android: Uses OkHttp engine
 * - iOS: Uses Darwin (native Apple) engine
 * - Native (Linux/macOS/Windows): Uses Curl engine
 * - JavaScript: Uses JS engine
 *
 * @param config Configuration containing API key, timeouts, and other settings
 * @return Configured HttpClient instance for the current platform
 */
expect fun createPlatformHttpClient(config: UrlScanConfig): HttpClient