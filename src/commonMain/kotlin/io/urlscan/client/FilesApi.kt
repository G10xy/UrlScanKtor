package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class FilesApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Download a file by SHA256 hash as a password-protected ZIP archive.
     * The downloaded ZIP file contains a single file named after the SHA256 hash.
     *
     * By default, the ZIP encryption password is "urlscan!"
     * To use a different password, specify it in the password parameter.
     *
     * @param fileHash SHA256 hash of the file to download
     * @param password The password to encrypt the ZIP file with (default: "urlscan!")
     * @param filename Optional custom filename for the downloaded ZIP (default: "$fileHash.zip")
     * @return ByteArray containing the encrypted ZIP file data
     */
    suspend fun downloadFile(
        fileHash: String,
        password: String = "urlscan!",
        filename: String? = null
    ): ByteArray {
        require(fileHash.isNotBlank()) { "File hash cannot be blank" }
        require(fileHash.length == 64) { "SHA256 hash must be 64 characters long" }
        require(password.isNotBlank()) { "Password cannot be blank" }

        return withContext(Dispatchers.Default) {
            val channel = httpClient.get(
                "${config.apiHost}/downloads/$fileHash"
            ) {
                headers {
                    append("API-Key", config.apiKey)
                }
                parameter("password", password)
                filename?.let { parameter("filename", it) }
            }.bodyAsChannel()

            channel.toByteArray()
        }
    }

    /**
     * Batch download multiple files.
     * Downloads files sequentially and returns a map of hash to ByteArray.
     *
     * @param fileHashes List of SHA256 hashes to download
     * @param password The password for ZIP encryption (default: "urlscan!")
     * @param filename Optional custom filename for the downloaded ZIP (default: "$fileHash.zip")
     * @concurrency Number of concurrent downloads (default: 10)
     * @return Map of file hash to ByteArray containing ZIP data
     */
    suspend fun downloadFilesParallelLimited(
        fileHashes: List<String>,
        password: String = "urlscan!",
        filename: String? = null,
        concurrency: Int = 10
    ): Map<String, Result<ByteArray>> = supervisorScope {
        val semaphore = Semaphore(concurrency)
        val deferred = fileHashes.map { hash ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    runCatching { downloadFile(hash, password, filename) }.fold(
                        onSuccess = { hash to Result.success(it) },
                        onFailure = { hash to Result.failure(it) }
                    )
                }
            }
        }
        deferred.awaitAll().toMap()
    }
}