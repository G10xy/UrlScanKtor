package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.urlscan.client.exception.handleClientException
import io.urlscan.client.model.ProUsernameResponse
import io.urlscan.client.model.QuotasResponse

class GenericApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    suspend fun getQuotas(): QuotasResponse {
        return try {
            httpClient.get("${config.apiHost}/api/v1/quotas") {
                headers {
                    append("API-Key", config.apiKey)
                }
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error during scan submission: ${e.message}", e)
        }
    }

    suspend fun getProUsername(): ProUsernameResponse {
        return try {
            httpClient.get("${config.apiHost}/api/v1/pro/username") {
                headers {
                    append("API-Key", config.apiKey)
                }
            }.body()
        } catch (e: ClientRequestException) {
            throw handleClientException(e)
        } catch (e: Exception) {
            throw Exception("Network error during scan submission: ${e.message}", e)
        }
    }
}