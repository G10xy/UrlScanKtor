package io.urlscan.client


import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.urlscan.client.model.AvailableBrandsResponse
import io.urlscan.client.model.Brand
import io.urlscan.client.model.BrandStatistics
import io.urlscan.client.model.BrandSummary

class BrandsApi internal constructor(
    private val httpClient: HttpClient,
    private val config: UrlScanConfig
) {
    /**
     * Get a list of all available brands tracked by urlscan.
     *
     * @return List of Brand containing all tracked brands
     */
    suspend fun getAvailableBrands(): List<Brand> {
        val response = httpClient.get(
            "${config.baseUrl}/api/v1/pro/availableBrands"
        ).body<AvailableBrandsResponse>()
        return response.kits
    }

    /**
     * Get comprehensive summary of brands tracked by urlscan, including detection statistics.
     *
     * @return List of BrandSummary containing brands with detection metrics
     */
    suspend fun getBrandSummaries(): List<BrandSummary> {
        return httpClient.get(
            "${config.baseUrl}/api/v1/pro/brands"
        ).body<List<BrandSummary>>()
    }

    /**
     * Get brand statistics including total count and breakdown by vertical and country.
     *
     * @return BrandStatistics containing overview metrics
     */
    suspend fun getBrandStatistics(): BrandStatistics {
        val brands = getAvailableBrands()
        return BrandStatistics(
            totalBrands = brands.size,
            totalVerticals = brands.flatMap { it.vertical }.toSet().size,
            totalCountries = brands.flatMap { it.country }.toSet().size,
            brandsWithLegitemateDomains = brands.count { it.terms.domains.isNotEmpty() },
            brandsWithAsnTerms = brands.count { it.terms.asns.isNotEmpty() }
        )
    }
}