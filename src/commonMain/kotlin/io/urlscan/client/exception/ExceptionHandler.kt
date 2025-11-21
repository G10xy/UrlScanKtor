package io.urlscan.client.exception

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException


/**
 * Installs comprehensive exception handling for all HTTP calls.
 *
 * This provides two layers of error handling:
 * 1. validateResponse: Handles HTTP-level errors (status codes) BEFORE deserialization
 * 2. handleResponseExceptionWithRequest: Handles transport/network errors with request context
 *
 * This replaces the need for try-catch blocks in every API method and provides
 * consistent, user-friendly error messages with full context.
 */
internal fun <T> T.installExceptionHandling() where T : io.ktor.client.HttpClientConfig<*> {
    install(HttpCallValidator) {
        validateResponse { response ->
            val statusCode = response.status.value

            if (statusCode >= 400) {
                val errorMessage = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    response.status.description
                }

                throw when (statusCode) {
                    401, 403 -> AuthenticationException(
                        "Invalid or missing API key for ${response.call.request.url}"
                    )
                    404 -> NotFoundException(
                        "Resource not found: ${response.call.request.url} - $errorMessage"
                    )
                    429 -> {
                        val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                        RateLimitException(
                            "Rate limit exceeded for ${response.call.request.url}",
                            retryAfter
                        )
                    }
                    400 -> ApiException(
                        400,
                        "Bad request to ${response.call.request.url}: $errorMessage"
                    )
                    in 500..599 -> ApiException(
                        statusCode,
                        "Server error ($statusCode) for ${response.call.request.url}: $errorMessage"
                    )
                    else -> ApiException(
                        statusCode,
                        "API error ($statusCode) for ${response.call.request.url}: $errorMessage"
                    )
                }
            }
        }

        handleResponseExceptionWithRequest { exception, request ->
            val method = request.method.value
            val url = request.url.toString()

            when (exception) {
                is AuthenticationException,
                is NotFoundException,
                is RateLimitException,
                is ApiException -> throw exception

                is ConnectTimeoutException -> {
                    throw ApiException(
                        statusCode = 0,
                        message = "Connection timeout while connecting to ${request.url.host}. " +
                                "Check your network connection or try again later."
                    )
                }

                is SocketTimeoutException -> {
                    throw ApiException(
                        statusCode = 0,
                        message = "Request timeout for $method $url. " +
                                "The request took too long to complete."
                    )
                }

                is TimeoutCancellationException -> {
                    throw ApiException(
                        statusCode = 0,
                        message = "Request timeout for $method $url. " +
                                "The operation exceeded the maximum allowed time."
                    )
                }

                is IOException -> {
                    throw ApiException(
                        statusCode = 0,
                        message = "Network error while calling $method $url: ${exception.message}"
                    )
                }

                is ResponseException -> {
                    throw ApiException(
                        statusCode = exception.response.status.value,
                        message = "HTTP error (${exception.response.status.value}) for $method $url"
                    )
                }

                else -> {
                    throw ApiException(
                        statusCode = 0,
                        message = "Unexpected error for $method $url: ${exception::class.simpleName} - ${exception.message}"
                    )
                }
            }
        }
    }
}