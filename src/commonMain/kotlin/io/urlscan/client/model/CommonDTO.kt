package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Submitter(
    val country: String? = null
)


@Serializable
data class AsnInfo(
    val ip: String,
    val asn: String,
    val country: String,
    val registrar: String,
    val date: String,
    val description: String,
    val route: String,
    val name: String? = null
)


@Serializable
data class GeoIpInfo(
    val country: String,
    val region: String? = null,
    val timezone: String? = null,
    val city: String? = null,
    val ll: List<Double>? = null,
    @SerialName("country_name")
    val countryName: String? = null,
    val metro: Int? = null,
    val area: Int? = null
)