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
import io.urlscan.client.model.AsnInfo
import io.urlscan.client.model.GeoIpInfo
import io.urlscan.client.model.HostnameData
import io.urlscan.client.model.HostnameHistoryResult
import io.urlscan.client.model.HostnameHistorySchema
import io.urlscan.client.model.HostnameHistorySource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostnamesApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createGeoIpInfo(
        country: String = "US",
        region: String? = "California",
        timezone: String? = "America/Los_Angeles",
        city: String? = "San Francisco",
        ll: List<Double>? = listOf(37.7749, -122.4194),
        countryName: String? = "United States",
        metro: Int? = 807,
        area: Int? = null
    ): GeoIpInfo {
        return GeoIpInfo(
            country = country,
            region = region,
            timezone = timezone,
            city = city,
            ll = ll,
            countryName = countryName,
            metro = metro,
            area = area
        )
    }

    private fun createAsnInfo(
        ip: String = "1.2.3.4",
        asn: String = "AS15169",
        country: String = "US",
        registrar: String = "ARIN",
        date: String = "2024-01-01",
        description: String = "Google LLC",
        route: String = "1.0.0.0/24",
        name: String? = "GOOGLE"
    ): AsnInfo {
        return AsnInfo(
            ip = ip,
            asn = asn,
            country = country,
            registrar = registrar,
            date = date,
            description = description,
            route = route,
            name = name
        )
    }

    private fun createHostnameData(
        ttl: Int? = 300,
        rdata: String? = "1.2.3.4",
        geoip: GeoIpInfo = createGeoIpInfo(),
        asn: AsnInfo = createAsnInfo()
    ): HostnameData {
        return HostnameData(
            ttl = ttl,
            rdata = rdata,
            geoip = geoip,
            asn = asn
        )
    }

    private fun createHostnameHistoryResult(
        seenOn: String = "2025-01-20T10:30:00Z",
        source: HostnameHistorySource = HostnameHistorySource.SCAN,
        subId: String = "sub-1",
        firstSeen: String = "2024-01-01T00:00:00Z",
        lastSeen: String = "2025-01-20T10:30:00Z",
        dataType: String? = "A",
        data: HostnameData? = createHostnameData()
    ): HostnameHistoryResult {
        return HostnameHistoryResult(
            seenOn = seenOn,
            source = source,
            subId = subId,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            dataType = dataType,
            data = data
        )
    }

    private fun createHostnameHistorySchema(
        pageState: String? = null,
        results: List<HostnameHistoryResult> = emptyList()
    ): HostnameHistorySchema {
        return HostnameHistorySchema(
            pageState = pageState,
            results = results
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

    private fun createHostnamesApi(
        httpClient: io.ktor.client.HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): HostnamesApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return HostnamesApi(httpClient, config)
    }

    @Test
    fun testGetHostnameHistoryBasic() = runTest {
        val result = createHostnameHistoryResult(
            seenOn = "2025-01-20T10:30:00Z",
            source = HostnameHistorySource.SCAN
        )
        val schema = createHostnameHistorySchema(results = listOf(result))

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(1, response.results.size)
        assertEquals("2025-01-20T10:30:00Z", response.results[0].seenOn)
        assertEquals(HostnameHistorySource.SCAN, response.results[0].source)
    }

    @Test
    fun testGetHostnameHistoryWithPagination() = runTest {
        val results = (1..10).map { i ->
            createHostnameHistoryResult(
                seenOn = "2025-01-${i.toString().padStart(2, '0')}T10:30:00Z",
                subId = "sub-$i"
            )
        }
        val pageState = "eyJpdGVtIjogIjEwIn0="
        val schema = createHostnameHistorySchema(results = results, pageState = pageState)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com", pageState = pageState)

        assertEquals(10, response.results.size)
        assertEquals(pageState, response.pageState)
    }

    @Test
    fun testGetHostnameHistoryWithCustomLimit() = runTest {
        val results = (1..50).map { i ->
            createHostnameHistoryResult(subId = "sub-$i")
        }
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com", limit = 5000)

        assertEquals(50, response.results.size)
    }

    @Test
    fun testGetHostnameHistoryMultipleSources() = runTest {
        val results = listOf(
            createHostnameHistoryResult(source = HostnameHistorySource.SCAN),
            createHostnameHistoryResult(source = HostnameHistorySource.PDNS),
            createHostnameHistoryResult(source = HostnameHistorySource.CT),
            createHostnameHistoryResult(source = HostnameHistorySource.ZONEFILE),
            createHostnameHistoryResult(source = HostnameHistorySource.SCAN_LINK),
            createHostnameHistoryResult(source = HostnameHistorySource.SCAN_CERT_SUBJECT)
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(6, response.results.size)
        assertEquals(HostnameHistorySource.SCAN, response.results[0].source)
        assertEquals(HostnameHistorySource.PDNS, response.results[1].source)
        assertEquals(HostnameHistorySource.CT, response.results[2].source)
        assertEquals(HostnameHistorySource.ZONEFILE, response.results[3].source)
        assertEquals(HostnameHistorySource.SCAN_LINK, response.results[4].source)
        assertEquals(HostnameHistorySource.SCAN_CERT_SUBJECT, response.results[5].source)
    }

    @Test
    fun testGetHostnameHistoryWithGeoipData() = runTest {
        val geoip = createGeoIpInfo(
            country = "IT",
            region = "Lombardy",
            city = "Milan",
            countryName = "Italy"
        )
        val data = createHostnameData(geoip = geoip)
        val result = createHostnameHistoryResult(data = data)
        val schema = createHostnameHistorySchema(results = listOf(result))

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(1, response.results.size)
        assertNotNull(response.results[0].data)
        assertEquals("IT", response.results[0].data!!.geoip.country)
        assertEquals("Milan", response.results[0].data!!.geoip.city)
    }

    @Test
    fun testGetHostnameHistoryWithAsnData() = runTest {
        val asn = createAsnInfo(
            asn = "AS12345",
            description = "Example Network",
            country = "DE"
        )
        val data = createHostnameData(asn = asn)
        val result = createHostnameHistoryResult(data = data)
        val schema = createHostnameHistorySchema(results = listOf(result))

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(1, response.results.size)
        assertNotNull(response.results[0].data)
        assertEquals("AS12345", response.results[0].data!!.asn.asn)
        assertEquals("Example Network", response.results[0].data!!.asn.description)
        assertEquals("DE", response.results[0].data!!.asn.country)
    }

    @Test
    fun testGetHostnameHistoryWithDnsRecords() = runTest {
        val results = listOf(
            createHostnameHistoryResult(
                dataType = "A",
                data = createHostnameData(rdata = "1.2.3.4")
            ),
            createHostnameHistoryResult(
                dataType = "AAAA",
                data = createHostnameData(rdata = "2001:db8::1")
            ),
            createHostnameHistoryResult(
                dataType = "MX",
                data = createHostnameData(rdata = "mail.example.com")
            ),
            createHostnameHistoryResult(
                dataType = "CNAME",
                data = createHostnameData(rdata = "alias.example.com")
            )
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(4, response.results.size)
        assertEquals("A", response.results[0].dataType)
        assertEquals("1.2.3.4", response.results[0].data!!.rdata)
        assertEquals("AAAA", response.results[1].dataType)
        assertEquals("2001:db8::1", response.results[1].data!!.rdata)
    }

    @Test
    fun testGetHostnameHistoryEmptyResults() = runTest {
        val schema = createHostnameHistorySchema(results = emptyList())

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("nonexistent.example.com")

        assertEquals(0, response.results.size)
        assertTrue(response.results.isEmpty())
    }

    @Test
    fun testGetHostnameHistoryNullPageState() = runTest {
        val schema = createHostnameHistorySchema(pageState = null, results = emptyList())

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertNull(response.pageState)
    }

    @Test
    fun testGetHostnameHistoryValidatesHostnameNotBlank() = runTest {
        val hostnamesApi = createHostnamesApi()

        assertFailsWith<IllegalArgumentException> {
            hostnamesApi.getHostnameHistory("")
        }
    }

    @Test
    fun testGetHostnameHistoryValidateLimitMinimum() = runTest {
        val hostnamesApi = createHostnamesApi()

        assertFailsWith<IllegalArgumentException> {
            hostnamesApi.getHostnameHistory("example.com", limit = 5)
        }
    }

    @Test
    fun testGetHostnameHistoryValidateLimitMaximum() = runTest {
        val hostnamesApi = createHostnamesApi()

        assertFailsWith<IllegalArgumentException> {
            hostnamesApi.getHostnameHistory("example.com", limit = 15000)
        }
    }

    @Test
    fun testGetHostnameHistoryValidateLimitAtMinimumBoundary() = runTest {
        val schema = createHostnameHistorySchema()

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        // Should not throw - 10 is valid
        hostnamesApi.getHostnameHistory("example.com", limit = 10)
    }

    @Test
    fun testGetHostnameHistoryValidateLimitAtMaximumBoundary() = runTest {
        val schema = createHostnameHistorySchema()

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        hostnamesApi.getHostnameHistory("example.com", limit = 10000)
    }


    @Test
    fun testGetHostnameHistoryAuthenticationError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
    }

    @Test
    fun testGetHostnameHistoryForbiddenError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
    }

    @Test
    fun testGetHostnameHistoryNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Hostname not found"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            hostnamesApi.getHostnameHistory("nonexistent.example.com")
        }
    }

    @Test
    fun testGetHostnameHistoryRateLimitError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Too Many Requests"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Retry-After" to listOf("90")
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
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
        assertEquals(90L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetHostnameHistoryServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
    }

    @Test
    fun testGetHostnameHistoryBadRequest() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
    }

    @Test
    fun testGetHostnameHistoryServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            hostnamesApi.getHostnameHistory("example.com")
        }
    }

    // ============= COMPLEX SCENARIO TESTS =============

    @Test
    fun testGetHostnameHistoryLargeDataset() = runTest {
        val results = (1..1000).map { i ->
            createHostnameHistoryResult(
                seenOn = "2025-01-${((i % 28) + 1).toString().padStart(2, '0')}T${(i % 24).toString().padStart(2, '0')}:30:00Z",
                subId = "sub-$i",
                source = HostnameHistorySource.values()[i % HostnameHistorySource.values().size]
            )
        }
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com", limit = 10000)

        assertEquals(1000, response.results.size)
        assertEquals("sub-1", response.results[0].subId)
        assertEquals("sub-1000", response.results[999].subId)
    }

    @Test
    fun testGetHostnameHistoryWithComplexPagination() = runTest {
        val batch1 = (1..100).map { i ->
            createHostnameHistoryResult(subId = "batch1-$i")
        }
        val schema1 = createHostnameHistorySchema(
            results = batch1,
            pageState = "batch2-start"
        )

        val mockClient = createMockHttpClient(responseData = schema1)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com", limit = 100, pageState = null)

        assertEquals(100, response.results.size)
        assertEquals("batch2-start", response.pageState)
    }

    @Test
    fun testGetHostnameHistoryWithVariousGeoLocations() = runTest {
        val results = listOf(
            createHostnameHistoryResult(
                data = createHostnameData(geoip = createGeoIpInfo(country = "US", city = "New York"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(geoip = createGeoIpInfo(country = "DE", city = "Berlin"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(geoip = createGeoIpInfo(country = "JP", city = "Tokyo"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(geoip = createGeoIpInfo(country = "AU", city = "Sydney"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(geoip = createGeoIpInfo(country = "IT", city = "Milan"))
            )
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("global-example.com")

        assertEquals(5, response.results.size)
        assertEquals("US", response.results[0].data?.geoip?.country)
        assertEquals("DE", response.results[1].data?.geoip?.country)
        assertEquals("JP", response.results[2].data?.geoip?.country)
        assertEquals("AU", response.results[3].data?.geoip?.country)
        assertEquals("IT", response.results[4].data?.geoip?.country)
    }

    @Test
    fun testGetHostnameHistoryWithVariousAsnData() = runTest {
        val results = listOf(
            createHostnameHistoryResult(
                data = createHostnameData(asn = createAsnInfo(asn = "AS15169", description = "Google"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(asn = createAsnInfo(asn = "AS8075", description = "Microsoft"))
            ),
            createHostnameHistoryResult(
                data = createHostnameData(asn = createAsnInfo(asn = "AS16509", description = "Amazon"))
            )
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(3, response.results.size)
        assertEquals("AS15169", response.results[0].data?.asn?.asn)
        assertEquals("AS8075", response.results[1].data?.asn?.asn)
        assertEquals("AS16509", response.results[2].data?.asn?.asn)
    }

    @Test
    fun testGetHostnameHistorySubdomains() = runTest {
        val results = listOf(
            createHostnameHistoryResult(),
            createHostnameHistoryResult(subId = "www"),
            createHostnameHistoryResult(subId = "api"),
            createHostnameHistoryResult(subId = "mail"),
            createHostnameHistoryResult(subId = "ftp")
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(5, response.results.size)
    }

    @Test
    fun testGetHostnameHistoryTimeSeriesData() = runTest {
        val results = listOf(
            createHostnameHistoryResult(
                seenOn = "2024-01-01T00:00:00Z",
                firstSeen = "2024-01-01T00:00:00Z",
                lastSeen = "2024-01-01T23:59:59Z"
            ),
            createHostnameHistoryResult(
                seenOn = "2024-06-01T00:00:00Z",
                firstSeen = "2024-06-01T00:00:00Z",
                lastSeen = "2024-06-30T23:59:59Z"
            ),
            createHostnameHistoryResult(
                seenOn = "2025-01-20T10:30:00Z",
                firstSeen = "2025-01-20T00:00:00Z",
                lastSeen = "2025-01-20T23:59:59Z"
            )
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(3, response.results.size)
        assertTrue(response.results[0].seenOn < response.results[1].seenOn)
        assertTrue(response.results[1].seenOn < response.results[2].seenOn)
    }

    @Test
    fun testGetHostnameHistoryNullDataFields() = runTest {
        val result = createHostnameHistoryResult(
            dataType = null,
            data = null
        )
        val schema = createHostnameHistorySchema(results = listOf(result))

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(1, response.results.size)
        assertNull(response.results[0].dataType)
        assertNull(response.results[0].data)
    }

    @Test
    fun testGetHostnameHistoryMixedNullAndNonNullData() = runTest {
        val results = listOf(
            createHostnameHistoryResult(data = createHostnameData()),
            createHostnameHistoryResult(data = null),
            createHostnameHistoryResult(data = createHostnameData()),
            createHostnameHistoryResult(data = null)
        )
        val schema = createHostnameHistorySchema(results = results)

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertEquals(4, response.results.size)
        assertNotNull(response.results[0].data)
        assertNull(response.results[1].data)
        assertNotNull(response.results[2].data)
        assertNull(response.results[3].data)
    }

    @Test
    fun testGetHostnameHistoryDeserializationIntegrity() = runTest {
        val geoip = createGeoIpInfo(
            country = "FR",
            region = "Île-de-France",
            timezone = "Europe/Paris",
            city = "Paris",
            ll = listOf(48.8566, 2.3522),
            countryName = "France",
            metro = null,
            area = 75
        )
        val asn = createAsnInfo(
            ip = "192.0.2.1",
            asn = "AS3352",
            country = "FR",
            registrar = "RIPENCC",
            date = "2020-01-15",
            description = "Orange Telecom France",
            route = "192.0.0.0/24",
            name = "ORANGE"
        )
        val data = createHostnameData(
            ttl = 3600,
            rdata = "192.0.2.1",
            geoip = geoip,
            asn = asn
        )
        val result = createHostnameHistoryResult(
            seenOn = "2025-01-20T14:30:00Z",
            source = HostnameHistorySource.PDNS,
            subId = "mail",
            firstSeen = "2024-06-01T00:00:00Z",
            lastSeen = "2025-01-20T14:30:00Z",
            dataType = "MX",
            data = data
        )
        val schema = createHostnameHistorySchema(results = listOf(result))

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        val resResult = response.results[0]
        assertEquals("2025-01-20T14:30:00Z", resResult.seenOn)
        assertEquals(HostnameHistorySource.PDNS, resResult.source)
        assertEquals("mail", resResult.subId)
        assertEquals("2024-06-01T00:00:00Z", resResult.firstSeen)
        assertEquals("2025-01-20T14:30:00Z", resResult.lastSeen)
        assertEquals("MX", resResult.dataType)
        assertNotNull(resResult.data)
        assertEquals(3600, resResult.data.ttl)
        assertEquals("192.0.2.1", resResult.data.rdata)
        assertEquals("FR", resResult.data.geoip.country)
        assertEquals("Paris", resResult.data.geoip.city)
        assertEquals("AS3352", resResult.data.asn.asn)
        assertEquals("Orange Telecom France", resResult.data.asn.description)
    }

    @Test
    fun testGetHostnameHistoryDefaultParameters() = runTest {
        val schema = createHostnameHistorySchema()

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        val response = hostnamesApi.getHostnameHistory("example.com")

        assertNotNull(response)
    }

    @Test
    fun testGetHostnameHistoryComplexHostnames() = runTest {
        val hostnames = listOf(
            "example.com",
            "sub.example.com",
            "deep.sub.example.com",
            "xn--bcher-kva.example.com",
            "example-with-dashes.com",
            "example_with_underscores.com"
        )
        val schema = createHostnameHistorySchema()

        val mockClient = createMockHttpClient(responseData = schema)
        val hostnamesApi = createHostnamesApi(httpClient = mockClient)

        hostnames.forEach { hostname ->
            hostnamesApi.getHostnameHistory(hostname)
        }
    }
}