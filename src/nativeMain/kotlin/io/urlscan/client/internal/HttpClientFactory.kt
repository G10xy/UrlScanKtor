package io.urlscan.client.internal


import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.urlscan.client.UrlScanConfig
import kotlinx.coroutines.invoke
import kotlinx.serialization.json.Json

/**
 * Native platforms implementation (Linux, macOS, Windows) using Curl engine.
 * This implementation is shared across all desktop/server native platforms.
 * Curl is a widely-used, reliable HTTP library available on most systems.
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(Curl) {
        // Curl Engine Configuration
        engine {
            sslVerify = true
        }

        // Content Negotiation
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = config.enableLogging
                isLenient = true
                encodeDefaults = true
                allowSpecialFloatingPointValues = true
                useAlternativeNames = true
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
            retryOnException(maxRetries = config.maxRetries)
            exponentialDelay()
        }

        // Logging
        if (config.enableLogging) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
                sanitizeHeader { header -> header == "API-Key" }
            }
        }

        // User Agent
        install(UserAgent) {
            agent = "UrlScan-Kotlin-Client/1.0.0 (Native)"
        }

        // Default Request Configuration
        defaultRequest {
            url(config.baseUrl)
        }

        expectSuccess = false
    }
}