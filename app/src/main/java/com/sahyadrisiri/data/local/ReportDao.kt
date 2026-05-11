package com.sahyadrisiri.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ReportSyncInfoLocal(
    val id: String,
    val timestamp: String
)

/**
 * Room DAO for offline-first report access.
 * Returns Flow so the UI reacts automatically to any DB change.
 */
@Dao
interface ReportDao {

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Observe all reports, newest first. UI collects this as a Flow. */
    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun observeAllReports(): Flow<List<ReportEntity>>

    /** One-shot read — used during sync */
    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    suspend fun getAllReports(): List<ReportEntity>

    /** Reports waiting to be pushed to Supabase (created while offline) */
    @Query("SELECT * FROM reports WHERE pendingSync = 1")
    suspend fun getPendingSyncReports(): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: String): ReportEntity?

    /** Lightweight read for delta syncing */
    @Query("SELECT id, timestamp FROM reports WHERE pendingSync = 0")
    suspend fun getSyncedReportsInfo(): List<ReportSyncInfoLocal>

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Upsert — replaces on conflict (safe for full refresh from Supabase) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReports(reports: List<ReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: ReportEntity)

    /** Mark a previously offline report as synced */
    @Query("UPDATE reports SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    // ── Delete ────────────────────────────────────────────────────────────────

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reports WHERE pendingSync = 0")
    suspend fun clearAllSynced()

    @Query("DELETE FROM reports")
    suspend fun clearAll()
}
