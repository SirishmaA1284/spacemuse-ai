package com.spacemuse.ai.ui.screens.arscan

import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.spacemuse.ai.camera.ArAvailability
import com.spacemuse.ai.camera.ArAvailabilityResult
import com.spacemuse.ai.camera.ArCameraPreview
import com.spacemuse.ai.camera.ArTrackingStatus
import com.spacemuse.ai.core.model.CreateRoomResponse
import com.spacemuse.ai.core.model.RoomCreateRequest
import com.spacemuse.ai.core.model.RoomMeasurementModel
import com.spacemuse.ai.core.network.ApiClient
import kotlinx.coroutines.launch

// A single point-to-point measurement the user has confirmed, plus the raw
// world-space poses it came from (needed so ArCameraPreview can keep
// rendering its line/markers every frame — see getConfirmedMeasurements).
private data class UiMeasurement(val label: String, val distanceCm: Float, val pointA: Pose, val pointB: Pose)

// A completed pointA/pointB pair waiting for the user to name it before it
// becomes a UiMeasurement (or gets discarded).
private data class PendingLabel(val distanceCm: Float, val pointA: Pose, val pointB: Pose)

private sealed interface CreateRoomState {
    data object Idle : CreateRoomState
    data object CapturingPhoto : CreateRoomState // waiting on the GL thread's onPhotoCaptured
    data object Uploading : CreateRoomState
    data class Success(val response: CreateRoomResponse) : CreateRoomState
    data class Error(val message: String) : CreateRoomState
}

