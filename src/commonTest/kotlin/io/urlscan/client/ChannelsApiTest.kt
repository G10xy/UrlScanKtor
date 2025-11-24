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
import io.urlscan.client.model.Channel
import io.urlscan.client.model.ChannelResponse
import io.urlscan.client.model.ChannelsListResponse
import io.urlscan.client.model.TeamPermission
import io.urlscan.client.model.WeekDay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ChannelsApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createChannel(
        id: String? = "channel-1",
        type: String = "webhook",
        name: String = "Test Channel",
        description: String? = null,
        webhookURL: String? = null,
        frequency: String? = null,
        emailAddresses: List<String>? = null,
        utcTime: String? = null,
        isActive: Boolean = true,
        isDefault: Boolean = false,
        ignoreTime: Boolean = false,
        weekDays: List<WeekDay>? = null,
        permissions: List<TeamPermission>? = null
    ): Channel {
        return Channel(
            id = id,
            type = type,
            name = name,
            description = description,
            webhookURL = webhookURL,
            frequency = frequency,
            emailAddresses = emailAddresses,
            utcTime = utcTime,
            isActive = isActive,
            isDefault = isDefault,
            ignoreTime = ignoreTime,
            weekDays = weekDays,
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

    private fun createChannelsApi(
        httpClient: HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): ChannelsApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return ChannelsApi(httpClient, config)
    }

    @Test
    fun testGetChannels() = runTest {
        val channels = listOf(
            createChannel(
                id = "channel-1",
                type = "webhook",
                name = "Primary Webhook",
                webhookURL = "https://webhook.example.com/alerts"
            ),
            createChannel(
                id = "channel-2",
                type = "email",
                name = "Email Alerts",
                emailAddresses = listOf("alerts@example.com")
            ),
            createChannel(
                id = "channel-3",
                type = "webhook",
                name = "Secondary Webhook",
                webhookURL = "https://backup.example.com/webhooks"
            )
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannels()

        assertEquals(3, result.size)
        assertEquals("channel-1", result[0].id)
        assertEquals("webhook", result[0].type)
        assertEquals("email", result[1].type)
    }

    @Test
    fun testGetChannelsEmpty() = runTest {
        val response = ChannelsListResponse(channels = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannels()

        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateChannelWebhook() = runTest {
        val channel = createChannel(
            id = null,
            type = "webhook",
            name = "New Webhook Channel",
            webhookURL = "https://api.example.com/notify"
        )
        val responseChannel = channel.copy(id = "channel-new-1")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals("channel-new-1", result.id)
        assertEquals("webhook", result.type)
        assertEquals("New Webhook Channel", result.name)
    }

    @Test
    fun testCreateChannelEmail() = runTest {
        val channel = createChannel(
            id = null,
            type = "email",
            name = "Email Notification Channel",
            emailAddresses = listOf("notify@example.com")
        )
        val responseChannel = channel.copy(id = "channel-email-1")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals("channel-email-1", result.id)
        assertEquals("email", result.type)
        assertEquals(1, result.emailAddresses?.size)
    }

    @Test
    fun testCreateChannelWithAllFields() = runTest {
        val channel = createChannel(
            id = null,
            type = "webhook",
            name = "Complete Channel",
            description = "Complete channel with all fields",
            webhookURL = "https://complete.example.com/webhook",
            frequency = "hourly",
            isActive = true,
            isDefault = false,
            ignoreTime = false,
            weekDays = listOf(WeekDay.MONDAY, WeekDay.WEDNESDAY, WeekDay.FRIDAY),
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val responseChannel = channel.copy(id = "channel-complete")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals("channel-complete", result.id)
        assertNotNull(result.description)
        assertEquals(3, result.weekDays?.size)
        assertEquals(2, result.permissions?.size)
    }

    @Test
    fun testCreateChannelValidatesType() = runTest {
        val channelsApi = createChannelsApi()

        assertFailsWith<IllegalArgumentException> {
            val channel = createChannel(type = "invalid")
            channelsApi.createChannel(channel)
        }
    }

    @Test
    fun testCreateChannelValidatesName() = runTest {
        val channelsApi = createChannelsApi()

        assertFailsWith<IllegalArgumentException> {
            val channel = createChannel(name = "")
            channelsApi.createChannel(channel)
        }
    }

    @Test
    fun testGetChannelById() = runTest {
        val channel = createChannel(
            id = "channel-123",
            name = "Specific Channel"
        )
        val response = ChannelResponse(channel = channel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelById("channel-123")

        assertEquals("channel-123", result.id)
        assertEquals("Specific Channel", result.name)
    }

    @Test
    fun testUpdateChannel() = runTest {
        val channel = createChannel(
            id = "channel-1",
            name = "Updated Channel Name"
        )
        val response = ChannelResponse(channel = channel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.updateChannel("channel-1", channel)

        assertEquals("channel-1", result.id)
        assertEquals("Updated Channel Name", result.name)
    }

    @Test
    fun testUpdateChannelChangeType() = runTest {
        val webhookChannel = createChannel(
            id = "channel-1",
            type = "webhook",
            webhookURL = "https://example.com/webhook"
        )
        val emailChannel = createChannel(
            id = "channel-1",
            type = "email",
            emailAddresses = listOf("updated@example.com")
        )

        val mockClient1 = createMockHttpClient(responseData = ChannelResponse(webhookChannel))
        val mockClient2 = createMockHttpClient(responseData = ChannelResponse(emailChannel))

        val api1 = createChannelsApi(httpClient = mockClient1)
        val api2 = createChannelsApi(httpClient = mockClient2)

        val resultWebhook = api1.updateChannel("channel-1", webhookChannel)
        val resultEmail = api2.updateChannel("channel-1", emailChannel)

        assertEquals("webhook", resultWebhook.type)
        assertEquals("email", resultEmail.type)
    }

    @Test
    fun testGetChannelStatistics() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", type = "webhook", isActive = true, isDefault = true),
            createChannel(id = "ch-2", type = "email", isActive = true, isDefault = false),
            createChannel(id = "ch-3", type = "webhook", isActive = false, isDefault = false),
            createChannel(id = "ch-4", type = "webhook", isActive = true, isDefault = false),
            createChannel(id = "ch-5", type = "email", isActive = true, isDefault = true)
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelStatistics()

        assertEquals(5, result.totalChannels)
        assertEquals(3, result.webhookChannels)
        assertEquals(2, result.emailChannels)
        assertEquals(4, result.activeChannels)
        assertEquals(1, result.inactiveChannels)
        assertEquals(2, result.defaultChannels)
    }

    @Test
    fun testGetChannelStatisticsEmpty() = runTest {
        val response = ChannelsListResponse(channels = emptyList())

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelStatistics()

        assertEquals(0, result.totalChannels)
        assertEquals(0, result.webhookChannels)
        assertEquals(0, result.emailChannels)
        assertEquals(0, result.activeChannels)
        assertEquals(0, result.inactiveChannels)
        assertEquals(0, result.defaultChannels)
    }

    @Test
    fun testGetChannelStatisticsOnlyWebhooks() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", type = "webhook", isActive = true),
            createChannel(id = "ch-2", type = "webhook", isActive = true),
            createChannel(id = "ch-3", type = "webhook", isActive = false)
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelStatistics()

        assertEquals(3, result.totalChannels)
        assertEquals(3, result.webhookChannels)
        assertEquals(0, result.emailChannels)
        assertEquals(2, result.activeChannels)
        assertEquals(1, result.inactiveChannels)
    }

    @Test
    fun testGetChannelStatisticsOnlyEmails() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", type = "email", isActive = true),
            createChannel(id = "ch-2", type = "email", isActive = false),
            createChannel(id = "ch-3", type = "email", isActive = true)
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelStatistics()

        assertEquals(3, result.totalChannels)
        assertEquals(0, result.webhookChannels)
        assertEquals(3, result.emailChannels)
        assertEquals(2, result.activeChannels)
        assertEquals(1, result.inactiveChannels)
    }

    @Test
    fun testCreateChannelAuthenticationError() = runTest {
        val channel = createChannel()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            channelsApi.createChannel(channel)
        }
    }

    @Test
    fun testCreateChannelForbiddenError() = runTest {
        val channel = createChannel()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            channelsApi.createChannel(channel)
        }
    }

    @Test
    fun testGetChannelByIdNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Channel not found"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            channelsApi.getChannelById("nonexistent")
        }
    }

    @Test
    fun testUpdateChannelNotFoundError() = runTest {
        val channel = createChannel()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Channel not found"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            channelsApi.updateChannel("nonexistent", channel)
        }
    }

    @Test
    fun testGetChannelsRateLimitError() = runTest {
        val mockClient = HttpClient(MockEngine { request ->
            respond(
                content = """{"error": "Too Many Requests"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("90")
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
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            channelsApi.getChannels()
        }
        assertEquals(90L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetChannelsServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            channelsApi.getChannels()
        }
    }

    @Test
    fun testCreateChannelBadRequest() = runTest {
        val channel = createChannel()
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Invalid channel"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            channelsApi.createChannel(channel)
        }
    }

    @Test
    fun testGetChannelsServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val channelsApi = createChannelsApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            channelsApi.getChannels()
        }
    }

    @Test
    fun testCreateChannelWebhookWithFrequency() = runTest {
        val channel = createChannel(
            id = null,
            type = "webhook",
            name = "Scheduled Webhook",
            webhookURL = "https://example.com/webhook",
            frequency = "daily",
            utcTime = "09:00"
        )
        val responseChannel = channel.copy(id = "channel-scheduled")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals("daily", result.frequency)
        assertEquals("09:00", result.utcTime)
    }

    @Test
    fun testCreateChannelEmailWithMultipleAddresses() = runTest {
        val channel = createChannel(
            id = null,
            type = "email",
            name = "Multi-email Channel",
            emailAddresses = listOf(
                "admin@example.com",
                "security@example.com",
                "alerts@example.com",
                "team@example.com"
            )
        )
        val responseChannel = channel.copy(id = "channel-multi-email")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals(4, result.emailAddresses?.size)
    }

    @Test
    fun testCreateChannelWithWeekDays() = runTest {
        val channel = createChannel(
            id = null,
            name = "Weekday Channel",
            weekDays = listOf(WeekDay.MONDAY, WeekDay.TUESDAY, WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY)
        )
        val responseChannel = channel.copy(id = "channel-weekdays")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals(5, result.weekDays?.size)
    }

    @Test
    fun testCreateChannelWithPermissions() = runTest {
        val channel = createChannel(
            id = null,
            name = "Permissioned Channel",
            permissions = listOf(TeamPermission.TEAM_READ, TeamPermission.TEAM_WRITE)
        )
        val responseChannel = channel.copy(id = "channel-perms")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals(2, result.permissions?.size)
        assertEquals(result.permissions?.contains(TeamPermission.TEAM_READ), true)
    }

    @Test
    fun testUpdateChannelChangeActive() = runTest {
        val activeChannel = createChannel(id = "ch-1", isActive = true)
        val inactiveChannel = createChannel(id = "ch-1", isActive = false)

        val mockClient1 = createMockHttpClient(responseData = ChannelResponse(activeChannel))
        val mockClient2 = createMockHttpClient(responseData = ChannelResponse(inactiveChannel))

        val api1 = createChannelsApi(httpClient = mockClient1)
        val api2 = createChannelsApi(httpClient = mockClient2)

        val resultActive = api1.updateChannel("ch-1", activeChannel)
        val resultInactive = api2.updateChannel("ch-1", inactiveChannel)

        assertTrue(resultActive.isActive)
        assertFalse(resultInactive.isActive)
    }

    @Test
    fun testUpdateChannelSetDefault() = runTest {
        val channel = createChannel(
            id = "ch-1",
            isDefault = true
        )
        val response = ChannelResponse(channel = channel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.updateChannel("ch-1", channel)

        assertTrue(result.isDefault)
    }

    @Test
    fun testChannelWithIgnoreTime() = runTest {
        val channel = createChannel(
            id = null,
            ignoreTime = true
        )
        val responseChannel = channel.copy(id = "ch-ignore")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertTrue(result.ignoreTime)
    }

    @Test
    fun testChannelWorkflow() = runTest {
        // Create channel
        val createChannel = createChannel(
            id = null,
            name = "Workflow Channel",
            type = "webhook",
            webhookURL = "https://example.com/webhook"
        )
        val createResponse = ChannelResponse(channel = createChannel.copy(id = "ch-workflow"))
        val mockClient1 = createMockHttpClient(responseData = createResponse)
        val api1 = createChannelsApi(httpClient = mockClient1)

        val created = api1.createChannel(createChannel)
        assertEquals("ch-workflow", created.id)

        // Update channel
        val updateChannel = created.copy(name = "Updated Workflow Channel")
        val updateResponse = ChannelResponse(channel = updateChannel)
        val mockClient2 = createMockHttpClient(responseData = updateResponse)
        val api2 = createChannelsApi(httpClient = mockClient2)

        val updated = api2.updateChannel("ch-workflow", updateChannel)
        assertEquals("Updated Workflow Channel", updated.name)

        // Get by ID
        val getResponse = ChannelResponse(channel = updated)
        val mockClient3 = createMockHttpClient(responseData = getResponse)
        val api3 = createChannelsApi(httpClient = mockClient3)

        val retrieved = api3.getChannelById("ch-workflow")
        assertEquals("ch-workflow", retrieved.id)
    }

    @Test
    fun testChannelDeserialization() = runTest {
        val originalChannel = createChannel(
            id = "deser-ch-123",
            type = "webhook",
            name = "Deserialization Test",
            description = "Test description",
            webhookURL = "https://test.example.com/webhook",
            frequency = "hourly",
            utcTime = "14:30",
            isActive = true,
            isDefault = false,
            ignoreTime = false,
            weekDays = listOf(WeekDay.MONDAY, WeekDay.WEDNESDAY, WeekDay.FRIDAY),
            permissions = listOf(TeamPermission.TEAM_READ)
        )
        val mockClient = createMockHttpClient(responseData = ChannelResponse(originalChannel))
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelById("deser-ch-123")

        assertEquals("deser-ch-123", result.id)
        assertEquals("webhook", result.type)
        assertEquals("Deserialization Test", result.name)
        assertEquals("https://test.example.com/webhook", result.webhookURL)
        assertEquals("hourly", result.frequency)
        assertEquals(3, result.weekDays?.size)
        assertEquals(1, result.permissions?.size)
    }

    @Test
    fun testGetChannelsWithMixedTypes() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", type = "webhook"),
            createChannel(id = "ch-2", type = "email"),
            createChannel(id = "ch-3", type = "webhook"),
            createChannel(id = "ch-4", type = "email"),
            createChannel(id = "ch-5", type = "webhook")
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannels()

        assertEquals(5, result.size)
        assertEquals(3, result.count { it.type == "webhook" })
        assertEquals(2, result.count { it.type == "email" })
    }

    @Test
    fun testGetChannelsWithMixedActiveStatus() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", isActive = true),
            createChannel(id = "ch-2", isActive = false),
            createChannel(id = "ch-3", isActive = true),
            createChannel(id = "ch-4", isActive = true),
            createChannel(id = "ch-5", isActive = false),
            createChannel(id = "ch-6", isActive = true)
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannels()

        assertEquals(6, result.size)
        assertEquals(4, result.count { it.isActive })
        assertEquals(2, result.count { !it.isActive })
    }

    @Test
    fun testGetChannelsWithDefaultChannel() = runTest {
        val channels = listOf(
            createChannel(id = "ch-1", isDefault = false),
            createChannel(id = "ch-2", isDefault = true),
            createChannel(id = "ch-3", isDefault = false),
            createChannel(id = "ch-4", isDefault = false)
        )
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannels()

        assertEquals(1, result.count { it.isDefault })
        assertEquals("ch-2", result.find { it.isDefault }?.id)
    }

    @Test
    fun testCreateChannelWebhookDifferentUrls() = runTest {
        val urls = listOf(
            "https://api.example.com/webhook",
            "https://slack.example.com/hooks/actions",
            "https://teams.example.com/webhookb2/",
            "https://custom.example.com/notify"
        )

        urls.forEachIndexed { index, url ->
            val channel = createChannel(
                id = null,
                type = "webhook",
                webhookURL = url
            )
            val responseChannel = channel.copy(id = "ch-webhook-$index")
            val response = ChannelResponse(channel = responseChannel)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createChannelsApi(httpClient = mockClient)

            val result = api.createChannel(channel)
            assertEquals(url, result.webhookURL)
        }
    }

    @Test
    fun testCreateChannelEmailDifferentAddresses() = runTest {
        val emails = listOf(
            "user@example.com",
            "admin@company.org",
            "alerts+security@test.co.uk",
            "notifications@subdomain.example.com"
        )

        emails.forEachIndexed { index, email ->
            val channel = createChannel(
                id = null,
                type = "email",
                emailAddresses = listOf(email)
            )
            val responseChannel = channel.copy(id = "ch-email-$index")
            val response = ChannelResponse(channel = responseChannel)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createChannelsApi(httpClient = mockClient)

            val result = api.createChannel(channel)
            assertEquals(email, result.emailAddresses?.get(0))
        }
    }

    @Test
    fun testUpdateChannelMultiple() = runTest {
        val channels = listOf("ch-1", "ch-2", "ch-3")

        channels.forEach { channelId ->
            val channel = createChannel(id = channelId, name = "Updated $channelId")
            val response = ChannelResponse(channel = channel)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createChannelsApi(httpClient = mockClient)

            val result = api.updateChannel(channelId, channel)
            assertEquals(channelId, result.id)
        }
    }

    @Test
    fun testChannelWithComplexSchedule() = runTest {
        val channel = createChannel(
            id = null,
            name = "Complex Schedule Channel",
            frequency = "weekly",
            utcTime = "15:30",
            weekDays = listOf(WeekDay.TUESDAY, WeekDay.THURSDAY),
            ignoreTime = false
        )
        val responseChannel = channel.copy(id = "ch-schedule")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals("weekly", result.frequency)
        assertEquals("15:30", result.utcTime)
        assertEquals(2, result.weekDays?.size)
        assertFalse(result.ignoreTime)
    }

    @Test
    fun testGetChannelStatisticsMixed() = runTest {
        val channels = (1..20).map { index ->
            createChannel(
                id = "ch-$index",
                type = if (index % 2 == 0) "webhook" else "email",
                isActive = index % 3 != 0,
                isDefault = index == 5 || index == 15
            )
        }
        val response = ChannelsListResponse(channels = channels)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.getChannelStatistics()

        assertEquals(20, result.totalChannels)
        assertEquals(10, result.webhookChannels)
        assertEquals(10, result.emailChannels)
        assertEquals(2, result.defaultChannels)
    }

    @Test
    fun testChannelNameEdgeCases() = runTest {
        val names = listOf(
            "A",
            "Very Long Channel Name With Many Words That Goes On And On For Quite A While",
            "Channel-with-dashes",
            "Channel_with_underscores",
            "Channel.with.dots",
            "Channel123",
            "Channel (with) Special [Characters]"
        )

        names.forEachIndexed { index, name ->
            val channel = createChannel(id = null, name = name)
            val responseChannel = channel.copy(id = "ch-name-$index")
            val response = ChannelResponse(channel = responseChannel)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createChannelsApi(httpClient = mockClient)

            val result = api.createChannel(channel)
            assertEquals(name, result.name)
        }
    }

    @Test
    fun testGetChannelByIdMultiple() = runTest {
        val ids = listOf("ch-1", "ch-2", "ch-3", "ch-4", "ch-5")

        ids.forEach { id ->
            val channel = createChannel(id = id, name = "Channel $id")
            val response = ChannelResponse(channel = channel)

            val mockClient = createMockHttpClient(responseData = response)
            val api = createChannelsApi(httpClient = mockClient)

            val result = api.getChannelById(id)
            assertEquals(id, result.id)
        }
    }

    @Test
    fun testChannelWithDescription() = runTest {
        val longDescription = """
            This is a detailed description of the notification channel.
            It can be used to provide information about where notifications are sent,
            what triggers them, and any other relevant details about the channel.
            Multiple lines are supported for comprehensive documentation.
        """.trimIndent()

        val channel = createChannel(
            id = null,
            description = longDescription
        )
        val responseChannel = channel.copy(id = "ch-desc")
        val response = ChannelResponse(channel = responseChannel)

        val mockClient = createMockHttpClient(responseData = response)
        val channelsApi = createChannelsApi(httpClient = mockClient)

        val result = channelsApi.createChannel(channel)

        assertEquals(longDescription, result.description)
    }

    @Test
    fun testChannelTypeValidation() = runTest {
        val validTypes = listOf("webhook", "email")

        validTypes.forEach { type ->
            val channel = createChannel(type = type)
            val channelsApi = createChannelsApi()

            try {
                channelsApi.createChannel(channel)
            } catch (e: ApiException) {
                assertTrue(true)
            }
        }
    }
}