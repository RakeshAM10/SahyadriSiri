package com.sahyadrisiri.ui.components

import android.util.Log
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.ui.glass.*
import com.sahyadrisiri.ui.screens.relativeTime
import com.sahyadrisiri.ui.screens.statusColor
import com.sahyadrisiri.ui.theme.*
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  ReportDetailSheet  ─  shown when user taps an alert card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailSheet(
    report: Report,
    onClose: () -> Unit,
    onDelete: (String) -> Unit,
    onViewOnMap: () -> Unit,
    onShare: () -> Unit = {},
    isOwner: Boolean = false,
    onEdit: (Report) -> Unit = {}
) {
    val statusColor = report.statusColor()
    val advice      = waterAdvice(report)

    ModalBottomSheet(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Background,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.15f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 56.dp)
        ) {

            // ── 1. HEADER  ─────────────────────────────────────────────────
            ReportHeader(report, statusColor, onDelete, onShare, isOwner, onEdit)

            Spacer(Modifier.height(20.dp))

            // ── 2. PHOTO  ──────────────────────────────────────────────────
            if (!report.photo.isNullOrBlank()) {
                PhotoCard(report.photo)
                Spacer(Modifier.height(16.dp))
            }

            // ── 3. WATER HEALTH SCORE  ─────────────────────────────────────
            WaterHealthScoreCard(report, statusColor)
            Spacer(Modifier.height(14.dp))

            // ── 4. RAW MEASUREMENTS  ───────────────────────────────────────
            MeasurementsRow(report, statusColor)
            Spacer(Modifier.height(14.dp))

            // ── 5. DESCRIPTION FROM REPORTER  ─────────────────────────────
            if (!report.description.isNullOrBlank()) {
                ReporterDescriptionCard(report.description)
                Spacer(Modifier.height(14.dp))
            }

            // ── 6. WATER CONDITION INTERPRETATION  ────────────────────────
            WaterConditionCard(advice)
            Spacer(Modifier.height(14.dp))

            // ── 7. CAUTION PANEL  ─────────────────────────────────────────
            CautionPanel(report, statusColor, advice)
            Spacer(Modifier.height(14.dp))

            // ── 8. SAFE / UNSAFE ACTIVITIES  ──────────────────────────────
            ActivitiesCard(report)
            Spacer(Modifier.height(14.dp))

            // ── 9. ECOSYSTEM IMPACT  ───────────────────────────────────────
            EcosystemCard(report)
            Spacer(Modifier.height(14.dp))

            // ── 10. LOCATION + MAP BUTTON  ─────────────────────────────────
            LocationCard(report, onViewOnMap)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportHeader(
    report: Report,
    statusColor: Color,
    onDelete: (String) -> Unit,
    onShare: () -> Unit,
    isOwner: Boolean,
    onEdit: (Report) -> Unit
) {
    Column {
        // Status badge + time
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusBadge(report.status, statusColor)
            if (report.isEdited) {
                // Show "Edited" badge if report was already edited
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Neutral200)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Edited", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Neutral500)
                }
            }
            Text(report.relativeTime(), fontSize = 13.sp, color = Neutral400)
            Spacer(Modifier.weight(1f))
            
            // Owner actions (Edit & Delete)
            if (isOwner) {
                if (!report.isEdited) {
                    SmallIconButton(Icons.Filled.Edit, OrangeFF9500) { onEdit(report) }
                }
                report.id?.let { id ->
                    SmallIconButton(Icons.Filled.Delete, RedFF3B30) { onDelete(id) }
                }
            }
            
            // Share
            SmallIconButton(Icons.Filled.Share, Blue007AFF) { onShare() }
        }
        Spacer(Modifier.height(10.dp))
        // Place name
        Text(
            report.name,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral800,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(4.dp))
        // Coordinates
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = Neutral400, modifier = Modifier.size(12.dp))
            Text(
                "%.5f, %.5f".format(report.lat, report.lng),
                fontSize = 12.sp,
                color = Neutral400,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. Photo card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhotoCard(photoUrl: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Glass.borderLight, RoundedCornerShape(24.dp))
    ) {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUrl) {
            value = withContext(Dispatchers.IO) {
                try {
                    if (photoUrl.startsWith("data:image")) {
                        val base64String = photoUrl.substringAfter("base64,")
                        val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ReportDetail", "Error decoding image", e)
                    null
                }
            }
        }

        Log.d("ReportDetail", "Loading image. Length: ${photoUrl.length}, Decoded: ${bitmap != null}")
        
        SubcomposeAsyncImage(
            model = bitmap ?: photoUrl,
            contentDescription = "Water condition photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(
                    Modifier.fillMaxSize().background(Neutral100),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Blue007AFF)
                }
            },
            error = {
                Box(
                    Modifier.fillMaxSize().background(Neutral100),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Warning, null, tint = Neutral400, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Image unavailable", color = Neutral500, fontSize = 12.sp)
                    }
                }
            }
        )
        // Bottom gradient scrim for text readability
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(0.35f))
                    )
                )
        )
        // Photo label
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(12.dp))
            Text("Field Photo", fontSize = 11.sp, color = Color.White.copy(0.85f), fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Water Health Score — circular gauge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaterHealthScoreCard(report: Report, statusColor: Color) {
    val score = waterHealthScore(report)
    val gradientBrush = Brush.linearGradient(
        listOf(Color.White.copy(0.90f), statusColor.copy(0.08f))
    )

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        tintBrush = gradientBrush
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Circular gauge
            WaterScoreGauge(score, statusColor)

            // Right column
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Water Health Score",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neutral400,
                    letterSpacing = 0.8.sp
                )
                Text(
                    score.label,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    score.summary,
                    fontSize = 13.sp,
                    color = Neutral600,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WaterScoreGauge(score: WaterScore, color: Color) {
    val animatedProgress by animateFloatAsState(
        targetValue = score.fraction,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "score_arc"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .drawBehind {
                val strokeWidth = 7.dp.toPx()
                val radius = (size.minDimension / 2f) - strokeWidth / 2f
                // Track
                drawCircle(color = Color(0xFFE5E5EA), radius = radius, style = Stroke(strokeWidth))
                // Progress arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${score.value}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text("/10", fontSize = 11.sp, color = Neutral400, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  4. Measurements row (Clarity / Flow / Smell)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MeasurementsRow(report: Report, statusColor: Color) {
    val brush = Brush.linearGradient(listOf(Color.White.copy(0.85f), statusColor.copy(0.06f)))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MeasurementBox("CLARITY", "${report.clarity}", "/5", clarityColor(report.clarity), brush, Modifier.weight(1f))
        MeasurementBox("FLOW",    report.flow,         null, flowColor(report.flow),      brush, Modifier.weight(1f))
        MeasurementBox("SMELL",   report.smell,        null, smellColor(report.smell),    brush, Modifier.weight(1f))
    }
}

@Composable
private fun MeasurementBox(
    label: String,
    value: String,
    suffix: String?,
    valueColor: Color,
    brush: Brush,
    modifier: Modifier
) {
    GlassPanel(modifier.height(96.dp), cornerRadius = 20.dp, tintBrush = brush) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Neutral400, letterSpacing = 1.sp)
            if (suffix != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.alignByBaseline())
                    Text(suffix, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neutral400, modifier = Modifier.alignByBaseline())
                }
            } else {
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = valueColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  5. Reporter description
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReporterDescriptionCard(description: String) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.verticalGradient(listOf(Color.White.copy(0.85f), Color.White.copy(0.60f)))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.FormatQuote, null, tint = Blue007AFF.copy(0.70f), modifier = Modifier.size(18.dp))
                Text("Reporter's Observation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neutral400, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(description, fontSize = 15.sp, color = Neutral800, lineHeight = 22.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  6. Water condition interpretation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WaterConditionCard(advice: WaterAdvice) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.linearGradient(listOf(Color.White.copy(0.90f), advice.color.copy(0.07f)))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidIconBox(color = advice.color, size = 40.dp, cornerRadius = 12.dp) {
                    Icon(Icons.Filled.Science, null, tint = advice.color, modifier = Modifier.size(18.dp))
                }
                Text("Condition Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Neutral800)
            }
            advice.conditions.forEach { condition ->
                ConditionRow(condition)
            }
        }
    }
}

