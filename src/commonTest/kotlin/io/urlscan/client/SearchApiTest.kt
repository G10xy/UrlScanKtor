package io.urlscan.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.urlscan.client.exception.ApiException
import io.urlscan.client.exception.AuthenticationException
import io.urlscan.client.exception.NotFoundException
import io.urlscan.client.exception.RateLimitException
import io.urlscan.client.model.Page
import io.urlscan.client.model.SearchResponse
import io.urlscan.client.model.SearchResult
import io.urlscan.client.model.Stats
import io.urlscan.client.model.Task
import io.urlscan.client.model.UrlscanVerdict
import io.urlscan.client.model.Verdicts
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createSearchResult(
        id: String = "scan-uuid-1",
        domain: String = "example.com",
        url: String = "https://example.com",
        malicious: Boolean = false,
        country: String = "US"
    ): SearchResult {
        return SearchResult(
            task = Task(
                visibility = "public",
                method = "GET",
                domain = domain,
                apexDomain = domain,
                time = "2025-01-01T00:00:00.000Z",
                uuid = id,
                url = url
            ),
            page = Page(
                country = country,
                server = "Apache",
                ip = "1.2.3.4",
                mimeType = "text/html",
                title = "Example Domain",
                url = url,
                domain = domain,
                apexDomain = domain,
                asnname = "Example ASN",
                asn = "AS12345",
                status = 200
            ),
            stats = Stats(
                uniqIPs = 1,
                uniqCountries = 1,
                dataLength = 1024,
                encodedDataLength = 512,
                requests = 10
            ),
            verdicts = Verdicts(
                score = if (malicious) 75 else 0,
                malicious = malicious,
                urlscan = UrlscanVerdict(malicious = malicious)
            ),
            result = "https://urlscan.io/result/$id/",
            screenshot = "https://urlscan.io/screenshots/$id.png",
            id = id,
            score = if (malicious) 75 else 0,
            sort = listOf(1704067200000L)
        )
    }

    private fun createSearchResponse(
        results: List<SearchResult> = emptyList(),
        total: Int = 0,
        took: Int = 10,
        hasMore: Boolean = false
    ): SearchResponse {
        return SearchResponse(
            results = results,
            total = total,
            took = took,
            hasMore = hasMore
        )
    }

    /**
     * Helper to create mock HTTP client that returns serialized DTO responses
     */
    private fun createMockHttpClient(
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        responseData: SearchResponse = createSearchResponse()
    ) = io.ktor.client.HttpClient(
        MockEngine { request ->
            respond(
                content = json.encodeToString(responseData),
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    ) {
        // ContentNegotiation to enable JSON deserialization
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private fun createSearchApi(
        httpClient: io.ktor.client.HttpClient = createMockHttpClient(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): SearchApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return SearchApi(httpClient, config)
    }

    @Test
    fun testSearchWithBasicQuery() = runTest {
        val searchResult = createSearchResult(
            id = "scan-uuid-1",
            domain = "example.com",
            malicious = false
        )
        val searchResponse = createSearchResponse(
            results = listOf(searchResult),
            total = 1,
            took = 50,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search("domain:example.com")

        assertEquals(1, response.total)
        assertEquals(1, response.results.size)
        assertEquals("scan-uuid-1", response.results[0].id)
        assertEquals("example.com", response.results[0].page.domain)
        assertFalse(response.hasMore)
    }

    @Test
    fun testSearchWithSizeParameter() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 100,
            took = 25,
            hasMore = true
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search(
            q = "domain:test.com",
            size = 50
        )

        assertEquals(100, response.total)
        assertTrue(response.hasMore)
    }

    @Test
    fun testSearchWithPagination() = runTest {
        val searchResult = createSearchResult(
            id = "scan-uuid-2",
            domain = "example2.com",
            url = "https://example2.com"
        )
        val searchResponse = createSearchResponse(
            results = listOf(searchResult),
            total = 2,
            took = 30,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search(
            q = "domain:test.com",
            size = 10,
            searchAfter = "1704067200000"
        )

        assertEquals(2, response.total)
        assertEquals(1, response.results.size)
        assertEquals("scan-uuid-2", response.results[0].id)
    }

    @Test
    fun testSearchWithDatasourceFilter() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 0,
            took = 15,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search(
            q = "domain:example.com",
            datasource = "scans"
        )

        assertEquals(0, response.total)
    }

    @Test
    fun testGetSimilarScans() = runTest {
        val searchResult = createSearchResult(
            id = "scan-uuid-3",
            domain = "similar.com",
            url = "https://similar.com"
        )
        val searchResponse = createSearchResponse(
            results = listOf(searchResult),
            total = 1,
            took = 40,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.getSimilarScans(
            scanId = "original-scan-uuid",
            q = "domain:example.com"
        )

        assertEquals(1, response.total)
        assertEquals("scan-uuid-3", response.results[0].id)
    }

    @Test
    fun testGetSimilarScansWithPagination() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 50,
            took = 35,
            hasMore = true
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.getSimilarScans(
            scanId = "original-scan-uuid",
            size = 20,
            searchAfter = "1704067200000"
        )

        assertEquals(50, response.total)
        assertTrue(response.hasMore)
    }

    @Test
    fun testSearchWithAllParameters() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 5,
            took = 60,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search(
            q = "domain:example.com country:US",
            size = 100,
            searchAfter = "1704067200000",
            datasource = "scans"
        )

        assertEquals(5, response.total)
        assertFalse(response.hasMore)
    }

    @Test
    fun testSearchWithMultipleMaliciousResults() = runTest {
        val results = listOf(
            createSearchResult(id = "scan-1", domain = "phishing1.com", malicious = true),
            createSearchResult(id = "scan-2", domain = "phishing2.com", malicious = true),
            createSearchResult(id = "scan-3", domain = "phishing3.com", malicious = true)
        )
        val searchResponse = createSearchResponse(
            results = results,
            total = 3,
            took = 45,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search("verdicts.urlscan.malicious:true")

        assertEquals(3, response.total)
        assertTrue(response.results.all { it.verdicts?.malicious ?: false })
    }

    @Test
    fun testSearchWithMixedResults() = runTest {
        val results = listOf(
            createSearchResult(id = "scan-1", domain = "benign.com", malicious = false, country = "US"),
            createSearchResult(id = "scan-2", domain = "suspicious.com", malicious = true, country = "RU"),
            createSearchResult(id = "scan-3", domain = "safe.com", malicious = false, country = "DE")
        )
        val searchResponse = createSearchResponse(
            results = results,
            total = 3,
            took = 55,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search("domain:*.com")

        assertEquals(3, response.total)
        assertEquals(1, response.results.count { it.verdicts!!.malicious })
        assertEquals(2, response.results.count { !it.verdicts!!.malicious })
    }

    @Test
    fun testSearchAuthenticationError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val searchApi = createSearchApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            searchApi.search("domain:example.com")
        }
    }

    @Test
    fun testSearchForbiddenError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Forbidden"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val searchApi = createSearchApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            searchApi.search("domain:example.com")
        }
    }

    @Test
    fun testSearchNotFoundError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Not Found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val searchApi = createSearchApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            searchApi.search("domain:nonexistent.com")
        }
    }

    @Test
    fun testSearchRateLimitError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Too Many Requests"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        "Retry-After", "60"
                    )
                )
            }
        )
        val searchApi = createSearchApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            searchApi.search("domain:example.com")
        }
        assertEquals(60L, exception.retryAfterSeconds)
    }

    @Test
    fun testSearchServerError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Internal Server Error"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        val searchApi = createSearchApi(httpClient = mockClient)

        assertFailsWith<io.urlscan.client.exception.ApiException> {
            searchApi.search("domain:example.com")
        }
    }

    @Test
    fun testSearchEmptyResults() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 0,
            took = 10,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search("domain:nonexistent-domain-xyz.com")

        assertEquals(0, response.total)
        assertTrue(response.results.isEmpty())
        assertFalse(response.hasMore)
    }

    @Test
    fun testSearchWithComplexElasticsearchQuery() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 1,
            took = 75,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val complexQuery = "domain:example.com AND country:US AND verdicts.urlscan.malicious:true"
        val response = searchApi.search(q = complexQuery)

        assertEquals(1, response.total)
    }

    @Test
    fun testGetSimilarScansWithoutOptionalParameters() = runTest {
        val searchResponse = createSearchResponse(
            results = emptyList(),
            total = 0,
            took = 20,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.getSimilarScans(scanId = "test-scan-uuid")

        assertEquals(0, response.total)
    }

    @Test
    fun testSearchResultDeserializationIntegrity() = runTest {
        val originalResult = createSearchResult(
            id = "test-scan-123",
            domain = "test.example.com",
            url = "https://test.example.com/path",
            malicious = true,
            country = "CN"
        )
        val searchResponse = createSearchResponse(
            results = listOf(originalResult),
            total = 1,
            took = 42,
            hasMore = false
        )

        val mockClient = createMockHttpClient(responseData = searchResponse)
        val searchApi = createSearchApi(httpClient = mockClient)

        val response = searchApi.search("domain:test.example.com")

        val result = response.results[0]
        assertEquals("test-scan-123", result.id)
        assertEquals("https://test.example.com/path", result.page.url)
        assertEquals("CN", result.page.country)
        assertEquals(true, result.verdicts!!.malicious)
        assertEquals(42, response.took)
    }
}