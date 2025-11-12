package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

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
            exponentialDelay(
                base = HttpClientDefaults.RETRY_BASE_DELAY,
                maxDelayMs = HttpClientDefaults.RETRY_MAX_DELAY_MS
            )

            retryIf { _, response ->
                response.status == HttpStatusCode.TooManyRequests ||
                        response.status == HttpStatusCode.ServiceUnavailable
            }
        }

        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = if (HttpClientDefaults.Logging.LOG_LEVEL_ALL) LogLevel.ALL else LogLevel.INFO

                sanitizeHeader { header ->
                    HttpClientDefaults.Logging.SANITIZED_HEADERS.contains(header)
                }

                if (HttpClientDefaults.Logging.FILTER_URLSCAN_ONLY) {
                    filter { request ->
                        request.url.host.contains("urlscan.io")
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
    }
}