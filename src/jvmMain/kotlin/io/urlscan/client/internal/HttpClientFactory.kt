package io.urlscan.client.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpPlainText
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.urlscan.client.UrlScanConfig
import io.urlscan.client.exception.installExceptionHandling
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Protocol

/**
 * JVM/Android implementation using OkHttp engine
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                if (HttpClientDefaults.ENABLE_HTTP2) {
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                }

                followRedirects(config.followRedirects)
                followSslRedirects(config.followRedirects)
                connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
                readTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
                writeTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
                callTimeout(config.timeout, TimeUnit.MILLISECONDS)
                retryOnConnectionFailure(HttpClientDefaults.JVM.RETRY_ON_CONNECTION_FAILURE)
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

            // Respect Retry-After header
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
                    println("Slow response: ${requestTime}ms for ${response.call.request.url}")
                }
            }
        }

        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = if (HttpClientDefaults.Logging.LOG_LEVEL_ALL) LogLevel.ALL else LogLevel.INFO

                sanitizeHeader { header ->
                    HttpClientDefaults.Logging.SANITIZED_HEADERS.any {
                        it.equals(header, ignoreCase = true)
                    }
                }

                if (HttpClientDefaults.Logging.FILTER_URLSCAN_ONLY) {
                    filter { request ->
                        request.url.host.contains(config.apiHost, ignoreCase = true)
                    }
                }
            }
        }

        install(UserAgent) {
            agent = HttpClientDefaults.JVM.USER_AGENT
        }

        defaultRequest {
            url(config.baseUrl)
            header(HttpHeaders.Accept, HttpClientDefaults.Headers.ACCEPT_JSON)
            header(HttpHeaders.ContentType, HttpClientDefaults.Headers.CONTENT_TYPE_JSON)
            accept(ContentType.Application.Json)
        }

        expectSuccess = false
        install(HttpPlainText)
        installExceptionHandling()
    }
}