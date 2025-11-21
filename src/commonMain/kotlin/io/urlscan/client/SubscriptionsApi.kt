package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.urlscan.client.exception.NotFoundException
import io.urlscan.client.model.Subscription
import io.urlscan.client.model.SubscriptionFrequency
import io.urlscan.client.model.SubscriptionRequest
import io.urlscan.client.model.SubscriptionResponse

class SubscriptionsApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get a list of all Subscriptions for the current user.
     * Subscriptions allow automated notifications for Saved Searches.
     *
     * @return List of Subscription containing all user's subscriptions
     */
    suspend fun getSubscriptions(): List<Subscription> {
        return httpClient.get(
            "${config.apiHost}/api/v1/user/subscriptions/"
        ).body<List<Subscription>>()
    }

    /**
     * Create a new Subscription for automated notifications.
     * Associates one or more Saved Searches with notification channels and frequency.
     *
     * @param subscription The Subscription containing configuration
     * @return Subscription containing the created subscription with generated ID
     */
    suspend fun createSubscription(subscription: Subscription): Subscription {
        val request = SubscriptionRequest(subscription = subscription)
        val response = httpClient.post(
            "${config.apiHost}/api/v1/user/subscriptions/"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<SubscriptionResponse>()
        return response.subscription
    }

    /**
     * Update an existing Subscription.
     *
     * @param subscriptionId The unique identifier of the subscription to update
     * @param subscription The Subscription containing updated configuration
     * @return Subscription containing the updated subscription
     */
    suspend fun updateSubscription(
        subscriptionId: String,
        subscription: Subscription
    ): Subscription {
        val request = SubscriptionRequest(subscription = subscription)
        val response = httpClient.put(
            "${config.apiHost}/api/v1/user/subscriptions/$subscriptionId/"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<SubscriptionResponse>()
        return response.subscription
    }

    /**
     * Delete a Subscription.
     *
     * @param subscriptionId The unique identifier of the subscription to delete
     */
    suspend fun deleteSubscription(subscriptionId: String) {
        httpClient.delete(
            "${config.apiHost}/api/v1/user/subscriptions/$subscriptionId/"
        )
    }

    /**
     * Get the search results for a specific subscription and datasource.
     * Returns a redirect to the Search API query containing the subscription results.
     *
     * @param subscriptionId The unique identifier of the subscription
     * @param datasource The data source to retrieve results from (scans or hostnames)
     * @return String containing the redirect URL or search results
     */
    suspend fun getSubscriptionResults(
        subscriptionId: String,
        datasource: String
    ): String {
        return httpClient.get(
            "${config.apiHost}/api/v1/user/subscriptions/$subscriptionId/results/$datasource/"
        ).body()
    }

    /**
     * Get a single Subscription by ID.
     *
     * @param subscriptionId The unique identifier of the subscription
     * @return Subscription containing the subscription details
     */
    suspend fun getSubscriptionById(subscriptionId: String): Subscription {
        val subscriptions = getSubscriptions()
        return subscriptions.find { it.id == subscriptionId }
            ?: throw NotFoundException("Subscription with ID '$subscriptionId' not found")
    }

    /**
     * Get all active subscriptions.
     *
     * @return List of Subscription that are currently active
     */
    suspend fun getActiveSubscriptions(): List<Subscription> {
        return getSubscriptions().filter { it.isActive }
    }

    /**
     * Get all inactive subscriptions.
     *
     * @return List of Subscription that are currently inactive
     */
    suspend fun getInactiveSubscriptions(): List<Subscription> {
        return getSubscriptions().filter { !it.isActive }
    }

    /**
     * Get subscriptions filtered by frequency.
     *
     * @param frequency The frequency to filter by (live, hourly, or daily)
     * @return List of Subscription matching the frequency
     */
    suspend fun getSubscriptionsByFrequency(frequency: SubscriptionFrequency): List<Subscription> {
        return getSubscriptions().filter { subscription ->
            subscription.frequency == frequency
        }
    }

    /**
     * Get subscriptions associated with a specific Saved Search.
     *
     * @param searchId The ID of the Saved Search
     * @return List of Subscription containing this search
     */
    suspend fun getSubscriptionsBySearchId(searchId: String): List<Subscription> {
        return getSubscriptions().filter { subscription ->
            subscription.searchIds.contains(searchId)
        }
    }

    /**
     * Get subscriptions associated with a specific notification channel.
     *
     * @param channelId The ID of the notification channel
     * @return List of Subscription using this channel
     */
    suspend fun getSubscriptionsByChannelId(channelId: String): List<Subscription> {
        return getSubscriptions().filter { subscription ->
            subscription.channelIds?.contains(channelId) == true
        }
    }
}