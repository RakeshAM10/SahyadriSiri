package com.sahyadrisiri.data.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the Supabase `reports` table schema exactly.
 * Uses both Gson (for Nominatim/OSRM) and kotlinx.serialization (for Supabase).
 */
@Serializable
data class Report(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val lat: Double,
    val lng: Double,
    val name: String,
    val clarity: Int,          // 1–5
    val flow: String,          // "High" | "Normal" | "Low"
    val smell: String,         // "None" | "Earthy" | "Chemical" | "Sewage"
    val description: String? = null,
    val timestamp: String,     // ISO-8601 string
    val status: String,        // "clean" | "warning" | "polluted"
    @SerialName("photo_url") val photo: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    @SerialName("is_edited") val isEdited: Boolean = false
)

@Serializable
data class ReportSyncInfoRemote(
    val id: String,
    val timestamp: String
)

/** Nominatim search result (uses Gson, not Supabase) */
data class PlaceResult(
    val lat: String,
    val lon: String,
    @SerializedName("display_name")
    val displayName: String
)

/** OSRM route response wrappers (uses Gson, not Supabase) */
data class OsrmResponse(val routes: List<OsrmRoute>?)
data class OsrmRoute(val geometry: OsrmGeometry)
data class OsrmGeometry(val coordinates: List<List<Double>>)
