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
import io.urlscan.client.model.Incident
import io.urlscan.client.model.IncidentResponse
import io.urlscan.client.model.IncidentState
import io.urlscan.client.model.IncidentStateEnum
import io.urlscan.client.model.IncidentStatesResponse
import io.urlscan.client.model.IncidentTypeEnum
import io.urlscan.client.model.IncidentVisibility
import io.urlscan.client.model.ScanIntervalModeEnum
import io.urlscan.client.model.SourceTypeEnum
import io.urlscan.client.model.WatchableAttributesResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IncidentsApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createIncident(
        id: String? = "incident-1",
        observable: String = "example.com",
        visibility: IncidentVisibility = IncidentVisibility.PRIVATE,
        channels: List<String> = listOf("channel-1"),
        scanInterval: Int? = 3600,
        scanIntervalMode: ScanIntervalModeEnum? = ScanIntervalModeEnum.AUTOMATIC,
        watchedAttributes: List<String>? = listOf("detections", "tls"),
        userAgents: List<String>? = null,
        userAgentsPerInterval: Int? = 1,
        countries: List<String>? = listOf("US"),
        countriesPerInterval: Int? = 1,
        stopDelaySuspended: Int? = null,
        stopDelayInactive: Int? = null,
        stopDelayMalicious: Int? = null,
        scanIntervalAfterSuspended: Int? = null,
        scanIntervalAfterMalicious: Int? = null,
        incidentProfile: String? = null,
        type: IncidentTypeEnum? = IncidentTypeEnum.HOSTNAME,
        state: IncidentStateEnum? = IncidentStateEnum.ACTIVE,
        stateSize: Int? = 1024,
        stateCount: Int? = 5,
        owner: String? = "user@example.com",
        sourceType: SourceTypeEnum? = SourceTypeEnum.MANUAL,
        sourceId: String? = null,
        createdAt: String? = "2025-01-01T00:00:00Z",
        scannedAt: String? = "2025-01-20T12:00:00Z",
        expireAt: String? = "2025-02-01T00:00:00Z",
        labels: List<String>? = null
    ): Incident {
        return Incident(
            id = id,
            observable = observable,
            visibility = visibility,
            channels = channels,
            scanInterval = scanInterval,
            scanIntervalMode = scanIntervalMode,
            watchedAttributes = watchedAttributes,
            userAgents = userAgents,
            userAgentsPerInterval = userAgentsPerInterval,
            countries = countries,
            countriesPerInterval = countriesPerInterval,
            stopDelaySuspended = stopDelaySuspended,
            stopDelayInactive = stopDelayInactive,
            stopDelayMalicious = stopDelayMalicious,
            scanIntervalAfterSuspended = scanIntervalAfterSuspended,
            scanIntervalAfterMalicious = scanIntervalAfterMalicious,
            incidentProfile = incidentProfile,
            type = type,
            state = state,
            stateSize = stateSize,
            stateCount = stateCount,
            owner = owner,
            sourceType = sourceType,
            sourceId = sourceId,
            createdAt = createdAt,
            scannedAt = scannedAt,
            expireAt = expireAt,
            labels = labels
        )
    }

    private fun createIncidentResponse(incident: Incident): IncidentResponse {
        return IncidentResponse(incident = incident)
    }

    private fun createIncidentState(
        id: String = "state-1",
        incident: String = "incident-1",
        timeStart: String = "2025-01-20T12:00:00Z",
        timeEnd: String = "2025-01-20T13:00:00Z"
    ): IncidentState {
        return IncidentState(
            id = id,
            incident = incident,
            state = null,
            timeStart = timeStart,
            timeEnd = timeEnd
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

    private fun createIncidentsApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): IncidentsApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return IncidentsApi(httpClient, config)
    }

    @Test
    fun testCreateIncidentWithAllFields() = runTest {
        val incident = createIncident(
            id = null,
            observable = "phishing.example.com",
            visibility = IncidentVisibility.PRIVATE,
            channels = listOf("channel-1", "channel-2"),
            scanInterval = 7200,
            scanIntervalMode = ScanIntervalModeEnum.AUTOMATIC,
            watchedAttributes = listOf("detections", "tls", "dns"),
            countries = listOf("US", "UK"),
            countriesPerInterval = 2
        )
        val responseIncident = incident.copy(
            id = "incident-new-123",
            state = IncidentStateEnum.ACTIVE,
            type = IncidentTypeEnum.HOSTNAME,
            owner = "user@example.com",
            sourceType = SourceTypeEnum.API,
            createdAt = "2025-01-20T12:00:00Z",
            scannedAt = "2025-01-20T12:00:00Z",
            expireAt = "2025-02-20T12:00:00Z"
        )
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals("incident-new-123", result.id)
        assertEquals("phishing.example.com", result.observable)
        assertEquals(IncidentVisibility.PRIVATE, result.visibility)
        assertEquals(2, result.channels.size)
        assertEquals(IncidentStateEnum.ACTIVE, result.state)
    }

    @Test
    fun testCreateIncidentMinimalFields() = runTest {
        val incident = createIncident(
            id = null,
            observable = "example.com",
            visibility = IncidentVisibility.UNLISTED,
            channels = listOf("channel-1"),
            scanInterval = null,
            scanIntervalMode = null,
            watchedAttributes = null,
            countries = null
        )
        val responseIncident = incident.copy(id = "incident-minimal-1")
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals("incident-minimal-1", result.id)
        assertEquals("example.com", result.observable)
    }

    @Test
    fun testCreateIncidentValidatesObservableNotBlank() = runTest {
        val incidentsApi = createIncidentsApi()

        assertFailsWith<IllegalArgumentException> {
            val incident = createIncident(observable = "")
            incidentsApi.createIncident(incident)
        }
    }

    @Test
    fun testCreateIncidentValidatesChannelsNotEmpty() = runTest {
        val incidentsApi = createIncidentsApi()

        assertFailsWith<IllegalArgumentException> {
            val incident = createIncident(channels = emptyList())
            incidentsApi.createIncident(incident)
        }
    }

    @Test
    fun testGetIncident() = runTest {
        val incident = createIncident(
            id = "incident-123",
            observable = "test.com"
        )
        val response = createIncidentResponse(incident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncident("incident-123")

        assertEquals("incident-123", result.id)
        assertEquals("test.com", result.observable)
    }

    @Test
    fun testGetIncidentNotFound() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Incident not found"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            incidentsApi.getIncident("nonexistent-incident")
        }
    }

    @Test
    fun testUpdateIncident() = runTest {
        val updatedIncident = createIncident(
            id = "incident-123",
            scanInterval = 10800,
            watchedAttributes = listOf("detections", "tls", "dns", "page")
        )
        val response = createIncidentResponse(updatedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.updateIncident(
            "incident-123",
            updatedIncident
        )

        assertEquals(10800, result.scanInterval)
        assertEquals(4, result.watchedAttributes?.size)
    }

    @Test
    fun testUpdateIncidentScanInterval() = runTest {
        val incident = createIncident(
            id = "incident-123",
            scanInterval = 5400
        )
        val response = createIncidentResponse(incident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.updateIncident("incident-123", incident)

        assertEquals(5400, result.scanInterval)
    }

    @Test
    fun testCloseIncident() = runTest {
        val closedIncident = createIncident(
            id = "incident-123",
            state = IncidentStateEnum.CLOSED
        )
        val response = createIncidentResponse(closedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.closeIncident("incident-123")

        assertEquals(IncidentStateEnum.CLOSED, result.state)
    }

    @Test
    fun testRestartIncident() = runTest {
        val restartedIncident = createIncident(
            id = "incident-123",
            state = IncidentStateEnum.ACTIVE,
            scannedAt = "2025-01-20T14:00:00Z"
        )
        val response = createIncidentResponse(restartedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.restartIncident("incident-123")

        assertEquals(IncidentStateEnum.ACTIVE, result.state)
        assertNotNull(result.scannedAt)
    }

    @Test
    fun testCopyIncident() = runTest {
        val originalIncident = createIncident(
            id = "incident-123",
            observable = "original.com"
        )
        val copiedIncident = originalIncident.copy(
            id = "incident-copy-456",
            sourceType = SourceTypeEnum.COPY,
            sourceId = "incident-123"
        )
        val response = createIncidentResponse(copiedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.copyIncident("incident-123")

        assertEquals("incident-copy-456", result.id)
        assertEquals("original.com", result.observable)
        assertEquals(SourceTypeEnum.COPY, result.sourceType)
        assertEquals("incident-123", result.sourceId)
    }

    @Test
    fun testForkIncident() = runTest {
        val originalIncident = createIncident(
            id = "incident-123",
            observable = "fork-source.com"
        )
        val forkedIncident = originalIncident.copy(
            id = "incident-fork-789",
            sourceType = SourceTypeEnum.FORK,
            sourceId = "incident-123"
        )
        val response = createIncidentResponse(forkedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.forkIncident("incident-123")

        assertEquals("incident-fork-789", result.id)
        assertEquals("fork-source.com", result.observable)
        assertEquals(SourceTypeEnum.FORK, result.sourceType)
        assertEquals("incident-123", result.sourceId)
    }

    @Test
    fun testGetWatchableAttributes() = runTest {
        val attributes = listOf(
            "detections",
            "tls",
            "dns",
            "labels",
            "page",
            "meta",
            "ip"
        )
        val response = WatchableAttributesResponse(attributes = attributes)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getWatchableAttributes()

        assertEquals(7, result.size)
        assertTrue(result.contains("detections"))
        assertTrue(result.contains("tls"))
        assertTrue(result.contains("dns"))
        assertTrue(result.contains("page"))
    }

    @Test
    fun testGetIncidentStates() = runTest {
        val states = listOf(
            createIncidentState(
                id = "state-1",
                incident = "incident-123",
                timeStart = "2025-01-20T12:00:00Z",
                timeEnd = "2025-01-20T13:00:00Z"
            ),
            createIncidentState(
                id = "state-2",
                incident = "incident-123",
                timeStart = "2025-01-20T13:00:00Z",
                timeEnd = "2025-01-20T14:00:00Z"
            ),
            createIncidentState(
                id = "state-3",
                incident = "incident-123",
                timeStart = "2025-01-20T14:00:00Z",
                timeEnd = "2025-01-20T15:00:00Z"
            )
        )
        val response = IncidentStatesResponse(incidentstates = states)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncidentStates("incident-123")

        assertEquals(3, result.size)
        assertEquals("state-1", result[0].id)
        assertEquals("incident-123", result[0].incident)
        assertEquals("state-3", result[2].id)
    }

    @Test
    fun testGetIncidentStatesEmpty() = runTest {
        val response = IncidentStatesResponse(incidentstates = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncidentStates("incident-123")

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateIncidentAuthenticationError() = runTest {
        val incident = createIncident()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            incidentsApi.createIncident(incident)
        }
    }

    @Test
    fun testCreateIncidentForbiddenError() = runTest {
        val incident = createIncident()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            incidentsApi.createIncident(incident)
        }
    }

    @Test
    fun testUpdateIncidentNotFoundError() = runTest {
        val incident = createIncident()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Incident not found"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            incidentsApi.updateIncident("nonexistent", incident)
        }
    }

    @Test
    fun testGetIncidentStatesNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Incident not found"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            incidentsApi.getIncidentStates("nonexistent")
        }
    }

    @Test
    fun testCloseIncidentServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            incidentsApi.closeIncident("incident-123")
        }
    }

    @Test
    fun testRestartIncidentServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            incidentsApi.restartIncident("incident-123")
        }
    }

    @Test
    fun testCopyIncidentBadRequest() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            incidentsApi.copyIncident("incident-123")
        }
    }

    @Test
    fun testForkIncidentBadRequest() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            incidentsApi.forkIncident("incident-123")
        }
    }

    @Test
    fun testGetWatchableAttributesRateLimitError() = runTest {
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
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            incidentsApi.getWatchableAttributes()
        }
        assertEquals(120L, exception.retryAfterSeconds)
    }

    @Test
    fun testCreateIncidentWithMultipleChannels() = runTest {
        val incident = createIncident(
            channels = listOf("channel-1", "channel-2", "channel-3")
        )
        val responseIncident = incident.copy(id = "incident-multi-channel")
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals(3, result.channels.size)
        assertTrue(result.channels.contains("channel-1"))
        assertTrue(result.channels.contains("channel-3"))
    }

    @Test
    fun testCreateIncidentWithMultipleCountries() = runTest {
        val incident = createIncident(
            countries = listOf("US", "UK", "DE", "FR", "IT")
        )
        val responseIncident = incident.copy(id = "incident-multi-country")
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals(5, result.countries?.size)
        assertTrue(result.countries?.contains("US") == true)
        assertTrue(result.countries?.contains("IT") == true)
    }

    @Test
    fun testCreateIncidentWithAllWatchedAttributes() = runTest {
        val allAttributes = listOf("detections", "tls", "dns", "labels", "page", "meta", "ip")
        val incident = createIncident(watchedAttributes = allAttributes)
        val responseIncident = incident.copy(id = "incident-all-attrs")
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals(7, result.watchedAttributes?.size)
        assertEquals(result.watchedAttributes?.containsAll(allAttributes), true)
    }

    @Test
    fun testUpdateIncidentChangeVisibility() = runTest {
        val incident = createIncident(
            id = "incident-123",
            visibility = IncidentVisibility.UNLISTED
        )
        val response = createIncidentResponse(incident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.updateIncident("incident-123", incident)

        assertEquals(IncidentVisibility.UNLISTED, result.visibility)
    }

    @Test
    fun testUpdateIncidentAddChannels() = runTest {
        val incident = createIncident(
            id = "incident-123",
            channels = listOf("channel-1", "channel-2", "channel-3", "channel-4")
        )
        val response = createIncidentResponse(incident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.updateIncident("incident-123", incident)

        assertEquals(4, result.channels.size)
    }

    @Test
    fun testGetIncidentWithCompleteMetadata() = runTest {
        val incident = createIncident(
            id = "incident-complete",
            observable = "security-test.com",
            owner = "security-team@example.com",
            sourceType = SourceTypeEnum.SUBSCRIPTION,
            sourceId = "subscription-42",
            createdAt = "2025-01-01T10:00:00Z",
            scannedAt = "2025-01-20T15:30:00Z",
            expireAt = "2025-03-01T10:00:00Z",
            labels = listOf("phishing", "high-priority", "in-progress")
        )
        val response = createIncidentResponse(incident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncident("incident-complete")

        assertEquals("security-test.com", result.observable)
        assertEquals("security-team@example.com", result.owner)
        assertEquals(SourceTypeEnum.SUBSCRIPTION, result.sourceType)
        assertEquals("subscription-42", result.sourceId)
        assertEquals(3, result.labels?.size)
        assertTrue(result.labels?.contains("phishing") == true)
    }

    @Test
    fun testGetIncidentStatesWithMultipleIntervals() = runTest {
        val states = (1..10).map { i ->
            createIncidentState(
                id = "state-$i",
                incident = "incident-123",
                timeStart = "2025-01-20T${i.toString().padStart(12, '0')}:00:00Z",
                timeEnd = "2025-01-20T${i.toString().padStart(13, '0')}:00:00Z"
            )
        }
        val response = IncidentStatesResponse(incidentstates = states)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncidentStates("incident-123")

        assertEquals(10, result.size)
        assertEquals("state-1", result[0].id)
        assertEquals("state-10", result[9].id)
    }

    @Test
    fun testCreateIncidentWithScanIntervalMode() = runTest {
        val manualIncident = createIncident(
            scanIntervalMode = ScanIntervalModeEnum.MANUAL
        )
        val autoIncident = createIncident(
            scanIntervalMode = ScanIntervalModeEnum.AUTOMATIC
        )
        val response1 = createIncidentResponse(manualIncident.copy(id = "incident-manual"))
        val response2 = createIncidentResponse(autoIncident.copy(id = "incident-auto"))

        val mockClient1 = createMockHttpClient(responseData = response1)
        val mockClient2 = createMockHttpClient(responseData = response2)

        val incidentsApi1 = createIncidentsApi(httpClient = mockClient1)
        val incidentsApi2 = createIncidentsApi(httpClient = mockClient2)

        val resultManual = incidentsApi1.createIncident(manualIncident)
        val resultAuto = incidentsApi2.createIncident(autoIncident)

        assertEquals(ScanIntervalModeEnum.MANUAL, resultManual.scanIntervalMode)
        assertEquals(ScanIntervalModeEnum.AUTOMATIC, resultAuto.scanIntervalMode)
    }

    @Test
    fun testCreateIncidentWithCustomScanIntervalDelays() = runTest {
        val incident = createIncident(
            stopDelaySuspended = 86400,
            stopDelayInactive = 604800,
            stopDelayMalicious = 3600,
            scanIntervalAfterSuspended = 86400,
            scanIntervalAfterMalicious = 1800
        )
        val responseIncident = incident.copy(id = "incident-delays")
        val response = createIncidentResponse(responseIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.createIncident(incident)

        assertEquals(86400, result.stopDelaySuspended)
        assertEquals(604800, result.stopDelayInactive)
        assertEquals(3600, result.stopDelayMalicious)
        assertEquals(86400, result.scanIntervalAfterSuspended)
        assertEquals(1800, result.scanIntervalAfterMalicious)
    }

    @Test
    fun testGetIncidentTypeVariations() = runTest {
        val hostIncident = createIncident(
            type = IncidentTypeEnum.HOSTNAME
        ).copy(id = "incident-host")
        val ipIncident = createIncident(
            type = IncidentTypeEnum.IP
        ).copy(id = "incident-ip")
        val urlIncident = createIncident(
            type = IncidentTypeEnum.URL
        ).copy(id = "incident-url")

        val mockClient1 = createMockHttpClient(responseData = createIncidentResponse(hostIncident))
        val mockClient2 = createMockHttpClient(responseData = createIncidentResponse(ipIncident))
        val mockClient3 = createMockHttpClient(responseData = createIncidentResponse(urlIncident))

        val incidentsApi1 = createIncidentsApi(httpClient = mockClient1)
        val incidentsApi2 = createIncidentsApi(httpClient = mockClient2)
        val incidentsApi3 = createIncidentsApi(httpClient = mockClient3)

        val resultHost = incidentsApi1.getIncident("incident-host")
        val resultIp = incidentsApi2.getIncident("incident-ip")
        val resultUrl = incidentsApi3.getIncident("incident-url")

        assertEquals(IncidentTypeEnum.HOSTNAME, resultHost.type)
        assertEquals(IncidentTypeEnum.IP, resultIp.type)
        assertEquals(IncidentTypeEnum.URL, resultUrl.type)
    }

    @Test
    fun testGetIncidentStateCountMetrics() = runTest {
        val stateCountRange = listOf(1, 5, 10, 25, 100)
        stateCountRange.forEach { count ->
            val incident = createIncident(stateCount = count)
            val response = createIncidentResponse(incident)
            val mockClient = createMockHttpClient(responseData = response)
            val incidentsApi = createIncidentsApi(httpClient = mockClient)

            val result = incidentsApi.getIncident("incident-$count")
            assertEquals(count, result.stateCount)
        }
    }

    @Test
    fun testGetWatchableAttributesExtensive() = runTest {
        val attributes = listOf(
            "detections",
            "tls",
            "dns",
            "labels",
            "page",
            "meta",
            "ip",
            "asn",
            "certificates"
        )
        val response = WatchableAttributesResponse(attributes = attributes)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getWatchableAttributes()

        assertEquals(9, result.size)
        assertTrue(result.containsAll(attributes))
    }

    @Test
    fun testCreateIncidentServiceUnavailable() = runTest {
        val incident = createIncident()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            incidentsApi.createIncident(incident)
        }
    }

    @Test
    fun testUpdateIncidentComplexScenario() = runTest {
        val originalIncident = createIncident(
            id = "incident-complex",
            observable = "complex.example.com",
            scanInterval = 3600,
            countries = listOf("US")
        )
        val updatedIncident = originalIncident.copy(
            scanInterval = 7200,
            countries = listOf("US", "UK", "DE"),
            watchedAttributes = listOf("detections", "tls", "dns", "page", "meta"),
            stateCount = 15
        )
        val response = createIncidentResponse(updatedIncident)

        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.updateIncident("incident-complex", updatedIncident)

        assertEquals(7200, result.scanInterval)
        assertEquals(3, result.countries?.size)
        assertEquals(5, result.watchedAttributes?.size)
        assertEquals(15, result.stateCount)
    }

    @Test
    fun testCreateIncidentAndCloseIncidentWorkflow() = runTest {
        val createdIncident = createIncident(
            id = "incident-workflow",
            state = IncidentStateEnum.ACTIVE
        )
        val closedIncident = createdIncident.copy(state = IncidentStateEnum.CLOSED)

        val mockClient1 = createMockHttpClient(responseData = createIncidentResponse(createdIncident))
        val mockClient2 = createMockHttpClient(responseData = createIncidentResponse(closedIncident))

        val incidentsApi1 = createIncidentsApi(httpClient = mockClient1)
        val incidentsApi2 = createIncidentsApi(httpClient = mockClient2)

        val created = incidentsApi1.createIncident(createdIncident.copy(id = null))
        assertEquals(IncidentStateEnum.ACTIVE, created.state)

        val closed = incidentsApi2.closeIncident(created.id!!)
        assertEquals(IncidentStateEnum.CLOSED, closed.state)
    }

    @Test
    fun testCopyAndForkIncidentsComparison() = runTest {
        val original = createIncident(id = "incident-original")
        val copied = original.copy(
            id = "incident-copy",
            sourceType = SourceTypeEnum.COPY,
            sourceId = "incident-original"
        )
        val forked = original.copy(
            id = "incident-fork",
            sourceType = SourceTypeEnum.FORK,
            sourceId = "incident-original"
        )

        val mockClient1 = createMockHttpClient(responseData = createIncidentResponse(copied))
        val mockClient2 = createMockHttpClient(responseData = createIncidentResponse(forked))

        val incidentsApi1 = createIncidentsApi(httpClient = mockClient1)
        val incidentsApi2 = createIncidentsApi(httpClient = mockClient2)

        val copiedResult = incidentsApi1.copyIncident("incident-original")
        val forkedResult = incidentsApi2.forkIncident("incident-original")

        assertEquals(SourceTypeEnum.COPY, copiedResult.sourceType)
        assertEquals(SourceTypeEnum.FORK, forkedResult.sourceType)
        assertEquals(copiedResult.observable, forkedResult.observable)
    }

    @Test
    fun testIncidentDeserialization() = runTest {
        val originalIncident = createIncident(
            id = "test-incident-123",
            observable = "test-observable.com",
            visibility = IncidentVisibility.PRIVATE,
            channels = listOf("channel-1", "channel-2"),
            scanInterval = 5400,
            scanIntervalMode = ScanIntervalModeEnum.AUTOMATIC,
            watchedAttributes = listOf("detections", "tls", "dns"),
            countries = listOf("US", "UK"),
            type = IncidentTypeEnum.HOSTNAME,
            state = IncidentStateEnum.ACTIVE,
            stateCount = 12,
            owner = "test@example.com",
            sourceType = SourceTypeEnum.API,
            labels = listOf("test", "critical")
        )
        val response = createIncidentResponse(originalIncident)
        val mockClient = createMockHttpClient(responseData = response)
        val incidentsApi = createIncidentsApi(httpClient = mockClient)

        val result = incidentsApi.getIncident("test-incident-123")

        assertEquals("test-incident-123", result.id)
        assertEquals("test-observable.com", result.observable)
        assertEquals(IncidentVisibility.PRIVATE, result.visibility)
        assertEquals(2, result.channels.size)
        assertEquals(5400, result.scanInterval)
        assertEquals(ScanIntervalModeEnum.AUTOMATIC, result.scanIntervalMode)
        assertEquals(3, result.watchedAttributes?.size)
        assertEquals(2, result.countries?.size)
        assertEquals(IncidentTypeEnum.HOSTNAME, result.type)
        assertEquals(IncidentStateEnum.ACTIVE, result.state)
        assertEquals(12, result.stateCount)
        assertEquals("test@example.com", result.owner)
        assertEquals(SourceTypeEnum.API, result.sourceType)
        assertEquals(2, result.labels?.size)
    }
}