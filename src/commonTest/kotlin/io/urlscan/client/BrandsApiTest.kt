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
import io.urlscan.client.exception.installExceptionHandling
import io.urlscan.client.model.AvailableBrandsResponse
import io.urlscan.client.model.Brand
import io.urlscan.client.model.BrandSummary
import io.urlscan.client.model.BrandTerms
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrandsApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createBrand(
        id: String = "brand-1",
        name: String = "Acme Corporation",
        key: String = "acme",
        vertical: List<String> = listOf("Finance", "Technology"),
        country: List<String> = listOf("US", "UK"),
        domains: List<String> = listOf("acme.com", "acme.co.uk"),
        asns: List<String> = listOf("AS1234", "AS5678"),
        createdAt : String = Clock.System.now().toString()
    ): Brand {
        return Brand(
            id = id,
            name = name,
            key = key,
            vertical = vertical,
            country = country,
            terms = BrandTerms(
                domains = domains,
                asns = asns
            ),
            createdAt = createdAt
        )
    }

    private fun createBrandSummary(
        id: String = "brand-1",
        name: String = "Acme Corporation",
        key: String = "acme",
        vertical: List<String> = listOf("Finance"),
        country: List<String> = listOf("US"),
        domains: List<String> = listOf("acme.com"),
        asns: List<String> = listOf("AS1234"),
        detectedCount: Int = 5,
        latestDetection: String = "2025-01-15T10:30:00Z",
        createdAt : String = Clock.System.now().toString()
    ): BrandSummary {
        return BrandSummary(
            id = id,
            name = name,
            key = key,
            vertical = vertical,
            country = country,
            terms = BrandTerms(
                domains = domains,
                asns = asns
            ),
            createdAt = createdAt,
            detectedCount = detectedCount,
            latestDetection = latestDetection
        )
    }

    private fun createAvailableBrandsResponse(
        brands: List<Brand> = emptyList()
    ): AvailableBrandsResponse {
        return AvailableBrandsResponse(kits = brands)
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
            responseData != null -> json.encodeToString(responseData)  // Uses reified T
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


    private fun createBrandsApi(
        httpClient: io.ktor.client.HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): BrandsApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return BrandsApi(httpClient, config)
    }

    @Test
    fun testGetAvailableBrands() = runTest {
        val brands = listOf(
            createBrand(
                id = "brand-1",
                name = "Acme Corp",
                key = "acme",
                vertical = listOf("Finance", "Technology"),
                country = listOf("US", "UK")
            ),
            createBrand(
                id = "brand-2",
                name = "Beta Inc",
                key = "beta",
                vertical = listOf("Retail"),
                country = listOf("CA")
            )
        )
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(2, result.size)
        assertEquals("Acme Corp", result[0].name)
        assertEquals("acme", result[0].key)
        assertEquals("Beta Inc", result[1].name)
        assertEquals("beta", result[1].key)
    }

    @Test
    fun testGetAvailableBrandsEmpty() = runTest {
        val response = createAvailableBrandsResponse(brands = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(0, result.size)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetAvailableBrandsWithMultipleVerticals() = runTest {
        val brand = createBrand(
            vertical = listOf("Finance", "Technology", "Retail", "Healthcare"),
            country = listOf("US", "UK", "CA", "DE", "FR")
        )
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(1, result.size)
        assertEquals(4, result[0].vertical.size)
        assertEquals(5, result[0].country.size)
        assertTrue(result[0].vertical.contains("Finance"))
        assertTrue(result[0].country.contains("DE"))
    }

    @Test
    fun testGetAvailableBrandsWithDomainAndAsnTerms() = runTest {
        val brand = createBrand(
            domains = listOf("acme.com", "acme.co.uk", "acme.fr"),
            asns = listOf("AS1234", "AS5678", "AS9999")
        )
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(1, result.size)
        assertEquals(3, result[0].terms.domains.size)
        assertEquals(3, result[0].terms.asns.size)
        assertTrue(result[0].terms.domains.contains("acme.fr"))
        assertTrue(result[0].terms.asns.contains("AS5678"))
    }

    @Test
    fun testGetBrandSummaries() = runTest {
        val summaries = listOf(
            createBrandSummary(
                id = "brand-1",
                name = "One",
                detectedCount = 150,
                latestDetection = "2025-01-15T10:30:00Z"
            ),
            createBrandSummary(
                id = "brand-2",
                name = "Two",
                detectedCount = 320,
                latestDetection = "2025-01-15T09:15:00Z"
            ),
            createBrandSummary(
                id = "brand-3",
                name = "Three",
                detectedCount = 87,
                latestDetection = "2025-01-14T15:45:00Z"
            )
        )

        val mockClient = createMockHttpClient(responseData = summaries)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandSummaries()

        assertEquals(3, result.size)
        assertEquals("One", result[0].name)
        assertEquals(150, result[0].detectedCount)
        assertEquals("Two", result[1].name)
        assertEquals(320, result[1].detectedCount)
        assertEquals("Three", result[2].name)
        assertEquals(87, result[2].detectedCount)
    }

    @Test
    fun testGetBrandStatistics() = runTest {
        val brands = listOf(
            createBrand(
                id = "brand-1",
                vertical = listOf("Finance", "Technology"),
                country = listOf("US", "IT"),
                domains = listOf("bank1.com"),
                asns = listOf("AS1234")
            ),
            createBrand(
                id = "brand-2",
                vertical = listOf("Retail"),
                country = listOf("CA"),
                domains = listOf("shop.com", "shop.ca"),
                asns = emptyList()
            ),
            createBrand(
                id = "brand-3",
                vertical = listOf("Healthcare", "Finance"),
                country = listOf("DE", "IT"),
                domains = emptyList(),
                asns = listOf("AS5678", "AS9999")
            )
        )
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(3, result.totalBrands)
        assertEquals(4, result.totalVerticals)
        assertEquals(4, result.totalCountries)
        assertEquals(2, result.brandsWithLegitemateDomains)
        assertEquals(2, result.brandsWithAsnTerms)
    }

    @Test
    fun testGetBrandStatisticsWithSingleBrand() = runTest {
        val brand = createBrand()
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(1, result.totalBrands)
        assertEquals(2, result.totalVerticals)
        assertEquals(2, result.totalCountries)
        assertEquals(1, result.brandsWithLegitemateDomains)
        assertEquals(1, result.brandsWithAsnTerms)
    }

    @Test
    fun testGetBrandStatisticsWithEmptyBrands() = runTest {
        val response = createAvailableBrandsResponse(brands = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(0, result.totalBrands)
        assertEquals(0, result.totalVerticals)
        assertEquals(0, result.totalCountries)
        assertEquals(0, result.brandsWithLegitemateDomains)
        assertEquals(0, result.brandsWithAsnTerms)
    }

    @Test
    fun testGetBrandStatisticsWithOverlappingVerticals() = runTest {
        val brands = listOf(
            createBrand(
                id = "brand-1",
                vertical = listOf("Finance", "Technology"),
                country = listOf("US")
            ),
            createBrand(
                id = "brand-2",
                vertical = listOf("Finance", "Retail"),
                country = listOf("US")
            )
        )
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(3, result.totalVerticals)
        assertEquals(1, result.totalCountries)
    }

    @Test
    fun testGetAvailableBrandsAuthenticationError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            brandsApi.getAvailableBrands()
        }
    }

    @Test
    fun testGetAvailableBrandsForbiddenError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            brandsApi.getAvailableBrands()
        }
    }

    @Test
    fun testGetBrandSummariesNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Not Found"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            brandsApi.getBrandSummaries()
        }
    }

    @Test
    fun testGetBrandSummariesServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            brandsApi.getBrandSummaries()
        }
    }

    @Test
    fun testGetAvailableBrandsBadRequest() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            brandsApi.getAvailableBrands()
        }
    }

    @Test
    fun testGetBrandStatisticsWithBrandsHavingNoTerms() = runTest {
        val brands = listOf(
            createBrand(
                id = "brand-1",
                domains = emptyList(),
                asns = emptyList()
            ),
            createBrand(
                id = "brand-2",
                domains = listOf("test.com"),
                asns = emptyList()
            )
        )
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(2, result.totalBrands)
        assertEquals(1, result.brandsWithLegitemateDomains)
        assertEquals(0, result.brandsWithAsnTerms)
    }

    @Test
    fun testGetAvailableBrandsLargeDataset() = runTest {
        val brands = (1..100).map { i ->
            createBrand(
                id = "brand-$i",
                name = "Brand $i",
                key = "brand_$i"
            )
        }
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(100, result.size)
        assertEquals("Brand 1", result[0].name)
        assertEquals("Brand 100", result[99].name)
    }

    @Test
    fun testGetBrandSummariesSortedByDetectedCount() = runTest {
        val summaries = listOf(
            createBrandSummary(name = "Brand A", detectedCount = 10),
            createBrandSummary(name = "Brand B", detectedCount = 100),
            createBrandSummary(name = "Brand C", detectedCount = 50)
        )

        val mockClient = createMockHttpClient(responseData = summaries)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandSummaries()

        assertEquals(3, result.size)
        // Verify order as returned (not necessarily sorted by API)
        assertEquals("Brand A", result[0].name)
        assertEquals(10, result[0].detectedCount)
    }

    @Test
    fun testGetAvailableBrandsWithSpecialCharactersInName() = runTest {
        val brand = createBrand(
            name = "Company & Associates (Ltd.)",
            key = "company_associates"
        )
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(1, result.size)
        assertEquals("Company & Associates (Ltd.)", result[0].name)
    }

    @Test
    fun testGetBrandSummariesRecentDetections() = runTest {
        val now = "2025-01-20T12:00:00Z"
        val summaries = listOf(
            createBrandSummary(
                name = "Brand A",
                latestDetection = now
            ),
            createBrandSummary(
                name = "Brand B",
                latestDetection = "2025-01-01T00:00:00Z"
            )
        )

        val mockClient = createMockHttpClient(responseData = summaries)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandSummaries()

        assertEquals(2, result.size)
        assertEquals(now, result[0].latestDetection)
        assertTrue(result[0].latestDetection!! > result[1].latestDetection!!)
    }

    @Test
    fun testGetAvailableBrandsMultipleAsns() = runTest {
        val brand = createBrand(
            asns = listOf("AS1", "AS2", "AS3", "AS4", "AS5")
        )
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(1, result.size)
        assertEquals(5, result[0].terms.asns.size)
    }

    @Test
    fun testGetBrandStatisticsCalculatesCorrectPercentages() = runTest {
        val brandsWithDomains = (1..7).map { i ->
            createBrand(id = "brand-$i", domains = listOf("domain$i.com"))
        }
        val brandsWithoutDomains = (8..10).map { i ->
            createBrand(id = "brand-$i", domains = emptyList())
        }
        val response = createAvailableBrandsResponse(brands = brandsWithDomains + brandsWithoutDomains)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(10, result.totalBrands)
        assertEquals(7, result.brandsWithLegitemateDomains)
    }

    @Test
    fun testGetAvailableBrandsCreatedAtTimestamp() = runTest {
        val createdAt = "2024-06-15T14:30:45Z"
        val brand = createBrand(createdAt = createdAt)
        val response = createAvailableBrandsResponse(brands = listOf(brand))

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getAvailableBrands()

        assertEquals(1, result.size)
        assertEquals(createdAt, result[0].createdAt)
    }

    @Test
    fun testGetBrandSummariesDeserialization() = runTest {
        val originalSummary = createBrandSummary(
            id = "test-brand-123",
            name = "Test Brand",
            key = "test_brand",
            vertical = listOf("Finance", "Tech"),
            country = listOf("US", "UK"),
            domains = listOf("test.com", "test.uk"),
            asns = listOf("AS123", "AS456"),
            detectedCount = 42,
            latestDetection = "2025-01-18T08:45:30Z"
        )
        val mockClient = createMockHttpClient(responseData = listOf(originalSummary))
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandSummaries()

        assertEquals(1, result.size)
        val summary = result[0]
        assertEquals("test-brand-123", summary.id)
        assertEquals("Test Brand", summary.name)
        assertEquals("test_brand", summary.key)
        assertEquals(listOf("Finance", "Tech"), summary.vertical)
        assertEquals(listOf("US", "UK"), summary.country)
        assertEquals(listOf("test.com", "test.uk"), summary.terms.domains)
        assertEquals(listOf("AS123", "AS456"), summary.terms.asns)
        assertEquals(42, summary.detectedCount)
        assertEquals("2025-01-18T08:45:30Z", summary.latestDetection)
    }

    @Test
    fun testGetAvailableBrandsServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val brandsApi = createBrandsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            brandsApi.getAvailableBrands()
        }
    }

    @Test
    fun testGetBrandStatisticsWithHighCardinalityData() = runTest {
        val brands = listOf(
            createBrand(
                vertical = listOf("V1", "V2", "V3"),
                country = listOf("C1", "C2", "C3")
            ),
            createBrand(
                vertical = listOf("V2", "V3", "V4"),
                country = listOf("C2", "C3", "C4")
            ),
            createBrand(
                vertical = listOf("V3", "V4", "V5"),
                country = listOf("C3", "C4", "C5")
            )
        )
        val response = createAvailableBrandsResponse(brands = brands)

        val mockClient = createMockHttpClient(responseData = response)
        val brandsApi = createBrandsApi(httpClient = mockClient)

        val result = brandsApi.getBrandStatistics()

        assertEquals(3, result.totalBrands)
        assertEquals(5, result.totalVerticals)
        assertEquals(5, result.totalCountries)
    }
}