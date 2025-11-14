package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object representing a brand tracked by urlscan.
 * Contains metadata about legitimate brand domains, ASNs, and operational regions.
 *
 * @property id Unique database ID of the brand
 * @property name User-facing name of the brand
 * @property key Searchable key for the brand
 * @property vertical List of industry verticals this brand operates in
 * @property country List of countries where the brand operates
 * @property terms Legitimate domains and ASNs associated with this brand
 * @property createdAt ISO8601 timestamp when the brand was added to tracking
 */
@Serializable
data class Brand(
    @SerialName("_id")
    val id: String,
    val name: String,
    val key: String,
    val vertical: List<String>,
    val country: List<String>,
    val terms: BrandTerms,
    val createdAt: String
)

/**
 * Data Transfer Object containing legitimate terms for a brand.
 * Includes domains and ASNs that are known to belong to this brand.
 *
 * @property domains List of legitimate domains for this brand
 * @property asns List of legitimate ASN numbers for this brand
 */
@Serializable
data class BrandTerms(
    val domains: List<String>,
    val asns: List<String>
)

/**
 * Data Transfer Object representing a brand with detection summary.
 * Includes statistics about detected phishing pages and latest detection dates.
 *
 * @property id Unique database ID of the brand
 * @property name User-facing name of the brand
 * @property key Searchable key for the brand
 * @property vertical List of industry verticals this brand operates in
 * @property country List of countries where the brand operates
 * @property terms Legitimate domains and ASNs associated with this brand
 * @property createdAt ISO8601 timestamp when the brand was added to tracking
 * @property detectedCount Total number of detected phishing pages for this brand
 * @property latestDetection ISO8601 timestamp of the most recent detection
 */
@Serializable
data class BrandSummary(
    @SerialName("_id")
    val id: String,
    val name: String,
    val key: String,
    val vertical: List<String>,
    val country: List<String>,
    val terms: BrandTerms,
    val createdAt: String,
    val detectedCount: Int? = null,
    val latestDetection: String? = null
)

/**
 * Response wrapper for the available brands endpoint.
 */
@Serializable
internal data class AvailableBrandsResponse(
    val kits: List<Brand>
)

/**
 * Data Transfer Object containing brand statistics and metrics.
 *
 * @property totalBrands Total number of tracked brands
 * @property totalVerticals Total unique industry verticals
 * @property totalCountries Total unique countries where brands operate
 * @property brandsWithLegitemateDomains Count of brands with domain terms defined
 * @property brandsWithAsnTerms Count of brands with ASN terms defined
 */
@Serializable
data class BrandStatistics(
    val totalBrands: Int,
    val totalVerticals: Int,
    val totalCountries: Int,
    val brandsWithLegitemateDomains: Int,
    val brandsWithAsnTerms: Int
)