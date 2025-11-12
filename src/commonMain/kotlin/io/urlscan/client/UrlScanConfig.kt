package io.urlscan.client

import io.urlscan.client.internal.HttpClientDefaults

/**
 * Proxy type for HTTP client configuration.
 */
enum class ProxyType {
    HTTP,
    HTTPS,
    SOCKS
}

/**
 * Configuration for the UrlScan client.
 * All parameters have sensible defaults that can be overridden.
 *
 * @property apiKey Your urlscan.io API key (required for most operations).
 * @property apiHost Hostname for the API (e.g., "api.urlscan.io"). Used when constructing requests if `baseUrl` is not overridden.
 * @property baseUrl Base URL for the API including scheme (e.g., "https://api.urlscan.io"). Overrides `apiHost` when provided.
 * @property timeout Request timeout in milliseconds for the entire request lifecycle.
 * @property connectTimeout Connection timeout in milliseconds — time allowed to establish a TCP connection.
 * @property socketTimeout Socket timeout in milliseconds — time to wait for data between packets after the connection is established.
 * @property enableLogging Enable debug logging for HTTP requests, responses, and client internals.
 * @property followRedirects Whether the HTTP client should automatically follow 3xx redirects.
 * @property maxRetries Maximum number of retry attempts for transient failures (non-negative).
 * @property isProxyNeeded Flag indicating whether requests should be routed through a proxy.
 * @property proxyUrl Optional proxy URL including scheme and host (e.g., "http://proxy.company.com:8080" or "socks://proxy:1080").
 * @property proxyPort Optional explicit proxy port; ignored if a port is already included in `proxyUrl` or if `proxyUrl` is null.
 * @property proxyType Type of proxy to use when `isProxyNeeded` is true (HTTP, HTTPS, or SOCKS).
 */
data class UrlScanConfig(
    val apiKey: String = "",
    val apiHost: String = HttpClientDefaults.DEFAULT_API_HOST,
    val baseUrl: String = HttpClientDefaults.DEFAULT_BASE_URL,
    val timeout: Long = HttpClientDefaults.DEFAULT_TIMEOUT_MS,
    val connectTimeout: Long = HttpClientDefaults.DEFAULT_CONNECT_TIMEOUT_MS,
    val socketTimeout: Long = HttpClientDefaults.DEFAULT_SOCKET_TIMEOUT_MS,
    val enableLogging: Boolean = HttpClientDefaults.DEFAULT_ENABLE_LOGGING,
    val followRedirects: Boolean = HttpClientDefaults.DEFAULT_FOLLOW_REDIRECTS,
    val maxRetries: Int = HttpClientDefaults.DEFAULT_MAX_RETRIES,
    val isProxyNeeded: Boolean = false,
    val proxyUrl: String? = null,
    val proxyPort: Int = 0,
    val proxyType: ProxyType = ProxyType.HTTP
) {
    init {
        require(timeout > 0) { "Timeout must be positive" }
        require(connectTimeout > 0) { "Connect timeout must be positive" }
        require(socketTimeout > 0) { "Socket timeout must be positive" }
        require(maxRetries >= 0) { "Max retries must be non-negative" }

        require(connectTimeout <= socketTimeout) {
            "Connect timeout ($connectTimeout ms) must be <= socket timeout ($socketTimeout ms)"
        }
        require(socketTimeout <= timeout) {
            "Socket timeout ($socketTimeout ms) must be <= request timeout ($timeout ms)"
        }

        // Validate proxy URL if provided
        proxyUrl?.let { url ->
            require(url.isNotBlank()) { "Proxy URL cannot be blank" }
            require(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("socks://")) {
                "Proxy URL must start with http://, https://, or socks://"
            }
        }
    }

    /**
     * Creates a copy of this config with the specified API key.
     */
    fun withApiKey(apiKey: String): UrlScanConfig = copy(apiKey = apiKey)

    /**
     * Creates a copy of this config with logging enabled.
     */
    fun withLogging(): UrlScanConfig = copy(enableLogging = true)

    /**
     * Creates a copy of this config with custom timeout.
     * Sets all timeouts to the same value.
     */
    fun withTimeout(timeout: Long): UrlScanConfig = copy(
        timeout = timeout,
        connectTimeout = timeout / 3,  // 1/3 of total timeout
        socketTimeout = timeout / 2    // 1/2 of total timeout
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

    /**
     * Creates a copy of this config with proxy settings.
     *
     * @param proxyUrl The proxy URL (e.g., "http://proxy.company.com:8080")
     * @param proxyType The type of proxy (default: HTTP)
     */
    fun withProxy(proxyUrl: String, proxyType: ProxyType = ProxyType.HTTP): UrlScanConfig =
        copy(proxyUrl = proxyUrl, proxyType = proxyType)

    companion object {
        /**
         * Creates a default configuration.
         */
        fun default(): UrlScanConfig = UrlScanConfig()

        /**
         * Creates a configuration with just an API key using all defaults.
         */
        fun withApiKey(apiKey: String): UrlScanConfig = UrlScanConfig(apiKey = apiKey)
    }
}