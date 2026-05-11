package com.sahyadrisiri.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sahyadrisiri.data.model.PlaceResult
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.data.repository.ReportRepository
import com.sahyadrisiri.data.api.SupabaseClient
import com.sahyadrisiri.service.NotificationHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

// ─── Tab enum ────────────────────────────────────────────────────────────────
enum class AppTab { MAP, ALERTS, WIKI }

// ─── UI state snapshots ───────────────────────────────────────────────────────
data class LatLng(val lat: Double, val lng: Double)
data class SearchedLocation(val lat: Double, val lng: Double, val name: String)
data class MapTarget(val lat: Double, val lng: Double, val zoom: Double = 15.0, val timestamp: Long = System.currentTimeMillis())

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReportRepository(application)

    // ─── Tab ─────────────────────────────────────────────────────────────────
    val activeTab = MutableStateFlow(AppTab.MAP)
    val lastViewedAlertsTime = MutableStateFlow(0L)

    // ─── Reports ─────────────────────────────────────────────────────────────
    val reports = MutableStateFlow<List<Report>>(emptyList())
    val selectedReport = MutableStateFlow<Report?>(null)

    // ─── Search ──────────────────────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")
    val placeResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val isSearchingPlaces = MutableStateFlow(false)
    val searchedLocation = MutableStateFlow<SearchedLocation?>(null)

    // ─── Overlays ────────────────────────────────────────────────────────────
    val showHealthScore = MutableStateFlow(true)
    val showFullAddReport = MutableStateFlow(true)

    // ─── Map ──────────────────────────────────────────────────────────────────
    val mapType = MutableStateFlow("street") // "street" | "satellite"
    val mapCenter = MutableStateFlow<MapTarget?>(MapTarget(22.0, 78.0, 4.5))
    val navigatingTo = MutableStateFlow<Report?>(null)
    val routePoints = MutableStateFlow<List<LatLng>>(emptyList())

    // ─── User Location ────────────────────────────────────────────────────────
    val userLocation = MutableStateFlow<LatLng?>(null)
    val isLocating = MutableStateFlow(false)

    // ─── Assistant ────────────────────────────────────────────────────────────
    val isAssistantOpen = MutableStateFlow(false)

    // ─── Current Location for Report ─────────────────────────────────────
    val isLocatingForReport = MutableStateFlow(false)

    // ─── Report Form ─────────────────────────────────────────────────────
    val isReporting = MutableStateFlow(false)
    val isSubmittingReport = MutableStateFlow(false)
    val formClarity = MutableStateFlow(3)
    val formFlow = MutableStateFlow("Normal")
    val formSmell = MutableStateFlow("None")
    val formIsAnon = MutableStateFlow(false)
    val formPhoto = MutableStateFlow<String?>(null)
    val formDescription = MutableStateFlow("")

    // ─── Edit Report ─────────────────────────────────────────────────────
    val isEditing = MutableStateFlow(false)
    val editingReport = MutableStateFlow<Report?>(null)
    val isUpdatingReport = MutableStateFlow(false)

    // ─── Profile ─────────────────────────────────────────────────────────────
    val showProfile = MutableStateFlow(false)
    val userName     = MutableStateFlow("Sahyadri Siri")
    val userEmail    = MutableStateFlow("user@sahyadrisiri.app")
    val memberSince  = MutableStateFlow("January 2025")
    val totalReports:    StateFlow<Int> = reports.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val cleanReports:    StateFlow<Int> = reports.map { it.count { r -> r.status == "clean" } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val warningReports:  StateFlow<Int> = reports.map { it.count { r -> r.status == "warning" } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pollutedReports: StateFlow<Int> = reports.map { it.count { r -> r.status == "polluted" } }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ─── Computed ─────────────────────────────────────────────────────────────
    val sortedReports: StateFlow<List<Report>> = reports
        .map { list -> list.sortedByDescending { isoToMillis(it.timestamp) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadAlertsCount: StateFlow<Int> = combine(reports, lastViewedAlertsTime) { r, time ->
        r.count { it.status != "clean" && isoToMillis(it.timestamp) > time }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val healthScore: StateFlow<Int> = reports.map { list ->
        if (list.isEmpty()) 100
        else {
            val sum = list.sumOf { r ->
                when (r.status) { "clean" -> 1.0; "warning" -> 0.5; else -> 0.0 }
            }
            ((sum / list.size) * 100).toInt()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    // ─── Debounce & Lifecycle ────────────────────────────────────────────────
    private var searchJob: Job? = null
    private var realtimeJob: Job? = null

    init {
        observeLocalDatabase()
        fetchReports()
        startAutoHideTimers()
        observeSearchQuery()
        loadUserProfile()
    }

    private fun observeLocalDatabase() {
        viewModelScope.launch {
            repo.observeReports().collect { data ->
                reports.value = data
            }
        }
    }
 
    /**
     * Starts or restarts the Realtime listener. 
     * Should be called after successful login to ensure the connection
     * uses the correct user permissions.
     */
    fun setupRealtimeListener() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            try {
                Log.d("Realtime", "Starting Realtime listener...")
                val channel = SupabaseClient.client.channel("reports-realtime")
                
                // Listen specifically to the reports table
                val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "reports"
                }
 
                channel.subscribe()
                Log.d("Realtime", "Channel subscription called")

                flow.collect { action ->
                    Log.d("Realtime", "Received action: $action")
                    

                    when (action) {
                        is PostgresAction.Insert -> {
                            val newRecord = action.decodeRecord<Report>()
                            Log.d("Realtime", "Insert event: ${newRecord.id}")
                            // Save to local DB instantly - the observeLocalDatabase() will update the UI
                            repo.saveReportLocally(newRecord)
                                
                            // Show notification if nearby for ALL conditions
                            val userPos = userLocation.value
                            if (userPos != null) {
                                val dist = NotificationHelper.distanceKm(
                                    userPos.lat, userPos.lng,
                                    newRecord.lat, newRecord.lng
                                )
                                if (dist <= NotificationHelper.NEARBY_RADIUS_KM) {
                                    NotificationHelper.showWaterAlert(getApplication(), newRecord)
                                }
                            }
                        }
                        is PostgresAction.Update -> {
                            val updatedRecord = action.decodeRecord<Report>()
                            Log.d("Realtime", "Update event: ${updatedRecord.id}")
                            repo.saveReportLocally(updatedRecord)
                        }
                        is PostgresAction.Delete -> {
                            val deletedId = action.oldRecord["id"]?.toString()?.removeSurrounding("\"")
                            Log.d("Realtime", "Delete event for ID: $deletedId")
                            if (deletedId != null && deletedId != "null") {
                                repo.deleteReportLocally(deletedId)
                                if (selectedReport.value?.id == deletedId) {
                                    selectedReport.value = null
                                }
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Realtime subscription failed", e)
            }
        }
    }

    fun refreshProfile() {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user != null) {
                    userEmail.value = user.email ?: "user@sahyadrisiri.app"
                    
                    // Try to get name from metadata, fallback to email prefix
                    val metadataName = user.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"")
                        ?: user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                    
                    if (!metadataName.isNullOrBlank() && metadataName != "null") {
                        userName.value = metadataName
                    } else if (!user.email.isNullOrBlank()) {
                        userName.value = user.email!!.substringBefore("@").replace(".", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    }

                    // Format member since from created_at if available
                    user.createdAt?.let {
                        try {
                            val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
                            memberSince.value = sdf.format(Date(it.toEpochMilliseconds()))
                        } catch (e: Exception) {
                            Log.e("ViewModel", "Failed to parse createdAt", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to load user profile", e)
            }
        }
    }

    fun fetchReports() {
        viewModelScope.launch {
            // 1. Load local reports immediately for instant UI
            val localData = repo.getLocalReports()
            if (localData.isNotEmpty()) {
                reports.value = localData
            }
            
            // 2. Fetch fresh data from network in background
            repo.getReports().onSuccess { freshData ->
                reports.value = freshData
            }.onFailure {
                Log.e("ViewModel", "Failed to fetch reports", it)
            }
        }
    }

    private fun startAutoHideTimers() {
        viewModelScope.launch {
            delay(10_000)
            showHealthScore.value = false
        }
        viewModelScope.launch {
            delay(15_000)
            showFullAddReport.value = false
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery.collectLatest { q ->
                searchJob?.cancel()
                if (q.isBlank()) {
                    placeResults.value = emptyList()
                    isSearchingPlaces.value = false
                    return@collectLatest
                }
                searchJob = launch {
                    delay(500)
                    isSearchingPlaces.value = true
                    val userPos = userLocation.value
                    repo.searchPlaces(q, userPos?.lat, userPos?.lng).onSuccess { results ->
                        placeResults.value = results
                    }.onFailure {
                        Log.e("ViewModel", "Place search failed", it)
                        placeResults.value = emptyList()
                    }
                    isSearchingPlaces.value = false
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        activeTab.value = tab
        if (tab == AppTab.ALERTS) lastViewedAlertsTime.value = System.currentTimeMillis()
    }

    fun toggleMapType() {
        mapType.value = if (mapType.value == "street") "satellite" else "street"
    }

    fun goToLocationOnMap(report: Report) {
        searchedLocation.value = null
        mapCenter.value = MapTarget(report.lat, report.lng, zoom = 16.0, timestamp = System.currentTimeMillis())
        activeTab.value = AppTab.MAP
    }

    fun performSearch() {
        val q = searchQuery.value
        if (q.isBlank()) return
        
        viewModelScope.launch {
            isSearchingPlaces.value = true
            val userPos = userLocation.value
            repo.searchPlaces(q, userPos?.lat, userPos?.lng).onSuccess { results ->
                val bestMatch = results.firstOrNull()
                if (bestMatch != null) {
                    selectPlace(bestMatch)
                }
            }.onFailure {
                Log.e("ViewModel", "Keyboard search failed", it)
            }
            isSearchingPlaces.value = false
        }
    }

    fun selectPlace(place: PlaceResult) {
        val lat = place.lat.toDoubleOrNull() ?: return
        val lon = place.lon.toDoubleOrNull() ?: return
        
        Log.d("ViewModel", "Selecting place: ${place.displayName} at $lat, $lon")
        
        navigatingTo.value = null
        searchedLocation.value = SearchedLocation(lat, lon, place.displayName)
        mapCenter.value = MapTarget(lat, lon, zoom = 16.0, timestamp = System.currentTimeMillis())
        
        searchQuery.value = ""
        placeResults.value = emptyList()
        isSearchingPlaces.value = false
    }

    /**
     * Uses the device's current GPS location for the report.
     * Reverse geocodes the coordinates to get a real place name,
     * then sets it as searchedLocation so the report gets proper tagging.
     */
    fun useCurrentLocation() {
        val loc = userLocation.value ?: return
        isLocatingForReport.value = true
        viewModelScope.launch {
            repo.reverseGeocode(loc.lat, loc.lng).onSuccess { place ->
                val name = place.displayName
                searchedLocation.value = SearchedLocation(loc.lat, loc.lng, name)
            }.onFailure {
                // Fallback: use raw coordinates as name
                searchedLocation.value = SearchedLocation(
                    loc.lat, loc.lng,
                    "Lat ${String.format("%.4f", loc.lat)}, Lng ${String.format("%.4f", loc.lng)}"
                )
            }
            isLocatingForReport.value = false
        }
    }

    fun requestRecenter() {
        userLocation.value?.let {
            searchedLocation.value = null
            mapCenter.value = MapTarget(it.lat, it.lng, zoom = 15.0, timestamp = System.currentTimeMillis())
        }
    }

    fun onLocationReceived(location: Location) {
        val newLatLng = LatLng(location.latitude, location.longitude)
        userLocation.value = newLatLng
        
        if (mapCenter.value == null) {
            mapCenter.value = MapTarget(newLatLng.lat, newLatLng.lng)
        }
        isLocating.value = false
    }

    fun submitReport(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isSubmittingReport.value) return
        
        val status = when {
            formClarity.value <= 2 || formSmell.value == "Chemical" || formSmell.value == "Sewage" -> "polluted"
            formClarity.value == 3 || formSmell.value == "Earthy" -> "warning"
            else -> "clean"
        }
        val sl = searchedLocation.value
        val ul = userLocation.value
        val reportLat = sl?.lat ?: ul?.lat ?: (17.9237 + (Math.random() - 0.5) * 0.05)
        val reportLng = sl?.lng ?: ul?.lng ?: (73.6538 + (Math.random() - 0.5) * 0.05)

        val currentUserId = try {
            SupabaseClient.client.auth.currentUserOrNull()?.id
        } catch (_: Exception) { null }

        val newReport = Report(
            id = null, // Let Supabase auto-generate UUID
            userId = if (formIsAnon.value) null else currentUserId,
            lat = reportLat,
            lng = reportLng,
            name = if (sl != null) "Community Report: ${sl.name.split(',')[0]}"
                   else "Community Report #${(Math.random() * 1000).toInt()}",
            clarity = formClarity.value,
            flow = formFlow.value,
            smell = formSmell.value,
            description = formDescription.value.trim().ifBlank { null },
            timestamp = iso8601Now(),
            status = status,
            photo = formPhoto.value,
            isAnonymous = formIsAnon.value
        )

        viewModelScope.launch {
            isSubmittingReport.value = true
            repo.createReport(newReport).onSuccess {
                // We DON'T manually add to reports.value here anymore.
                // We let the Realtime listener handle the Insert event for 100% accuracy.
                isReporting.value = false
                resetForm()
                onSuccess()
            }.onFailure {
                onError("Failed to submit report. Please check your connection.")
            }
            isSubmittingReport.value = false
        }
    }

    fun deleteReport(id: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            repo.deleteReport(id).onSuccess {
                reports.value = reports.value.filter { it.id != id }
                if (selectedReport.value?.id == id) selectedReport.value = null
            }.onFailure {
                onError("Failed to delete report")
            }
        }
    }

    /**
     * Starts editing a report — populates form fields with existing values.
     * Only allowed if the report has not been edited before (one-time edit).
     */
    fun startEditReport(report: Report) {
        if (report.isEdited) return  // Already edited once
        editingReport.value = report
        formClarity.value = report.clarity
        formFlow.value = report.flow
        formSmell.value = report.smell
        formIsAnon.value = report.isAnonymous
        formPhoto.value = report.photo
        formDescription.value = report.description ?: ""
        selectedReport.value = null  // Close detail sheet
        isEditing.value = true       // Open edit sheet
    }

    /**
     * Submits edited report to Supabase. Marks report as edited so it
     * cannot be edited again (one-time edit policy).
     */
    fun updateReport(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val original = editingReport.value ?: return
        if (isUpdatingReport.value) return

        val status = when {
            formClarity.value <= 2 || formSmell.value == "Chemical" || formSmell.value == "Sewage" -> "polluted"
            formClarity.value == 3 || formSmell.value == "Earthy" -> "warning"
            else -> "clean"
        }

        val updated = original.copy(
            clarity = formClarity.value,
            flow = formFlow.value,
            smell = formSmell.value,
            description = formDescription.value.trim().ifBlank { null },
            photo = formPhoto.value,
            isAnonymous = formIsAnon.value,
            status = status,
            isEdited = true  // Mark as edited — no further edits allowed
        )

        viewModelScope.launch {
            isUpdatingReport.value = true
            repo.updateReport(updated).onSuccess {
                reports.value = reports.value.map { if (it.id == updated.id) updated else it }
                isEditing.value = false
                editingReport.value = null
                resetForm()
                onSuccess()
            }.onFailure {
                Log.e("MainViewModel", "Update failed", it)
                onError("Failed to update report. Please check your connection.")
            }
            isUpdatingReport.value = false
        }
    }

    private fun resetForm() {
        formClarity.value = 3
        formFlow.value = "Normal"
        formSmell.value = "None"
        formIsAnon.value = false
        formPhoto.value = null
        formDescription.value = ""
    }

    private fun iso8601Now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .also { it.timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    companion object {
        fun isoToMillis(iso: String): Long = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }
}