// Milestones 1-3 of Phase 7 (Spatial/AR — see
// docs/architecture/spatial-architecture.md): ARCore passthrough +
// tracking (1), plane visualization (2), and now tap-to-measure plus
// persistence (3) — tap two points on a tracked plane to record a
// real-world distance (MEASURED, per the measurement trust order in
// spatial-architecture.md), then "Finish Scan" captures a photo and POSTs
// everything to the backend's /rooms endpoint alongside Gemini's own
// ESTIMATED object list. Left as a second entry point alongside the
// existing single-photo CameraScreen/"Scan My Space" flow, not a
// replacement for it.
@Composable
fun ArScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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

    var measurements by remember { mutableStateOf<List<UiMeasurement>>(emptyList()) }
    var hasPendingPoint by remember { mutableStateOf(false) }
    var pendingLabel by remember { mutableStateOf<PendingLabel?>(null) }
    var captureEpoch by remember { mutableStateOf(0) }
    var createState by remember { mutableStateOf<CreateRoomState>(CreateRoomState.Idle) }

    fun uploadRoom(jpegBytes: ByteArray) {
        createState = CreateRoomState.Uploading
        val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val measuredMeasurements = measurements.map {
            RoomMeasurementModel(label = it.label, valueCm = it.distanceCm, measurementSource = "MEASURED")
        }
        coroutineScope.launch {
            try {
                val response = ApiClient.api.createRoom(
                    RoomCreateRequest(imageBase64 = base64Image, measuredMeasurements = measuredMeasurements)
                )
                createState = CreateRoomState.Success(response)
            } catch (error: Exception) {
                createState = CreateRoomState.Error(
                    error.message ?: "Could not reach the SpaceMuse AI backend."
                )
            }
        }
    }

    fun finishScan() {
        if (measurements.isEmpty()) return
        createState = CreateRoomState.CapturingPhoto
        captureEpoch += 1
    }

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
                    onSessionError = { sessionError = it },
                    onPendingPointChanged = { hasPendingPoint = it },
                    onMeasurementCompleted = { pointA, pointB, distanceCm ->
                        pendingLabel = PendingLabel(distanceCm, pointA, pointB)
                    },
                    getConfirmedMeasurements = { measurements.map { it.pointA to it.pointB } },
                    captureRequestEpoch = captureEpoch,
                    onPhotoCaptured = ::uploadRoom,
                    onCaptureError = { message -> createState = CreateRoomState.Error(message) }
                )
                // Stacked in a Column (rather than two independently
                // bottom-aligned composables with a guessed padding gap
                // between them) so the badge and panel never overlap
                // regardless of the panel's actual height — that guess
                // previously assumed the panel was hidden until the first
                // measurement, but it's always visible while Idle (title +
                // disabled Finish Scan button even at zero measurements).
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TrackingStatusBadge(
                        modifier = Modifier.padding(bottom = 12.dp),
                        status = trackingStatus,
                        planeCount = planeCount,
                        hasPendingPoint = hasPendingPoint
                    )

                    if (createState is CreateRoomState.Idle) {
                        MeasurementPanel(
                            measurements = measurements,
                            onRemove = { target -> measurements = measurements.filter { it !== target } },
                            onFinishScan = ::finishScan
                        )
                    }
                }
            }
        }

        ArScanTopBar(onBack = onBack)

        pendingLabel?.let { pending ->
            LabelPromptDialog(
                distanceCm = pending.distanceCm,
                suggestedLabel = "measurement_${measurements.size + 1}",
                onConfirm = { label ->
                    measurements = measurements + UiMeasurement(label, pending.distanceCm, pending.pointA, pending.pointB)
                    pendingLabel = null
                },
                onDiscard = { pendingLabel = null }
            )
        }

        when (val state = createState) {
            is CreateRoomState.CapturingPhoto, is CreateRoomState.Uploading ->
                CreatingRoomOverlay(
                    message = if (state is CreateRoomState.CapturingPhoto) "Capturing photo…" else "Saving your room…",
                    onCancel = { createState = CreateRoomState.Idle }
                )

            is CreateRoomState.Success -> RoomCreatedOverlay(response = state.response, onDone = onBack)

            is CreateRoomState.Error -> RoomCreateErrorOverlay(
                message = state.message,
                onRetry = ::finishScan,
                onCancel = { createState = CreateRoomState.Idle }
            )

            CreateRoomState.Idle -> Unit
        }
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
private fun TrackingStatusBadge(
    modifier: Modifier = Modifier,
    status: ArTrackingStatus?,
    planeCount: Int,
    hasPendingPoint: Boolean
) {
    val text = if (status?.state == TrackingState.TRACKING) {
        when {
            hasPendingPoint -> "Tap a second point to measure"
            planeCount == 0 -> "Move your phone to find a wall or floor"
            else -> "Tap two points to measure — planes detected: $planeCount"
        }
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
private fun MeasurementPanel(
    modifier: Modifier = Modifier,
    measurements: List<UiMeasurement>,
    onRemove: (UiMeasurement) -> Unit,
    onFinishScan: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        Text(
            text = "Measurements (${measurements.size})",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        if (measurements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(40.dp * minOf(measurements.size, 3))) {
                items(measurements) { measurement ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${measurement.label}: ${"%.0f".format(measurement.distanceCm)} cm",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Remove",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { onRemove(measurement) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onFinishScan,
            enabled = measurements.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finish Scan")
        }
    }
}

@Composable
private fun LabelPromptDialog(
    distanceCm: Float,
    suggestedLabel: String,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var label by remember(suggestedLabel) { mutableStateOf(suggestedLabel) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Name this measurement", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${"%.0f".format(distanceCm)} cm",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDiscard) { Text("Discard") }
                    Button(
                        onClick = { onConfirm(label.ifBlank { suggestedLabel }) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add measurement") }
                }
            }
        }
    }
}

@Composable
private fun CreatingRoomOverlay(message: String, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun RoomCreateErrorOverlay(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Couldn't save this room", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun RoomCreatedOverlay(response: CreateRoomResponse, onDone: () -> Unit) {
    val analysis = response.analysis
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Room saved",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (analysis.source == "demo") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "DEMO DATA",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = analysis.roomType.replace('_', ' '),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = analysis.summary, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Measured",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.height(100.dp)) {
                    items(analysis.measurements.filter { it.measurementSource == "MEASURED" }) { measurement ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = measurement.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${"%.0f".format(measurement.valueCm)} cm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
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
