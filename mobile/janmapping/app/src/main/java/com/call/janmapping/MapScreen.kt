package com.call.janmapping

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(jans: List<String>, productNames: List<String> = emptyList(), vm: MapViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var hasLocationPermission by remember { mutableStateOf(false) }

    LaunchedEffect(jans, productNames) {
        Log.d("MapScreen", "LaunchedEffect(jans, productNames): jans=${jans.joinToString()}, products=${productNames.joinToString()}")
        val safeJans = if (jans.isEmpty()) listOf("4901234567890") else jans
        vm.init(safeJans, productNames = productNames)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MapScreen", "permission result = $permissions")
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        hasLocationPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        launcher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ))
    }

    val client = remember { LocationServices.getFusedLocationProviderClient(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    client.lastLocation.addOnSuccessListener { loc ->
                        Log.d("MapScreen", "lastLocation=$loc")
                        loc?.let { vm.setCenter(LatLng(it.latitude, it.longitude)) }
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(s.center, s.radiusKm, s.janList) {
        Log.d("MapScreen", "LaunchedEffect(fetch) center=${s.center} radius=${s.radiusKm} janSize=${s.janList.size}")
        vm.fetchStores()
    }

    LaunchedEffect(s.stores) {
        if (s.stores.isNotEmpty() && !s.monitoringEnabled) {
            vm.startLocationMonitoring(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.stopLocationMonitoring(context)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("半径: ${s.radiusKm.toInt()}km", modifier = Modifier.weight(1f))
            Slider(
                value = s.radiusKm, valueRange = 1f..10f, steps = 8,
                onValueChange = { vm.setRadius(it) }, modifier = Modifier.weight(3f)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (s.monitoringEnabled) "📍 店舗接近通知: ON" else "📍 店舗接近通知: OFF",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    if (s.monitoringEnabled) {
                        vm.stopLocationMonitoring(context)
                    } else {
                        vm.startLocationMonitoring(context)
                    }
                },
                enabled = s.stores.isNotEmpty()
            ) {
                Text(if (s.monitoringEnabled) "停止" else "開始")
            }
        }

        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(s.center, 14f)
        }
        LaunchedEffect(s.center) {
            cameraPositionState.animate(CameraUpdateFactory.newLatLng(s.center))
        }

        Box(Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                onMapLongClick = { vm.setCenter(it) }
            ) {
                Circle(center = s.center, radius = (s.radiusKm * 1000).toDouble(), strokeWidth = 2f)
                s.stores.forEach { st ->
                    val hue = when (st.grade) { "◎" -> BitmapDescriptorFactory.HUE_GREEN; "○" -> BitmapDescriptorFactory.HUE_YELLOW; else -> BitmapDescriptorFactory.HUE_RED }
                    Marker(
                        state = MarkerState(LatLng(st.lat, st.lng)),
                        title = st.name,
                        snippet = "${st.grade} 直近: ${st.last_sold_at ?: "-"}",
                        onClick = { vm.selectStore(st.store_id); false },
                        icon = BitmapDescriptorFactory.defaultMarker(hue)
                    )
                }
            }

            if (s.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.error?.let { Text(it, color = Color.Red, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)) }

            Column(Modifier.align(Alignment.BottomCenter).background(Color.White.copy(alpha = 0.95f)).fillMaxWidth()) {
                Text("店舗 ${s.stores.size} 件", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    items(s.stores) { st ->
                        Card(Modifier.padding(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(st.name, fontWeight = FontWeight.SemiBold)
                                Text("評価: ${st.grade} / 週${st.weekly_count}件")
                                Text("直近: ${st.last_sold_at ?: "-"}")
                                Button(onClick = { activity?.let { openMaps(it, st.lat, st.lng) } }) { Text("ナビ") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openMaps(activity: Activity, lat: Double, lng: Double) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(店舗)")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
    activity.startActivity(intent)
}
