package com.sahyadrisiri.data.api

import com.sahyadrisiri.data.model.PlaceResult
import retrofit2.http.GET
import retrofit2.http.Query

/** Nominatim OpenStreetMap geocoding — adds country bias and essential params */
interface NominatimService {

    @GET("search")
    suspend fun search(
        @Query("format") format: String = "json",
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countryCodes: String = "in", // Bias towards India for Sahyadri app
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): List<PlaceResult>

    @GET("reverse")
    suspend fun reverse(
        @Query("format") format: String = "json",
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int = 16,
        @Query("addressdetails") addressDetails: Int = 1
    ): PlaceResult
}
