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
import io.urlscan.client.model.SavedSearchRequest
import io.urlscan.client.model.SavedSearchRequestWrapper
import io.urlscan.client.model.SavedSearchResponse
import io.urlscan.client.model.SavedSearchResponseWrapper
import io.urlscan.client.model.SavedSearchesListResponse
import io.urlscan.client.model.SearchResponse

class SavedSearchesApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get a list of all Saved Searches for the current user.
     *
     * @return List of SavedSearchResponse containing all user's saved searches
     */
    suspend fun getSavedSearches(): List<SavedSearchResponse> {
        val response: SavedSearchesListResponse = httpClient.get(
            "${config.apiHost}/api/v1/user/searches/"
        ).body()
        return response.searches
    }

    /**
     * Create a new Saved Search.
     * Saved Searches are rules that are executed inline against new incoming scans and hostnames.
     *
     * @param request The SavedSearchRequest containing search configuration
     * @return SavedSearchResponse containing the created search with generated ID
     */
    suspend fun createSavedSearch(request: SavedSearchRequest): SavedSearchResponse {
        val wrapper = SavedSearchRequestWrapper(search = request)
        val response: SavedSearchResponseWrapper = httpClient.post(
            "${config.apiHost}/api/v1/user/searches/"
        ) {
            contentType(ContentType.Application.Json)
            setBody(wrapper)
        }.body()
        return response.search
    }

    /**
     * Update an existing Saved Search.
     *
     * @param searchId The unique identifier of the saved search to update
     * @param request The SavedSearchRequest containing updated search configuration
     * @return SavedSearchResponse containing the updated search
     */
    suspend fun updateSavedSearch(
        searchId: String,
        request: SavedSearchRequest
    ): SavedSearchResponse {
        val wrapper = SavedSearchRequestWrapper(search = request)
        val response: SavedSearchResponseWrapper = httpClient.put(
            "${config.apiHost}/api/v1/user/searches/$searchId/"
        ) {
            contentType(ContentType.Application.Json)
            setBody(wrapper)
        }.body()
        return response.search
    }

    /**
     * Delete a Saved Search.
     * @param searchId The unique identifier of the saved search to delete
     */
    suspend fun deleteSavedSearch(searchId: String) {
        httpClient.delete(
            "${config.apiHost}/api/v1/user/searches/$searchId/"
        )
    }

    /**
     * Get the search results for a specific Saved Search.
     * This executes the saved search query and returns matching results.
     *
     * @param searchId The unique identifier of the saved search
     * @return SearchResponse containing the results matching the saved search query
     */
    suspend fun getSavedSearchResults(searchId: String): SearchResponse {
        return httpClient.get(
            "${config.apiHost}/api/v1/user/searches/$searchId/results/"
        ).body()
    }

    /**
     * Get a single Saved Search by ID.
     *
     * @param searchId The unique identifier of the saved search
     * @return SavedSearchResponse containing the search details
     */
    suspend fun getSavedSearchById(searchId: String): SavedSearchResponse {
        return getSavedSearches().find { it.id == searchId }
            ?: throw NotFoundException("Saved search with ID '$searchId' not found")
    }

    /**
     * Get all Saved Searches for a specific data source.
     *
     * @param datasource The data source to filter by (scans or hostnames)
     * @return List of SavedSearchResponse filtered by datasource
     */
    suspend fun getSavedSearchesByDatasource(datasource: String): List<SavedSearchResponse> {
        return getSavedSearches().filter { search ->
            search.datasource.toString().equals(datasource, ignoreCase = true)
        }
    }

    /**
     * Search for Saved Searches by name pattern.
     *
     * @param pattern The name pattern to search for (case-insensitive substring match)
     * @return List of SavedSearchResponse matching the pattern
     */
    suspend fun searchSavedSearchesByName(pattern: String): List<SavedSearchResponse> {
        return getSavedSearches().filter { search ->
            search.name.contains(pattern, ignoreCase = true)
        }
    }
}