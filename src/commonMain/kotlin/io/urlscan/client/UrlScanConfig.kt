package io.urlscan.client


/**
 * Configuration for the UrlScan client.
 *
 * @property apiKey Your urlscan.io API key (required for most operations)
 * @property baseUrl Base URL for the API (default: https://urlscan.io/api/v1)
 * @property timeout Request timeout in milliseconds (default: 30000)
 * @property connectTimeout Connection timeout in milliseconds (default: 10000)
 * @property socketTimeout Socket timeout in milliseconds (default: 30000)
 * @property enableLogging Enable debug logging (default: false)
 * @property followRedirects Follow HTTP redirects (default: true)
 * @property maxRetries Maximum number of retry attempts on failure (default: 3)
 *
 */
data class UrlScanConfig(
    val apiKey: String = "",
    val baseUrl: String = "https://urlscan.io/api/v1",
    val timeout: Long = 30_000,
    val connectTimeout: Long = 10_000,
    val socketTimeout: Long = 30_000,
    val enableLogging: Boolean = false,
    val followRedirects: Boolean = true,
    val maxRetries: Int = 3
) {
    init {
        require(timeout > 0) { "Timeout must be positive" }
        require(connectTimeout > 0) { "Connect timeout must be positive" }
        require(socketTimeout > 0) { "Socket timeout must be positive" }
        require(maxRetries >= 0) { "Max retries must be non-negative" }
    }

    /**
     * Creates a copy of this config with the specified API key.
     * Useful for creating configs from environment variables.
     */
    fun withApiKey(apiKey: String): UrlScanConfig = copy(apiKey = apiKey)

    /**
     * Creates a copy of this config with logging enabled.
     */
    fun withLogging(): UrlScanConfig = copy(enableLogging = true)

    /**
     * Creates a copy of this config with custom timeout.
     */
    fun withTimeout(timeout: Long): UrlScanConfig = copy(
        timeout = timeout,
        connectTimeout = timeout,
        socketTimeout = timeout
    )

    /**
     * Creates a copy of this config with a specific number of retries.
     */
    fun withRetries(count: Int): UrlScanConfig = copy(maxRetries = count)

    /**
     * Creates a copy of this config that does not follow HTTP redirects.
     */
    fun withoutRedirects(): UrlScanConfig = copy(followRedirects = false)

    /**
     * Creates a copy of this config with a custom base URL.
     * Useful for pointing to a proxy or a test environment.
     */
    fun withBaseUrl(url: String): UrlScanConfig = copy(baseUrl = url)

    /**
     * Creates a copy of this config with a custom connection timeout.
     */
    fun withConnectTimeout(timeout: Long): UrlScanConfig = copy(connectTimeout = timeout)

    /**
     * Creates a copy of this config with a custom socket timeout.
     */
    fun withSocketTimeout(timeout: Long): UrlScanConfig = copy(socketTimeout = timeout)

}
