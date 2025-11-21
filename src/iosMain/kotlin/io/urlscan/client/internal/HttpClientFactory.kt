package io.urlscan.client.internal

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.urlscan.client.UrlScanConfig
import io.urlscan.client.exception.installExceptionHandling
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLRequestNetworkServiceTypeDefault
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.setWaitsForConnectivity

/**
 * iOS implementation using Darwin (native Apple) engine
 */
actual fun createPlatformHttpClient(config: UrlScanConfig): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(HttpClientDefaults.iOS.ALLOW_CELLULAR_ACCESS)
                setAllowsConstrainedNetworkAccess(HttpClientDefaults.iOS.ALLOW_CONSTRAINED_NETWORK)
                setAllowsExpensiveNetworkAccess(HttpClientDefaults.iOS.ALLOW_EXPENSIVE_NETWORK)

                setTimeoutInterval(config.timeout.toDouble() / 1000.0)
                setWaitsForConnectivity(true)
                setNetworkServiceType(NSURLRequestNetworkServiceTypeDefault)
            }

            // SSL/TLS Challenge Handling
            handleChallenge { _, _, challenge, completionHandler ->
                val authMethod = challenge.protectionSpace.authenticationMethod

                when (authMethod) {
                    NSURLAuthenticationMethodServerTrust -> {
                        //TODO Accept server trust (add proper validation)
                        completionHandler(NSURLSessionAuthChallengeUseCredential.toInt(), null)
                    }
                    else -> {
                        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge.toInt(), null)
                    }
                }
            }

            config.proxyUrl?.let {
                if (config.enableLogging) {
                    println("iOS uses system proxy settings. Custom proxy URL ignored: ${config.proxyUrl}")
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
                    println("Slow response: ${requestTime} ms for ${response.call.request.url}")
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
            agent = HttpClientDefaults.iOS.USER_AGENT
        }

        defaultRequest {
            url(config.baseUrl)
            header(HttpHeaders.Accept, HttpClientDefaults.Headers.ACCEPT_JSON)
            header(HttpHeaders.ContentType, HttpClientDefaults.Headers.CONTENT_TYPE_JSON)
            if (config.apiKey.isNotBlank()) {
                header(HttpClientDefaults.Headers.API_KEY_HEADER, config.apiKey)
            }
            accept(ContentType.Application.Json)
        }

        expectSuccess = false
        install(HttpPlainText)
        installExceptionHandling()
    }
}