@Composable
private fun ConditionRow(condition: ConditionPoint) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(condition.color.copy(0.07f))
            .border(0.5.dp, condition.color.copy(0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(condition.icon, null, tint = condition.color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(condition.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Neutral800)
            Text(condition.detail, fontSize = 13.sp, color = Neutral600, lineHeight = 18.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  7. Caution panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CautionPanel(report: Report, statusColor: Color, advice: WaterAdvice) {
    val icon = when (report.status) {
        "polluted" -> Icons.Filled.Warning
        "warning"  -> Icons.Filled.ReportProblem
        else       -> Icons.Filled.CheckCircle
    }
    val headerText = when (report.status) {
        "polluted" -> "⚠ Serious Caution"
        "warning"  -> "⚡ Advisory Notice"
        else       -> "✓ Generally Safe"
    }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.linearGradient(
            listOf(statusColor.copy(0.10f), statusColor.copy(0.04f))
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(0.15f))
                        .border(0.5.dp, statusColor.copy(0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
                Text(headerText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }
            // Advisory points
            advice.cautions.forEach { caution ->
                CautionRow(caution, statusColor)
            }
        }
    }
}

@Composable
private fun CautionRow(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text, fontSize = 14.sp, color = Neutral800, lineHeight = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  8. Activities card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivitiesCard(report: Report) {
    val activities = activityAdvice(report.status)

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.verticalGradient(listOf(Color.White.copy(0.90f), Color.White.copy(0.65f)))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Activity Guide",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral800
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Safe column
                ActivityColumn(
                    title = "Safe",
                    color = Green34C759,
                    items = activities.safe,
                    modifier = Modifier.weight(1f)
                )
                // Avoid column
                ActivityColumn(
                    title = "Avoid",
                    color = RedFF3B30,
                    items = activities.avoid,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActivityColumn(title: String, color: Color, items: List<String>, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(0.06f))
            .border(0.5.dp, color.copy(0.18f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (color == Green34C759) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                Text(item, fontSize = 13.sp, color = Neutral800, lineHeight = 18.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  9. Ecosystem card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcosystemCard(report: Report) {
    val (eco, ecoColor) = ecosystemImpact(report)

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.linearGradient(listOf(Color.White.copy(0.85f), ecoColor.copy(0.07f)))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidIconBox(color = ecoColor, size = 40.dp, cornerRadius = 12.dp) {
                    Icon(Icons.Filled.Forest, null, tint = ecoColor, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Ecosystem Impact", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Neutral400, letterSpacing = 0.8.sp)
                    Text(eco.level, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ecoColor)
                }
            }
            // Impact bars
            eco.indicators.forEach { indicator ->
                EcoIndicatorBar(indicator, ecoColor)
            }
            // Footer note
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF2F2F7))
                    .padding(12.dp)
            ) {
                Text(eco.note, fontSize = 13.sp, color = Neutral600, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun EcoIndicatorBar(indicator: EcoIndicator, color: Color) {
    val animatedFraction by animateFloatAsState(
        targetValue = indicator.fraction,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "eco_bar_${indicator.label}"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(indicator.label, fontSize = 13.sp, color = Neutral600, fontWeight = FontWeight.Medium)
            Text(indicator.value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Neutral200)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(color.copy(0.70f), color))
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  10. Location + map button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationCard(report: Report, onViewOnMap: () -> Unit) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        tintBrush = Brush.linearGradient(listOf(Blue007AFF.copy(0.08f), Blue007AFF.copy(0.04f)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocationOn, null, tint = Blue007AFF, modifier = Modifier.size(12.dp))
                    Text("GPS Location", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Blue007AFF.copy(0.80f), letterSpacing = 0.5.sp)
                }
                Text(
                    "%.5f".format(report.lat),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Blue007AFF,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "%.5f".format(report.lng),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Blue007AFF,
                    fontFamily = FontFamily.Monospace
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Blue007AFF)
                    .glassClickable(onViewOnMap)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Map, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("View on Map", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared small components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.80f), color.copy(0.12f))))
            .border(0.5.dp, color.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(status.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, color.copy(0.06f))))
            .border(0.5.dp, color.copy(0.20f), CircleShape)
            .glassClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Data models & logic
// ─────────────────────────────────────────────────────────────────────────────

data class WaterScore(val value: Int, val fraction: Float, val label: String, val summary: String)
data class ConditionPoint(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val detail: String,
    val color: Color
)
data class WaterAdvice(val color: Color, val conditions: List<ConditionPoint>, val cautions: List<String>)
data class ActivitySet(val safe: List<String>, val avoid: List<String>)
data class EcoIndicator(val label: String, val value: String, val fraction: Float)
data class EcoImpact(val level: String, val indicators: List<EcoIndicator>, val note: String)

fun waterHealthScore(report: Report): WaterScore {
    val base = report.clarity * 2  // 2–10
    val flowPenalty = if (report.flow == "High") 1 else 0
    val smellPenalty = when (report.smell) { "Sewage" -> 3; "Chemical" -> 3; "Earthy" -> 1; else -> 0 }
    val value = (base - flowPenalty - smellPenalty).coerceIn(1, 10)
    return WaterScore(
        value  = value,
        fraction = value / 10f,
        label  = when { value >= 8 -> "Excellent"; value >= 6 -> "Good"; value >= 4 -> "Fair"; else -> "Poor" },
        summary = when { value >= 8 -> "Pristine stream, healthy ecosystem"; value >= 6 -> "Acceptable quality, minor concerns"; value >= 4 -> "Degraded — use with caution"; else -> "Severely compromised, avoid contact" }
    )
}

fun waterAdvice(report: Report): WaterAdvice {
    val conditions = mutableListOf<ConditionPoint>()
    val cautions   = mutableListOf<String>()

    // Clarity
    when {
        report.clarity <= 1 -> {
            conditions += ConditionPoint(Icons.Filled.Visibility, "Extremely Turbid Water", "Clarity score ${report.clarity}/5 — water is nearly opaque. This level of turbidity suggests large-scale erosion, landslide runoff, or active construction upstream.", RedFF3B30)
            cautions += "Do not drink even after boiling — suspended particles may carry pathogens"
            cautions += "Avoid any skin contact; turbid water often carries heavy metals and bacteria"
        }
        report.clarity <= 2 -> {
            conditions += ConditionPoint(Icons.Filled.Visibility, "High Turbidity", "Clarity ${report.clarity}/5 — noticeably murky. Could be caused by recent rainfall wash-off, upstream soil disturbance, or sewage mixing.", OrangeFF9500)
            cautions += "Not safe for drinking without advanced filtration"
            cautions += "Avoid prolonged contact with skin; especially avoid eyes and mouth"
        }
        report.clarity <= 3 -> {
            conditions += ConditionPoint(Icons.Filled.Visibility, "Moderate Clarity", "Clarity ${report.clarity}/5 — somewhat cloudy. Natural variation during light rain is possible, but persistent cloudiness warrants monitoring.", OrangeFF9500)
            cautions += "Treat before drinking; basic filters may not be sufficient"
        }
        else -> {
            conditions += ConditionPoint(Icons.Filled.Visibility, "Good Clarity", "Clarity ${report.clarity}/5 — water appears clear. The stream is likely healthy at the observation point.", Green34C759)
        }
    }

    // Smell
    when (report.smell) {
        "Sewage" -> {
            conditions += ConditionPoint(Icons.Filled.Warning, "Sewage Contamination Detected", "Sewage smell indicates faecal coliform bacteria and pathogens. This is a serious public health concern for communities downstream.", RedFF3B30)
            cautions += "Sewage smell = active bacterial contamination; report to local Panchayat or Water Authority"
            cautions += "Downstream communities must stop drawing water until authorities confirm safety"
            cautions += "Keep livestock and animals away from this water source"
        }
        "Chemical" -> {
            conditions += ConditionPoint(Icons.Filled.Science, "Chemical Discharge Detected", "Chemical odour suggests industrial or agricultural runoff — possibly fertiliser, pesticide, or factory effluent. This requires urgent investigation.", RedFF3B30)
            cautions += "Report immediately to KSPCB (Karnataka State Pollution Control Board): 1800-425-8000"
            cautions += "Do not use water for irrigation — chemicals may enter the food chain"
            cautions += "Avoid skin contact; chemical contamination can cause dermatitis"
        }
        "Earthy" -> {
            conditions += ConditionPoint(Icons.Filled.Info, "Natural Earthy Odour", "An earthy smell is typically caused by geosmin released by soil bacteria and algae — common and usually harmless in monsoon season.", OrangeFF9500)
            cautions += "Monitor for changes; earthy smell with discoloration may indicate algae bloom"
        }
        else -> {
            conditions += ConditionPoint(Icons.Filled.CheckCircle, "No Detectable Odour", "No abnormal smell reported. This is a positive indicator of water quality at this checkpoint.", Green34C759)
        }
    }

    // Flow
    when (report.flow) {
        "High" -> {
            conditions += ConditionPoint(Icons.Filled.Water, "Unusually High Flow", "High flow can indicate recent heavy rainfall or possible upstream dam/check dam overflow. Strong currents pose physical safety risks.", OrangeFF9500)
            cautions += "Do not attempt to cross the stream on foot during high flow"
            cautions += "High-flow water stirs up sediment and may carry more pollutants"
        }
        "Low" -> {
            conditions += ConditionPoint(Icons.Filled.Water, "Low Flow Detected", "Low flow during expected wet periods could indicate illegal upstream diversion, over-extraction, or blockage. This concentrates pollutants in the reduced volume of water.", OrangeFF9500)
            cautions += "Low flow concentrates existing pollutants — toxicity may be higher than usual"
            cautions += "Investigate possible upstream blockage or diversion"
        }
    }

    // Status level cautions
    if (cautions.isEmpty() && report.status == "clean") {
        cautions += "Water appears safe — always boil or treat before drinking from wild sources"
        cautions += "Practice Leave No Trace — pack out all waste"
        cautions += "Report any changes you observe during your visit"
    }

    val color = when (report.status) { "polluted" -> RedFF3B30; "warning" -> OrangeFF9500; else -> Green34C759 }
    return WaterAdvice(color, conditions, cautions)
}

fun activityAdvice(status: String): ActivitySet = when (status) {
    "clean" -> ActivitySet(
        safe  = listOf("Swimming", "Fishing", "Irrigation", "Wildlife viewing", "Photography"),
        avoid = listOf("Littering", "Washing vehicles", "Disposing waste", "Camping in flood zone")
    )
    "warning" -> ActivitySet(
        safe  = listOf("Photography", "Wildlife observation", "Shoreline walking"),
        avoid = listOf("Swimming", "Drinking water", "Direct skin contact", "Fishing for food", "Irrigation")
    )
    else -> ActivitySet(
        safe  = listOf("Photography from shore", "Reporting to authorities"),
        avoid = listOf("Swimming", "Any water contact", "Drinking or cooking", "Fishing", "Irrigation", "Livestock access")
    )
}

fun ecosystemImpact(report: Report): Pair<EcoImpact, Color> {
    val color = when (report.status) { "polluted" -> RedFF3B30; "warning" -> OrangeFF9500; else -> Green34C759 }
    val fishFraction     = when { report.clarity >= 4 && report.smell == "None" -> 0.85f; report.clarity >= 3 -> 0.50f; else -> 0.15f }
    val vegetationFrac   = when { report.flow == "Normal" && report.clarity >= 3 -> 0.80f; report.flow == "Low" -> 0.40f; else -> 0.60f }
    val oxygenFrac       = when { report.smell == "Sewage" || report.smell == "Chemical" -> 0.25f; report.clarity <= 2 -> 0.45f; else -> 0.78f }

    val level = when (report.status) { "clean" -> "Low Impact"; "warning" -> "Moderate Stress"; else -> "High Impact" }
    val note  = when (report.status) {
        "clean"    -> "Stream ecosystem appears healthy. Macroinvertebrates and fish populations are likely stable. Continue monitoring during monsoon peak."
        "warning"  -> "Ecosystem under mild stress. Sensitive species may be affected. Sustained conditions could lead to fish migration away from this stretch."
        else       -> "Severe ecological distress. Dissolved oxygen levels are likely depleted. Aquatic life downstream is at significant risk. Immediate intervention needed."
    }

    val impact = EcoImpact(
        level = level,
        indicators = listOf(
            EcoIndicator("Fish Habitat Quality",    if (fishFraction >= 0.7f) "Good" else if (fishFraction >= 0.4f) "Fair" else "Poor",    fishFraction),
            EcoIndicator("Riparian Vegetation",     if (vegetationFrac >= 0.7f) "Healthy" else if (vegetationFrac >= 0.4f) "Stressed" else "Degraded", vegetationFrac),
            EcoIndicator("Dissolved Oxygen (est.)", if (oxygenFrac >= 0.7f) "Normal" else if (oxygenFrac >= 0.4f) "Low" else "Critical", oxygenFrac)
        ),
        note = note
    )
    return Pair(impact, color)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Colour helpers
// ─────────────────────────────────────────────────────────────────────────────

fun clarityColor(clarity: Int): Color = when { clarity >= 4 -> Green34C759; clarity >= 3 -> OrangeFF9500; else -> RedFF3B30 }
fun flowColor(flow: String): Color = when (flow) { "Normal" -> Green34C759; "Low" -> OrangeFF9500; else -> OrangeFF9500 }
fun smellColor(smell: String): Color = when (smell) { "None" -> Green34C759; "Earthy" -> OrangeFF9500; else -> RedFF3B30 }
