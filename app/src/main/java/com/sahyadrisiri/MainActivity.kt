package com.sahyadrisiri

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sahyadrisiri.data.model.PlaceResult
import com.sahyadrisiri.data.model.Report
import com.sahyadrisiri.ui.components.*
import com.sahyadrisiri.ui.glass.*
import com.sahyadrisiri.ui.screens.*
import com.sahyadrisiri.ui.theme.*
import com.sahyadrisiri.viewmodel.AppTab
import com.sahyadrisiri.viewmodel.AuthState
import com.sahyadrisiri.viewmodel.AuthViewModel
import com.sahyadrisiri.viewmodel.MainViewModel
import io.github.jan.supabase.auth.auth
import androidx.work.*
import com.sahyadrisiri.service.SyncWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            getCurrentLocation()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(this, "Notification permission denied. You won't receive water quality alerts.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize notification channels for offline syncing and alerts
        com.sahyadrisiri.service.NotificationHelper.createChannels(this)
        
        // Schedule periodic background sync (Beast-tier delta sync)
        setupPeriodicSync()

        setContent {
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            
            SahyadriSiriTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        authState is AuthState.Success -> {
                            MainContent(mainViewModel, authViewModel) { getCurrentLocation() }
                        }
                        authState is AuthState.Loading -> {
                            // Show a loading indicator while restoring session from storage
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Blue007AFF)
                            }
                        }
                        else -> {
                            val state = authState
                            val error = if (state is AuthState.Error) state.message else null
                            AuthScreen(
                                onSignIn = { e, p -> authViewModel.signIn(e, p) },
                                onSignUp = { n, e, p -> authViewModel.signUp(n, e, p) },
                                onGoogleAuthSuccess = { t -> authViewModel.signInWithGoogle(t) },
                                isLoading = false,
                                globalError = error,
                                onClearError = { authViewModel.resetState() }
                            )
                        }
                    }
                }
            }
        }

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SahyadriSiriDeltaSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                location?.let {
                    mainViewModel.onLocationReceived(it)
                }
            }
    }
}

