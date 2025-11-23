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
import io.urlscan.client.model.Limits
import io.urlscan.client.model.ProUsernameResponse
import io.urlscan.client.model.QuotasResponse
import io.urlscan.client.model.Used
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenericApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun createQuotasResponse(
        publicLimit: Int = 100,
        privateLimit: Int = 50,
        unlistedLimit: Int = 25,
        retrieveLimit: Int = 1000,
        searchLimit: Int = 500,
        publicUsed: Int = 30,
        privateUsed: Int = 15,
        unlistedUsed: Int = 8,
        retrieveUsed: Int = 250,
        searchUsed: Int = 150
    ): QuotasResponse {
        return QuotasResponse(
            limits = Limits(
                public = publicLimit,
                private = privateLimit,
                unlisted = unlistedLimit,
                retrieve = retrieveLimit,
                search = searchLimit
            ),
            used = Used(
                public = publicUsed,
                private = privateUsed,
                unlisted = unlistedUsed,
                retrieve = retrieveUsed,
                search = searchUsed
            )
        )
    }

    private fun createProUsernameResponse(
        username: String = "test-user"
    ): ProUsernameResponse {
        return ProUsernameResponse(username = username)
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

    private fun createGenericApi(
        httpClient: io.ktor.client.HttpClient = createMockHttpClient<Unit>(),
        apiKey: String = "test-api-key",
        apiHost: String = "api.urlscan.io"
    ): GenericApi {
        val config = UrlScanConfig(
            apiKey = apiKey,
            apiHost = apiHost,
            baseUrl = "https://$apiHost"
        )
        return GenericApi(httpClient, config)
    }

    @Test
    fun testGetQuotasBasic() = runTest {
        val quotasResponse = createQuotasResponse()

        val mockClient = createMockHttpClient(responseData = quotasResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getQuotas()

        assertEquals(100, result.limits.public)
        assertEquals(50, result.limits.private)
        assertEquals(25, result.limits.unlisted)
        assertEquals(1000, result.limits.retrieve)
        assertEquals(500, result.limits.search)
        assertEquals(30, result.used.public)
        assertEquals(15, result.used.private)
        assertEquals(8, result.used.unlisted)
        assertEquals(250, result.used.retrieve)
        assertEquals(150, result.used.search)
    }

    @Test
    fun testGetQuotasFullyUtilized() = runTest {
        val quotasResponse = createQuotasResponse(
            publicLimit = 100,
            privateLimit = 50,
            unlistedLimit = 25,
            retrieveLimit = 1000,
            searchLimit = 500,
            publicUsed = 100,
            privateUsed = 50,
            unlistedUsed = 25,
            retrieveUsed = 1000,
            searchUsed = 500
        )

        val mockClient = createMockHttpClient(responseData = quotasResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getQuotas()

        assertEquals(result.limits.public, result.used.public)
        assertEquals(result.limits.private, result.used.private)
        assertEquals(result.limits.unlisted, result.used.unlisted)
        assertEquals(result.limits.retrieve, result.used.retrieve)
        assertEquals(result.limits.search, result.used.search)
    }

    @Test
    fun testGetQuotasPartiallyUtilized() = runTest {
        val quotasResponse = createQuotasResponse(
            publicLimit = 100,
            publicUsed = 75,
            privateLimit = 50,
            privateUsed = 25
        )

        val mockClient = createMockHttpClient(responseData = quotasResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getQuotas()

        val publicPercentage = (result.used.public.toDouble() / result.limits.public * 100).toInt()
        val privatePercentage = (result.used.private.toDouble() / result.limits.private * 100).toInt()

        assertEquals(75, publicPercentage)
        assertEquals(50, privatePercentage)
    }

    @Test
    fun testGetQuotasLargeNumbers() = runTest {
        val quotasResponse = createQuotasResponse(
            publicLimit = 1_000_000,
            privateLimit = 500_000,
            unlistedLimit = 250_000,
            retrieveLimit = 10_000_000,
            searchLimit = 5_000_000,
            publicUsed = 750_000,
            privateUsed = 400_000,
            unlistedUsed = 200_000,
            retrieveUsed = 8_000_000,
            searchUsed = 4_000_000
        )

        val mockClient = createMockHttpClient(responseData = quotasResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getQuotas()

        assertEquals(1_000_000, result.limits.public)
        assertEquals(750_000, result.used.public)
        assertEquals(10_000_000, result.limits.retrieve)
        assertEquals(8_000_000, result.used.retrieve)
    }

    @Test
    fun testGetQuotasAuthenticationError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            genericApi.getQuotas()
        }
    }

    @Test
    fun testGetQuotasForbiddenError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            genericApi.getQuotas()
        }
    }

    @Test
    fun testGetQuotasNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Not Found"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            genericApi.getQuotas()
        }
    }

    @Test
    fun testGetQuotasRateLimitError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Too Many Requests"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Retry-After" to listOf("60")
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
        val genericApi = createGenericApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            genericApi.getQuotas()
        }
        assertEquals(60L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetQuotasServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            genericApi.getQuotas()
        }
    }

    @Test
    fun testGetQuotasServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            genericApi.getQuotas()
        }
    }

    @Test
    fun testGetQuotasDeserializationIntegrity() = runTest {
        val quotasResponse = createQuotasResponse(
            publicLimit = 123,
            privateLimit = 456,
            unlistedLimit = 789,
            retrieveLimit = 999,
            searchLimit = 888,
            publicUsed = 50,
            privateUsed = 100,
            unlistedUsed = 200,
            retrieveUsed = 300,
            searchUsed = 400
        )

        val mockClient = createMockHttpClient(responseData = quotasResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getQuotas()

        assertEquals(123, result.limits.public)
        assertEquals(456, result.limits.private)
        assertEquals(789, result.limits.unlisted)
        assertEquals(999, result.limits.retrieve)
        assertEquals(888, result.limits.search)
        assertEquals(50, result.used.public)
        assertEquals(100, result.used.private)
        assertEquals(200, result.used.unlisted)
        assertEquals(300, result.used.retrieve)
        assertEquals(400, result.used.search)
    }


    @Test
    fun testGetProUsernameBasic() = runTest {
        val proUsernameResponse = createProUsernameResponse(username = "john-doe")

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals("john-doe", result.username)
    }

    @Test
    fun testGetProUsernameWithSpecialCharacters() = runTest {
        val proUsernameResponse = createProUsernameResponse(username = "user-name_123.test")

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals("user-name_123.test", result.username)
    }

    @Test
    fun testGetProUsernameWithEmailFormat() = runTest {
        val proUsernameResponse = createProUsernameResponse(username = "user@example.com")

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals("user@example.com", result.username)
    }

    @Test
    fun testGetProUsernameEmptyString() = runTest {
        val proUsernameResponse = createProUsernameResponse(username = "")

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals("", result.username)
    }

    @Test
    fun testGetProUsernameWithNumbers() = runTest {
        val proUsernameResponse = createProUsernameResponse(username = "12345")

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals("12345", result.username)
    }

    @Test
    fun testGetProUsernameAuthenticationError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Unauthorized,
            errorContent = """{"error": "Unauthorized"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameForbiddenError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.Forbidden,
            errorContent = """{"error": "Forbidden"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<AuthenticationException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameNotFoundError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.NotFound,
            errorContent = """{"error": "Not Found"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<NotFoundException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameRateLimitError() = runTest {
        val mockClient = io.ktor.client.HttpClient(
            MockEngine { request ->
                respond(
                    content = """{"error": "Too Many Requests"}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        "Retry-After" to listOf("45")
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
        val genericApi = createGenericApi(httpClient = mockClient)

        val exception = assertFailsWith<RateLimitException> {
            genericApi.getProUsername()
        }
        assertEquals(45L, exception.retryAfterSeconds)
    }

    @Test
    fun testGetProUsernameServerError() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.InternalServerError,
            errorContent = """{"error": "Internal Server Error"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameBadRequest() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.BadRequest,
            errorContent = """{"error": "Bad Request"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameServiceUnavailable() = runTest {
        val mockClient = createMockHttpClient<Unit>(
            statusCode = HttpStatusCode.ServiceUnavailable,
            errorContent = """{"error": "Service Unavailable"}"""
        )
        val genericApi = createGenericApi(httpClient = mockClient)

        assertFailsWith<ApiException> {
            genericApi.getProUsername()
        }
    }

    @Test
    fun testGetProUsernameDeserializationIntegrity() = runTest {
        val username = "complex-user_123.test@domain.com"
        val proUsernameResponse = createProUsernameResponse(username = username)

        val mockClient = createMockHttpClient(responseData = proUsernameResponse)
        val genericApi = createGenericApi(httpClient = mockClient)

        val result = genericApi.getProUsername()

        assertEquals(username, result.username)
    }
}