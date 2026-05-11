package com.sahyadrisiri.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sahyadrisiri.data.model.Report

/**
 * Room entity — mirrors the Report data class exactly.
 * Stored locally for offline-first access.
 */
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lng: Double,
    val name: String,
    val clarity: Int,
    val flow: String,
    val smell: String,
    val description: String?,
    val timestamp: String,
    val status: String,
    val photo: String?,
    val userId: String?,
    val isAnonymous: Boolean,
    val isEdited: Boolean,
    // Sync flag — true means this report hasn't been pushed to Supabase yet
    // (created offline, waiting for connectivity)
    val pendingSync: Boolean = false
)

// ── Conversion helpers ────────────────────────────────────────────────────────

fun ReportEntity.toReport() = Report(
    id = id, userId = userId, lat = lat, lng = lng, name = name,
    clarity = clarity, flow = flow, smell = smell,
    description = description, timestamp = timestamp,
    status = status, photo = photo,
    isAnonymous = isAnonymous, isEdited = isEdited
)

fun Report.toEntity(pendingSync: Boolean = false) = ReportEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    userId = userId, lat = lat, lng = lng, name = name,
    clarity = clarity, flow = flow, smell = smell,
    description = description, timestamp = timestamp,
    status = status, photo = photo,
    isAnonymous = isAnonymous, isEdited = isEdited,
    pendingSync = pendingSync
)
