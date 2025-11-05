package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLSessionAuthChallengeUseCredential

/**
 * iOS implementation using Darwin (native Apple) engine.
 * Darwin uses NSURLSession under the hood, which is optimized for Apple platforms.
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(Darwin) {
        // Darwin Engine Configuration
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setAllowsConstrainedNetworkAccess(true)
                setAllowsExpensiveNetworkAccess(true)
                setTimeoutInterval(config.timeout.toDouble() / 1000.0)
            }

            // Handle challenges (SSL, authentication)
            handleChallenge { session, task, challenge, completionHandler ->
                completionHandler(NSURLSessionAuthChallengeUseCredential.toInt(), null)
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
            agent = "UrlScan-Kotlin-Client/1.0.0 (iOS)"
        }

        // Default Request Configuration
        defaultRequest {
            url(config.baseUrl)
        }

        // Response Validation
        expectSuccess = false // We handle errors manually
    }
}

