package io.urlscan.client.exception

/**
 * Base sealed class for all custom exceptions related to the UrlScan API client.
 * Using a sealed class allows for exhaustive `when` checks on exception types.
 */
sealed class UrlScanException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when the API key is invalid, missing, or lacks permissions.
 * Corresponds to HTTP 401 Unauthorized or 403 Forbidden.
 */
class AuthenticationException(message: String) : UrlScanException(message)

/**
 * Thrown when a requested resource (like a scan result) is not found.
 * Corresponds to HTTP 404 Not Found.
 */
class NotFoundException(message: String) : UrlScanException(message)

/**
 * Thrown when the API rate limit has been exceeded.
 * Corresponds to HTTP 429 Too Many Requests.
 *
 * @property retryAfterSeconds The number of seconds to wait before retrying, if provided by the API.
 */
class RateLimitException(
    message: String,
    val retryAfterSeconds: Long? = null
) : UrlScanException(message)

/**
 * A generic exception for other API-related errors.
 *
 * @property statusCode The HTTP status code returned by the API.
 */
class ApiException(
    val statusCode: Int,
    message: String
) : UrlScanException(message)
