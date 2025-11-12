package io.urlscan.client.config

import platform.Foundation.NSProcessInfo

/**
 * iOS implementation of environment configuration.
 */
actual object EnvironmentConfig {
    actual fun get(key: String): String? {
        return NSProcessInfo.processInfo.environment[key] as? String
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