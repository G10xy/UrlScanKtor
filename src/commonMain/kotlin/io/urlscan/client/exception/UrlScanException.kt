package io.urlscan.client.exception

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Base sealed class for all custom exceptions related to the UrlScan API client.
 */
@Serializable
sealed class UrlScanException(
    @Transient override val message: String = "An error occurred",
    @Contextual override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when the API key is invalid, missing, or lacks permissions.
 */
@Serializable
class AuthenticationException(
    override val message: String
) : UrlScanException(message)

/**
 * Thrown when a requested resource (like a scan result) is not found.
 */
@Serializable
class NotFoundException(
    override val message: String
) : UrlScanException(message)

/**
 * Thrown when the API rate limit has been exceeded.
 */
@Serializable
class RateLimitException(
    override val message: String,
    val retryAfterSeconds: Long? = null
) : UrlScanException(message)

/**
 * A generic exception for other API-related errors.
 */
@Serializable
class ApiException(
    val statusCode: Int,
    override val message: String,
    @Transient override val cause: Throwable? = null
) : UrlScanException(message, cause)

