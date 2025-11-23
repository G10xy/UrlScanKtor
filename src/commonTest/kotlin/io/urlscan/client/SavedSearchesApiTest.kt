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
import io.urlscan.client.model.SavedSearchDatasource
import io.urlscan.client.model.SavedSearchRequest
import io.urlscan.client.model.SavedSearchResponse
import io.urlscan.client.model.SavedSearchResponseWrapper
import io.urlscan.client.model.SavedSearchesListResponse
import io.urlscan.client.model.SearchResponse
import io.urlscan.client.model.SearchResult
import io.urlscan.client.model.Task
import io.urlscan.client.model.Page
import io.urlscan.client.model.Stats
import io.urlscan.client.model.TeamPermission
import io.urlscan.client.model.Tlp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SavedSearchesApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createSearchResult(
        id: String = "scan-uuid-1",
        domain: String = "example.com",
        url: String = "https://example.com",
        status: Int = 200
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
                country = "IT",
                server = "Apache",
                ip = "1.2.3.4",
                mimeType = "text/html",
                title = "Example Domain",
                url = url,
                domain = domain,
                apexDomain = domain,
                status = status
            ),
            stats = Stats(
                uniqIPs = 1,
                uniqCountries = 1,
                dataLength = 1024,
                encodedDataLength = 512,
                requests = 10
            ),
            result = "https://urlscan.io/result/$id/",
            screenshot = "https://urlscan.io/screenshots/$id.png",
            id = id,
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

    private fun createSavedSearchRequest(
        datasource: SavedSearchDatasource = SavedSearchDatasource.SCANS,
        name: String = "Test Search",
        query: String = "domain:example.com",
        description: String? = null,
        longDescription: String? = null,
        tlp: Tlp? = null,
        userTags: List<String>? = null,
        permissions: List<TeamPermission>? = null
    ): SavedSearchRequest {
        return SavedSearchRequest(
            datasource = datasource,
            name = name,
            query = query,
            description = description,
            longDescription = longDescription,
            tlp = tlp,
            userTags = userTags,
            permissions = permissions
        )
    }

    private fun createSavedSearchResponse(
        id: String = "search-1",
        datasource: SavedSearchDatasource = SavedSearchDatasource.SCANS,
        name: String = "Test Search",
        query: String = "domain:example.com",
        description: String? = null,
        longDescription: String? = null,
        tlp: Tlp? = null,
        userTags: List<String>? = null,
        permissions: List<TeamPermission>? = null,
        createdAt: String = "2025-01-01T00:00:00Z",
        ownerDescription: String? = null
    ): SavedSearchResponse {
        return SavedSearchResponse(
            id = id,
            datasource = datasource,
            createdAt = createdAt,
            name = name,
            description = description,
            longDescription = longDescription,
            ownerDescription = ownerDescription,
            query = query,
            tlp = tlp,
            userTags = userTags,
            permissions = permissions
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

    private fun createSavedSearchesApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): SavedSearchesApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return SavedSearchesApi(httpClient, config)
    }

    @Test
    fun testGetSavedSearches() = runTest {
        val searches = listOf(
            createSavedSearchResponse(
                id = "search-1",
                name = "Phishing Detection",
                query = "verdicts.urlscan.malicious:true"
            ),
            createSavedSearchResponse(
                id = "search-2",
                name = "Domain Changes",
                query = "domain:example.com"
            ),
            createSavedSearchResponse(
                id = "search-3",
                name = "Recent Scans",
                query = "task.time:[now-1d TO now]"
            )
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearches()

        assertEquals(3, result.size)
        assertEquals("search-1", result[0].id)
        assertEquals("Phishing Detection", result[0].name)
        assertEquals("search-3", result[2].id)
    }

    @Test
    fun testGetSavedSearchesEmpty() = runTest {
        val response = SavedSearchesListResponse(searches = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearches()

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateSavedSearchBasic() = runTest {
        val request = createSavedSearchRequest(
            name = "New Search",
            query = "domain:newdomain.com"
        )
        val responseSearch = createSavedSearchResponse(
            id = "search-new-1",
            name = "New Search",
            query = "domain:newdomain.com"
        )
        val wrapper = SavedSearchResponseWrapper(search = responseSearch)

        val mockClient = createMockHttpClient(responseData = wrapper)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.createSavedSearch(request)

        assertEquals("search-new-1", result.id)
        assertEquals("New Search", result.name)
        assertEquals("domain:newdomain.com", result.query)
    }

    @Test
    fun testCreateSavedSearchWithAllFields() = runTest {
        val request = createSavedSearchRequest(
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Complete Search",
            query = "hostname:*.example.com AND country:US",
            description = "Short description",
            longDescription = "This is a longer description of the saved search",
            tlp = Tlp.AMBER,
            userTags = listOf("public.important", "team.security"),
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val responseSearch = createSavedSearchResponse(
            id = "search-complete",
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Complete Search",
            query = "hostname:*.example.com AND country:IT",
            description = "Short description",
            longDescription = "This is a longer description of the saved search",
            tlp = Tlp.AMBER,
            userTags = listOf("public.important", "team.security"),
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val wrapper = SavedSearchResponseWrapper(search = responseSearch)

        val mockClient = createMockHttpClient(responseData = wrapper)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.createSavedSearch(request)

        assertEquals("search-complete", result.id)
        assertEquals(SavedSearchDatasource.HOSTNAMES, result.datasource)
        assertEquals(Tlp.AMBER, result.tlp)
        assertEquals(2, result.userTags?.size)
        assertEquals(2, result.permissions?.size)
    }

    @Test
    fun testCreateSavedSearchValidatesNameNotBlank() = runTest {
        val savedSearchesApi = createSavedSearchesApi()

        assertFailsWith<IllegalArgumentException> {
            val request = createSavedSearchRequest(name = "")
            savedSearchesApi.createSavedSearch(request)
        }
    }

    @Test
    fun testCreateSavedSearchValidatesQueryNotBlank() = runTest {
        val savedSearchesApi = createSavedSearchesApi()

        assertFailsWith<IllegalArgumentException> {
            val request = createSavedSearchRequest(query = "")
            savedSearchesApi.createSavedSearch(request)
        }
    }

    @Test
    fun testUpdateSavedSearch() = runTest {
        val request = createSavedSearchRequest(
            name = "Updated Search",
            query = "domain:updated.com"
        )
        val responseSearch = createSavedSearchResponse(
            id = "search-1",
            name = "Updated Search",
            query = "domain:updated.com"
        )
        val wrapper = SavedSearchResponseWrapper(search = responseSearch)

        val mockClient = createMockHttpClient(responseData = wrapper)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.updateSavedSearch("search-1", request)

        assertEquals("search-1", result.id)
        assertEquals("Updated Search", result.name)
        assertEquals("domain:updated.com", result.query)
    }

    @Test
    fun testUpdateSavedSearchWithNewTags() = runTest {
        val request = createSavedSearchRequest(
            name = "Tagged Search",
            query = "domain:example.com",
            userTags = listOf("pro.high-priority", "team.security", "private.internal")
        )
        val responseSearch = createSavedSearchResponse(
            id = "search-1",
            name = "Tagged Search",
            userTags = listOf("pro.high-priority", "team.security", "private.internal")
        )
        val wrapper = SavedSearchResponseWrapper(search = responseSearch)

        val mockClient = createMockHttpClient(responseData = wrapper)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.updateSavedSearch("search-1", request)

        assertEquals(3, result.userTags?.size)
        assertEquals(result.userTags?.contains("pro.high-priority"), true)
    }

    @Test
    fun testDeleteSavedSearch() = runTest {
        val mockClient = createMockHttpClient<Unit>(statusCode = HttpStatusCode.NoContent)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        savedSearchesApi.deleteSavedSearch("search-1")
    }

    @Test
    fun testGetSavedSearchResults() = runTest {
        val results = listOf(
            createSearchResult(id = "scan-1", domain = "example.com"),
            createSearchResult(id = "scan-2", domain = "example.com"),
            createSearchResult(id = "scan-3", domain = "example.com")
        )
        val response = createSearchResponse(results = results, total = 3)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearchResults("search-1")

        assertEquals(3, result.total)
        assertEquals(3, result.results.size)
        assertEquals("scan-1", result.results[0].id)
    }

    @Test
    fun testGetSavedSearchResultsEmpty() = runTest {
        val response = createSearchResponse(results = emptyList(), total = 0)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearchResults("search-1")

        assertEquals(0, result.total)
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun testGetSavedSearchById() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "First Search"),
            createSavedSearchResponse(id = "search-2", name = "Second Search"),
            createSavedSearchResponse(id = "search-3", name = "Third Search")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearchById("search-2")

        assertEquals("search-2", result.id)
        assertEquals("Second Search", result.name)
    }

    @Test
    fun testGetSavedSearchByIdNotFound() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "First Search"),
            createSavedSearchResponse(id = "search-2", name = "Second Search")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            savedSearchesApi.getSavedSearchById("search-nonexistent")
        }
    }

    @Test
    fun testGetSavedSearchesByDatasource() = runTest {
        val searches = listOf(
            createSavedSearchResponse(
                id = "search-1",
                datasource = SavedSearchDatasource.SCANS,
                name = "Scans Search"
            ),
            createSavedSearchResponse(
                id = "search-2",
                datasource = SavedSearchDatasource.HOSTNAMES,
                name = "Hostnames Search"
            ),
            createSavedSearchResponse(
                id = "search-3",
                datasource = SavedSearchDatasource.SCANS,
                name = "Another Scans Search"
            )
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearchesByDatasource("scans")

        assertEquals(2, result.size)
        assertTrue(result.all { it.datasource == SavedSearchDatasource.SCANS })
    }

    @Test
    fun testGetSavedSearchesByDatasourceHostnames() = runTest {
        val searches = listOf(
            createSavedSearchResponse(
                id = "search-1",
                datasource = SavedSearchDatasource.SCANS
            ),
            createSavedSearchResponse(
                id = "search-2",
                datasource = SavedSearchDatasource.HOSTNAMES
            )
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.getSavedSearchesByDatasource("hostnames")

        assertEquals(1, result.size)
        assertEquals(SavedSearchDatasource.HOSTNAMES, result[0].datasource)
    }

    @Test
    fun testSearchSavedSearchesByName() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "Phishing Detection"),
            createSavedSearchResponse(id = "search-2", name = "Malware Analysis"),
            createSavedSearchResponse(id = "search-3", name = "Phishing Variants"),
            createSavedSearchResponse(id = "search-4", name = "SSL Certificate Issues")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.searchSavedSearchesByName("Phishing")

        assertEquals(2, result.size)
        assertTrue(result.all { it.name.contains("Phishing") })
    }

    @Test
    fun testSearchSavedSearchesByNameCaseInsensitive() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "MALWARE DETECTION"),
            createSavedSearchResponse(id = "search-2", name = "Phishing Analysis"),
            createSavedSearchResponse(id = "search-3", name = "malware variants")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.searchSavedSearchesByName("malware")

        assertEquals(2, result.size)
    }

    @Test
    fun testSearchSavedSearchesByNameNoMatches() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "Phishing Detection"),
            createSavedSearchResponse(id = "search-2", name = "Malware Analysis")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val result = savedSearchesApi.searchSavedSearchesByName("ransomware")

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateSavedSearchAuthenticationError() = runTest {
        val request = createSavedSearchRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            savedSearchesApi.createSavedSearch(request)
        }
    }

    @Test
    fun testCreateSavedSearchForbiddenError() = runTest {
        val request = createSavedSearchRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            savedSearchesApi.createSavedSearch(request)
        }
    }

    @Test
    fun testUpdateSavedSearchNotFoundError() = runTest {
        val request = createSavedSearchRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Search not found"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            savedSearchesApi.updateSavedSearch("nonexistent", request)
        }
    }

    @Test
    fun testDeleteSavedSearchNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Search not found"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            savedSearchesApi.deleteSavedSearch("nonexistent")
        }
    }

    @Test
    fun testGetSavedSearchResultsNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Search not found"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            savedSearchesApi.getSavedSearchResults("nonexistent")
        }
    }

    @Test
    fun testCreateSavedSearchRateLimitError() = runTest {
        val request = createSavedSearchRequest()
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"error": "Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("120")
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
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            savedSearchesApi.createSavedSearch(request)
        }
        assertEquals(120L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetSavedSearchesServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            savedSearchesApi.getSavedSearches()
        }
    }

    @Test
    fun testCreateSavedSearchBadRequest() = runTest {
        val request = createSavedSearchRequest()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Invalid query"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            savedSearchesApi.createSavedSearch(request)
        }
    }

    @Test
    fun testGetSavedSearchesServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val savedSearchesApi = createSavedSearchesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            savedSearchesApi.getSavedSearches()
        }
    }

    @Test
    fun testCreateSavedSearchWithDifferentDatasources() = runTest {
        val scansRequest = createSavedSearchRequest(
            datasource = SavedSearchDatasource.SCANS,
            name = "Scans Search"
        )
        val hostnamesRequest = createSavedSearchRequest(
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Hostnames Search"
        )

        val scansResponse = createSavedSearchResponse(
            id = "search-scans",
            datasource = SavedSearchDatasource.SCANS
        )
        val hostnamesResponse = createSavedSearchResponse(
            id = "search-hostnames",
            datasource = SavedSearchDatasource.HOSTNAMES
        )

        val mockClient1 = createMockHttpClient(responseData = SavedSearchResponseWrapper(scansResponse))
        val mockClient2 = createMockHttpClient(responseData = SavedSearchResponseWrapper(hostnamesResponse))

        val api1 = createSavedSearchesApi(httpClient = mockClient1)
        val api2 = createSavedSearchesApi(httpClient = mockClient2)

        val resultScans = api1.createSavedSearch(scansRequest)
        val resultHostnames = api2.createSavedSearch(hostnamesRequest)

        assertEquals(SavedSearchDatasource.SCANS, resultScans.datasource)
        assertEquals(SavedSearchDatasource.HOSTNAMES, resultHostnames.datasource)
    }

    @Test
    fun testCreateSavedSearchWithAllTlpLevels() = runTest {
        val tlpLevels = listOf(Tlp.RED, Tlp.AMBER_STRICT, Tlp.AMBER, Tlp.GREEN, Tlp.CLEAR)

        tlpLevels.forEachIndexed { index, tlp ->
            val request = createSavedSearchRequest(
                name = "Search ${index + 1}",
                tlp = tlp
            )
            val response = createSavedSearchResponse(
                id = "search-tlp-$index",
                name = "Search ${index + 1}",
                tlp = tlp
            )
            val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
            val api = createSavedSearchesApi(httpClient = mockClient)

            val result = api.createSavedSearch(request)
            assertEquals(tlp, result.tlp)
        }
    }

    @Test
    fun testCreateSavedSearchWithMultiplePermissions() = runTest {
        val request = createSavedSearchRequest(
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val response = createSavedSearchResponse(
            id = "search-perms",
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.createSavedSearch(request)

        assertEquals(2, result.permissions?.size)
        assertEquals(result.permissions?.contains(TeamPermission.TEAM_READ), true)
        assertEquals(result.permissions?.contains(TeamPermission.TEAM_WRITE), true)
    }

    @Test
    fun testGetSavedSearchesWithMixedDatasources() = runTest {
        val searches = listOf(
            createSavedSearchResponse(
                id = "search-1",
                datasource = SavedSearchDatasource.SCANS
            ),
            createSavedSearchResponse(
                id = "search-2",
                datasource = SavedSearchDatasource.HOSTNAMES
            ),
            createSavedSearchResponse(
                id = "search-3",
                datasource = SavedSearchDatasource.SCANS
            )
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearches()

        assertEquals(3, result.size)
        assertEquals(2, result.count { it.datasource == SavedSearchDatasource.SCANS })
        assertEquals(1, result.count { it.datasource == SavedSearchDatasource.HOSTNAMES })
    }

    @Test
    fun testComplexSearchQuery() = runTest {
        val complexQuery = "domain:example.com AND country:(US OR IT) AND verdicts.urlscan.malicious:true AND task.time:[now-7d TO now]"
        val request = createSavedSearchRequest(
            name = "Complex Query",
            query = complexQuery
        )
        val response = createSavedSearchResponse(
            id = "search-complex",
            name = "Complex Query",
            query = complexQuery
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.createSavedSearch(request)

        assertEquals(complexQuery, result.query)
    }

    @Test
    fun testSavedSearchWithLongDescription() = runTest {
        val longDesc = "This is a very long description that provides detailed information about what " +
                "this saved search does, how it should be used, when it should be triggered, and what " +
                "kind of results you can expect from running it. It includes multiple paragraphs and " +
                "detailed guidelines for team members."

        val request = createSavedSearchRequest(
            name = "Long Description Search",
            longDescription = longDesc
        )
        val response = createSavedSearchResponse(
            id = "search-long-desc",
            longDescription = longDesc
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.createSavedSearch(request)

        assertEquals(longDesc, result.longDescription)
    }

    @Test
    fun testSavedSearchWithMultipleTags() = runTest {
        val tags = listOf(
            "pro.phishing",
            "pro.urgent",
            "public.security",
            "team.analysis",
            "private.internal"
        )
        val request = createSavedSearchRequest(
            userTags = tags
        )
        val response = createSavedSearchResponse(
            id = "search-tags",
            userTags = tags
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.createSavedSearch(request)

        assertEquals(5, result.userTags?.size)
        assertEquals(result.userTags?.containsAll(tags), true)
    }

    @Test
    fun testGetSavedSearchResultsWithPagination() = runTest {
        val results = (1..10).map { i ->
            createSearchResult(id = "scan-$i", domain = "example$i.com")
        }
        val response = createSearchResponse(
            results = results,
            total = 100,
            took = 45,
            hasMore = true
        )

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearchResults("search-1")

        assertEquals(100, result.total)
        assertEquals(10, result.results.size)
        assertTrue(result.hasMore)
    }

    @Test
    fun testSavedSearchWorkflow() = runTest {
        // Create search
        val createRequest = createSavedSearchRequest(
            name = "Workflow Search",
            query = "domain:workflow-test.com"
        )
        val createResponse = createSavedSearchResponse(
            id = "search-workflow",
            name = "Workflow Search"
        )
        val mockClient1 = createMockHttpClient(responseData = SavedSearchResponseWrapper(createResponse))
        val api1 = createSavedSearchesApi(httpClient = mockClient1)

        val created = api1.createSavedSearch(createRequest)
        assertEquals("search-workflow", created.id)

        // Update search
        val updateRequest = createSavedSearchRequest(
            name = "Updated Workflow Search",
            query = "domain:updated-workflow.com"
        )
        val updateResponse = createSavedSearchResponse(
            id = "search-workflow",
            name = "Updated Workflow Search"
        )
        val mockClient2 = createMockHttpClient(responseData = SavedSearchResponseWrapper(updateResponse))
        val api2 = createSavedSearchesApi(httpClient = mockClient2)

        val updated = api2.updateSavedSearch("search-workflow", updateRequest)
        assertEquals("Updated Workflow Search", updated.name)

        // Get results
        val resultsResponse = createSearchResponse(
            results = listOf(createSearchResult()),
            total = 1
        )
        val mockClient3 = createMockHttpClient(responseData = resultsResponse)
        val api3 = createSavedSearchesApi(httpClient = mockClient3)

        val results = api3.getSavedSearchResults("search-workflow")
        assertEquals(1, results.total)
    }

    @Test
    fun testSavedSearchDeserialization() = runTest {
        val originalSearch = createSavedSearchResponse(
            id = "search-deser-123",
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Deserialization Test",
            query = "hostname:test.com",
            description = "Test description",
            longDescription = "Test long description",
            tlp = Tlp.GREEN,
            userTags = listOf("public.test", "team.qa"),
            permissions = listOf(TeamPermission.TEAM_READ),
            createdAt = "2025-01-20T14:30:00Z",
            ownerDescription = "Owner description"
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchesListResponse(listOf(originalSearch)))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearches()[0]

        assertEquals("search-deser-123", result.id)
        assertEquals(SavedSearchDatasource.HOSTNAMES, result.datasource)
        assertEquals("Deserialization Test", result.name)
        assertEquals("hostname:test.com", result.query)
        assertEquals("Test description", result.description)
        assertEquals("Test long description", result.longDescription)
        assertEquals(Tlp.GREEN, result.tlp)
        assertEquals(2, result.userTags?.size)
        assertEquals(1, result.permissions?.size)
        assertEquals("2025-01-20T14:30:00Z", result.createdAt)
        assertEquals("Owner description", result.ownerDescription)
    }

    @Test
    fun testGetSavedSearchesByDatasourceCaseInsensitive() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", datasource = SavedSearchDatasource.SCANS),
            createSavedSearchResponse(id = "search-2", datasource = SavedSearchDatasource.HOSTNAMES)
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearchesByDatasource("SCANS")

        assertEquals(1, result.size)
        assertEquals(SavedSearchDatasource.SCANS, result[0].datasource)
    }

    @Test
    fun testSearchSavedSearchesByNamePartialMatch() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", name = "Phishing Detection Framework"),
            createSavedSearchResponse(id = "search-2", name = "Advanced Phishing Analytics"),
            createSavedSearchResponse(id = "search-3", name = "Domain Reputation Analysis")
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.searchSavedSearchesByName("Detection")

        assertEquals(1, result.size)
        assertEquals("search-1", result[0].id)
    }

    @Test
    fun testUpdateSavedSearchChangeDatasource() = runTest {
        val request = createSavedSearchRequest(
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Changed Datasource"
        )
        val response = createSavedSearchResponse(
            id = "search-1",
            datasource = SavedSearchDatasource.HOSTNAMES,
            name = "Changed Datasource"
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.updateSavedSearch("search-1", request)

        assertEquals(SavedSearchDatasource.HOSTNAMES, result.datasource)
    }

    @Test
    fun testGetMultipleSavedSearchesWithDifferentTlpLevels() = runTest {
        val searches = listOf(
            createSavedSearchResponse(id = "search-1", tlp = Tlp.RED),
            createSavedSearchResponse(id = "search-2", tlp = Tlp.AMBER),
            createSavedSearchResponse(id = "search-3", tlp = Tlp.GREEN),
            createSavedSearchResponse(id = "search-4", tlp = null)
        )
        val response = SavedSearchesListResponse(searches = searches)

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearches()

        assertEquals(4, result.size)
        assertEquals(Tlp.RED, result[0].tlp)
        assertEquals(Tlp.GREEN, result[2].tlp)
        assertEquals(null, result[3].tlp)
    }

    @Test
    fun testCreateSavedSearchWithSpecialCharactersInQuery() = runTest {
        val specialQuery = "domain:\"special-chars-!@#$.com\" OR path:/api/*/endpoint"
        val request = createSavedSearchRequest(query = specialQuery)
        val response = createSavedSearchResponse(id = "search-special", query = specialQuery)

        val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.createSavedSearch(request)

        assertEquals(specialQuery, result.query)
    }

    @Test
    fun testSavedSearchWithOwnerDescription() = runTest {
        val ownerDesc = "Created by Security Team for monitoring phishing attempts"
        val search = createSavedSearchResponse(
            id = "search-owner",
            ownerDescription = ownerDesc
        )
        val mockClient = createMockHttpClient(responseData = SavedSearchesListResponse(listOf(search)))
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearches()[0]

        assertEquals(ownerDesc, result.ownerDescription)
    }

    @Test
    fun testGetSavedSearchResultsLargeDataset() = runTest {
        val results = (1..100).map { i ->
            createSearchResult(id = "scan-$i")
        }
        val response = createSearchResponse(
            results = results,
            total = 1000,
            hasMore = true
        )

        val mockClient = createMockHttpClient(responseData = response)
        val api = createSavedSearchesApi(httpClient = mockClient)

        val result = api.getSavedSearchResults("search-1")

        assertEquals(1000, result.total)
        assertEquals(100, result.results.size)
        assertTrue(result.hasMore)
    }

    @Test
    fun testSavedSearchNameEdgeCases() = runTest {
        val names = listOf(
            "A",
            "A very long name that contains many words and goes on and on for quite a while",
            "Name with Numbers 123",
            "Name-with-dashes",
            "Name_with_underscores",
            "Name.with.dots"
        )

        names.forEachIndexed { index, name ->
            val request = createSavedSearchRequest(name = name)
            val response = createSavedSearchResponse(id = "search-$index", name = name)
            val mockClient = createMockHttpClient(responseData = SavedSearchResponseWrapper(response))
            val api = createSavedSearchesApi(httpClient = mockClient)

            val result = api.createSavedSearch(request)
            assertEquals(name, result.name)
        }
    }

    @Test
    fun testDeleteSavedSearchMultiple() = runTest {
        val ids = listOf("search-1", "search-2", "search-3")

        ids.forEach { id ->
            val mockClient = createMockHttpClient<Unit>(statusCode = HttpStatusCode.NoContent)
            val api = createSavedSearchesApi(httpClient = mockClient)
            api.deleteSavedSearch(id)
        }

        assertTrue(true)
    }
}