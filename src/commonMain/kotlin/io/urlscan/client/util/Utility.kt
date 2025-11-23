package io.urlscan.client.util

object Utility {

    fun generateRandomString(length: Int): String {
        val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return List(length) { charPool.random() }.joinToString("")
    }

}