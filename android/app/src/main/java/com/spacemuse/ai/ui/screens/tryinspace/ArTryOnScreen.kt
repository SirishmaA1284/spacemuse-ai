package com.spacemuse.ai.ui.screens.tryinspace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.spacemuse.ai.camera.AnchorProjection
import com.spacemuse.ai.camera.ArAvailability
import com.spacemuse.ai.camera.ArAvailabilityResult
import com.spacemuse.ai.camera.ArTrackingStatus
import com.spacemuse.ai.camera.ArTryOnPreview
import com.spacemuse.ai.core.model.Product

private sealed interface ArTryOnStep {
    data object SearchProduct : ArTryOnStep
    data class Placing(val product: Product) : ArTryOnStep
}

// Product photo overlays don't carry real-world dimensions from most
// retailers, and even when Product.widthCm is present it's often an
// ESTIMATED value (see roomAnalysis.schema.ts's MeasurementSource) -- this
// is a reasonable furniture-scale fallback, not a claim of accuracy.
private const val DEFAULT_PRODUCT_WIDTH_METERS = 0.6f
private val BASE_OVERLAY_SIZE = 200.dp

// Milestone 5 of Phase 7 (Spatial/AR -- see
// docs/architecture/spatial-architecture.md): anchor the Milestone 4
// product-overlay idea into the live AR scene using a real ARCore Anchor
// (ArTryOnPreview), sized using the anchor's real-world screen-space scale
// (ADR-006's Level 4) rather than a fixed on-screen size. Pinch/twist
// still let the user nudge the size/rotation manually on top of that
// physical estimate, since product dimension data is frequently missing or
// approximate. Persisting the placement + a dedicated buy flow is
// Milestone 6 -- "View / Buy" here just reuses the existing
// external-retailer-link pattern, same as Milestone 4.
@Composable
fun ArTryOnScreen(onBack: () -> Unit) {
    var step by remember { mutableStateOf<ArTryOnStep>(ArTryOnStep.SearchProduct) }

    when (val current = step) {
        is ArTryOnStep.SearchProduct ->
            ProductSearchStep(onBack = onBack, onProductSelected = { step = ArTryOnStep.Placing(it) })

        is ArTryOnStep.Placing ->
            ArPlacingStep(product = current.product, onBack = { step = ArTryOnStep.SearchProduct })
    }
}

@Composable
private fun ArPlacingStep(product: Product, onBack: () -> Unit) {
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
    var anchorProjection by remember { mutableStateOf<AnchorProjection?>(null) }

    var userScale by remember { mutableStateOf(1f) }
    var userRotation by remember { mutableStateOf(0f) }

    val baseSizePx = with(LocalDensity.current) { BASE_OVERLAY_SIZE.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !hasCameraPermission -> PermissionRequest(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )

            availability != ArAvailabilityResult.Supported -> UnsupportedMessage(availability)

            sessionError != null -> SessionErrorMessage(message = sessionError.orEmpty())

            else -> {
                ArTryOnPreview(
                    modifier = Modifier.fillMaxSize(),
                    onTrackingStatusChanged = { trackingStatus = it },
                    onPlaneCountChanged = { planeCount = it },
                    onSessionError = { sessionError = it },
                    onAnchorProjectionChanged = { anchorProjection = it }
                )

                anchorProjection?.let { projection ->
                    val widthMeters = product.widthCm?.let { it / 100f } ?: DEFAULT_PRODUCT_WIDTH_METERS
                    val physicalScale = (projection.pixelsPerMeter * widthMeters) / baseSizePx

                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(BASE_OVERLAY_SIZE)
                            .graphicsLayer {
                                translationX = projection.screenX - baseSizePx / 2f
                                translationY = projection.screenY - baseSizePx / 2f
                                scaleX = physicalScale * userScale
                                scaleY = physicalScale * userScale
                                rotationZ = userRotation
                            }
                            .pointerInput(Unit) {
                                // Pan is deliberately ignored here -- position
                                // is anchor-driven (tap elsewhere on a plane
                                // to move it), not draggable, so it can't
                                // drift away from the real-world point it's
                                // supposed to represent.
                                detectTransformGestures { _, _, zoom, rotationChange ->
                                    userScale = (userScale * zoom).coerceIn(0.3f, 3f)
                                    userRotation += rotationChange
                                }
                            }
                    )
                }

                StatusBadge(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp),
                    status = trackingStatus,
                    planeCount = planeCount,
                    hasAnchor = anchorProjection != null
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(16.dp)
                ) {
                    Text(product.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.productUrl)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("View / Buy")
                    }
                }
            }
        }

        TryInSpaceTopBar(title = "Place ${product.name}", onBack = onBack)
    }
}

@Composable
private fun StatusBadge(modifier: Modifier = Modifier, status: ArTrackingStatus?, planeCount: Int, hasAnchor: Boolean) {
    val text = if (status?.state == TrackingState.TRACKING) {
        when {
            hasAnchor -> "Pinch to resize, twist to rotate — tap elsewhere to move it"
            planeCount == 0 -> "Move your phone to find a wall or floor"
            else -> "Tap a wall or floor to place it — planes detected: $planeCount"
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
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SpaceMuse AI needs camera access to place products in your room.",
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
private fun UnsupportedMessage(availability: ArAvailabilityResult) {
    val message = when (availability) {
        ArAvailabilityResult.NeedsInstall, ArAvailabilityResult.NeedsUpdate ->
            "This device needs Google Play Services for AR installed or updated before AR placement works."
        else ->
            "AR placement isn't supported on this device — try the standard Try in Space flow instead."
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
private fun SessionErrorMessage(message: String) {
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
