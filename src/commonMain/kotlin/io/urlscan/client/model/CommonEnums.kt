package io.urlscan.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WeekDay {
    @SerialName("Monday") MONDAY,
    @SerialName("Tuesday") TUESDAY,
    @SerialName("Wednesday") WEDNESDAY,
    @SerialName("Thursday") THURSDAY,
    @SerialName("Friday") FRIDAY,
    @SerialName("Saturday") SATURDAY,
    @SerialName("Sunday") SUNDAY
}

@Serializable
enum class TeamPermission {
    @SerialName("team:read") TEAM_READ,
    @SerialName("team:write") TEAM_WRITE
}

@Serializable
enum class Visibility {
    @SerialName("public") PUBLIC,
    @SerialName("unlisted") UNLISTED,
    @SerialName("private") PRIVATE
}