package com.example.locationinformation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.locationinformation.ui.theme.LocationinformationTheme
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch


data class CustomMarkerData(
    val latLng: LatLng,
    val address: String
)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocationinformationTheme {
                MapScreen()
            }
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current

    // used for accessing location services
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // state vars for user location, device address, selected address (at marker), custom markers, and camera pos
    // separate states for device and custom marker addresses to distinguish them + was working on making location work for emulator device
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var deviceAddress by remember { mutableStateOf("Address not available") }
    var selectedAddress by remember { mutableStateOf("Address not available") }
    var customMarkers by remember { mutableStateOf(listOf<CustomMarkerData>()) }
    val cameraPositionState = rememberCameraPositionState()

    // requesting location permission state
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasLocationPermission = isGranted }
    )
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            hasLocationPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // gets device last known location & sets camera pos
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null).addOnSuccessListener { location ->
                if (location != null) {
                    val loc = LatLng(location.latitude, location.longitude)
                    userLocation = loc
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(loc, 15f)

                    // provides readable address from lat/long
                    val geocoder = Geocoder(context, Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        deviceAddress = if (!addresses.isNullOrEmpty()) {
                            addresses[0].getAddressLine(0) ?: "No address found"
                        } else {
                            "No address found"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        deviceAddress = "Error retrieving address"
                    }
                    // initially the address is set to the devices address
                    selectedAddress = deviceAddress
                }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // UI for map
    Column(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            cameraPositionState = cameraPositionState,
            // custom marker/pins dropped when map is tapped
            onMapClick = { latLng ->
                coroutineScope.launch {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    val markerAddress = if (!addresses.isNullOrEmpty()) {
                        addresses[0].getAddressLine(0)
                    } else {
                        "No address found"
                    }
                    customMarkers = customMarkers + CustomMarkerData(latLng, markerAddress ?: "No address found")
                }
            }
        ) {
            // marker for device's location
            userLocation?.let { location ->
                Marker(
                    state = MarkerState(position = location),
                    title = "Your Location",
                    snippet = deviceAddress,
                    onClick = {
                        selectedAddress = deviceAddress
                        false
                    }
                )
            }
            // custom location markers
            customMarkers.forEach { markerData ->
                Marker(
                    state = MarkerState(position = markerData.latLng),
                    title = "Custom Marker",
                    snippet = markerData.address,
                    onClick = {
                        selectedAddress = markerData.address
                        false
                    }
                )
            }
        }
        // text at the bottom below the map
        Text(
            text = "Address: $selectedAddress",
            modifier = Modifier.padding(16.dp)
        )
    }
}