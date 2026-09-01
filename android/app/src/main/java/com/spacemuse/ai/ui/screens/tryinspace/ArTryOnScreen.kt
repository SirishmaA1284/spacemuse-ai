package com.spacemuse.ai.ui.screens.tryinspace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
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

// Milestone 5 of Phase 7 (Spatial/AR -- see
// docs/architecture/spatial-architecture.md): anchor the Milestone 4
// product-overlay idea into the live AR scene using a real ARCore Anchor.
// Rendering itself (position, real-world scale, surface-tilted
// orientation) all happens inside ArTryOnPreview's GL layer now, not here
// -- this screen just supplies the cutout product bitmap and its
// real-world width, and shows the status text / bottom info panel around
// the AR view. (An earlier version rendered a flat Compose overlay
// on top instead, which never tilted/foreshortened with the actual
// surface as the camera moved -- real user feedback led to moving
// rendering into the AR scene itself; see ArTryOnPreview.kt.)
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
    var hasAnchor by remember { mutableStateOf(false) }

    val cutout = rememberProductCutout(product)
    val cutoutBitmap = (cutout as? CutoutResult.Ready)?.bitmap
    val widthMeters = product.widthCm?.let { it / 100f } ?: DEFAULT_PRODUCT_WIDTH_METERS

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
                    getProductBitmap = { cutoutBitmap },
                    getProductWidthMeters = { widthMeters },
                    onTrackingStatusChanged = { trackingStatus = it },
                    onPlaneCountChanged = { planeCount = it },
                    onSessionError = { sessionError = it },
                    onAnchorPlaced = { hasAnchor = it }
                )

                // Stacked in a Column, not two independently
                // bottom-aligned composables with a guessed padding gap --
                // ArScanScreen.kt hit exactly this overlap bug once already
                // (a hardcoded bottom padding assuming fixed content
                // height) and this screen repeated the same shape. A
                // Column sizes itself from the two children's actual
                // heights instead of a guess.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusBadge(
                        modifier = Modifier.padding(bottom = 12.dp),
                        status = trackingStatus,
                        planeCount = planeCount,
                        hasAnchor = hasAnchor,
                        cutoutReady = cutoutBitmap != null
                    )

                    Column(
                        modifier = Modifier
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
        }

        TryInSpaceTopBar(title = "Place ${product.name}", onBack = onBack)
    }
}

@Composable
private fun StatusBadge(
    modifier: Modifier = Modifier,
    status: ArTrackingStatus?,
    planeCount: Int,
    hasAnchor: Boolean,
    cutoutReady: Boolean
) {
    val text = if (status?.state == TrackingState.TRACKING) {
        when {
            !cutoutReady -> "Preparing product photo…"
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
