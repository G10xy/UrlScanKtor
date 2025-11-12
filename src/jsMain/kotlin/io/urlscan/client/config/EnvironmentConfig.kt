package io.urlscan.client.config

/**
 * JavaScript implementation for both Browser and Node.js.
 *
 * In Node.js: Uses process.env
 * In Browser: Environment variables must be injected at build time
 *             (e.g., via webpack DefinePlugin or similar)
 */
actual object EnvironmentConfig {
    actual fun get(key: String): String? {
        return try {
            // Check if we're in Node.js environment
            if (js("typeof process !== 'undefined' && process.env") as Boolean) {
                js("process.env[key]") as? String
            } else {
                // Browser environment - check window object for injected config
                js("typeof window !== 'undefined' && window.ENV && window.ENV[key]") as? String
            }
        } catch (e: Throwable) {
            null
        }
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