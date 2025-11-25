package io.urlscan.client.internal


/**
 * Default configuration values for HTTP client.
 * These can be overridden via UrlScanConfig or environment variables.
 */
internal object HttpClientDefaults {
    const val DEFAULT_API_HOST = "urlscan.io"
    const val DEFAULT_BASE_URL = "https://$DEFAULT_API_HOST"
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
    const val DEFAULT_SOCKET_TIMEOUT_MS = 30_000L
    const val DEFAULT_MAX_RETRIES = 3
    const val DEFAULT_FOLLOW_REDIRECTS = false
    const val DEFAULT_ENABLE_LOGGING = false

    // User Agent
    const val CLIENT_VERSION = "1.0.0"
    const val USER_AGENT_PREFIX = "UrlScan-Kotlin-Client"

    // Retry Configuration
    const val RETRY_BASE_DELAY = 2.0
    const val RETRY_MAX_DELAY_MS = 60_000L

    // HTTP/2 Configuration (JVM only)
    const val ENABLE_HTTP2 = true

    // Platform-specific defaults
    object JVM {
        const val USER_AGENT = "$USER_AGENT_PREFIX/$CLIENT_VERSION (JVM; Ktor/3.3.0)"
        const val RETRY_ON_CONNECTION_FAILURE = true
    }

    object iOS {
        const val USER_AGENT = "$USER_AGENT_PREFIX/$CLIENT_VERSION (iOS)"
        const val ALLOW_CELLULAR_ACCESS = true
        const val ALLOW_CONSTRAINED_NETWORK = true
        const val ALLOW_EXPENSIVE_NETWORK = true
    }

    object Native {
        const val USER_AGENT = "$USER_AGENT_PREFIX/$CLIENT_VERSION (Native)"
        const val SSL_VERIFY = true
    }

    object JS {
        const val USER_AGENT = "$USER_AGENT_PREFIX/$CLIENT_VERSION (JavaScript; Ktor/3.3.0)"
    }

    object Json {
        const val IGNORE_UNKNOWN_KEYS = true
        const val IS_LENIENT = true
        const val ENCODE_DEFAULTS = true
        const val ALLOW_SPECIAL_FLOATING_POINT = true
        const val USE_ALTERNATIVE_NAMES = true
        const val ALLOW_STRUCTURED_MAP_KEYS = true
        const val COERCE_INPUT_VALUES = true
    }

    object Headers {
        const val API_KEY_HEADER = "API-Key"
        const val ACCEPT_JSON = "application/json"
        const val CONTENT_TYPE_JSON = "application/json"
    }

    object Logging {
        val SANITIZED_HEADERS = setOf("api-Key")
        const val LOG_LEVEL_ALL = true
        const val FILTER_URLSCAN_ONLY = true
    }
}
