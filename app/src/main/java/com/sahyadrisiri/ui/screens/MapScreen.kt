package com.sahyadrisiri.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.viewmodel.LatLng
import com.sahyadrisiri.viewmodel.MapTarget
import com.sahyadrisiri.viewmodel.SearchedLocation
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory

private const val OSM_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "&copy; OpenStreetMap contributors"
    },
    "arcgis": {
      "type": "raster",
      "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256
    }
  },
  "layers": [
    {
      "id": "osm-layer",
      "type": "raster",
      "source": "osm",
      "layout": { "visibility": "visible" },
      "paint": { "raster-brightness-max": 0.9, "raster-contrast": 0.1 }
    },
    {
      "id": "satellite-layer",
      "type": "raster",
      "source": "arcgis",
      "layout": { "visibility": "none" },
      "paint": { "raster-saturation": 0.2, "raster-contrast": 0.1 }
    }
  ]
}
"""

private val COLOR_CLEAN    = Color.parseColor("#3B82F6")
private val COLOR_WARNING  = Color.parseColor("#F59E0B")
private val COLOR_POLLUTED = Color.parseColor("#9A3412")
private val COLOR_USER_LOC = Color.parseColor("#3B82F6")
private val COLOR_SEARCH   = Color.parseColor("#A855F7")
private val COLOR_ROUTE    = Color.parseColor("#2563EB")

@Composable
fun OsmdroidMapView(
    reports: List<Report>,
    userLocation: LatLng?,
    mapCenter: MapTarget?,
    navigatingTo: Report?,
    searchedLocation: SearchedLocation?,
    routePoints: List<MapLibreLatLng>,
    mapType: String,
    onMapClick: (MapLibreLatLng) -> Unit,
    onMarkerClick: (Report) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Cache icons for performance (don't recreate on every frame)
    val pinCache = remember(context) { mutableMapOf<Int, org.maplibre.android.annotations.Icon>() }
    val iconFactory = remember(context) { IconFactory.getInstance(context) }

    val mapView = remember(context) {
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .compassEnabled(false)
            .logoEnabled(false)
            .attributionEnabled(false)
            .rotateGesturesEnabled(false) // FIXED: Disable rotation
            .tiltGesturesEnabled(false)   // FIXED: Disable tilt (keep it flat)
            .camera(CameraPosition.Builder().target(MapLibreLatLng(22.0, 78.0)).zoom(4.5).build())
            .pixelRatio(context.resources.displayMetrics.density)

        MapView(context, options).apply {
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(OSM_STYLE))
                map.addOnMapClickListener { point ->
                    onMapClick(point)
                    true
                }
                map.setOnMarkerClickListener { marker ->
                    // Find report by title (which is report.name)
                    val report = reports.find { it.name == marker.title }
                    if (report != null) {
                        onMarkerClick(report)
                    }
                    true // Consume the event
                }
                
                // Ensure camera doesn't rotate automatically
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> try { mapView.onStart() } catch (_: Exception) {}
                Lifecycle.Event.ON_RESUME -> try { mapView.onResume() } catch (_: Exception) {}
                Lifecycle.Event.ON_PAUSE -> try { mapView.onPause() } catch (_: Exception) {}
                Lifecycle.Event.ON_STOP -> try { mapView.onStop() } catch (_: Exception) {}
                Lifecycle.Event.ON_DESTROY -> try { mapView.onDestroy() } catch (_: Exception) {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lastProcessedTarget = remember { object { var timestamp = 0L } }

    AndroidView(
        factory = { mapView },
        update = { mv ->
            mv.getMapAsync { map ->
                map.getStyle { style ->
                    val isSatellite = mapType == "satellite"
                    style.getLayer("osm-layer")?.setProperties(
                        PropertyFactory.visibility(if (isSatellite) "none" else "visible")
                    )
                    style.getLayer("satellite-layer")?.setProperties(
                        PropertyFactory.visibility(if (isSatellite) "visible" else "none")
                    )

                    map.clear()

                    fun getCachedIcon(color: Int): org.maplibre.android.annotations.Icon {
                        return pinCache.getOrPut(color) {
                            iconFactory.fromBitmap(createPinBitmap(context, color))
                        }
                    }

                    if (routePoints.isNotEmpty()) {
                        map.addPolyline(PolylineOptions()
                            .addAll(routePoints)
                            .color(COLOR_ROUTE)
                            .width(6f))
                    }

                    reports.forEach { report ->
                        val color = when (report.status) {
                            "clean"    -> COLOR_CLEAN
                            "warning"  -> COLOR_WARNING
                            "polluted" -> COLOR_POLLUTED
                            else       -> Color.GRAY
                        }
                        
                        map.addMarker(MarkerOptions()
                            .position(MapLibreLatLng(report.lat, report.lng))
                            .title(report.name)
                            .icon(getCachedIcon(color)))
                    }

                    if (userLocation != null) {
                        val bitmap = createDotBitmap(context, COLOR_USER_LOC, 26)
                        val icon = IconFactory.getInstance(context).fromBitmap(bitmap)
                        map.addMarker(MarkerOptions()
                            .position(MapLibreLatLng(userLocation.lat, userLocation.lng))
                            .icon(icon))
                    }

                    if (searchedLocation != null) {
                        val bitmap = createPinBitmap(context, COLOR_SEARCH)
                        val icon = IconFactory.getInstance(context).fromBitmap(bitmap)
                        map.addMarker(MarkerOptions()
                            .position(MapLibreLatLng(searchedLocation.lat, searchedLocation.lng))
                            .title(searchedLocation.name)
                            .icon(icon))
                    }

                    val target = mapCenter
                    if (target != null && target.timestamp > lastProcessedTarget.timestamp) {
                        lastProcessedTarget.timestamp = target.timestamp
                        
                        val cameraPosition = CameraPosition.Builder()
                            .target(MapLibreLatLng(target.lat, target.lng))
                            .zoom(target.zoom)
                            .bearing(0.0) // Enforce North-up
                            .tilt(0.0)    // Enforce flat view
                            .build()

                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1200)
                    }
                }
            }
        }
    )
}

private fun createPinBitmap(context: Context, color: Int): Bitmap {
    val width = 96
    val height = 124
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.BLACK
        alpha = 30
        maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val cx = width / 2f
    val r = width * 0.38f
    val circleY = r + 10f
    canvas.drawCircle(cx, height - 12f, 15f, shadowPaint)
    val path = Path().apply {
        moveTo(cx, height.toFloat())
        cubicTo(cx - r * 1.2f, height * 0.7f, cx - r, circleY + r, cx - r, circleY)
        arcTo(cx - r, circleY - r, cx + r, circleY + r, 180f, 180f, false)
        cubicTo(cx + r, circleY + r, cx + r * 1.2f, height * 0.7f, cx, height.toFloat())
        close()
    }
    canvas.drawPath(path, paint)
    canvas.drawPath(path, strokePaint)
    paint.color = Color.WHITE
    paint.alpha = 200
    canvas.drawCircle(cx, circleY, r * 0.4f, paint)
    return bitmap
}

private fun createDotBitmap(context: Context, color: Int, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size.toFloat()
    val cy = size.toFloat()
    val r = (size / 2f) - 4f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        alpha = 40
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, r + 10f, ringPaint)
    canvas.drawCircle(cx, cy, r, paint)
    canvas.drawCircle(cx, cy, r, borderPaint)
    return bitmap
}
