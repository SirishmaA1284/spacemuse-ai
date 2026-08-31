package com.spacemuse.ai.ui.screens.arscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.spacemuse.ai.camera.ArAvailability
import com.spacemuse.ai.camera.ArAvailabilityResult
import com.spacemuse.ai.camera.ArCameraPreview
import com.spacemuse.ai.camera.ArTrackingStatus

// Milestones 1-2 of Phase 7 (Spatial/AR — see docs/architecture/spatial-architecture.md):
// proves ARCore initializes, renders camera passthrough, and visualizes
// detected planes on a real device before measurement capture/persistence
// is built on top of it (Milestone 3). Deliberately does nothing else yet —
// no scanning, no persistence, no product logic. Left as a second entry
// point alongside the existing single-photo CameraScreen/"Scan My Space"
// flow, not a replacement for it.
@Composable
fun ArScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val availability = remember { ArAvailability.check(context) }
    var trackingStatus by remember { mutableStateOf<ArTrackingStatus?>(null) }
    var planeCount by remember { mutableStateOf(0) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasCameraPermission -> ArPermissionRequest(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )

            availability != ArAvailabilityResult.Supported -> ArUnsupportedMessage(availability)

            sessionError != null -> ArSessionErrorMessage(message = sessionError.orEmpty())

            else -> {
                ArCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onTrackingStatusChanged = { trackingStatus = it },
                    onPlaneCountChanged = { planeCount = it },
                    onSessionError = { sessionError = it }
                )
                TrackingStatusBadge(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                    status = trackingStatus,
                    planeCount = planeCount
                )
            }
        }

        ArScanTopBar(onBack = onBack)
    }
}

@Composable
private fun ArScanTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = "←", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun TrackingStatusBadge(modifier: Modifier = Modifier, status: ArTrackingStatus?, planeCount: Int) {
    val text = if (status?.state == TrackingState.TRACKING) {
        "Tracking — planes detected: $planeCount"
    } else {
        trackingStatusText(status)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun trackingStatusText(status: ArTrackingStatus?): String {
    if (status == null) return "Starting AR session…"
    return when (status.state) {
        TrackingState.TRACKING -> "Tracking"
        TrackingState.STOPPED -> "AR session stopped"
        TrackingState.PAUSED -> when (status.failureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Move to a brighter area"
            TrackingFailureReason.EXCESSIVE_MOTION -> "Move your phone more slowly"
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a more detailed surface"
            TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
            TrackingFailureReason.BAD_STATE -> "AR tracking lost — try moving around"
            else -> "Initializing… move your phone slowly"
        }
        else -> "Initializing…"
    }
}

@Composable
private fun ArPermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SpaceMuse AI needs camera access to scan your room.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text("Grant camera permission")
        }
    }
}

@Composable
private fun ArUnsupportedMessage(availability: ArAvailabilityResult) {
    val message = when (availability) {
        ArAvailabilityResult.NeedsInstall, ArAvailabilityResult.NeedsUpdate ->
            "This device needs Google Play Services for AR installed or updated before AR scanning works."
        else ->
            "AR scanning isn't supported on this device — use standard room scan instead."
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ArSessionErrorMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Couldn't start the AR session",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
