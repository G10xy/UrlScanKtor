package io.urlscan.client.exception

import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText


/**
 * Installs centralized exception handling for all HTTP calls.
 * This replaces the need for try-catch blocks in every API method.
 */
internal fun <T> T.installExceptionHandling() where T : io.ktor.client.HttpClientConfig<*> {
    install(HttpCallValidator) {
        validateResponse { response ->
            val statusCode = response.status.value

            // Only validate error responses
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

        // Handle any exceptions
        handleResponseExceptionWithRequest { exception, request ->
            when (exception) {
                is ResponseException -> {
                    // handled in validateResponse
                    throw exception
                }
                // other exceptions (network errors, timeouts) pass through
                else -> throw exception
            }
        }
    }
}