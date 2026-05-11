package com.sahyadrisiri.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sahyadrisiri.ui.glass.GlassPanel
import com.sahyadrisiri.ui.glass.LiquidIconBox
import com.sahyadrisiri.ui.theme.*

@Composable
fun WikiScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Wiki", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)) }
        item {
            WikiGlassCard(
                tag = "Ecosystem", title = "The Sahyadri",
                body = "Millions rely on these pristine, rain-fed streams for survival downstream. Protecting them at the source prevents ecological collapse.",
                icon = Icons.Filled.Landscape, iconColor = Blue007AFF,
                gradientEnd = Color(0xFFE5F0FF)
            )
        }
        item { Spacer(Modifier.height(8.dp)); Text("Guidance", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
        item { WikiGlassCard(title = "Clarity Score", body = "Clear water (5/5) means the ecosystem is functioning. Sudden cloudiness or turbidity means upstream soil erosion or construction wash-off.", icon = Icons.Filled.WaterDrop, iconColor = Blue007AFF, gradientEnd = Color(0xFFE5F0FF)) }
        item { WikiGlassCard(title = "Flow Rate", body = "Report abnormal flow. A dry stream during monsoons or unexpected high flow might indicate illegal damming or sudden discharge upstream.", icon = Icons.Filled.Waves, iconColor = OrangeFF9500, gradientEnd = Color(0xFFFFF4E5)) }
        item { WikiGlassCard(title = "Odor / Smell", body = "A natural earthy smell is fine. Chemical or sewage smells indicate illegal dumping from factories or overloaded local septic tanks.", icon = Icons.Filled.Air, iconColor = RedFF3B30, gradientEnd = Color(0xFFFFEBE5)) }
        item { WikiGlassCard(title = "Reporting Safety", body = "Always prioritize personal safety. Only report from stable terrain and safe distances. Turn on Anonymous Reporting if observing illegal activities.", icon = Icons.Filled.Shield, iconColor = Green34C759, gradientEnd = Color(0xFFE5F5EA)) }
    }
}

@Composable
private fun WikiGlassCard(title: String, body: String, icon: ImageVector, iconColor: Color, gradientEnd: Color, tag: String? = null) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp,
        tintBrush = Brush.linearGradient(listOf(Color.White.copy(0.72f), gradientEnd.copy(0.60f)))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            LiquidIconBox(color = iconColor, size = 56.dp, cornerRadius = 18.dp) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (tag != null) {
                    Text(tag.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = iconColor, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Neutral800)
                Spacer(Modifier.height(6.dp))
                Text(body, fontSize = 14.sp, color = Neutral600, lineHeight = 20.sp)
            }
        }
    }
}
