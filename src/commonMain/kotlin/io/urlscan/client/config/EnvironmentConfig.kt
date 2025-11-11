package io.urlscan.client.config

import io.urlscan.client.internal.HttpClientDefaults


/**
 * Environment-based configuration loader.
 * This is an expect/actual pattern for loading environment variables
 * across different platforms.
 */
expect object EnvironmentConfig {
    fun get(key: String): String?
    fun getOrDefault(key: String, default: String): String
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun getLong(key: String, default: Long): Long
    fun getInt(key: String, default: Int): Int
}

/**
 * Configuration keys for environment variables.
 */
object ConfigKeys {
    const val API_KEY = "URLSCAN_API_KEY"
    const val BASE_URL = "URLSCAN_BASE_URL"
    const val TIMEOUT = "URLSCAN_TIMEOUT_MS"
    const val CONNECT_TIMEOUT = "URLSCAN_CONNECT_TIMEOUT_MS"
    const val SOCKET_TIMEOUT = "URLSCAN_SOCKET_TIMEOUT_MS"
    const val MAX_RETRIES = "URLSCAN_MAX_RETRIES"
    const val ENABLE_LOGGING = "URLSCAN_ENABLE_LOGGING"
    const val FOLLOW_REDIRECTS = "URLSCAN_FOLLOW_REDIRECTS"
}

object ConfigBuilder {
    /**
     * Create a config from environment variables with fallback to defaults.
     */
    fun fromEnvironment(apiKey: String? = null): io.urlscan.client.UrlScanConfig {
        return io.urlscan.client.UrlScanConfig(
            apiKey = apiKey
                ?: EnvironmentConfig.get(ConfigKeys.API_KEY)
                ?: "",
            baseUrl = EnvironmentConfig.getOrDefault(
                ConfigKeys.BASE_URL,
                HttpClientDefaults.DEFAULT_BASE_URL
            ),
            timeout = EnvironmentConfig.getLong(
                ConfigKeys.TIMEOUT,
                HttpClientDefaults.DEFAULT_TIMEOUT_MS
            ),
            connectTimeout = EnvironmentConfig.getLong(
                ConfigKeys.CONNECT_TIMEOUT,
                HttpClientDefaults.DEFAULT_CONNECT_TIMEOUT_MS
            ),
            socketTimeout = EnvironmentConfig.getLong(
                ConfigKeys.SOCKET_TIMEOUT,
                HttpClientDefaults.DEFAULT_SOCKET_TIMEOUT_MS
            ),
            enableLogging = EnvironmentConfig.getBoolean(
                ConfigKeys.ENABLE_LOGGING,
                HttpClientDefaults.DEFAULT_ENABLE_LOGGING
            ),
            followRedirects = EnvironmentConfig.getBoolean(
                ConfigKeys.FOLLOW_REDIRECTS,
                HttpClientDefaults.DEFAULT_FOLLOW_REDIRECTS
            ),
            maxRetries = EnvironmentConfig.getInt(
                ConfigKeys.MAX_RETRIES,
                HttpClientDefaults.DEFAULT_MAX_RETRIES
            )
        )
    }
}
