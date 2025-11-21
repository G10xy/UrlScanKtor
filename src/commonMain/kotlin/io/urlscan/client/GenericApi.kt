package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.urlscan.client.model.ProUsernameResponse
import io.urlscan.client.model.QuotasResponse

class GenericApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    suspend fun getQuotas(): QuotasResponse {
        return httpClient.get("${config.apiHost}/api/v1/quotas").body()
    }

    suspend fun getProUsername(): ProUsernameResponse {
        return httpClient.get("${config.apiHost}/api/v1/pro/username").body()
    }
}