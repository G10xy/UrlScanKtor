package io.urlscan.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.urlscan.client.model.Channel
import io.urlscan.client.model.ChannelRequest
import io.urlscan.client.model.ChannelResponse
import io.urlscan.client.model.ChannelStatistics
import io.urlscan.client.model.ChannelsListResponse

class ChannelsApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get a list of all notification channels for the current user.
     * Channels are used to deliver notifications from subscriptions.
     *
     * @return List of Channel containing all user's notification channels
     */
    suspend fun getChannels(): List<Channel> {
        val response = httpClient.get(
            "${config.apiHost}/api/v1/user/channels/"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body<ChannelsListResponse>()
        return response.channels
    }

    /**
     * Create a new notification channel.
     * Channels can be webhooks for integration or email for direct notifications.
     *
     * @param channel The Channel containing configuration
     * @return Channel containing the created channel with generated ID
     */
    suspend fun createChannel(channel: Channel): Channel {
        val request = ChannelRequest(channel = channel)
        val response = httpClient.post(
            "${config.apiHost}/api/v1/user/channels/"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<ChannelResponse>()
        return response.channel
    }

    /**
     * Get a specific notification channel by ID.
     *
     * @param channelId The unique identifier of the channel
     * @return Channel containing the channel details
     */
    suspend fun getChannelById(channelId: String): Channel {
        val response = httpClient.get(
            "${config.apiHost}/api/v1/user/channels/$channelId"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body<ChannelResponse>()
        return response.channel
    }

    /**
     * Update an existing notification channel.
     *
     * @param channelId The unique identifier of the channel to update
     * @param channel The Channel containing updated configuration
     * @return Channel containing the updated channel
     */
    suspend fun updateChannel(
        channelId: String,
        channel: Channel
    ): Channel {
        val request = ChannelRequest(channel = channel)
        val response = httpClient.put(
            "${config.apiHost}/api/v1/user/channels/$channelId"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<ChannelResponse>()
        return response.channel
    }

    /**
     * Get channel statistics.
     *
     * @return ChannelStatistics containing overview metrics
     */
    suspend fun getChannelStatistics(): ChannelStatistics {
        val channels = getChannels()
        return ChannelStatistics(
            totalChannels = channels.size,
            webhookChannels = channels.count { it.type.equals("webhook", ignoreCase = true) },
            emailChannels = channels.count { it.type.equals("email", ignoreCase = true) },
            activeChannels = channels.count { it.isActive },
            inactiveChannels = channels.count { !it.isActive },
            defaultChannels = channels.count { it.isDefault }
        )
    }
}