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
import io.urlscan.client.model.ScanRequest
import io.urlscan.client.model.ScanResponse
import io.urlscan.client.model.ScanResult
import io.urlscan.client.model.ScanData
import io.urlscan.client.model.ScanStats
import io.urlscan.client.model.ScanMeta
import io.urlscan.client.model.ScanTask
import io.urlscan.client.model.ScanPage
import io.urlscan.client.model.ScanLists
import io.urlscan.client.model.ScanVerdicts
import io.urlscan.client.model.VerdictDetails
import io.urlscan.client.model.EngineVerdicts
import io.urlscan.client.model.CommunityVerdicts
import io.urlscan.client.model.UserAgentsResponse
import io.urlscan.client.model.UserAgentGroup
import io.urlscan.client.model.Visibility
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ScanningApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createScanRequest(
        url: String = "https://example.com",
        visibility: Visibility = Visibility.PUBLIC,
        country: String? = null,
        tags: List<String>? = null,
        overrideSafety: Boolean? = null,
        referer: String? = null,
        customagent: String? = null
    ): ScanRequest {
        return ScanRequest(
            url = url,
            visibility = visibility,
            country = country,
            tags = tags,
            overrideSafety = overrideSafety,
            referer = referer,
            customagent = customagent
        )
    }

    private fun createScanResponse(
        uuid: String = "scan-uuid-123",
        url: String = "https://example.com",
        visibility: String = "public",
        country: String = "US"
    ): ScanResponse {
        return ScanResponse(
            uuid = uuid,
            visibility = visibility,
            url = url,
            country = country
        )
    }

    private fun createScanResult(
        uuid: String = "scan-result-123",
        url: String = "https://example.com",
        status: Int = 200,
        malicious: Boolean = false
    ): ScanResult {
        return ScanResult(
            data = ScanData(),
            stats = ScanStats(),
            meta = ScanMeta(),
            task = ScanTask(
                uuid = uuid,
                time = "2025-01-20T12:00:00Z",
                url = url,
                visibility = "public",
                method = "GET",
                reportURL = "https://urlscan.io/result/$uuid/",
                screenshotURL = "https://urlscan.io/screenshots/$uuid.png",
                domURL = "https://urlscan.io/dom/$uuid/"
            ),
            page = ScanPage(
                url = url,
                domain = "example.com",
                ip = "1.2.3.4",
                country = "US",
                server = "Apache"
            ),
            lists = ScanLists(),
            verdicts = ScanVerdicts(
                overall = VerdictDetails(
                    score = if (malicious) 75 else 0,
                    malicious = malicious,
                    hasVerdicts = true
                ),
                urlscan = VerdictDetails(
                    score = if (malicious) 75 else 0,
                    malicious = malicious,
                    hasVerdicts = true
                ),
                engines = EngineVerdicts(
                    score = if (malicious) 75 else 0,
                    malicious = malicious,
                    enginesTotal = 60,
                    maliciousTotal = if (malicious) 30 else 0,
                    benignTotal = 0,
                    hasVerdicts = false
                ),
                community = CommunityVerdicts(
                    score = 0,
                    malicious = false,
                    votesBenign = 5,
                    votesMalicious = 0,
                    votesTotal = 5,
                    hasVerdicts = true
                )
            )
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

    private fun createMockBinaryResponse(
        binary: ByteArray,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
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

    private fun createScanningApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): ScanningApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return ScanningApi(httpClient, config)
    }

    private fun createPngBytes(): ByteArray {
        // PNG file signature
        return byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
    }

    @Test
    fun testSubmitScanBasic() = runTest {
        val request = createScanRequest(url = "https://example.com")
        val response = createScanResponse(url = "https://example.com")

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertEquals("scan-uuid-123", result.uuid)
        assertEquals("https://example.com", result.url)
        assertEquals("public", result.visibility)
    }

    @Test
    fun testSubmitScanWithAllParameters() = runTest {
        val request = createScanRequest(
            url = "https://test.com",
            visibility = Visibility.PRIVATE,
            country = "IT",
            tags = listOf("phishing", "urgent"),
            overrideSafety = true,
            referer = "https://referrer.com",
            customagent = "Custom-Agent/1.0"
        )
        val response = createScanResponse(
            uuid = "scan-custom-123",
            url = "https://test.com",
            visibility = "private",
            country = "IT"
        )

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertEquals("scan-custom-123", result.uuid)
        assertEquals("IT", result.country)
        assertEquals("private", result.visibility)
    }

    @Test
    fun testSubmitScanWithDifferentVisibility() = runTest {
        val publicRequest = createScanRequest(visibility = Visibility.PUBLIC)
        val unlistedRequest = createScanRequest(visibility = Visibility.UNLISTED)
        val privateRequest = createScanRequest(visibility = Visibility.PRIVATE)

        val mockClient1 = createMockHttpClient(responseData = createScanResponse(visibility = "public"))
        val mockClient2 = createMockHttpClient(responseData = createScanResponse(visibility = "unlisted"))
        val mockClient3 = createMockHttpClient(responseData = createScanResponse(visibility = "private"))

        val api1 = createScanningApi(httpClient = mockClient1)
        val api2 = createScanningApi(httpClient = mockClient2)
        val api3 = createScanningApi(httpClient = mockClient3)

        val resultPublic = api1.submitScan(publicRequest)
        val resultUnlisted = api2.submitScan(unlistedRequest)
        val resultPrivate = api3.submitScan(privateRequest)

        assertEquals("public", resultPublic.visibility)
        assertEquals("unlisted", resultUnlisted.visibility)
        assertEquals("private", resultPrivate.visibility)
    }

    @Test
    fun testSubmitScanWithTags() = runTest {
        val request = createScanRequest(
            url = "https://tagged.com",
            tags = listOf("malware", "phishing", "urgent", "high-priority")
        )
        val response = createScanResponse(url = "https://tagged.com")

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertNotNull(result.uuid)
    }

    @Test
    fun testSubmitScanWithCustomUserAgent() = runTest {
        val customAgent = "Mozilla/5.0 (Custom Agent)"
        val request = createScanRequest(
            url = "https://example.com",
            customagent = customAgent
        )
        val response = createScanResponse()

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertNotNull(result.uuid)
    }

    @Test
    fun testGetResult() = runTest {
        val scanResult = createScanResult(
            uuid = "result-uuid-123",
            url = "https://example.com",
            status = 200,
            malicious = false
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("result-uuid-123")

        assertEquals("https://example.com", result.page.url)
        assertFalse(result.verdicts.overall.malicious)
    }

    @Test
    fun testGetResultMalicious() = runTest {
        val scanResult = createScanResult(
            uuid = "malicious-scan-123",
            url = "https://malicious.com",
            malicious = true
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("malicious-scan-123")

        assertTrue(result.verdicts.overall.malicious)
        assertEquals(75, result.verdicts.overall.score)
    }

    @Test
    fun testGetScreenshot() = runTest {
        val pngBytes = createPngBytes()
        val mockClient = createMockBinaryResponse(pngBytes)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getScreenshot("scan-uuid-123")

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testGetDom() = runTest {
        val domHtml = """<html><body><h1>Test Page</h1></body></html>"""

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
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getDom("scan-uuid-123")

        assertTrue(result.contains("<html>"))
        assertTrue(result.contains("Test Page"))
    }

    @Test
    fun testGetDomComplexContent() = runTest {
        val domHtml = """
            <html>
                <head><title>Complex Page</title></head>
                <body>
                    <div id="main">
                        <form id="loginForm">
                            <input type="text" id="username" placeholder="Username" />
                            <input type="password" id="password" placeholder="Password" />
                            <button type="submit">Login</button>
                        </form>
                    </div>
                    <script>
                        console.log('Page loaded');
                    </script>
                </body>
            </html>
        """.trimIndent()

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
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getDom("scan-uuid-123")

        assertTrue(result.contains("loginForm"))
        assertTrue(result.contains("console.log"))
    }

    @Test
    fun testGetAvailableCountries() = runTest {
        val response = listOf("US", "UK", "DE", "FR", "JP", "AU", "CA", "IT", "BR", "MX")

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getAvailableCountries()

        assertEquals(10, result.size)
        assertTrue(result.contains("IT"))
        assertTrue(result.contains("JP"))
    }

    @Test
    fun testGetAvailableCountriesExtensive() = runTest {
        val response = (1..50).map { "COUNTRY-$it" }
        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getAvailableCountries()

        assertEquals(50, result.size)
    }

    @Test
    fun testGetUserAgents() = runTest {
        val userAgents = listOf(
            UserAgentGroup(
                group = "Chrome",
                useragents = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/91.0.4472.124",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/91.0.4472.124"
                )
            ),
            UserAgentGroup(
                group = "Firefox",
                useragents = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
                    "Mozilla/5.0 (X11; Linux x86_64; rv:89.0) Gecko/20100101 Firefox/89.0"
                )
            )
        )
        val response = UserAgentsResponse(userAgents = userAgents)

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getUserAgents()

        assertEquals(2, result.userAgents.size)
        assertEquals("Chrome", result.userAgents[0].group)
        assertEquals(2, result.userAgents[0].useragents.size)
    }

    @Test
    fun testGetUserAgentsMultipleGroups() = runTest {
        val userAgents = listOf(
            UserAgentGroup(group = "Chrome", useragents = listOf("Chrome UA 1", "Chrome UA 2")),
            UserAgentGroup(group = "Firefox", useragents = listOf("Firefox UA 1")),
            UserAgentGroup(group = "Safari", useragents = listOf("Safari UA 1", "Safari UA 2", "Safari UA 3")),
            UserAgentGroup(group = "Edge", useragents = listOf("Edge UA 1"))
        )
        val response = UserAgentsResponse(userAgents = userAgents)

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getUserAgents()

        assertEquals(4, result.userAgents.size)
        assertTrue(result.userAgents.any { it.group == "Safari" })
    }

    @Test
    fun testSubmitScanAuthenticationError() = runTest {
        val request = createScanRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            scanningApi.submitScan(request)
        }
    }

    @Test
    fun testSubmitScanForbiddenError() = runTest {
        val request = createScanRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            scanningApi.submitScan(request)
        }
    }

    @Test
    fun testGetResultNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Result not found"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            scanningApi.getResult("nonexistent-scan")
        }
    }

    @Test
    fun testGetScreenshotNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Screenshot not found"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            scanningApi.getScreenshot("nonexistent-scan")
        }
    }

    @Test
    fun testGetDomNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "DOM not found"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            scanningApi.getDom("nonexistent-scan")
        }
    }

    @Test
    fun testSubmitScanRateLimitError() = runTest {
        val request = createScanRequest()
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"error": "Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("300")
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
        val scanningApi = createScanningApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            scanningApi.submitScan(request)
        }
        assertEquals(300L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetResultServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            scanningApi.getResult("scan-uuid-123")
        }
    }

    @Test
    fun testGetAvailableCountriesServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            scanningApi.getAvailableCountries()
        }
    }

    @Test
    fun testSubmitScanBadRequest() = runTest {
        val request = createScanRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Invalid URL"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            scanningApi.submitScan(request)
        }
    }

    @Test
    fun testGetUserAgentsServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val scanningApi = createScanningApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            scanningApi.getUserAgents()
        }
    }

    @Test
    fun testSubmitScanWithMultipleCountries() = runTest {
        val countries = listOf("US", "UK", "OT", "FR")

        countries.forEach { country ->
            val request = createScanRequest(
                url = "https://test-$country.com",
                country = country
            )
            val response = createScanResponse(
                uuid = "scan-$country",
                url = "https://test-$country.com",
                country = country
            )
            val mockClient = createMockHttpClient(responseData = response)
            val api = createScanningApi(httpClient = mockClient)

            val result = api.submitScan(request)
            assertEquals(country, result.country)
        }
    }

    @Test
    fun testGetResultWithCompleteData() = runTest {
        val scanResult = createScanResult(
            uuid = "complete-result-123",
            url = "https://complete-test.com",
            status = 200,
            malicious = false
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("complete-result-123")

        assertNotNull(result.task)
        assertNotNull(result.page)
        assertNotNull(result.verdicts)
        assertEquals("https://complete-test.com", result.page.url)
    }

    @Test
    fun testSubmitScanWithReferer() = runTest {
        val request = createScanRequest(
            url = "https://example.com",
            referer = "https://source.com/page"
        )
        val response = createScanResponse()

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertNotNull(result.uuid)
    }

    @Test
    fun testSubmitScanWithOverrideSafety() = runTest {
        val request = createScanRequest(
            url = "https://example.com",
            overrideSafety = true
        )
        val response = createScanResponse()

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertNotNull(result.uuid)
    }

    @Test
    fun testGetResultVerdicts() = runTest {
        val scanResult = createScanResult(
            malicious = true
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("scan-uuid-123")

        assertTrue(result.verdicts.overall.malicious)
        assertTrue(result.verdicts.urlscan.malicious)
        assertTrue(result.verdicts.engines.malicious)
        assertFalse(result.verdicts.community.malicious)
    }

    @Test
    fun testGetResultEngineVerdicts() = runTest {
        val scanResult = createScanResult(malicious = true)

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("scan-uuid-123")

        assertEquals(60, result.verdicts.engines.enginesTotal)
        assertEquals(30, result.verdicts.engines.maliciousTotal)
    }

    @Test
    fun testScanWorkflow() = runTest {
        // Submit scan
        val submitRequest = createScanRequest(url = "https://workflow-test.com")
        val submitResponse = createScanResponse(
            uuid = "workflow-scan-123",
            url = "https://workflow-test.com"
        )
        val mockClient1 = createMockHttpClient(responseData = submitResponse)
        val api1 = createScanningApi(httpClient = mockClient1)

        val submitted = api1.submitScan(submitRequest)
        assertEquals("workflow-scan-123", submitted.uuid)

        // Get result
        val scanResult = createScanResult(
            uuid = "workflow-scan-123",
            url = "https://workflow-test.com"
        )
        val mockClient2 = createMockHttpClient(responseData = scanResult)
        val api2 = createScanningApi(httpClient = mockClient2)

        val result = api2.getResult("workflow-scan-123")
        assertEquals("https://workflow-test.com", result.page.url)

        // Get screenshot
        val pngBytes = createPngBytes()
        val mockClient3 = createMockBinaryResponse(pngBytes)
        val api3 = createScanningApi(httpClient = mockClient3)

        val screenshot = api3.getScreenshot("workflow-scan-123")
        assertTrue(screenshot.isNotEmpty())
    }

    @Test
    fun testScanResponseDeserialization() = runTest {
        val originalResponse = createScanResponse(
            uuid = "deser-uuid-123",
            url = "https://deserialization-test.com",
            visibility = "unlisted",
            country = "DE"
        )
        val mockClient = createMockHttpClient(responseData = originalResponse)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val request = createScanRequest()
        val result = scanningApi.submitScan(request)

        assertEquals("deser-uuid-123", result.uuid)
        assertEquals("https://deserialization-test.com", result.url)
        assertEquals("unlisted", result.visibility)
        assertEquals("DE", result.country)
    }

    @Test
    fun testScanResultDeserialization() = runTest {
        val originalResult = createScanResult(
            uuid = "result-deser-123",
            url = "https://result-test.com",
            status = 200,
            malicious = false
        )
        val mockClient = createMockHttpClient(responseData = originalResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("result-deser-123")

        assertEquals("result-deser-123", result.task.uuid)
        assertEquals("https://result-test.com", result.page.url)
        assertFalse(result.verdicts.overall.malicious)
    }

    @Test
    fun testGetUserAgentsEmpty() = runTest {
        val response = UserAgentsResponse(userAgents = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getUserAgents()

        assertTrue(result.userAgents.isEmpty())
    }

    @Test
    fun testSubmitScanDifferentUrls() = runTest {
        val urls = listOf(
            "https://example.com",
            "https://test.org",
            "https://mysite.net",
            "https://example.co.uk"
        )

        urls.forEach { url ->
            val request = createScanRequest(url = url)
            val response = createScanResponse(url = url)
            val mockClient = createMockHttpClient(responseData = response)
            val api = createScanningApi(httpClient = mockClient)

            val result = api.submitScan(request)
            assertEquals(url, result.url)
        }
    }

    @Test
    fun testGetDomLargeContent() = runTest {
        val largeHtml = """
            <html>
                <head><title>Large Page</title></head>
                <body>
                    ${"<div>Content block</div>".repeat(1000)}
                </body>
            </html>
        """.trimIndent()

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = largeHtml,
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
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getDom("scan-uuid-123")

        assertTrue(result.contains("Large Page"))
        assertTrue(result.length > 10000)
    }

    @Test
    fun testSubmitScanMultipleTags() = runTest {
        val tags = listOf("phishing", "malware", "urgent", "high-priority", "verified")
        val request = createScanRequest(
            url = "https://test.com",
            tags = tags
        )
        val response = createScanResponse()

        val mockClient = createMockHttpClient(responseData = response)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.submitScan(request)

        assertNotNull(result.uuid)
    }

    @Test
    fun testGetResultCommunityVerdicts() = runTest {
        val scanResult = createScanResult()

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("scan-uuid-123")

        assertEquals(5, result.verdicts.community.votesBenign)
        assertEquals(0, result.verdicts.community.votesMalicious)
        assertEquals(5, result.verdicts.community.votesTotal)
        assertFalse(result.verdicts.community.malicious)
    }

    @Test
    fun testGetScreenshotMultipleTimes() = runTest {
        val pngBytes = createPngBytes()

        repeat(3) {
            val mockClient = createMockBinaryResponse(pngBytes)
            val scanningApi = createScanningApi(httpClient = mockClient)

            val result = scanningApi.getScreenshot("scan-uuid-$it")

            assertTrue(result.isNotEmpty())
        }
    }

    @Test
    fun testGetResultTaskMetadata() = runTest {
        val scanResult = createScanResult(
            uuid = "task-meta-123"
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("task-meta-123")

        assertEquals("task-meta-123", result.task.uuid)
        assertEquals("GET", result.task.method)
        assertTrue(result.task.reportURL.contains("task-meta-123"))
        assertTrue(result.task.screenshotURL.contains("task-meta-123"))
    }

    @Test
    fun testGetResultPageMetadata() = runTest {
        val scanResult = createScanResult(
            url = "https://metadata-test.com"
        )

        val mockClient = createMockHttpClient(responseData = scanResult)
        val scanningApi = createScanningApi(httpClient = mockClient)

        val result = scanningApi.getResult("scan-uuid-123")

        assertEquals("https://metadata-test.com", result.page.url)
        assertEquals("example.com", result.page.domain)
        assertEquals("1.2.3.4", result.page.ip)
        assertEquals("US", result.page.country)
        assertEquals("Apache", result.page.server)
    }
}