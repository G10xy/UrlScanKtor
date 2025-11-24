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
import io.urlscan.client.model.IncidentCreationMode
import io.urlscan.client.model.IncidentWatchKey
import io.urlscan.client.model.Subscription
import io.urlscan.client.model.SubscriptionFrequency
import io.urlscan.client.model.SubscriptionResponse
import io.urlscan.client.model.TeamPermission
import io.urlscan.client.model.WeekDay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionsApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createSubscription(
        id: String? = "subscription-1",
        searchIds: List<String> = listOf("search-1"),
        frequency: SubscriptionFrequency = SubscriptionFrequency.DAILY,
        emailAddresses: List<String> = listOf("user@example.com"),
        name: String = "Test Subscription",
        description: String? = null,
        isActive: Boolean = true,
        ignoreTime: Boolean = false,
        weekDays: List<WeekDay>? = null,
        permissions: List<TeamPermission>? = null,
        channelIds: List<String>? = null,
        incidentChannelIds: List<String>? = null,
        incidentProfileId: String? = null,
        incidentVisibility: String? = null,
        incidentCreationMode: IncidentCreationMode? = null,
        incidentWatchKeys: IncidentWatchKey? = null
    ): Subscription {
        return Subscription(
            id = id,
            searchIds = searchIds,
            frequency = frequency,
            emailAddresses = emailAddresses,
            name = name,
            description = description,
            isActive = isActive,
            ignoreTime = ignoreTime,
            weekDays = weekDays,
            permissions = permissions,
            channelIds = channelIds,
            incidentChannelIds = incidentChannelIds,
            incidentProfileId = incidentProfileId,
            incidentVisibility = incidentVisibility,
            incidentCreationMode = incidentCreationMode,
            incidentWatchKeys = incidentWatchKeys
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

    private fun createSubscriptionsApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): SubscriptionsApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return SubscriptionsApi(httpClient, config)
    }

    @Test
    fun testGetSubscriptions() = runTest {
        val subscriptions = listOf(
            createSubscription(
                id = "sub-1",
                name = "Daily Phishing Check",
                frequency = SubscriptionFrequency.DAILY
            ),
            createSubscription(
                id = "sub-2",
                name = "Hourly Monitor",
                frequency = SubscriptionFrequency.HOURLY
            ),
            createSubscription(
                id = "sub-3",
                name = "Live Alerts",
                frequency = SubscriptionFrequency.LIVE
            )
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptions()

        assertEquals(3, result.size)
        assertEquals("sub-1", result[0].id)
        assertEquals("Daily Phishing Check", result[0].name)
        assertEquals(SubscriptionFrequency.LIVE, result[2].frequency)
    }

    @Test
    fun testGetSubscriptionsEmpty() = runTest {
        val mockClient = createMockHttpClient(responseData = emptyList<Subscription>())
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateSubscriptionBasic() = runTest {
        val subscription = createSubscription(
            id = null,
            name = "New Subscription",
            searchIds = listOf("search-1"),
            frequency = SubscriptionFrequency.DAILY,
            emailAddresses = listOf("alert@example.com")
        )
        val responseSubscription = subscription.copy(id = "sub-new-1")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals("sub-new-1", result.id)
        assertEquals("New Subscription", result.name)
        assertEquals(SubscriptionFrequency.DAILY, result.frequency)
    }

    @Test
    fun testCreateSubscriptionWithAllFields() = runTest {
        val subscription = createSubscription(
            id = null,
            name = "Complete Subscription",
            searchIds = listOf("search-1", "search-2", "search-3"),
            frequency = SubscriptionFrequency.HOURLY,
            emailAddresses = listOf("admin@example.com", "security@example.com"),
            description = "Complete subscription with all fields",
            isActive = true,
            ignoreTime = false,
            weekDays = WeekDay.entries,
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE),
            channelIds = listOf("channel-1", "channel-2"),
            incidentChannelIds = listOf("incident-channel-1"),
            incidentProfileId = "profile-1",
            incidentVisibility = "private",
            incidentCreationMode = IncidentCreationMode.DEFAULT,
            incidentWatchKeys = IncidentWatchKey.SCANS_PAGE_URL
        )
        val responseSubscription = subscription.copy(id = "sub-complete")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals("sub-complete", result.id)
        assertEquals(3, result.searchIds.size)
        assertEquals(2, result.emailAddresses.size)
        assertEquals(7, result.weekDays?.size)
        assertEquals(IncidentCreationMode.DEFAULT, result.incidentCreationMode)
    }

    @Test
    fun testCreateSubscriptionValidatesSearchIds() = runTest {
        val subscriptionsApi = createSubscriptionsApi()

        assertFailsWith<IllegalArgumentException> {
            val subscription = createSubscription(searchIds = emptyList())
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testCreateSubscriptionValidatesEmails() = runTest {
        val subscriptionsApi = createSubscriptionsApi()

        assertFailsWith<IllegalArgumentException> {
            val subscription = createSubscription(emailAddresses = emptyList())
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testCreateSubscriptionValidatesName() = runTest {
        val subscriptionsApi = createSubscriptionsApi()

        assertFailsWith<IllegalArgumentException> {
            val subscription = createSubscription(name = "")
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testUpdateSubscription() = runTest {
        val subscription = createSubscription(
            id = "sub-1",
            name = "Updated Subscription",
            frequency = SubscriptionFrequency.HOURLY
        )
        val response = SubscriptionResponse(subscription = subscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.updateSubscription("sub-1", subscription)

        assertEquals("sub-1", result.id)
        assertEquals("Updated Subscription", result.name)
        assertEquals(SubscriptionFrequency.HOURLY, result.frequency)
    }

    @Test
    fun testUpdateSubscriptionChangeFrequency() = runTest {
        val subscription = createSubscription(
            id = "sub-1",
            frequency = SubscriptionFrequency.LIVE
        )
        val response = SubscriptionResponse(subscription = subscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.updateSubscription("sub-1", subscription)

        assertEquals(SubscriptionFrequency.LIVE, result.frequency)
    }

    @Test
    fun testDeleteSubscription() = runTest {
        val mockClient = createMockHttpClient<Unit>(statusCode = HttpStatusCode.NoContent)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        subscriptionsApi.deleteSubscription("sub-1")
    }

    @Test
    fun testGetSubscriptionResults() = runTest {
        val resultUrl = """https://urlscan.io/api/v1/search?q=search_query"""

        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = resultUrl,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/plain"))
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
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionResults("sub-1", "scans")

        assertTrue(result.contains("urlscan.io"))
    }

    @Test
    fun testGetSubscriptionById() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", name = "First"),
            createSubscription(id = "sub-2", name = "Second"),
            createSubscription(id = "sub-3", name = "Third")
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionById("sub-2")

        assertEquals("sub-2", result.id)
        assertEquals("Second", result.name)
    }

    @Test
    fun testGetSubscriptionByIdNotFound() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1"),
            createSubscription(id = "sub-2")
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            subscriptionsApi.getSubscriptionById("sub-nonexistent")
        }
    }

    @Test
    fun testGetActiveSubscriptions() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", isActive = true),
            createSubscription(id = "sub-2", isActive = false),
            createSubscription(id = "sub-3", isActive = true),
            createSubscription(id = "sub-4", isActive = false)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getActiveSubscriptions()

        assertEquals(2, result.size)
        assertTrue(result.all { it.isActive })
    }

    @Test
    fun testGetInactiveSubscriptions() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", isActive = true),
            createSubscription(id = "sub-2", isActive = false),
            createSubscription(id = "sub-3", isActive = true),
            createSubscription(id = "sub-4", isActive = false)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getInactiveSubscriptions()

        assertEquals(2, result.size)
        assertTrue(result.all { !it.isActive })
    }

    @Test
    fun testGetSubscriptionsByFrequencyDaily() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", frequency = SubscriptionFrequency.DAILY),
            createSubscription(id = "sub-2", frequency = SubscriptionFrequency.HOURLY),
            createSubscription(id = "sub-3", frequency = SubscriptionFrequency.DAILY),
            createSubscription(id = "sub-4", frequency = SubscriptionFrequency.LIVE)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.DAILY)

        assertEquals(2, result.size)
        assertTrue(result.all { it.frequency == SubscriptionFrequency.DAILY })
    }

    @Test
    fun testGetSubscriptionsByFrequencyHourly() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", frequency = SubscriptionFrequency.DAILY),
            createSubscription(id = "sub-2", frequency = SubscriptionFrequency.HOURLY),
            createSubscription(id = "sub-3", frequency = SubscriptionFrequency.HOURLY)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.HOURLY)

        assertEquals(2, result.size)
        assertTrue(result.all { it.frequency == SubscriptionFrequency.HOURLY })
    }

    @Test
    fun testGetSubscriptionsByFrequencyLive() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", frequency = SubscriptionFrequency.DAILY),
            createSubscription(id = "sub-2", frequency = SubscriptionFrequency.LIVE),
            createSubscription(id = "sub-3", frequency = SubscriptionFrequency.LIVE)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.LIVE)

        assertEquals(2, result.size)
        assertTrue(result.all { it.frequency == SubscriptionFrequency.LIVE })
    }

    @Test
    fun testGetSubscriptionsBySearchId() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", searchIds = listOf("search-1", "search-2")),
            createSubscription(id = "sub-2", searchIds = listOf("search-2", "search-3")),
            createSubscription(id = "sub-3", searchIds = listOf("search-1")),
            createSubscription(id = "sub-4", searchIds = listOf("search-3", "search-4"))
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionsBySearchId("search-1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.searchIds.contains("search-1") })
    }

    @Test
    fun testGetSubscriptionsByChannelId() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", channelIds = listOf("channel-1", "channel-2")),
            createSubscription(id = "sub-2", channelIds = listOf("channel-2", "channel-3")),
            createSubscription(id = "sub-3", channelIds = listOf("channel-1")),
            createSubscription(id = "sub-4", channelIds = null)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptionsByChannelId("channel-1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.channelIds?.contains("channel-1") == true })
    }

    @Test
    fun testCreateSubscriptionAuthenticationError() = runTest {
        val subscription = createSubscription()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testCreateSubscriptionForbiddenError() = runTest {
        val subscription = createSubscription()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testUpdateSubscriptionNotFoundError() = runTest {
        val subscription = createSubscription()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Subscription not found"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            subscriptionsApi.updateSubscription("nonexistent", subscription)
        }
    }

    @Test
    fun testDeleteSubscriptionNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Subscription not found"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            subscriptionsApi.deleteSubscription("nonexistent")
        }
    }

    @Test
    fun testGetSubscriptionResultsNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Subscription not found"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            subscriptionsApi.getSubscriptionResults("nonexistent", "scans")
        }
    }

    @Test
    fun testCreateSubscriptionRateLimitError() = runTest {
        val subscription = createSubscription()
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"error": "Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("60")
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
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            subscriptionsApi.createSubscription(subscription)
        }
        assertEquals(60L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetSubscriptionsServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            subscriptionsApi.getSubscriptions()
        }
    }

    @Test
    fun testCreateSubscriptionBadRequest() = runTest {
        val subscription = createSubscription()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Invalid subscription"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            subscriptionsApi.createSubscription(subscription)
        }
    }

    @Test
    fun testGetSubscriptionsServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            subscriptionsApi.getSubscriptions()
        }
    }

    @Test
    fun testCreateSubscriptionWithMultipleSearches() = runTest {
        val subscription = createSubscription(
            id = null,
            searchIds = listOf("search-1", "search-2", "search-3", "search-4", "search-5")
        )
        val responseSubscription = subscription.copy(id = "sub-multi")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals(5, result.searchIds.size)
    }

    @Test
    fun testCreateSubscriptionWithMultipleEmails() = runTest {
        val subscription = createSubscription(
            id = null,
            emailAddresses = listOf(
                "admin@example.com",
                "security@example.com",
                "alerts@example.com",
                "team@example.com"
            )
        )
        val responseSubscription = subscription.copy(id = "sub-emails")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals(4, result.emailAddresses.size)
    }

    @Test
    fun testCreateSubscriptionWithWeekDays() = runTest {
        val subscription = createSubscription(
            id = null,
            weekDays = WeekDay.entries
        )
        val responseSubscription = subscription.copy(id = "sub-weekdays")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals(7, result.weekDays?.size)
    }

    @Test
    fun testCreateSubscriptionWithPermissions() = runTest {
        val subscription = createSubscription(
            id = null,
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val responseSubscription = subscription.copy(id = "sub-perms")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals(2, result.permissions?.size)
    }

    @Test
    fun testUpdateSubscriptionAddSearches() = runTest {
        val subscription = createSubscription(
            id = "sub-1",
            searchIds = listOf("search-1", "search-2", "search-3")
        )
        val response = SubscriptionResponse(subscription = subscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.updateSubscription("sub-1", subscription)

        assertEquals(3, result.searchIds.size)
    }

    @Test
    fun testUpdateSubscriptionChangeActive() = runTest {
        val activeSubscription = createSubscription(id = "sub-1", isActive = true)
        val inactiveSubscription = createSubscription(id = "sub-1", isActive = false)

        val mockClient1 = createMockHttpClient(responseData = SubscriptionResponse(activeSubscription))
        val mockClient2 = createMockHttpClient(responseData = SubscriptionResponse(inactiveSubscription))

        val api1 = createSubscriptionsApi(httpClient = mockClient1)
        val api2 = createSubscriptionsApi(httpClient = mockClient2)

        val resultActive = api1.updateSubscription("sub-1", activeSubscription)
        val resultInactive = api2.updateSubscription("sub-1", inactiveSubscription)

        assertTrue(resultActive.isActive)
        assertFalse(resultInactive.isActive)
    }

    @Test
    fun testSubscriptionWithIncidentCreation() = runTest {
        val subscription = createSubscription(
            id = null,
            incidentProfileId = "profile-123",
            incidentCreationMode = IncidentCreationMode.ALWAYS,
            incidentVisibility = "private",
            incidentChannelIds = listOf("channel-1", "channel-2")
        )
        val responseSubscription = subscription.copy(id = "sub-incident")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertEquals("profile-123", result.incidentProfileId)
        assertEquals(IncidentCreationMode.ALWAYS, result.incidentCreationMode)
        assertEquals("private", result.incidentVisibility)
    }

    @Test
    fun testSubscriptionWithDifferentIncidentModes() = runTest {
        val modes = listOf(
            IncidentCreationMode.NONE,
            IncidentCreationMode.DEFAULT,
            IncidentCreationMode.ALWAYS,
            IncidentCreationMode.IGNORE_IF_EXISTS
        )

        modes.forEachIndexed { index, mode ->
            val subscription = createSubscription(
                id = null,
                incidentCreationMode = mode
            )
            val responseSubscription = subscription.copy(id = "sub-mode-$index")
            val response = SubscriptionResponse(subscription = responseSubscription)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createSubscriptionsApi(httpClient = mockClient)

            val result = api.createSubscription(subscription)
            assertEquals(mode, result.incidentCreationMode)
        }
    }

    @Test
    fun testSubscriptionWithDifferentWatchKeys() = runTest {
        val watchKeys = listOf(
            IncidentWatchKey.SCANS_PAGE_URL,
            IncidentWatchKey.SCANS_PAGE_DOMAIN,
            IncidentWatchKey.SCANS_PAGE_IP,
            IncidentWatchKey.HOSTNAMES_HOSTNAME
        )

        watchKeys.forEachIndexed { index, key ->
            val subscription = createSubscription(
                id = null,
                incidentWatchKeys = key
            )
            val responseSubscription = subscription.copy(id = "sub-key-$index")
            val response = SubscriptionResponse(subscription = responseSubscription)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createSubscriptionsApi(httpClient = mockClient)

            val result = api.createSubscription(subscription)
            assertEquals(key, result.incidentWatchKeys)
        }
    }

    @Test
    fun testSubscriptionWorkflow() = runTest {
        // Create subscription
        val createSub = createSubscription(
            id = null,
            name = "Workflow Subscription",
            frequency = SubscriptionFrequency.DAILY
        )
        val createResponse = SubscriptionResponse(subscription = createSub.copy(id = "sub-workflow"))
        val mockClient1 = createMockHttpClient(responseData = createResponse)
        val api1 = createSubscriptionsApi(httpClient = mockClient1)

        val created = api1.createSubscription(createSub)
        assertEquals("sub-workflow", created.id)

        // Update subscription
        val updateSub = created.copy(
            name = "Updated Workflow Subscription",
            frequency = SubscriptionFrequency.HOURLY
        )
        val updateResponse = SubscriptionResponse(subscription = updateSub)
        val mockClient2 = createMockHttpClient(responseData = updateResponse)
        val api2 = createSubscriptionsApi(httpClient = mockClient2)

        val updated = api2.updateSubscription("sub-workflow", updateSub)
        assertEquals("Updated Workflow Subscription", updated.name)
        assertEquals(SubscriptionFrequency.HOURLY, updated.frequency)

        // Get by ID
        val subscriptions = listOf(updated)
        val mockClient3 = createMockHttpClient(responseData = subscriptions)
        val api3 = createSubscriptionsApi(httpClient = mockClient3)

        val retrieved = api3.getSubscriptionById("sub-workflow")
        assertEquals("sub-workflow", retrieved.id)
    }

    @Test
    fun testSubscriptionDeserialization() = runTest {
        val originalSubscription = createSubscription(
            id = "deser-sub-123",
            searchIds = listOf("search-1", "search-2"),
            frequency = SubscriptionFrequency.HOURLY,
            emailAddresses = listOf("test@example.com", "alert@example.com"),
            name = "Deserialization Test",
            description = "Test description",
            isActive = true,
            ignoreTime = false,
            weekDays = listOf(WeekDay.MONDAY, WeekDay.WEDNESDAY, WeekDay.FRIDAY),
            permissions = listOf(TeamPermission.TEAM_READ),
            channelIds = listOf("channel-1"),
            incidentCreationMode = IncidentCreationMode.DEFAULT
        )
        val mockClient = createMockHttpClient(responseData = listOf(originalSubscription))
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.getSubscriptions()[0]

        assertEquals("deser-sub-123", result.id)
        assertEquals(2, result.searchIds.size)
        assertEquals(SubscriptionFrequency.HOURLY, result.frequency)
        assertEquals(2, result.emailAddresses.size)
        assertEquals("Deserialization Test", result.name)
        assertEquals(true, result.isActive)
        assertEquals(3, result.weekDays?.size)
        assertEquals(1, result.permissions?.size)
    }

    @Test
    fun testGetSubscriptionsWithMixedActiveStatus() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", isActive = true),
            createSubscription(id = "sub-2", isActive = false),
            createSubscription(id = "sub-3", isActive = true),
            createSubscription(id = "sub-4", isActive = true),
            createSubscription(id = "sub-5", isActive = false)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val active = subscriptionsApi.getActiveSubscriptions()
        val inactive = subscriptionsApi.getInactiveSubscriptions()

        assertEquals(3, active.size)
        assertEquals(2, inactive.size)
        assertEquals(5, subscriptionsApi.getSubscriptions().size)
    }

    @Test
    fun testGetSubscriptionsWithMultipleFrequencies() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", frequency = SubscriptionFrequency.LIVE),
            createSubscription(id = "sub-2", frequency = SubscriptionFrequency.HOURLY),
            createSubscription(id = "sub-3", frequency = SubscriptionFrequency.DAILY),
            createSubscription(id = "sub-4", frequency = SubscriptionFrequency.LIVE),
            createSubscription(id = "sub-5", frequency = SubscriptionFrequency.HOURLY),
            createSubscription(id = "sub-6", frequency = SubscriptionFrequency.DAILY)
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val live = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.LIVE)
        val hourly = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.HOURLY)
        val daily = subscriptionsApi.getSubscriptionsByFrequency(SubscriptionFrequency.DAILY)

        assertEquals(2, live.size)
        assertEquals(2, hourly.size)
        assertEquals(2, daily.size)
    }

    @Test
    fun testGetSubscriptionsBySearchIdMultipleSearches() = runTest {
        val subscriptions = listOf(
            createSubscription(id = "sub-1", searchIds = listOf("search-1", "search-2", "search-3")),
            createSubscription(id = "sub-2", searchIds = listOf("search-2", "search-4")),
            createSubscription(id = "sub-3", searchIds = listOf("search-1")),
            createSubscription(id = "sub-4", searchIds = listOf("search-3", "search-5"))
        )

        val mockClient = createMockHttpClient(responseData = subscriptions)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val search1 = subscriptionsApi.getSubscriptionsBySearchId("search-1")
        val search2 = subscriptionsApi.getSubscriptionsBySearchId("search-2")
        val search3 = subscriptionsApi.getSubscriptionsBySearchId("search-3")
        val search5 = subscriptionsApi.getSubscriptionsBySearchId("search-5")

        assertEquals(2, search1.size)
        assertEquals(2, search2.size)
        assertEquals(2, search3.size)
        assertEquals(1, search5.size)
    }

    @Test
    fun testDeleteSubscriptionMultiple() = runTest {
        val ids = listOf("sub-1", "sub-2", "sub-3")

        ids.forEach { id ->
            val mockClient = createMockHttpClient<Unit>(statusCode = HttpStatusCode.NoContent)
            val api = createSubscriptionsApi(httpClient = mockClient)
            api.deleteSubscription(id)
        }

        assertTrue(true)
    }

    @Test
    fun testCreateSubscriptionIgnoreTime() = runTest {
        val subscription = createSubscription(
            id = null,
            ignoreTime = true
        )
        val responseSubscription = subscription.copy(id = "sub-ignore")
        val response = SubscriptionResponse(subscription = responseSubscription)

        val mockClient = createMockHttpClient(responseData = response)
        val subscriptionsApi = createSubscriptionsApi(httpClient = mockClient)

        val result = subscriptionsApi.createSubscription(subscription)

        assertTrue(result.ignoreTime)
    }

    @Test
    fun testGetSubscriptionResultsDifferentDatasources() = runTest {
        val scansUrl = """https://urlscan.io/api/v1/search?datasource=scans"""
        val hostnamesUrl = """https://urlscan.io/api/v1/search?datasource=hostnames"""

        val mockClient1 = HttpClient(MockEngine { request ->
            respond(
                content = scansUrl,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/plain"))
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
                content = hostnamesUrl,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType to listOf("text/plain"))
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

        val api1 = createSubscriptionsApi(httpClient = mockClient1)
        val api2 = createSubscriptionsApi(httpClient = mockClient2)

        val scansResult = api1.getSubscriptionResults("sub-1", "scans")
        val hostnamesResult = api2.getSubscriptionResults("sub-1", "hostnames")

        assertTrue(scansResult.contains("scans"))
        assertTrue(hostnamesResult.contains("hostnames"))
    }
}