@Composable
fun MainContent(
    viewModel: MainViewModel, 
    authViewModel: AuthViewModel,
    onRequestLocation: () -> Unit
) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val selectedReport by viewModel.selectedReport.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsStateWithLifecycle()
    val placeResults by viewModel.placeResults.collectAsStateWithLifecycle()
    val searchedLocation by viewModel.searchedLocation.collectAsStateWithLifecycle()
    val mapType by viewModel.mapType.collectAsStateWithLifecycle()
    val healthScore by viewModel.healthScore.collectAsStateWithLifecycle()
    val isReporting by viewModel.isReporting.collectAsStateWithLifecycle()
    val isSubmittingReport by viewModel.isSubmittingReport.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    val editingReport by viewModel.editingReport.collectAsStateWithLifecycle()
    val isUpdatingReport by viewModel.isUpdatingReport.collectAsStateWithLifecycle()
    val isLocatingForReport by viewModel.isLocatingForReport.collectAsStateWithLifecycle()
    val showHealthScore by viewModel.showHealthScore.collectAsStateWithLifecycle()
    val showFullAddReport by viewModel.showFullAddReport.collectAsStateWithLifecycle()
    
    val showProfile by viewModel.showProfile.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val memberSince by viewModel.memberSince.collectAsStateWithLifecycle()
    val totalReports by viewModel.totalReports.collectAsStateWithLifecycle()
    val cleanReports by viewModel.cleanReports.collectAsStateWithLifecycle()
    val warningReports by viewModel.warningReports.collectAsStateWithLifecycle()
    val pollutedReports by viewModel.pollutedReports.collectAsStateWithLifecycle()
    
    var showChatBot by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Initialize Realtime when MainContent is loaded (post-auth)
    LaunchedEffect(Unit) {
        viewModel.setupRealtimeListener()
        viewModel.refreshProfile()
    }

    Box(Modifier.fillMaxSize()) {
        // Layer 0: Map (Persistent background — isolated recomposition scope)
        MapLayer(viewModel = viewModel, keyboardController = keyboardController)

        // Layer 1: Solid background for non-map tabs
        if (activeTab != AppTab.MAP) {
            Box(Modifier.fillMaxSize().background(Background))
        }

        // Layer 2: UI Content
        Column(Modifier.fillMaxSize()) {
            if (activeTab == AppTab.MAP) {
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 10.dp)) {
                    SearchBar(
                        query = searchQuery,
                        userName = userName,
                        onAvatarClick = { viewModel.showProfile.value = true },
                        onQueryChange = { viewModel.searchQuery.value = it },
                        onClear = { viewModel.searchQuery.value = "" },
                        onSearch = { 
                            viewModel.performSearch()
                            keyboardController?.hide()
                        }
                    )
                }
                
                if (placeResults.isNotEmpty() || isSearchingPlaces) {
                    SearchSuggestions(
                        results = placeResults,
                        isSearching = isSearchingPlaces,
                        onResultClick = { 
                            viewModel.selectPlace(it)
                            keyboardController?.hide()
                        }
                    )
                }
            } else {
                Spacer(Modifier.statusBarsPadding())
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (activeTab) {
                    AppTab.ALERTS -> {
                        val sortedReports by viewModel.sortedReports.collectAsStateWithLifecycle()
                        AlertsScreen(
                            sortedReports = sortedReports,
                            onReportClick = { viewModel.selectedReport.value = it },
                            onViewOnMap = { 
                                viewModel.setTab(AppTab.MAP)
                                viewModel.goToLocationOnMap(it)
                            }
                        )
                    }
                    AppTab.WIKI -> WikiScreen()
                    else -> {}
                }
            }
        }

        // Layer 3: Overlays - Buttons Column on the Right
        Box(Modifier.fillMaxSize()) {
            // Health Score Card - Fixed position and alignment
            if (activeTab == AppTab.MAP) {
                AnimatedVisibility(
                    visible = showHealthScore,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                    modifier = Modifier
                        .padding(top = 110.dp, end = 20.dp) // Proper spacing from search bar
                        .align(Alignment.TopEnd)
                        .width(185.dp) // Explicit width to prevent clipping
                ) {
                    HealthScoreGlassCard(score = healthScore)
                }
            }

            // Right column of action buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 112.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Map controls group
                AnimatedVisibility(
                    visible = activeTab == AppTab.MAP,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(bottom = 14.dp)
                    ) {
                        // Map Type toggle
                        GlassFAB(onClick = { viewModel.toggleMapType() }, size = 56.dp) {
                            val isSatellite = mapType == "satellite"
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Toggle Map Type",
                                tint = if (isSatellite) Blue007AFF else Neutral800, 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Recenter Button - FIXED: Force location update and map move
                        GlassFAB(
                            onClick = { 
                                onRequestLocation()
                                viewModel.requestRecenter() 
                            }, 
                            size = 56.dp
                        ) {
                            Icon(Icons.Rounded.MyLocation, null, tint = Blue007AFF, modifier = Modifier.size(24.dp))
                        }

                        // Add Report with ultra-smooth width transition
                        AddReportSmoothFAB(
                            isExpanded = showFullAddReport,
                            onClick = { viewModel.isReporting.value = true }
                        )
                    }
                }

                // AI Assistant Bot - Persistent across all tabs
                GlassFAB(onClick = { showChatBot = true }, size = 56.dp) {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = "AI Assistant",
                        tint = Blue007AFF,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Bottom Navigation Bar
        Box(Modifier.align(Alignment.BottomCenter)) {
            GlassNavBar {
                NavItem(AppTab.MAP, activeTab == AppTab.MAP, Icons.Default.Map, "Map") { viewModel.setTab(AppTab.MAP) }
                NavItem(AppTab.ALERTS, activeTab == AppTab.ALERTS, Icons.Default.Notifications, "Alerts") { viewModel.setTab(AppTab.ALERTS) }
                NavItem(AppTab.WIKI, activeTab == AppTab.WIKI, Icons.Default.MenuBook, "Wiki") { viewModel.setTab(AppTab.WIKI) }
            }
        }

        if (showChatBot) {
            ChatBotSheet(onClose = { showChatBot = false })
        }

        if (isReporting) {
            val clarity by viewModel.formClarity.collectAsStateWithLifecycle()
            val flow by viewModel.formFlow.collectAsStateWithLifecycle()
            val smell by viewModel.formSmell.collectAsStateWithLifecycle()
            val isAnon by viewModel.formIsAnon.collectAsStateWithLifecycle()
            val photo by viewModel.formPhoto.collectAsStateWithLifecycle()
            val description by viewModel.formDescription.collectAsStateWithLifecycle()

            AddReportSheet(
                clarity = clarity,
                flow = flow,
                smell = smell,
                isAnon = isAnon,
                photo = photo,
                description = description,
                searchedLocation = searchedLocation,
                isSubmitting = isSubmittingReport,
                isLocatingLocation = isLocatingForReport,
                onClarityChange = { viewModel.formClarity.value = it },
                onFlowChange = { viewModel.formFlow.value = it },
                onSmellChange = { viewModel.formSmell.value = it },
                onAnonChange = { viewModel.formIsAnon.value = it },
                onPhotoChange = { viewModel.formPhoto.value = it },
                onDescriptionChange = { viewModel.formDescription.value = it },
                onUseCurrentLocation = {
                    onRequestLocation()
                    viewModel.useCurrentLocation()
                },
                onSubmit = { 
                    viewModel.submitReport(
                        onSuccess = { Toast.makeText(context, "Observation submitted successfully", Toast.LENGTH_SHORT).show() },
                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    )
                },
                onDismiss = { viewModel.isReporting.value = false }
            )
        }

        selectedReport?.let { report ->
            // Check if the current user owns this report
            val currentUserId = try {
                com.sahyadrisiri.data.api.SupabaseClient.client.auth.currentUserOrNull()?.id
            } catch (_: Exception) { null }
            val isOwner = currentUserId != null && report.userId == currentUserId

            ReportDetailSheet(
                report = report,
                onClose = { viewModel.selectedReport.value = null },
                onDelete = { 
                    viewModel.deleteReport(it) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onViewOnMap = { viewModel.goToLocationOnMap(report) },
                isOwner = isOwner,
                onEdit = { viewModel.startEditReport(it) }
            )
        }

        // ── Edit Report Sheet (reuses AddReportSheet form) ──────────────
        if (isEditing && editingReport != null) {
            val clarity by viewModel.formClarity.collectAsStateWithLifecycle()
            val flow by viewModel.formFlow.collectAsStateWithLifecycle()
            val smell by viewModel.formSmell.collectAsStateWithLifecycle()
            val isAnon by viewModel.formIsAnon.collectAsStateWithLifecycle()
            val photo by viewModel.formPhoto.collectAsStateWithLifecycle()
            val description by viewModel.formDescription.collectAsStateWithLifecycle()

            AddReportSheet(
                clarity = clarity,
                flow = flow,
                smell = smell,
                isAnon = isAnon,
                photo = photo,
                description = description,
                searchedLocation = null,
                isSubmitting = isUpdatingReport,
                onClarityChange = { viewModel.formClarity.value = it },
                onFlowChange = { viewModel.formFlow.value = it },
                onSmellChange = { viewModel.formSmell.value = it },
                onAnonChange = { viewModel.formIsAnon.value = it },
                onPhotoChange = { viewModel.formPhoto.value = it },
                onDescriptionChange = { viewModel.formDescription.value = it },
                onSubmit = {
                    viewModel.updateReport(
                        onSuccess = { Toast.makeText(context, "Report updated successfully", Toast.LENGTH_SHORT).show() },
                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                    )
                },
                onDismiss = {
                    viewModel.isEditing.value = false
                    viewModel.editingReport.value = null
                }
            )
        }

        if (showProfile) {
            com.sahyadrisiri.ui.components.ProfileSheet(
                fullName = userName,
                email = userEmail,
                memberSince = memberSince,
                totalReports = totalReports,
                cleanReports = cleanReports,
                warningReports = warningReports,
                pollutedReports = pollutedReports,
                onDismiss = { viewModel.showProfile.value = false },
                onSignOut = { 
                    viewModel.showProfile.value = false
                    authViewModel.signOut()
                }
            )
        }
    }
}

/**
 * Isolated recomposition scope for the map.
 * Only reads the 7 states the map actually needs, so unrelated state changes
 * (profile, search query, alerts, etc.) won't trigger map update/invalidate.
 */
@Composable
private fun MapLayer(
    viewModel: MainViewModel,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val mapCenter by viewModel.mapCenter.collectAsStateWithLifecycle()
    val navigatingTo by viewModel.navigatingTo.collectAsStateWithLifecycle()
    val searchedLocation by viewModel.searchedLocation.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    val mapType by viewModel.mapType.collectAsStateWithLifecycle()

    OsmdroidMapView(
        reports = reports,
        userLocation = userLocation,
        mapCenter = mapCenter,
        navigatingTo = navigatingTo,
        searchedLocation = searchedLocation,
        routePoints = routePoints.map { MapLibreLatLng(it.lat, it.lng) },
        mapType = mapType,
        onMapClick = { point ->
            viewModel.searchedLocation.value = null
            keyboardController?.hide()
        },
        onMarkerClick = { viewModel.selectedReport.value = it }
    )
}

/**
 * Enhanced FAB for "Add Report" with refined width transition for a high-end feel.
 */
@Composable
private fun AddReportSmoothFAB(isExpanded: Boolean, onClick: () -> Unit) {
    val fabSize = 56.dp
    val width by animateDpAsState(
        targetValue = if (isExpanded) 154.dp else fabSize,
        animationSpec = tween(durationMillis = 450),
        label = "fab_expand"
    )

    Box(
        modifier = Modifier
            .height(fabSize)
            .width(width)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.85f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White, Glass.borderHair)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateContentSize(animationSpec = tween(450))
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = Blue007AFF,
                modifier = Modifier.size(28.dp).padding(start = if (isExpanded) 12.dp else 0.dp)
            )
            
            if (isExpanded) {
                Text(
                    "Add Report",
                    color = Blue007AFF,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String, 
    userName: String,
    onAvatarClick: () -> Unit,
    onQueryChange: (String) -> Unit, 
    onClear: () -> Unit, 
    onSearch: () -> Unit
) {
    GlassPanel(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(50.dp), cornerRadius = 25.dp) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Neutral400)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                textStyle = TextStyle(fontSize = 16.sp, color = Neutral800),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search location...", color = Neutral400, fontSize = 16.sp)
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, null, tint = Neutral400) }
            }
            SearchBarAvatar(
                name = userName,
                onClick = onAvatarClick
            )
        }
    }
}

