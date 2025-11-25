package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.utils.io.core.Closeable
import io.urlscan.client.internal.createPlatformHttpClient
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Main client for interacting with the urlscan.io API.
 * @property config Configuration for the client including API key and base URL
 * @property httpClient Ktor HTTP client used for making requests
 */
class UrlScanClient(
    private val config: UrlScanConfig = UrlScanConfig(""),
    private val httpClient: HttpClient = createPlatformHttpClient(config)
) : Closeable {

    @OptIn(ExperimentalAtomicApi::class)
    private val closed = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    override fun close() {
        if (closed.compareAndSet(expectedValue = false, newValue = true)) {
            httpClient.close()
        }
    }

    val generic: GenericApi by lazy {
        GenericApi(httpClient, config)
    }

    val scanning: ScanningApi by lazy {
        ScanningApi(httpClient, config)
    }

    val search: SearchApi by lazy {
        SearchApi(httpClient, config)
    }

    val liveScanning: LiveScanningApi by lazy {
        LiveScanningApi(httpClient, config)
    }

    val subscriptions: SubscriptionsApi by lazy {
        SubscriptionsApi(httpClient, config)
    }

    val hostname: HostnamesApi by lazy {
        HostnamesApi(httpClient, config)
    }

    val brands: BrandsApi by lazy {
        BrandsApi(httpClient, config)
    }

    val channels: ChannelsApi by lazy {
        ChannelsApi(httpClient, config)
    }

    val incidents: IncidentsApi by lazy {
        IncidentsApi(httpClient, config)
    }

    val files: FilesApi by lazy {
        FilesApi(httpClient, config)
    }

    val savedSearches: SavedSearchesApi by lazy {
        SavedSearchesApi(httpClient, config)
    }
}