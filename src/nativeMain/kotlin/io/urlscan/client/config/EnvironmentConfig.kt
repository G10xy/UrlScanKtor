package io.urlscan.client.config

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Native platforms (Linux, macOS, Windows) implementation.
 * Uses POSIX getenv() function.
 */
@OptIn(ExperimentalForeignApi::class)
actual object EnvironmentConfig {
    actual fun get(key: String): String? {
        return getenv(key)?.toKString()
    }

    actual fun getOrDefault(key: String, default: String): String {
        return get(key) ?: default
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return get(key)?.lowercase()?.toBooleanStrictOrNull() ?: default
    }

    actual fun getLong(key: String, default: Long): Long {
        return get(key)?.toLongOrNull() ?: default
    }

    actual fun getInt(key: String, default: Int): Int {
        return get(key)?.toIntOrNull() ?: default
    }
}