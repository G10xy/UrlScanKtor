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
import io.urlscan.client.model.Incident
import io.urlscan.client.model.IncidentRequest
import io.urlscan.client.model.IncidentResponse
import io.urlscan.client.model.IncidentState
import io.urlscan.client.model.IncidentStatesResponse
import io.urlscan.client.model.WatchableAttributesResponse

class IncidentsApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Create a new incident with specific configuration options.
     * Incidents track observables (hostnames, domains, IPs, or URLs) and automatically scan for changes.
     *
     * @param incident The Incident containing configuration
     * @return Incident containing the created incident with generated ID
     */
    suspend fun createIncident(incident: Incident): Incident {
        val request = IncidentRequest(incident = incident)
        val response = httpClient.post("${config.apiHost}/api/v1/user/incidents") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Get details for a specific incident.
     *
     * @param incidentId The unique identifier of the incident
     * @return Incident containing the incident details
     */
    suspend fun getIncident(incidentId: String): Incident {
        val response = httpClient.get("${config.apiHost}/api/v1/user/incidents/$incidentId") {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Update specific runtime options of an incident.
     * Allows modifying scan intervals, watched attributes, notification settings, and more.
     *
     * @param incidentId The unique identifier of the incident to update
     * @param incident The Incident containing updated configuration
     * @return Incident containing the updated incident
     */
    suspend fun updateIncident(
        incidentId: String,
        incident: Incident
    ): Incident {
        val request = IncidentRequest(incident = incident)
        val response = httpClient.put("${config.apiHost}/api/v1/user/incidents/$incidentId") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Close (stop) an active incident.
     * The incident will no longer perform automatic scans.
     *
     * @param incidentId The unique identifier of the incident to close
     * @return Incident containing the closed incident with state set to "closed"
     */
    suspend fun closeIncident(incidentId: String): Incident {
        val response = httpClient.put("${config.apiHost}/api/v1/user/incidents/$incidentId/close") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Restart a closed incident.
     * Reopens an incident and starts automatic scanning again.
     * Automatically extends the incident expireAt timestamp.
     * Starts with new incident states (does not restore previous state history).
     *
     * @param incidentId The unique identifier of the incident to restart
     * @return Incident containing the restarted incident with state set to "active"
     */
    suspend fun restartIncident(incidentId: String): Incident {
        val response = httpClient.put("${config.apiHost}/api/v1/user/incidents/$incidentId/restart") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Copy an incident without its history.
     * Creates a new incident with the same configuration as the source.
     * The new incident will not have any incident state history.
     *
     * @param incidentId The unique identifier of the incident to copy
     * @return Incident containing the newly created copy
     */
    suspend fun copyIncident(incidentId: String): Incident {
        val response = httpClient.post("${config.apiHost}/api/v1/user/incidents/$incidentId/copy") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Fork an incident with its history.
     * Creates a new incident with the same configuration and complete incident state history.
     * Useful for branching off an incident with all historical data preserved.
     *
     * @param incidentId The unique identifier of the incident to fork
     * @return Incident containing the newly created fork with history
     */
    suspend fun forkIncident(incidentId: String): Incident {
        val response = httpClient.post("${config.apiHost}/api/v1/user/incidents/$incidentId/fork") {
            headers {
                append("API-Key", config.apiKey)
            }
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body<IncidentResponse>()
        return response.incident
    }

    /**
     * Get the list of attributes which can be supplied to the watchedAttributes property.
     * These are the attributes that incidents can monitor for changes.
     *
     * @return List of String containing available watchable attribute names
     */
    suspend fun getWatchableAttributes(): List<String> {
        return httpClient.get(
            "${config.apiHost}/api/v1/user/watchableAttributes"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body<WatchableAttributesResponse>()
            .attributes
    }

    /**
     * Retrieve individual incident states of an incident.
     * Each state represents a snapshot of the incident at a particular time interval.
     *
     * @param incidentId The unique identifier of the incident
     * @return List of IncidentState containing state snapshots
     */
    suspend fun getIncidentStates(incidentId: String): List<IncidentState> {
        val response = httpClient.get(
            "${config.apiHost}/api/v1/user/incidentstates/$incidentId/"
        ) {
            headers {
                append("API-Key", config.apiKey)
            }
        }.body<IncidentStatesResponse>()
        return response.incidentstates
    }
}