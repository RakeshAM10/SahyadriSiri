package com.sahyadrisiri.data.api

import com.sahyadrisiri.data.model.Report
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service — now only used as a fallback/legacy interface.
 * Primary data access is via Supabase (see ReportRepository).
 * Auth is handled entirely by Supabase Auth (see AuthViewModel).
 */
interface ApiService {

    @GET("api/reports")
    suspend fun getReports(): List<Report>

    @POST("api/reports")
    suspend fun createReport(@Body report: Report): Response<Unit>

    @DELETE("api/reports/{id}")
    suspend fun deleteReport(@Path("id") id: String): Response<Unit>
}
