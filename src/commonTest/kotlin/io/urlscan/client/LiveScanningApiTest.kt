package io.urlscan.client

import io.ktor.client.HttpClient
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
import io.urlscan.client.model.LiveScanRequest
import io.urlscan.client.model.LiveScanResponse
import io.urlscan.client.model.LiveScanTask
import io.urlscan.client.model.LiveScannerConfig
import io.urlscan.client.model.LiveScannerInfo
import io.urlscan.client.model.Visibility
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveScanningApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createLiveScanRequest(
        url: String = "https://example.com",
        visibility: Visibility = Visibility.PUBLIC,
        pageTimeout: Int? = null,
        captureDelay: Int? = null,
        extraHeaders: Map<String, String>? = null,
        enableFeatures: List<String>? = null,
        disableFeatures: List<String>? = null
    ): LiveScanRequest {
        return LiveScanRequest(
            task = LiveScanTask(url = url, visibility = visibility),
            scanner = if (pageTimeout != null || captureDelay != null || extraHeaders != null ||
                enableFeatures != null || disableFeatures != null
            ) {
                LiveScannerConfig(
                    pageTimeout = pageTimeout,
                    captureDelay = captureDelay,
                    extraHeaders = extraHeaders,
                    enableFeatures = enableFeatures,
                    disableFeatures = disableFeatures
                )
            } else {
                null
            }
        )
    }

    private fun createLiveScanResponse(uuid: String = "scan-uuid-123"): LiveScanResponse {
        return LiveScanResponse(uuid = uuid)
    }

    private fun createLiveScannerInfo(
        id: String = "scanner-1",
        location: String = "US-East",
        status: String = "active",
        loadPercentage: Int = 45,
        supportedCountries: List<String>? = listOf("US", "UK", "IT"),
        features: List<String>? = listOf("javascript", "cookies", "console"),
        lastHealthCheck: String = "2025-01-20T12:00:00Z"
    ): LiveScannerInfo {
        return LiveScannerInfo(
            id = id,
            location = location,
            status = status,
            loadPercentage = loadPercentage,
            supportedCountries = supportedCountries,
            features = features,
            lastHealthCheck = lastHealthCheck
        )
    }

    /**
     * Helper to create mock HTTP client that returns serialized DTO responses
     */
    @OptIn(InternalSerializationApi::class)
    private inline fun <reified T> createMockHttpClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseData: T? = null,
        errorContent: String? = null
    ) = HttpClient(MockEngine { request ->
        val content = when {
            statusCode.value >= 400 && errorContent != null -> errorContent
            responseData != null -> json.encodeToString(responseData)
            else -> "{}"
        }

        respond(
            content = content,
            status = statusCode,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        installExceptionHandling()
    }

    private fun createLiveScanningApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): LiveScanningApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return LiveScanningApi(httpClient, config)
    }

    private fun createMockBinaryResponse(
        binary: ByteArray,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        // For testing binary responses, there is the need to use a string representation that does not mismatch Content-Length
        val contentString = binary.joinToString("") { it.toString() }
        return HttpClient(MockEngine { request ->
            respond(
                content = contentString,
                status = statusCode,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/octet-stream")
                )
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
    }

    private fun createPngBytes(): ByteArray {
        // PNG file signature
        return byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
    }

    private fun createZipBytes(): ByteArray {
        // ZIP file signature
        return byteArrayOf(
            0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte(),
            0x14.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
        )
    }

    @Test
    fun testGetLiveScanners() = runTest {
        val scanners = listOf(
            createLiveScannerInfo(
                id = "scanner-1",
                location = "US-East",
                status = "active",
                loadPercentage = 45
            ),
            createLiveScannerInfo(
                id = "scanner-2",
                location = "EU-West",
                status = "active",
                loadPercentage = 62
            ),
            createLiveScannerInfo(
                id = "scanner-3",
                location = "US-West",
                status = "active",
                loadPercentage = 78
            )
        )

        val mockClient = createMockHttpClient(responseData = scanners)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertEquals(3, result.size)
        assertEquals("scanner-1", result[0].id)
        assertEquals("US-East", result[0].location)
        assertEquals("scanner-3", result[2].id)
        assertEquals(78, result[2].loadPercentage)
    }

    @Test
    fun testGetLiveScannersEmpty() = runTest {
        val mockClient = createMockHttpClient(responseData = emptyList<LiveScannerInfo>())
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetLiveScannersWithFeatures() = runTest {
        val scanner = createLiveScannerInfo(
            features = listOf("javascript", "cookies", "console", "network", "dom")
        )
        val mockClient = createMockHttpClient(responseData = listOf(scanner))
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertEquals(1, result.size)
        assertEquals(5, result[0].features?.size)
        assertEquals(result[0].features?.contains("javascript"), true)
    }

    @Test
    fun testTriggerLiveScanNonBlockingBasic() = runTest {
        val request = createLiveScanRequest(url = "https://example.com")
        val response = createLiveScanResponse("scan-uuid-nonblock-1")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScanNonBlocking("scanner-1", request)

        assertEquals("scan-uuid-nonblock-1", result.uuid)
        assertNotNull(result.uuid)
    }

    @Test
    fun testTriggerLiveScanNonBlockingWithConfig() = runTest {
        val request = createLiveScanRequest(
            url = "https://example.com",
            visibility = Visibility.UNLISTED,
            pageTimeout = 30000,
            captureDelay = 5000,
            extraHeaders = mapOf(
                "User-Agent" to "Custom-Agent",
                "Authorization" to "Bearer token123"
            )
        )
        val response = createLiveScanResponse("scan-uuid-configured")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScanNonBlocking("scanner-1", request)

        assertEquals("scan-uuid-configured", result.uuid)
    }

    @Test
    fun testTriggerLiveScanBlockingBasic() = runTest {
        val request = createLiveScanRequest(url = "https://example.com")
        val response = createLiveScanResponse("scan-uuid-block-1")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("scan-uuid-block-1", result.uuid)
    }

    @Test
    fun testTriggerLiveScanWithDifferentVisibility() = runTest {
        val publicRequest = createLiveScanRequest(visibility = Visibility.PUBLIC)
        val unlistedRequest = createLiveScanRequest(visibility = Visibility.UNLISTED)
        val privateRequest = createLiveScanRequest(visibility = Visibility.PRIVATE)

        val mockClient1 = createMockHttpClient(responseData = createLiveScanResponse("scan-public"))
        val mockClient2 = createMockHttpClient(responseData = createLiveScanResponse("scan-unlisted"))
        val mockClient3 = createMockHttpClient(responseData = createLiveScanResponse("scan-private"))

        val api1 = createLiveScanningApi(httpClient = mockClient1)
        val api2 = createLiveScanningApi(httpClient = mockClient2)
        val api3 = createLiveScanningApi(httpClient = mockClient3)

        val resultPublic = api1.triggerLiveScan("scanner-1", publicRequest)
        val resultUnlisted = api2.triggerLiveScan("scanner-1", unlistedRequest)
        val resultPrivate = api3.triggerLiveScan("scanner-1", privateRequest)

        assertEquals("scan-public", resultPublic.uuid)
        assertEquals("scan-unlisted", resultUnlisted.uuid)
        assertEquals("scan-private", resultPrivate.uuid)
    }

    @Test
    fun testGetLiveScanResultAsString() = runTest {
        val resultJson = """{"status": "success", "data": "result content"}"""

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = resultJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsString(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.RESULT,
            "scan-uuid-123"
        )

        assertTrue(result.contains("success"))
        assertTrue(result.contains("result content"))
    }

    @Test
    fun testGetLiveScanDomAsString() = runTest {
        val domHtml = """<html><body><div id="content">DOM content</div></body></html>"""

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = domHtml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/html"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsString(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.DOM,
            "scan-uuid-123"
        )

        assertTrue(result.contains("<html>"))
        assertTrue(result.contains("DOM content"))
    }

    @Test
    fun testGetLiveScanScreenshotAsBinary() = runTest {
        val pngBytes = createPngBytes()
        val mockClient = createMockBinaryResponse(pngBytes)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsBinary(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.SCREENSHOT,
            "scan-uuid-123"
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testGetLiveScanResponseAsBinary() = runTest {
        val responseBytes = createZipBytes()
        val mockClient = createMockBinaryResponse(responseBytes)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsBinary(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.RESPONSE,
            "scan-uuid-123"
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testGetLiveScanDownloadAsBinary() = runTest {
        val downloadBytes = createZipBytes()
        val mockClient = createMockBinaryResponse(downloadBytes)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsBinary(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.DOWNLOAD,
            "scan-uuid-123"
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testGetLiveScanResourceAsStringWithIncompatibleType() = runTest {
        val mockClient = createMockHttpClient<Unit>()
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<IllegalArgumentException> {
            liveScanningApi.getLiveScanResourceAsString(
                "scanner-1",
                LiveScanningApi.LiveScanResourceType.SCREENSHOT,
                "scan-uuid-123"
            )
        }
    }

    @Test
    fun testGetLiveScanResourceAsBinaryWithIncompatibleType() = runTest {
        val mockClient = createMockHttpClient<Unit>()
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<IllegalArgumentException> {
            liveScanningApi.getLiveScanResourceAsBinary(
                "scanner-1",
                LiveScanningApi.LiveScanResourceType.RESULT,
                "scan-uuid-123"
            )
        }
    }

    @Test
    fun testStoreLiveScan() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.storeLiveScan(
            "scanner-1",
            "scan-uuid-123",
            Visibility.PUBLIC
        )

        assertTrue(result.contains("success"))
    }

    @Test
    fun testStoreLiveScanDifferentVisibility() = runTest {
        val mockClient1 = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true, "visibility": "public"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }

        val mockClient2 = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true, "visibility": "unlisted"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }

        val api1 = createLiveScanningApi(httpClient = mockClient1)
        val api2 = createLiveScanningApi(httpClient = mockClient2)

        val resultPublic = api1.storeLiveScan("scanner-1", "scan-uuid-1", Visibility.PUBLIC)
        val resultUnlisted = api2.storeLiveScan("scanner-1", "scan-uuid-2", Visibility.UNLISTED)

        assertTrue(resultPublic.contains("public"))
        assertTrue(resultUnlisted.contains("unlisted"))
    }

    @Test
    fun testPurgeLiveScan() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true, "message": "Scan purged"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.purgeLiveScan("scanner-1", "scan-uuid-123")

        assertTrue(result.contains("purged"))
    }

    @Test
    fun testTriggerLiveScanAuthenticationError() = runTest {
        val request = createLiveScanRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            liveScanningApi.triggerLiveScan("scanner-1", request)
        }
    }

    @Test
    fun testTriggerLiveScanNonBlockingForbiddenError() = runTest {
        val request = createLiveScanRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            liveScanningApi.triggerLiveScanNonBlocking("scanner-1", request)
        }
    }

    @Test
    fun testGetLiveScannersAuthenticationError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            liveScanningApi.getLiveScanners()
        }
    }

    @Test
    fun testGetLiveScanResultNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Scan not found"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            liveScanningApi.getLiveScanResourceAsString(
                "scanner-1",
                LiveScanningApi.LiveScanResourceType.RESULT,
                "nonexistent-scan"
            )
        }
    }

    @Test
    fun testStoreLiveScanNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Scan not found"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            liveScanningApi.storeLiveScan("scanner-1", "nonexistent-scan", Visibility.PUBLIC)
        }
    }

    @Test
    fun testPurgeLiveScanNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Scan not found"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            liveScanningApi.purgeLiveScan("scanner-1", "nonexistent-scan")
        }
    }

    @Test
    fun testTriggerLiveScanRateLimitError() = runTest {
        val request = createLiveScanRequest()
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"error": "Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("180")
                )
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            liveScanningApi.triggerLiveScan("scanner-1", request)
        }
        assertEquals(180L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetLiveScannersServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            liveScanningApi.getLiveScanners()
        }
    }

    @Test
    fun testStoreLiveScanServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            liveScanningApi.storeLiveScan("scanner-1", "scan-uuid-123", Visibility.PUBLIC)
        }
    }

    @Test
    fun testPurgeLiveScanServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            liveScanningApi.purgeLiveScan("scanner-1", "scan-uuid-123")
        }
    }

    @Test
    fun testGetLiveScannersServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            liveScanningApi.getLiveScanners()
        }
    }

    @Test
    fun testTriggerLiveScanBadRequest() = runTest {
        val request = createLiveScanRequest(url = "invalid-url")
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Invalid URL"}"""
        )
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            liveScanningApi.triggerLiveScan("scanner-1", request)
        }
    }

    @Test
    fun testGetLiveScannersWithLoadBalancing() = runTest {
        val scanners = listOf(
            createLiveScannerInfo(id = "scanner-1", loadPercentage = 25),
            createLiveScannerInfo(id = "scanner-2", loadPercentage = 50),
            createLiveScannerInfo(id = "scanner-3", loadPercentage = 75),
            createLiveScannerInfo(id = "scanner-4", loadPercentage = 90)
        )

        val mockClient = createMockHttpClient(responseData = scanners)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertEquals(4, result.size)
        val sortedByLoad = result.sortedBy { it.loadPercentage }
        assertEquals("scanner-1", sortedByLoad[0].id)
        assertEquals("scanner-4", sortedByLoad[3].id)
    }

    @Test
    fun testGetLiveScannersWithMultipleLocations() = runTest {
        val locations = listOf("US-East", "US-West", "EU-West", "APAC-East", "APAC-Southeast")
        val scanners = locations.mapIndexed { index, location ->
            createLiveScannerInfo(
                id = "scanner-${index + 1}",
                location = location
            )
        }

        val mockClient = createMockHttpClient(responseData = scanners)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertEquals(5, result.size)
        assertTrue(result.any { it.location == "US-East" })
        assertTrue(result.any { it.location == "APAC-Southeast" })
    }

    @Test
    fun testTriggerLiveScanWithExtraHeaders() = runTest {
        val request = createLiveScanRequest(
            url = "https://api.example.com",
            extraHeaders = mapOf(
                "Authorization" to "Bearer token123",
                "X-Custom-Header" to "custom-value",
                "User-Agent" to "Custom-Scanner"
            )
        )
        val response = createLiveScanResponse("scan-with-headers")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("scan-with-headers", result.uuid)
    }

    @Test
    fun testTriggerLiveScanWithFeatureControl() = runTest {
        val request = createLiveScanRequest(
            url = "https://example.com",
            enableFeatures = listOf("javascript", "cookies"),
            disableFeatures = listOf("flash", "plugins")
        )
        val response = createLiveScanResponse("scan-features")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("scan-features", result.uuid)
    }

    @Test
    fun testGetLiveScanResultJsonContent() = runTest {
        val resultJson = """{
            "status": "complete",
            "score": 85,
            "categories": ["phishing", "malware"],
            "timestamp": "2025-01-20T12:00:00Z"
        }"""

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = resultJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsString(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.RESULT,
            "scan-uuid-123"
        )

        assertTrue(result.contains("phishing"))
        assertTrue(result.contains("score"))
    }

    @Test
    fun testGetLiveScanDomComplexHtml() = runTest {
        val domContent = """<html>
            <head><title>Example Page</title></head>
            <body>
                <div id="main">
                    <h1>Welcome</h1>
                    <form id="loginForm">
                        <input type="text" id="username" />
                        <input type="password" id="password" />
                        <button type="submit">Login</button>
                    </form>
                </div>
            </body>
        </html>"""

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = domContent,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/html"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsString(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.DOM,
            "scan-uuid-123"
        )

        assertTrue(result.contains("loginForm"))
        assertTrue(result.contains("Example Page"))
    }

    @Test
    fun testGetLiveScanScreenshotBinaryData() = runTest {
        val pngHeader = createPngBytes()
        val mockClient = createMockBinaryResponse(pngHeader)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanResourceAsBinary(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.SCREENSHOT,
            "scan-uuid-123"
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testTriggerLiveScanWorkflow() = runTest {
        val request = createLiveScanRequest(url = "https://example.com")
        val scanResponse = createLiveScanResponse("scan-workflow-123")

        val mockClient1 = createMockHttpClient(responseData = scanResponse)
        val api1 = createLiveScanningApi(httpClient = mockClient1)

        val triggered = api1.triggerLiveScan("scanner-1", request)
        assertEquals("scan-workflow-123", triggered.uuid)

        val resultJson = """{"status": "complete", "verdict": "malicious"}"""
        val mockClient2 = HttpClient(MockEngine { request ->
            respond(
                content = resultJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val api2 = createLiveScanningApi(httpClient = mockClient2)

        val result = api2.getLiveScanResourceAsString(
            "scanner-1",
            LiveScanningApi.LiveScanResourceType.RESULT,
            triggered.uuid
        )
        assertTrue(result.contains("malicious"))

        val mockClient3 = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val api3 = createLiveScanningApi(httpClient = mockClient3)

        val stored = api3.storeLiveScan("scanner-1", triggered.uuid, Visibility.UNLISTED)
        assertTrue(stored.contains("success"))
    }

    @Test
    fun testGetLiveScannersWithDifferentStatuses() = runTest {
        val scanners = listOf(
            createLiveScannerInfo(id = "scanner-1", status = "active"),
            createLiveScannerInfo(id = "scanner-2", status = "active"),
            createLiveScannerInfo(id = "scanner-3", status = "degraded"),
            createLiveScannerInfo(id = "scanner-4", status = "maintenance")
        )

        val mockClient = createMockHttpClient(responseData = scanners)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        val activeCount = result.count { it.status == "active" }
        assertEquals(2, activeCount)
    }

    @Test
    fun testTriggerLiveScanWithTimeoutConfig() = runTest {
        val request = createLiveScanRequest(
            url = "https://example.com",
            pageTimeout = 45000,
            captureDelay = 3000
        )
        val response = createLiveScanResponse("scan-timeout-config")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("scan-timeout-config", result.uuid)
    }

    @Test
    fun testResourceTypeEnum() = runTest {
        val allTypes = listOf(
            LiveScanningApi.LiveScanResourceType.RESULT,
            LiveScanningApi.LiveScanResourceType.SCREENSHOT,
            LiveScanningApi.LiveScanResourceType.DOM,
            LiveScanningApi.LiveScanResourceType.RESPONSE,
            LiveScanningApi.LiveScanResourceType.DOWNLOAD
        )

        assertEquals(5, allTypes.size)

        // Verify text resources
        assertFalse(LiveScanningApi.LiveScanResourceType.RESULT.isBinary)
        assertFalse(LiveScanningApi.LiveScanResourceType.DOM.isBinary)

        // Verify binary resources
        assertTrue(LiveScanningApi.LiveScanResourceType.SCREENSHOT.isBinary)
        assertTrue(LiveScanningApi.LiveScanResourceType.RESPONSE.isBinary)
        assertTrue(LiveScanningApi.LiveScanResourceType.DOWNLOAD.isBinary)
    }

    @Test
    fun testTriggerLiveScanNonBlockingMultipleScanners() = runTest {
        val request = createLiveScanRequest()
        val scanners = listOf("scanner-1", "scanner-2", "scanner-3")
        val responses = listOf(
            createLiveScanResponse("scan-1"),
            createLiveScanResponse("scan-2"),
            createLiveScanResponse("scan-3")
        )

        val results = scanners.mapIndexed { index, scanner ->
            val mockClient = createMockHttpClient(responseData = responses[index])
            val api = createLiveScanningApi(httpClient = mockClient)
            api.triggerLiveScanNonBlocking(scanner, request)
        }

        assertEquals(3, results.size)
        assertEquals("scan-1", results[0].uuid)
        assertEquals("scan-3", results[2].uuid)
    }

    @Test
    fun testStoreLiveScanAndPurgeWorkflow() = runTest {
        val scanId = "scan-workflow-store-purge"

        // Store scan
        val mockClient1 = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true, "message": "Stored"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val api1 = createLiveScanningApi(httpClient = mockClient1)

        val stored = api1.storeLiveScan("scanner-1", scanId, Visibility.PUBLIC)
        assertTrue(stored.contains("Stored"))

        // Purge scan
        val mockClient2 = HttpClient(MockEngine { request ->
            respond(
                content = """{"success": true, "message": "Purged"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("application/json"))
            )
        }) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            installExceptionHandling()
        }
        val api2 = createLiveScanningApi(httpClient = mockClient2)

        val purged = api2.purgeLiveScan("scanner-1", scanId)
        assertTrue(purged.contains("Purged"))
    }

    @Test
    fun testGetLiveScannersDeserialization() = runTest {
        val originalScanners = listOf(
            createLiveScannerInfo(
                id = "scanner-test-123",
                location = "Test-Location",
                status = "active",
                loadPercentage = 55,
                supportedCountries = listOf("US", "UK", "DE", "IT"),
                features = listOf("javascript", "cookies", "console", "storage"),
                lastHealthCheck = "2025-01-20T14:30:00Z"
            )
        )

        val mockClient = createMockHttpClient(responseData = originalScanners)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.getLiveScanners()

        assertEquals(1, result.size)
        val scanner = result[0]
        assertEquals("scanner-test-123", scanner.id)
        assertEquals("Test-Location", scanner.location)
        assertEquals("active", scanner.status)
        assertEquals(55, scanner.loadPercentage)
        assertEquals(4, scanner.supportedCountries?.size)
        assertEquals(4, scanner.features?.size)
        assertEquals("2025-01-20T14:30:00Z", scanner.lastHealthCheck)
    }

    @Test
    fun testLiveScanResponseDeserialization() = runTest {
        val originalResponse = createLiveScanResponse("test-uuid-12345")
        val mockClient = createMockHttpClient(responseData = originalResponse)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val request = createLiveScanRequest()
        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("test-uuid-12345", result.uuid)
    }

    @Test
    fun testTriggerScanWithAllScannerConfig() = runTest {
        val request = createLiveScanRequest(
            url = "https://complex-test.com",
            visibility = Visibility.PRIVATE,
            pageTimeout = 60000,
            captureDelay = 10000,
            extraHeaders = mapOf(
                "Authorization" to "Bearer token",
                "X-Custom" to "value"
            ),
            enableFeatures = listOf("js", "cookies", "storage"),
            disableFeatures = listOf("flash", "plugins")
        )
        val response = createLiveScanResponse("complex-scan")

        val mockClient = createMockHttpClient(responseData = response)
        val liveScanningApi = createLiveScanningApi(httpClient = mockClient)

        val result = liveScanningApi.triggerLiveScan("scanner-1", request)

        assertEquals("complex-scan", result.uuid)
    }
}