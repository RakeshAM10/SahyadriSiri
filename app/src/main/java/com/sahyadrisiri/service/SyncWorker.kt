package com.sahyadrisiri.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sahyadrisiri.data.api.SupabaseClient
import com.sahyadrisiri.data.local.ReportDatabase
import com.sahyadrisiri.data.local.toReport
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that syncs offline-created reports to Supabase
 * when the device regains internet connection.
 */
class SyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = ReportDatabase.getInstance(appContext).reportDao()
        val supabase = SupabaseClient.client
        
        try {
            // Find all reports created while offline
            val pendingReports = dao.getPendingSyncReports()
            
            if (pendingReports.isEmpty()) {
                return@withContext Result.success()
            }
            
            Log.d("SyncWorker", "Found ${pendingReports.size} pending reports to sync.")
            
            // Show silent notification that we are syncing
            NotificationHelper.showSyncNotification(appContext, pendingReports.size)
            
            var successCount = 0
            
            for (entity in pendingReports) {
                try {
                    // Upsert to Supabase (handles both new inserts and updates to existing rows)
                    supabase.from("reports").upsert(entity.toReport())
                    
                    // Mark as synced in local Room DB
                    dao.markSynced(entity.id)
                    successCount++
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync report ${entity.id}", e)
                    // Continue trying other reports even if one fails
                }
            }
            
            // --- 2. Pull down any new reports from server (Delta Sync) ---
            // This ensures our local cache is fresh even if the app was closed.
            val repository = com.sahyadrisiri.data.repository.ReportRepository(appContext)
            repository.getReports()

            NotificationHelper.dismissSyncNotification(appContext)
            Log.d("SyncWorker", "Successfully synced $successCount/${pendingReports.size} reports and updated local cache.")
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Critical sync failure", e)
            NotificationHelper.dismissSyncNotification(appContext)
            Result.retry()
        }
    }
}
