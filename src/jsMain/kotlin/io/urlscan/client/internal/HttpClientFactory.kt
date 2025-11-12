package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import kotlinx.serialization.json.Json


/**
 * JavaScript (Browser/Node.js) implementation using JS engine
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(Js) {
        // Proxy support:
        // - In Node.js: Respects http_proxy/https_proxy environment variables
        // - In Browser: Cannot be configured (uses browser proxy settings)

        config.proxyUrl?.let {
            if (config.enableLogging) {
                val environment = if (isNodeJs()) "Node.js" else "Browser"
                println("$environment proxy: ${config.proxyUrl}")
                if (!isNodeJs()) {
                    println("Browser environments use system proxy settings. Custom proxy ignored.")
                }
            }
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = HttpClientDefaults.Json.IGNORE_UNKNOWN_KEYS
                prettyPrint = config.enableLogging
                isLenient = HttpClientDefaults.Json.IS_LENIENT
                encodeDefaults = HttpClientDefaults.Json.ENCODE_DEFAULTS
                allowSpecialFloatingPointValues = HttpClientDefaults.Json.ALLOW_SPECIAL_FLOATING_POINT
                useAlternativeNames = HttpClientDefaults.Json.USE_ALTERNATIVE_NAMES
                allowStructuredMapKeys = HttpClientDefaults.Json.ALLOW_STRUCTURED_MAP_KEYS
                coerceInputValues = HttpClientDefaults.Json.COERCE_INPUT_VALUES
            })
        }

        install(ContentEncoding) {
            gzip()
            deflate()
            identity()
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout
            connectTimeoutMillis = config.connectTimeout
            socketTimeoutMillis = config.socketTimeout
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = config.maxRetries)
            retryOnException(maxRetries = config.maxRetries, retryOnTimeout = true)

            // Exponential backoff with jitter
            exponentialDelay(
                base = HttpClientDefaults.RETRY_BASE_DELAY,
                maxDelayMs = HttpClientDefaults.RETRY_MAX_DELAY_MS,
                randomizationMs = 1000
            )

            retryIf { _, response ->
                when {
                    response.status == HttpStatusCode.TooManyRequests -> true
                    response.status == HttpStatusCode.ServiceUnavailable -> true
                    response.status.value in 502..504 -> true
                    response.status.value in 400..499 -> false
                    response.status.value >= 500 -> true
                    else -> false
                }
            }

            delayMillis { retry ->
                response?.headers["Retry-After"]?.toLongOrNull()?.times(1000)
                    ?: minOf(1000L * (1 shl retry), HttpClientDefaults.RETRY_MAX_DELAY_MS)
            }

            modifyRequest { request ->
                request.headers.append("X-Retry-Count", retryCount.toString())
            }
        }

        install(HttpCallValidator) {
            validateResponse { response ->
                val requestTime = response.responseTime.timestamp - response.requestTime.timestamp
                if (requestTime > 5000 && config.enableLogging) {
                    console.log("Slow response: ${requestTime}ms for ${response.call.request.url}")
                }
            }
        }

        if (config.enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        console.log(message)
                    }
                }
                level = if (HttpClientDefaults.Logging.LOG_LEVEL_ALL) LogLevel.ALL else LogLevel.INFO

                sanitizeHeader { header ->
                    HttpClientDefaults.Logging.SANITIZED_HEADERS.any {
                        it.equals(header, ignoreCase = true)
                    }
                }

                if (HttpClientDefaults.Logging.FILTER_URLSCAN_ONLY) {
                    filter { request ->
                        request.url.host.contains("urlscan.io", ignoreCase = true)
                    }
                }
            }
        }

        install(UserAgent) {
            agent = HttpClientDefaults.JS.USER_AGENT
        }

        defaultRequest {
            url(config.baseUrl)
            header(HttpHeaders.Accept, HttpClientDefaults.Headers.ACCEPT_JSON)
            header(HttpHeaders.ContentType, HttpClientDefaults.Headers.CONTENT_TYPE_JSON)
            accept(ContentType.Application.Json)
        }

        expectSuccess = false
        install(HttpPlainText)
    }
}

/**
 * Check if running in Node.js environment
 */
private fun isNodeJs(): Boolean {
    return try {
        js("typeof process !== 'undefined' && process.versions && process.versions.node") as Boolean
    } catch (e: Throwable) {
        false
    }
}