@Composable
private fun SearchBarAvatar(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    
    // The 4 app colors used in reporting (Blue, Red, Orange, Green)
    val ringColors = listOf(
        Color(0xFF007AFF), // Blue
        Color(0xFFFF3B30), // Red
        Color(0xFFFF9500), // Orange
        Color(0xFF34C759), // Green
        Color(0xFF007AFF)  // Close the loop
    )

    Box(
        modifier.size(38.dp).clip(CircleShape)
            .border(2.dp, Brush.sweepGradient(ringColors), CircleShape)
            .padding(3.dp) // inner padding for the ring gap
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF007AFF))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SearchSuggestions(results: List<PlaceResult>, isSearching: Boolean, onResultClick: (PlaceResult) -> Unit) {
    GlassPanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), cornerRadius = 20.dp) {
        Column {
            results.forEach { result ->
                Column(Modifier.fillMaxWidth().clickable { onResultClick(result) }.padding(16.dp)) {
                    Text(result.displayName, fontSize = 15.sp, color = Neutral800, maxLines = 1)
                    HorizontalDivider(color = Color.Black.copy(0.04f))
                }
            }
            if (isSearching) Text("Searching places...", fontSize = 13.sp, color = Neutral400, modifier = Modifier.padding(18.dp).fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HealthScoreGlassCard(score: Int) {
    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("District Health Score", fontSize = 10.sp, color = Neutral500, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$score", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, modifier = Modifier.alignByBaseline())
                    Text(" / 100", fontSize = 12.sp, color = Neutral400, modifier = Modifier.alignByBaseline())
                }
            }
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxSize(), color = Green34C759, trackColor = Neutral200, strokeWidth = 3.dp)
                Text("$score%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Green34C759)
            }
        }
    }
}

@Composable
private fun NavItem(tab: AppTab, isSelected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (isSelected) Blue007AFF else Neutral400, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 10.sp, color = if (isSelected) Blue007AFF else Neutral400, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
    }
}
