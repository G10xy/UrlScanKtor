package io.urlscan.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.urlscan.client.exception.ApiException
import io.urlscan.client.exception.AuthenticationException
import io.urlscan.client.exception.NotFoundException
import io.urlscan.client.exception.RateLimitException
import io.urlscan.client.exception.installExceptionHandling
import io.urlscan.client.util.Utility
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilesApiTest {

    private fun createValidSha256Hash(): String {
        return Utility.generateRandomString(64)
    }



    private fun createInvalidSha256Hash(): String {
        return "invalid_hash"
    }

    private fun createZipFileBytes(): ByteArray {
        // Mock ZIP file header (PK signature)
        return byteArrayOf(
            0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte(),
            0x14.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x08.toByte(), 0x00.toByte()
        )
    }

    private fun createMockHttpClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseBytes: ByteArray = createZipFileBytes(),
        errorContent: String? = null
    ) = io.ktor.client.HttpClient(
        MockEngine { request ->
            val content = when {
                statusCode.value >= 400 && errorContent != null -> errorContent
                else -> responseBytes.decodeToString()
            }
            respond(
                content = content,
                status = statusCode,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/zip"),
                    HttpHeaders.ContentLength to listOf(content.length.toString())
                )
            )
        }
    ) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        installExceptionHandling()
    }

    private fun createFilesApi(
        httpClient: io.ktor.client.HttpClient = createMockHttpClient(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): FilesApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return FilesApi(httpClient, config)
    }

    @Test
    fun testDownloadFileWithAllParameters() = runTest {
        val hash = createValidSha256Hash()
        val password = "custom-password"
        val filename = "my-file.zip"
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFile(hash, password = password, filename = filename)

        assertContentEquals(expectedZipBytes, result)
    }

    @Test
    fun testDownloadFileValidatesHashLength() = runTest {
        val filesApi = createFilesApi()

        assertFailsWith<IllegalArgumentException> {
            filesApi.downloadFile(createInvalidSha256Hash())
        }
    }

    @Test
    fun testDownloadFileValidatesHashNotBlank() = runTest {
        val filesApi = createFilesApi()

        assertFailsWith<IllegalArgumentException> {
            filesApi.downloadFile("")
        }
    }

    @Test
    fun testDownloadFileValidatesPasswordNotBlank() = runTest {
        val hash = createValidSha256Hash()
        val filesApi = createFilesApi()

        assertFailsWith<IllegalArgumentException> {
            filesApi.downloadFile(hash, password = "")
        }
    }

    @Test
    fun testDownloadFileAuthenticationError() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFileForbiddenError() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFileNotFoundError() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "File not found"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFileRateLimitError() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Too Many Requests"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Retry-After" to listOf("300")
                    )
                )
            }
        ) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val filesApi = createFilesApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            filesApi.downloadFile(hash)
        }
        assertEquals(300L, exception.retryAfterSeconds)
    }

    @Test
    fun testDownloadFileServerError() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFileBadRequest() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFileServiceUnavailable() = runTest {
        val hash = createValidSha256Hash()
        val mockClient = createMockHttpClient(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val filesApi = createFilesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            filesApi.downloadFile(hash)
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedWithSingleFile() = runTest {
        val hash = createValidSha256Hash()
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(listOf(hash))

        assertEquals(1, result.size)
        assertTrue(result.containsKey(hash))
        assertTrue(result[hash]!!.isSuccess)
        assertContentEquals(expectedZipBytes, result[hash]!!.getOrNull())
    }

    @Test
    fun testDownloadFilesParallelLimitedWithMultipleFiles() = runTest {
        val hashes = (1..5).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes)

        assertEquals(5, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedWithConcurrency() = runTest {
        val hashes = (1..10).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes, concurrency = 3)

        assertEquals(10, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedWithDefaultConcurrency() = runTest {
        val hashes = (1..15).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes)

        assertEquals(15, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedWithAllParameters() = runTest {
        val hashes = (1..5).map { createValidSha256Hash() }
        val password = "secure-password"
        val filename = "archive.zip"
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(
            hashes,
            password = password,
            filename = filename,
            concurrency = 2
        )

        assertEquals(5, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedWithPartialFailures() = runTest {
        val hash1 = createValidSha256Hash().removeRange(startIndex = 57, endIndex = 64)+"success"
        val hash2 = createValidSha256Hash().removeRange(startIndex = 57, endIndex = 64)+"failure"
        val expectedZipBytes = createZipFileBytes()

        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                val url = request.url.toString()
                val status = if (url.contains("success")) {
                    HttpStatusCode.OK
                } else {
                    HttpStatusCode.NotFound
                }

                respond(
                    content = if (status == HttpStatusCode.OK) {
                        expectedZipBytes.decodeToString()
                    } else {
                        """{"error": "Not found"}"""
                    },
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/zip"))
                )
            }
        ) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(listOf(hash1, hash2))

        assertEquals(2, result.size)
        assertTrue(result[hash1]!!.isSuccess)
        assertTrue(result[hash2]!!.isFailure)
    }

    @Test
    fun testDownloadFilesParallelLimitedPreserveHashKeys() = runTest {
        val hashes = listOf(
            createValidSha256Hash(),
            createValidSha256Hash(),
            createValidSha256Hash()
        )
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes)

        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
        }
    }

    @Test
    fun testDownloadFilesParallelLimitedHighConcurrency() = runTest {
        val hashes = (1..20).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes, concurrency = 20)

        assertEquals(20, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }

    @Test
    fun testDownloadFileZipHeaderValidation() = runTest {
        val hash = createValidSha256Hash()
        val zipWithHeader = byteArrayOf(
            0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte()
        )

        val mockClient = createMockHttpClient(responseBytes = zipWithHeader)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFile(hash)

        // Verify ZIP header is preserved
        assertTrue(result.size >= 4)
        assertEquals(0x50.toByte(), result[0])
        assertEquals(0x4B.toByte(), result[1])
    }

    @Test
    fun testDownloadFileWithLowConcurrency() = runTest {
        val hashes = (1..5).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes, concurrency = 1)

        assertEquals(5, result.size)
        hashes.forEach { hash ->
            assertTrue(result.containsKey(hash))
            assertTrue(result[hash]!!.isSuccess)
        }
    }


    @Test
    fun testDownloadFilesParallelLimitedResultTypeChecks() = runTest {
        val hashes = (1..3).map { createValidSha256Hash() }
        val expectedZipBytes = createZipFileBytes()

        val mockClient = createMockHttpClient(responseBytes = expectedZipBytes)
        val filesApi = createFilesApi(httpClient = mockClient)

        val result = filesApi.downloadFilesParallelLimited(hashes)

        // Verify all results are Result<ByteArray> type
        result.forEach { (hash, resultObj) ->
            assertTrue(resultObj.isSuccess)
            resultObj.getOrNull()?.let { bytes ->
                assertContentEquals(expectedZipBytes, bytes)
            }
        }
    }
}