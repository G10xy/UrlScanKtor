package io.urlscan.client.exception

import io.ktor.client.plugins.ClientRequestException

/**
 * Helper function to handle REST client exceptions
 */
fun handleClientException(e: ClientRequestException): UrlScanException {
    return when (e.response.status.value) {
        401, 403 -> AuthenticationException("Invalid or missing API key")
        404 -> NotFoundException("Resource not found: ${e.message}")
        429 -> {
            val retryAfter = e.response.headers["Retry-After"]?.toLongOrNull()
            RateLimitException("Rate limit exceeded", retryAfter)
        }
        400 -> ApiException(400, "Bad request: ${e.message}")
        else -> ApiException(
            e.response.status.value,
            "API error (${e.response.status.value}): ${e.message}"
        )
    }
}