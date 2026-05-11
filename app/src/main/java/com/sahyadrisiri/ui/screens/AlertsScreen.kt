package com.sahyadrisiri.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.ui.glass.GlassPanel
import com.sahyadrisiri.ui.glass.LiquidIconBox
import com.sahyadrisiri.ui.glass.StatusGlassCard
import com.sahyadrisiri.ui.glass.glassClickable
import com.sahyadrisiri.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import android.os.Build
import android.text.format.DateUtils
import java.time.Instant

@Composable
fun AlertsScreen(
    sortedReports: List<Report>,
    onReportClick: (Report) -> Unit,
    onViewOnMap: (Report) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Heading
        Text(
            "Community Reports",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp, top = 20.dp)
        )

        if (sortedReports.isEmpty()) {
            // OUTSIDE ALIGNMENT: Centered in available screen space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 80.dp), // Visual offset for bottom nav
                contentAlignment = Alignment.Center
            ) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(0.90f),
                    cornerRadius = 32.dp
                ) {
                    // INSIDE ALIGNMENT: Centered within the card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LiquidIconBox(color = Green34C759, size = 88.dp, cornerRadius = 26.dp) {
                            Icon(Icons.Filled.WaterDrop, null, tint = Green34C759, modifier = Modifier.size(42.dp))
                        }
                        
                        Spacer(Modifier.height(28.dp))
                        
                        Text(
                            "All Clear",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Neutral800,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        Text(
                            "All monitored streams are currently running clear.",
                            fontSize = 16.sp,
                            color = Neutral500,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                    }
                }
            }
        } else {
            // Standard list alignment
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sortedReports, key = { it.id ?: it.hashCode() }) { report ->
                    AlertGlassCard(report, { onReportClick(report) }, { onViewOnMap(report) })
                }
            }
        }
    }
}

@Composable
private fun AlertGlassCard(report: Report, onClick: () -> Unit, onViewMap: () -> Unit) {
    val statusColor = report.statusColor()
    val relativeTimeStr = remember(report.timestamp) { report.relativeTime() }
    val summary = remember(report) {
        buildString {
            if (report.clarity <= 2) append("High Turbidity. ")
            if (report.smell != "None") append("${report.smell} smell. ")
            append(when (report.status) { "clean" -> "Water is reported clean."; "polluted" -> "Please avoid contact."; else -> "Caution is advised." })
        }
    }
    
    StatusGlassCard(status = report.status, modifier = Modifier.fillMaxWidth().glassClickable(onClick)) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                LiquidIconBox(color = statusColor, size = 64.dp) {
                    Icon(Icons.Filled.WaterDrop, null, tint = statusColor, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Text(report.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Neutral800, lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 2)
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(0.70f), statusColor.copy(0.12f)))).border(0.5.dp, statusColor.copy(0.30f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(report.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(relativeTimeStr, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Neutral400, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(summary, fontSize = 14.sp, color = Neutral600, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassStatChip("Clarity", "${report.clarity}/5", Modifier.weight(1f))
                GlassStatChip("Flow", report.flow, Modifier.weight(1f))
                GlassStatChip("Smell", report.smell, Modifier.weight(1f))
                Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(0.75f), Blue007AFF.copy(0.06f)))).border(0.5.dp, Color.White.copy(0.80f), RoundedCornerShape(14.dp)).glassClickable(onViewMap), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LocationOn, null, tint = Blue007AFF, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("View", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Blue007AFF)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassStatChip(label: String, value: String, modifier: Modifier) {
    Box(modifier.height(56.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(0.72f), Color.White.copy(0.40f)))).border(0.5.dp, Color.White.copy(0.80f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Neutral400, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neutral800, maxLines = 1)
        }
    }
}

fun Report.statusColor() = when (status) { "clean" -> Green34C759; "polluted" -> RedFF3B30; else -> OrangeFF9500 }
fun Report.bgGradient() = when (status) { "clean" -> listOf(Color.White.copy(0.7f), Color(0xFFE5F5EA).copy(0.7f)); "polluted" -> listOf(Color.White.copy(0.7f), Color(0xFFFFEBE5).copy(0.7f)); else -> listOf(Color.White.copy(0.7f), Color(0xFFFFF4E5).copy(0.7f)) }
fun Report.relativeTime(): String {
    return try {
        val timeInMillis = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(timestamp).toEpochMilli()
        } else {
            // Fallback for older devices (API 24-25)
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ"
            )
            var parsedTime: Long? = null
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    // Replace 'Z' with +0000 for SimpleDateFormat compatibility
                    parsedTime = sdf.parse(timestamp.replace("Z", "+0000"))?.time
                    if (parsedTime != null) break
                } catch (e: Exception) {
                    // Ignore and try next format
                }
            }
            parsedTime ?: return timestamp
        }

        // Use Android's standard DateUtils for production-level localized relative time
        DateUtils.getRelativeTimeSpanString(
            timeInMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } catch (e: Exception) {
        // Fallback to raw string if parsing fails entirely
        timestamp
    }
}
