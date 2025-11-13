package io.urlscan.client.exception

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.statement.bodyAsText


/**
 * Installs centralized exception handling for all HTTP calls.
 * This replaces the need for try-catch blocks in every API method.
 */
internal fun <T> T.installExceptionHandling() where T : io.ktor.client.HttpClientConfig<*> {
    install(HttpCallValidator) {
        handleResponseExceptionWithRequest { exception, request ->
            when (exception) {
                is ClientRequestException -> {
                    val statusCode = exception.response.status.value
                    val errorMessage = try {
                        exception.response.bodyAsText()
                    } catch (e: Exception) {
                        exception.message
                    }

                    throw when (statusCode) {
                        401, 403 -> AuthenticationException(
                            "Invalid or missing API key for ${request.url}"
                        )
                        404 -> NotFoundException(
                            "Resource not found: ${request.url} - $errorMessage"
                        )
                        429 -> {
                            val retryAfter = exception.response.headers["Retry-After"]?.toLongOrNull()
                            RateLimitException(
                                "Rate limit exceeded for ${request.url}",
                                retryAfter
                            )
                        }
                        400 -> ApiException(
                            400,
                            "Bad request to ${request.url}: $errorMessage"
                        )
                        in 500..599 -> ApiException(
                            statusCode,
                            "Server error ($statusCode) for ${request.url}: $errorMessage"
                        )
                        else -> ApiException(
                            statusCode,
                            "API error ($statusCode) for ${request.url}: $errorMessage"
                        )
                    }
                }
                // Let other exceptions (network errors, timeouts) pass through
                else -> throw exception
            }
        }
    }
}