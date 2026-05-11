package com.sahyadrisiri.ui.components

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sahyadrisiri.ui.glass.*
import com.sahyadrisiri.ui.theme.*
import com.sahyadrisiri.viewmodel.SearchedLocation
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReportSheet(
    clarity: Int, flow: String, smell: String, isAnon: Boolean,
    photo: String?, description: String, searchedLocation: SearchedLocation?,
    isSubmitting: Boolean,
    isLocatingLocation: Boolean = false,
    onClarityChange: (Int) -> Unit, onFlowChange: (String) -> Unit,
    onSmellChange: (String) -> Unit, onAnonChange: (Boolean) -> Unit,
    onPhotoChange: (String?) -> Unit, onDescriptionChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit = {},
    onSubmit: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCompressing by remember { mutableStateOf(false) }
    var localPreviewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { u ->
            // Set compressing to true on UI thread immediately
            isCompressing = true
            scope.launch(Dispatchers.IO) {
                try {
                    // Use FileDescriptor for more stable reading of large images
                    context.contentResolver.openFileDescriptor(u, "r")?.use { pfd ->
                        val fd = pfd.fileDescriptor
                        
                        // 1. Get original dimensions
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, options)
                        
                        // 2. Calculate sample size (aim for 1080p-ish base)
                        val maxTarget = 1600
                        var inSampleSize = 1
                        if (options.outHeight > maxTarget || options.outWidth > maxTarget) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while (halfHeight / inSampleSize >= maxTarget && halfWidth / inSampleSize >= maxTarget) {
                                inSampleSize *= 2
                            }
                        }
                        
                        // 3. Decode scaled version
                        val scaledOptions = android.graphics.BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                        }
                        val scaledBitmap = android.graphics.BitmapFactory.decodeFileDescriptor(fd, null, scaledOptions)
                        
                        if (scaledBitmap == null) {
                            withContext(Dispatchers.Main) {
                                isCompressing = false
                                android.widget.Toast.makeText(context, "Could not read image data", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        // 4. Final resize to exactly 800px max dimension
                        val maxDim = 800
                        val (finalW, finalH) = if (scaledBitmap.width > scaledBitmap.height) {
                            val ratio = maxDim.toFloat() / scaledBitmap.width
                            maxDim to (scaledBitmap.height * ratio).toInt().coerceAtLeast(1)
                        } else {
                            val ratio = maxDim.toFloat() / scaledBitmap.height
                            (scaledBitmap.width * ratio).toInt().coerceAtLeast(1) to maxDim
                        }

                        val finalBitmap = android.graphics.Bitmap.createScaledBitmap(scaledBitmap, finalW, finalH, true)
                        
                        // 5. Compress to JPEG (Lower quality to reduce size further)
                        val out = java.io.ByteArrayOutputStream()
                        finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                        val base64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        
                        withContext(Dispatchers.Main) {
                            localPreviewBitmap = finalBitmap
                            onPhotoChange(base64)
                            isCompressing = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AddReportSheet", "Photo processing failed", e)
                    withContext(Dispatchers.Main) {
                        isCompressing = false
                        android.widget.Toast.makeText(context, "Failed to process photo: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = if (isSubmitting) ({}) else onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xFFF9F9F9),
        dragHandle = { Box(Modifier.padding(top = 12.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.Black.copy(0.15f))) }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 48.dp)) {

            Spacer(Modifier.height(4.dp))
            Text("Report Quality", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))

            // Info banner
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp, surfaceColor = Color.White.copy(0.50f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = Neutral400, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your report helps communities downstream. Tag your location below for accurate mapping.",
                        fontSize = 14.sp, color = Neutral600, lineHeight = 20.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── Location Tagging ────────────────────────────────────────
            val locationTagged = searchedLocation != null
            val tagBgColor by animateColorAsState(
                targetValue = if (locationTagged) Color(0xFFE8F5E9) else Color.White,
                animationSpec = tween(400), label = "loc_bg"
            )
            val tagBorderColor by animateColorAsState(
                targetValue = if (locationTagged) Green34C759.copy(0.4f) else Blue007AFF.copy(0.25f),
                animationSpec = tween(400), label = "loc_border"
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tagBgColor)
                    .border(1.dp, tagBorderColor, RoundedCornerShape(16.dp))
                    .then(if (!isSubmitting && !isLocatingLocation) Modifier.glassClickable { onUseCurrentLocation() } else Modifier)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Crosshair icon container
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (locationTagged)
                                    Brush.radialGradient(listOf(Green34C759.copy(0.15f), Green34C759.copy(0.05f)))
                                else
                                    Brush.radialGradient(listOf(Blue007AFF.copy(0.12f), Blue007AFF.copy(0.04f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLocatingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Blue007AFF,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.MyLocation,
                                contentDescription = "Use current location",
                                tint = if (locationTagged) Green34C759 else Blue007AFF,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        if (locationTagged) {
                            Text(
                                "Location tagged",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Green34C759,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                searchedLocation!!.name.split(",").take(2).joinToString(","),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Neutral800,
                                maxLines = 1
                            )
                        } else if (isLocatingLocation) {
                            Text(
                                "Fetching your location...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Neutral500
                            )
                        } else {
                            Text(
                                "Use Current Location",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Neutral800
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Tag report with your GPS position",
                                fontSize = 12.sp,
                                color = Neutral400
                            )
                        }
                    }

                    if (locationTagged) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Green34C759,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Clarity ─────────────────────────────────────────────────
            SectionLabel("Clarity Score")
            Spacer(Modifier.height(10.dp))
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 20.dp, surfaceColor = Color(0xFFF2F2F7), showSpecular = false, showBorder = false) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { v ->
                        val selected = clarity == v
                        val (bg, fg) = clarityColors(v, selected)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (selected) bg else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .then(if (selected) Modifier.border(0.5.dp, Color.White.copy(0.70f), RoundedCornerShape(14.dp)) else Modifier)
                                .glassClickable { if (!isSubmitting) onClarityChange(v) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$v", fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) fg else Neutral500)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1 - Muddy", fontSize = 12.sp, color = Neutral400)
                Text("5 - Crystal Clear", fontSize = 12.sp, color = Neutral400)
            }

            Spacer(Modifier.height(22.dp))

            // ── Flow ────────────────────────────────────────────────────
            SectionLabel("Water Flow")
            Spacer(Modifier.height(10.dp))
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp, surfaceColor = Color(0xFFF2F2F7).copy(0.50f), showSpecular = false, showBorder = false) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Low","Normal","High").forEach { f ->
                        val selected = flow == f
                        val (bg, fg) = flowColors(f, selected)
                        Box(
                            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) bg else Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent)))
                                .then(if (selected) Modifier.border(0.5.dp, Color.White.copy(0.70f), RoundedCornerShape(12.dp)) else Modifier)
                                .glassClickable { if (!isSubmitting) onFlowChange(f) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(f, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) fg else Neutral500)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Smell ───────────────────────────────────────────────────
            SectionLabel("Odor")
            Spacer(Modifier.height(10.dp))
            val smells = listOf("None","Earthy","Chemical","Sewage")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    smells.take(2).forEach { s -> 
                        SmellChipGlass(s, smell == s, Modifier.weight(1f)) { if (!isSubmitting) onSmellChange(s) } 
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    smells.drop(2).forEach { s -> 
                        SmellChipGlass(s, smell == s, Modifier.weight(1f)) { if (!isSubmitting) onSmellChange(s) } 
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Photo ───────────────────────────────────────────────────
            SectionLabel("Attach Photo")
            Spacer(Modifier.height(10.dp))
            if (photo != null || isCompressing) {
                Box(Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(20.dp))) {
                    if (isCompressing) {
                        Box(Modifier.fillMaxSize().background(Neutral100), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Blue007AFF, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.height(12.dp))
                                Text("Optimizing image...", color = Neutral500, fontSize = 14.sp)
                            }
                        }
                    } else {
                        if (localPreviewBitmap != null) {
                            Image(
                                bitmap = localPreviewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = photo,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (!isSubmitting) {
                            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.50f)))).glassClickable { 
                                onPhotoChange(null)
                                localPreviewBitmap = null
                            }.padding(12.dp), contentAlignment = Alignment.Center) {
                                Text("Remove Photo", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassPhotoButton("Take Photo", Icons.Filled.CameraAlt, Blue007AFF, Modifier.weight(1f)) { if (!isSubmitting) pickImage.launch("image/*") }
                    GlassPhotoButton("Upload", Icons.Filled.Upload, Neutral500, Modifier.weight(1f)) { if (!isSubmitting) pickImage.launch("image/*") }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Description ─────────────────────────────────────────────
            SectionLabel("Describe Condition (Optional)")
            Spacer(Modifier.height(10.dp))
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp, surfaceColor = Color.White.copy(0.60f), showBorder = false, showSpecular = false) {
                TextField(
                    value = description, onValueChange = onDescriptionChange,
                    enabled = !isSubmitting,
                    placeholder = { Text("Share more details about the water condition...", color = Neutral400, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── Anonymous toggle ─────────────────────────────────────────
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 16.dp, surfaceColor = Color.White.copy(0.60f), showBorder = false, showSpecular = false) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Anonymous Report", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text("Hide identity. Useful for reporting industrial dumping.", fontSize = 13.sp, color = Neutral500, lineHeight = 18.sp)
                    }
                    Switch(checked = isAnon, onCheckedChange = onAnonChange, enabled = !isSubmitting, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Blue007AFF, uncheckedTrackColor = Neutral200, uncheckedThumbColor = Color.White))
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Submit ────────────────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (isSubmitting) SolidColor(Neutral200) else Brush.linearGradient(listOf(Blue007AFF, Color(0xFF0062CC))))
                    .border(0.5.dp, Color.White.copy(0.30f), RoundedCornerShape(18.dp))
                    .then(if (!isSubmitting) Modifier.glassClickable(onSubmit) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    // Specular highlight
                    Box(Modifier.fillMaxWidth().height(1.5.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(0.40f), Color.Transparent))).align(Alignment.TopCenter))
                    Text("Submit Observation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Black)

@Composable
private fun SmellChipGlass(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = smellColors(label, selected)
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .then(if (selected) Modifier.border(0.5.dp, (fg as? Color ?: Color.White).copy(0.30f), RoundedCornerShape(16.dp)) else Modifier)
            .glassClickable(onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = fg as Color) }
}

@Composable
private fun GlassPhotoButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(112.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White, iconColor.copy(0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White, iconColor.copy(0.15f))), RoundedCornerShape(20.dp))
            .glassClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(iconColor.copy(0.10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Neutral800)
        }
    }
}

private fun clarityColors(v: Int, s: Boolean): Pair<Brush, Color> {
    if (!s) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)) to Neutral500
    return when (v) {
        1 -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFEBE5))) to RedFF3B30
        2 -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF4E5))) to OrangeFF9500
        3 -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF9CC))) to Color(0xFFB8860B)
        4 -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE5F5EA))) to Green34C759
        5 -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE5F0FF))) to Blue007AFF
        else -> Brush.verticalGradient(listOf(Color.White, Color.White)) to Black
    }
}
private fun flowColors(v: String, s: Boolean): Pair<Brush, Color> {
    if (!s) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)) to Neutral500
    return when (v) {
        "Low"    -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF4E5))) to OrangeFF9500
        "Normal" -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE5F0FF))) to Blue007AFF
        "High"   -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFEBE5))) to RedFF3B30
        else     -> Brush.verticalGradient(listOf(Color.White, Color.White)) to Black
    }
}
private fun smellColors(v: String, s: Boolean): Pair<Brush, Color> {
    if (!s) return Brush.verticalGradient(listOf(Color(0xFFF2F2F7), Color(0xFFF2F2F7))) to Neutral600
    return when (v) {
        "None"     -> Brush.verticalGradient(listOf(Color.White, Color(0xFFE5F5EA))) to Green34C759
        "Earthy"   -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF4E5))) to OrangeFF9500
        else       -> Brush.verticalGradient(listOf(Color.White, Color(0xFFFFEBE5))) to RedFF3B30
    }
}
