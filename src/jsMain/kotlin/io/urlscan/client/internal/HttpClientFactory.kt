package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import kotlinx.serialization.json.Json

/**
 * JavaScript (Browser/Node.js) implementation using JS engine.
 * Compatible with Kotlin 2.1.0 and Ktor 3.3.0
 *
 * Uses Fetch API in browsers and http/https modules in Node.js.
 * This engine automatically adapts to the runtime environment.
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(Js) {
        // JS Engine Configuration

        // Content Negotiation
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
            exponentialDelay(base = 2.0, maxDelayMs = 2)

            retryIf { request, response ->
                response.status == HttpStatusCode.TooManyRequests ||
                        response.status == HttpStatusCode.ServiceUnavailable
            }
        }

        // Logging (configurable)
        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
                sanitizeHeader { header -> header == "API-Key" }

                filter { request ->
                    request.url.host.contains("urlscan.io")
                }
            }
        }

        // ========================================
        // User Agent
        // ========================================
        install(UserAgent) {
            agent = "UrlScan-Kotlin-Client/1.0.0 (JavaScript; Ktor/3.3.0)"
        }

        // ========================================
        // Default Request Configuration
        // ========================================
        defaultRequest {
            url(config.baseUrl)

            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)

            accept(ContentType.Application.Json)
        }
        expectSuccess = false

        // HTTP Configuration
        install(HttpPlainText)
    }
}
