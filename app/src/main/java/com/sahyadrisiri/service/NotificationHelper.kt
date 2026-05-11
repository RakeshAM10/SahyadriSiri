package com.sahyadrisiri.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sahyadrisiri.MainActivity
import com.sahyadrisiri.R
import com.sahyadrisiri.data.model.Report
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
//  NotificationHelper
//  Handles creating notification channels and showing water quality alerts.
//  Called by ReportRepository when Supabase Realtime delivers a new report.
// ─────────────────────────────────────────────────────────────────────────────

object NotificationHelper {

    // Channel IDs
    private const val CHANNEL_POLLUTION  = "sahyadri_pollution_alerts"
    private const val CHANNEL_WARNING    = "sahyadri_warning_alerts"
    private const val CHANNEL_SYNC       = "sahyadri_sync"

    // How close (km) a report must be to trigger a notification
    const val NEARBY_RADIUS_KM = 10.0

    /**
     * Must be called once on app start (in Application or MainActivity.onCreate).
     * Creates all notification channels — required on Android 8+.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        // High importance — pollution alert (vibrates + sound)
        NotificationChannel(
            CHANNEL_POLLUTION,
            "Pollution Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a nearby stream is reported polluted"
            enableVibration(true)
            manager.createNotificationChannel(this)
        }

        // Default importance — warning alert
        NotificationChannel(
            CHANNEL_WARNING,
            "Water Quality Warnings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a nearby stream shows warning signs"
            manager.createNotificationChannel(this)
        }

        // Low importance — background sync status
        NotificationChannel(
            CHANNEL_SYNC,
            "Background Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when offline reports are being synced"
            manager.createNotificationChannel(this)
        }
    }

    /**
     * Shows a pollution / warning notification for a newly reported stream.
     * Only called when the report is within NEARBY_RADIUS_KM of the user.
     */
    fun showWaterAlert(context: Context, report: Report) {
        val (title, body, channelId) = when (report.status) {
            "polluted" -> Triple(
                "⚠️ Pollution Alert Nearby",
                "${report.name} has been reported as polluted. Clarity: ${report.clarity}/5. ${if (report.smell != "None") "Smell: ${report.smell}." else ""}",
                CHANNEL_POLLUTION
            )
            "warning" -> Triple(
                "⚡ Water Quality Warning",
                "${report.name} shows warning signs. Clarity: ${report.clarity}/5. Please use caution.",
                CHANNEL_WARNING
            )
            "clean" -> Triple(
                "🌿 Clean Water Reported",
                "${report.name} is reported as clean. Clarity: ${report.clarity}/5. Thank you for monitoring!",
                CHANNEL_SYNC
            )
            else -> return
        }

        // Deep link into app when notification tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_report_id", report.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, report.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (report.status == "polluted") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(
                when (report.status) {
                    "polluted" -> 0xFFFF3B30.toInt() // Red
                    "warning" -> 0xFFFF9500.toInt()  // Orange
                    else -> 0xFF34C759.toInt()      // Green
                }
            )
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(report.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently skip
        }
    }

    /**
     * Shows a silent notification while offline reports are being synced.
     */
    fun showSyncNotification(context: Context, pendingCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Syncing Reports")
            .setContentText("Uploading $pendingCount offline report${if (pendingCount > 1) "s" else ""} to the server...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, notification)
        } catch (e: SecurityException) { /* skip */ }
    }

    fun dismissSyncNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(9999)
    }

    // ── Location helper ───────────────────────────────────────────────────────

    /**
     * Haversine distance in km between two lat/lng points.
     * Used to check if a new report is within NEARBY_RADIUS_KM of the user.
     */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
