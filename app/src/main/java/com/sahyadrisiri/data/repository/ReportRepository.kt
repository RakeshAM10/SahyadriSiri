package com.sahyadrisiri.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.sahyadrisiri.data.api.RetrofitClient
import com.sahyadrisiri.data.api.SupabaseClient
import com.sahyadrisiri.data.local.ReportDatabase
import com.sahyadrisiri.data.local.toEntity
import com.sahyadrisiri.data.local.toReport
import com.sahyadrisiri.data.model.OsrmResponse
import com.sahyadrisiri.data.model.PlaceResult
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.viewmodel.LatLng
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.flow.*

class ReportRepository(private val context: Context? = null) {

    private val supabase = SupabaseClient.client
    private val nominatim = RetrofitClient.nominatimService
    private val http = OkHttpClient()
    private val gson = Gson()
    
    // SharedPreferences for sync timestamps
    private val prefs: SharedPreferences? = context?.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    
    // Inject the DAO if context is provided
    private val dao = context?.let { ReportDatabase.getInstance(it).reportDao() }

    private fun getLastSyncTimestamp(): String? = prefs?.getString("last_sync_timestamp", null)
    private fun setLastSyncTimestamp(timestamp: String) { prefs?.edit()?.putString("last_sync_timestamp", timestamp)?.apply() }

    // ─── Reports CRUD (Supabase Postgrest + Room) ─────────────────────────

    /**
     * Optimized Delta Syncing:
     * 1. Returns local data immediately.
     * 2. Fetches only reports created/updated since the last sync.
     * 3. Merges and updates the local cache.
     */
    suspend fun getReports(): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch locally first for instant UI response
            val localReports = dao?.getAllReports()?.map { it.toReport() } ?: emptyList()
            
            // 2. Fetch fresh data (Delta Sync)
            try {
                val lastSync = getLastSyncTimestamp()
                
                val remoteReports = supabase.from("reports")
                    .select {
                        order("timestamp", Order.DESCENDING)
                        if (lastSync != null) {
                            filter {
                                Report::timestamp gt lastSync
                            }
                        }
                    }
                    .decodeList<Report>()
                    
                // 3. Update Room with new data only
                if (remoteReports.isNotEmpty()) {
                    dao?.let { room ->
                        room.upsertReports(remoteReports.map { it.toEntity(pendingSync = false) })
                    }
                    
                    // Update last sync timestamp to the newest report received
                    val latest = remoteReports.maxOfOrNull { it.timestamp }
                    if (latest != null) {
                        setLastSyncTimestamp(latest)
                    }
                }
                
                // Return fresh data (local + new delta)
                val finalReports = dao?.getAllReports()?.map { it.toReport() } ?: (localReports + remoteReports)
                Result.success(finalReports)
            } catch (networkError: Exception) {
                Log.e("ReportRepository", "Sync failed, using offline data", networkError)
                if (localReports.isNotEmpty()) {
                    Result.success(localReports)
                } else {
                    Result.failure(networkError)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocalReports(): List<Report> = withContext(Dispatchers.IO) {
        dao?.getAllReports()?.map { it.toReport() } ?: emptyList()
    }

    suspend fun createReport(report: Report): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Try to upload to Supabase directly
            Log.d("ReportRepository", "Uploading report. Photo length: ${report.photo?.length ?: 0}")
            supabase.from("reports").insert(report)
            
            // Success! Save locally as synced
            dao?.upsertReport(report.toEntity(pendingSync = false))
            
            Result.success(Unit)
        } catch (e: Exception) {
            // Network failed. Save locally as pending sync (Offline Mode)
            dao?.upsertReport(report.toEntity(pendingSync = true))
            
            // Schedule WorkManager to upload it in the background when internet returns
            context?.let { ctx ->
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                    
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.sahyadrisiri.service.SyncWorker>()
                    .setConstraints(constraints)
                    .build()
                    
                androidx.work.WorkManager.getInstance(ctx).enqueue(workRequest)
            }
            
            Log.d("ReportRepository", "Network offline. Saved report locally. Scheduled SyncWorker.")
            Result.success(Unit) // We return success because the app saved it locally!
        }
    }

    suspend fun deleteReport(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Try to delete from Supabase
            val response = supabase.from("reports").delete {
                filter { eq("id", id) }
            }
            
            // 2. ALWAYS delete from local DAO to ensure it's gone from user's screen
            dao?.deleteById(id)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReportRepository", "Delete failed for ID $id, checking local", e)
            // Even if network fails, if we can delete it locally, we should try
            dao?.deleteById(id)
            Result.success(Unit) // Return success because it's removed from local view
        }
    }

    suspend fun updateReport(report: Report): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Try to update on Supabase
            supabase.from("reports").update(report) {
                filter { eq("id", report.id ?: "") }
            }
            // 2. Mark as synced locally
            dao?.upsertReport(report.toEntity(pendingSync = false))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReportRepository", "updateReport failed, saving locally for later sync", e)
            // 3. Save locally as pending if network fails
            dao?.upsertReport(report.toEntity(pendingSync = true))
            
            // 4. Schedule background sync
            context?.let { ctx ->
                val constraints = androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.sahyadrisiri.service.SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
                    .build()
                androidx.work.WorkManager.getInstance(ctx).enqueue(workRequest)
            }
            Result.success(Unit) // Return success so UI can continue
        }
    }

    // ─── Place Search (Nominatim — unchanged) ─────────────────────────────

    suspend fun searchPlaces(query: String, lat: Double? = null, lon: Double? = null): Result<List<PlaceResult>> =
        withContext(Dispatchers.IO) {
            runCatching { nominatim.search(query = query, lat = lat, lon = lon) }
        }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<PlaceResult> =
        withContext(Dispatchers.IO) {
            runCatching { nominatim.reverse(lat = lat, lon = lon) }
        }

    // ─── OSRM Routing ────────────────────────────────────────────────────

    suspend fun fetchRoute(
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double
    ): List<LatLng> = withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/foot/" +
                    "$startLng,$startLat;$endLng,$endLat" +
                    "?overview=full&geometries=geojson"
            val request = Request.Builder().url(url)
                .header("User-Agent", "SahyadriSiriApp/1.0")
                .build()
            val body = http.newCall(request).execute().use { it.body?.string() ?: "" }
            val response = gson.fromJson(body, OsrmResponse::class.java)
            val route = response.routes?.firstOrNull()
            if (route != null) {
                route.geometry.coordinates.map { coord ->
                    LatLng(coord[1], coord[0])
                }
            } else {
                listOf(LatLng(startLat, startLng), LatLng(endLat, endLng))
            }
        } catch (e: Exception) {
            Log.e("ReportRepository", "Route fetch failed", e)
            listOf(LatLng(startLat, startLng), LatLng(endLat, endLng))
        }
    }

    fun observeReports(): Flow<List<Report>> {
        return dao?.observeAllReports()?.map { entities ->
            entities.map { it.toReport() }
        } ?: flowOf(emptyList())
    }

    suspend fun saveReportLocally(report: Report) = withContext(Dispatchers.IO) {
        dao?.upsertReport(report.toEntity(pendingSync = false))
    }

    suspend fun deleteReportLocally(id: String) = withContext(Dispatchers.IO) {
        dao?.deleteById(id)
    }
}
