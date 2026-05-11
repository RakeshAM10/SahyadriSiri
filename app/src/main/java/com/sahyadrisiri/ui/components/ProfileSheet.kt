package com.sahyadrisiri.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyadrisiri.ui.glass.GlassPanel
import com.sahyadrisiri.ui.glass.LiquidIconBox
import com.sahyadrisiri.ui.glass.glassClickable
import com.sahyadrisiri.ui.theme.*
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  ProfileSheet
//  Triggered by tapping the avatar circle to the right of the search bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    fullName: String,
    email: String,
    memberSince: String,
    totalReports: Int,
    cleanReports: Int,
    warningReports: Int,
    pollutedReports: Int,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xFFF9F9F9),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.15f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── IDENTITY CARD ─────────────────────────────────────────────────
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                tintBrush = Brush.linearGradient(
                    listOf(Color.White.copy(0.82f), Color(0xFFE5F0FF).copy(0.55f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Animated initials avatar
                    ProfileAvatar(name = fullName)

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = fullName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Neutral800,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = email,
                        fontSize = 14.sp,
                        color = Neutral500,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(Modifier.height(6.dp))

                    // Member since pill
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Blue007AFF.copy(0.08f))
                            .border(0.5.dp, Blue007AFF.copy(0.20f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Member since $memberSince",
                            fontSize = 12.sp,
                            color = Blue007AFF,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── YOUR IMPACT CARD ──────────────────────────────────────────────
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                surfaceColor = Color.White.copy(0.72f)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "YOUR IMPACT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Neutral400,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    // Stats grid — 2×2
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ImpactStatChip(
                            value = totalReports,
                            label = "Total",
                            color = Blue007AFF,
                            modifier = Modifier.weight(1f)
                        )
                        ImpactStatChip(
                            value = cleanReports,
                            label = "Clean",
                            color = Green34C759,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ImpactStatChip(
                            value = warningReports,
                            label = "Warning",
                            color = OrangeFF9500,
                            modifier = Modifier.weight(1f)
                        )
                        ImpactStatChip(
                            value = pollutedReports,
                            label = "Polluted",
                            color = RedFF3B30,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── SIGN OUT BUTTON ───────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(0.90f), RedFF3B30.copy(0.04f))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White, RedFF3B30.copy(0.20f))
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .glassClickable(onSignOut),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.ExitToApp,
                        contentDescription = null,
                        tint = RedFF3B30,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sign Out",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RedFF3B30
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ProfileAvatar — initials circle with gradient + liquid wave + pulse ring
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileAvatar(name: String, size: Int = 80) {
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    val infiniteTransition = rememberInfiniteTransition(label = "avatar")

    // Pulse ring
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(2500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Liquid wave inside
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing)
        ),
        label = "wave"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size((size * 1.35f).dp)
    ) {
        // Outer glow ring
        Box(
            Modifier
                .size((size * 1.35f).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Green34C759.copy(0.18f), Color.Transparent)
                    )
                )
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
        )

        // Avatar circle
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF34C759), Color(0xFF007AFF))
                    )
                )
                .border(
                    2.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.60f), Color.White.copy(0.15f))),
                    CircleShape
                )
                .drawBehind { drawAvatarWave(wavePhase) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = (size * 0.32f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}

private fun DrawScope.drawAvatarWave(phase: Float) {
    val w = size.width
    val h = size.height
    val baseY = h * 0.62f
    val amp = h * 0.05f
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(w, h)
        lineTo(w, baseY + amp * sin(phase + 1.5f))
        val steps = 24
        for (i in steps downTo 0) {
            val x = w * i / steps
            lineTo(x, baseY + amp * sin(phase + x / w * 5f))
        }
        close()
    }
    drawPath(path, Color.White.copy(alpha = 0.18f))
}

// ─────────────────────────────────────────────────────────────────────────────
//  ImpactStatChip — tinted liquid glass chip with large number
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImpactStatChip(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chip_$label")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing)),
        label = "wave"
    )

    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(0.82f), color.copy(0.10f))
                )
            )
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.90f), color.copy(0.15f))),
                RoundedCornerShape(20.dp)
            )
            .drawBehind { drawChipWave(wavePhase, color) },
        contentAlignment = Alignment.Center
    ) {
        // Specular top line
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(0.80f), Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$value",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neutral500,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private fun DrawScope.drawChipWave(phase: Float, color: Color) {
    val w = size.width
    val h = size.height
    val baseY = h * 0.72f
    val amp = h * 0.04f
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(w, h)
        lineTo(w, baseY + amp * sin(phase + 1f))
        val steps = 20
        for (i in steps downTo 0) {
            val x = w * i / steps
            lineTo(x, baseY + amp * sin(phase + x / w * 4f))
        }
        close()
    }
    drawPath(path, color.copy(alpha = 0.14f))
}
