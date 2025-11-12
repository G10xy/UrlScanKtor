package io.urlscan.client.config

/**
 * JVM/Android implementation of environment configuration.
 */
actual object EnvironmentConfig {
    actual fun get(key: String): String? {
        // Check system properties first (can be set via -D flags)
        return System.getProperty(key) ?: System.getenv(key)
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