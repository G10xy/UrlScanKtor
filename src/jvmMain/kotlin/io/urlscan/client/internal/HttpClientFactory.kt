package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * OkHttp is the standard HTTP client for JVM and Android platforms,
 * providing excellent performance and HTTP/2 support.
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(OkHttp) {
        // OkHttp Engine Configuration
        engine {
            config {
                protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

                followRedirects(config.followRedirects)
                followSslRedirects(config.followRedirects)

                connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
                readTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
                writeTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
                callTimeout(config.timeout, TimeUnit.MILLISECONDS)

                retryOnConnectionFailure(true)
            }
        }

        // Content Negotiation - JSON
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = config.enableLogging
                isLenient = true
                encodeDefaults = true
                allowSpecialFloatingPointValues = true
                useAlternativeNames = true
                allowStructuredMapKeys = true
                coerceInputValues = true
            })
        }

        // Timeout Configuration
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout
            connectTimeoutMillis = config.connectTimeout
            socketTimeoutMillis = config.socketTimeout
        }

        // Retry Configuration
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = config.maxRetries)
            retryOnException(maxRetries = config.maxRetries, retryOnTimeout = true)
            exponentialDelay(base = 2.0, maxDelayMs = 60000)

            // Retry conditions
            retryIf { request, response ->
                // Retry on 429 (rate limit) and 503 (service unavailable)
                response.status == HttpStatusCode.TooManyRequests ||
                        response.status == HttpStatusCode.ServiceUnavailable
            }
        }

        // Logging (if enabled)
        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL

                sanitizeHeader { header ->
                    header.equals("API-Key", ignoreCase = true) ||
                            header.equals("Authorization", ignoreCase = true)
                }

                filter { request ->
                    request.url.host.contains("urlscan.io")
                }
            }
        }

        // User Agent
        install(UserAgent) {
            agent = "UrlScan-Kotlin-Client/1.0.0 (JVM; Ktor/3.3.0)"
        }

        // Default Request Configuration
        defaultRequest {
            url(config.baseUrl)

            // Default headers
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)

            // Request encoding
            accept(ContentType.Application.Json)
        }
        expectSuccess = false

        install(HttpPlainText)
    }
}