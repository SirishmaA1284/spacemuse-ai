package com.spacemuse.ai.ui.screens.tryinspace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.spacemuse.ai.camera.ArAvailability
import com.spacemuse.ai.camera.ArAvailabilityResult
import com.spacemuse.ai.camera.ArPhotoCapture
import com.spacemuse.ai.camera.ArPhotoCaptureResult
import com.spacemuse.ai.camera.ArTrackingStatus
import com.spacemuse.ai.camera.CameraPreview
import com.spacemuse.ai.core.model.Product

private sealed interface TryInSpaceStep {
    data object SearchProduct : TryInSpaceStep
    data class CaptureRoomPhoto(val product: Product) : TryInSpaceStep
    data class Compose(val product: Product, val roomPhotoJpeg: ByteArray, val pixelsPerMeter: Float?) : TryInSpaceStep
}

// Product photo overlays don't carry real-world dimensions from most
// retailers, and even when Product.widthCm is present it's often an
// ESTIMATED value (see roomAnalysis.schema.ts's MeasurementSource) -- this
// is a reasonable furniture-scale fallback, not a claim of accuracy.
private const val DEFAULT_PRODUCT_WIDTH_METERS = 0.6f
private val BASE_OVERLAY_SIZE = 200.dp

// Milestone 4 of Phase 7 (Spatial/AR — see
// docs/architecture/spatial-architecture.md): product image overlay
// compositing on a static room photo — search a product, take a photo of
// your room, then drag/pinch/rotate the product's photo on top of it.
// Made measurement-aware by capturing the room photo through the AR
// camera (ArPhotoCapture) instead of a plain camera: tapping a reference
// point before capturing records a real-world scale (screen pixels per
// metre at that point's depth), used to size the product correctly by
// default — pinch still adjusts on top of that starting point. The tap is
// optional; skipping it falls back to the old fixed-size behavior. Devices
// without ARCore fall back to a plain CameraX capture with no scale
// reference at all (FallbackCameraCapture below), same graceful-degrade
// pattern as ArScanScreen. AR-anchored live placement (Milestone 5) is
// ArTryOnScreen.kt; persisting the placement plus a dedicated "Buy" flow
// is Milestone 6 — this screen's "View / Buy" link just reuses the
// existing external-retailer-link pattern already used by CameraScreen's
// product results, nothing new.
@Composable
fun TryInSpaceScreen(onBack: () -> Unit) {
    var step by remember { mutableStateOf<TryInSpaceStep>(TryInSpaceStep.SearchProduct) }

    when (val current = step) {
        is TryInSpaceStep.SearchProduct ->
            ProductSearchStep(onBack = onBack, onProductSelected = { step = TryInSpaceStep.CaptureRoomPhoto(it) })

        is TryInSpaceStep.CaptureRoomPhoto ->
            CaptureRoomPhotoStep(
                onBack = { step = TryInSpaceStep.SearchProduct },
                onPhotoCaptured = { jpeg, pixelsPerMeter ->
                    step = TryInSpaceStep.Compose(current.product, jpeg, pixelsPerMeter)
                }
            )

        is TryInSpaceStep.Compose ->
            ComposeStep(
                product = current.product,
                roomPhotoJpeg = current.roomPhotoJpeg,
                pixelsPerMeter = current.pixelsPerMeter,
                onRetakePhoto = { step = TryInSpaceStep.CaptureRoomPhoto(current.product) },
                onDone = onBack
            )
    }
}

@Composable
private fun CaptureRoomPhotoStep(onBack: () -> Unit, onPhotoCaptured: (ByteArray, Float?) -> Unit) {
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

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SpaceMuse AI needs camera access to photograph your room.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            }
            TryInSpaceTopBar(title = "Photograph your room", onBack = onBack)
        }
    } else if (availability == ArAvailabilityResult.Supported) {
        ArAssistedCaptureStep(onBack = onBack, onPhotoCaptured = onPhotoCaptured)
    } else {
        FallbackCameraCapture(onBack = onBack, onPhotoCaptured = { jpeg -> onPhotoCaptured(jpeg, null) })
    }
}

@Composable
private fun ArAssistedCaptureStep(onBack: () -> Unit, onPhotoCaptured: (ByteArray, Float?) -> Unit) {
    var trackingStatus by remember { mutableStateOf<ArTrackingStatus?>(null) }
    var planeCount by remember { mutableStateOf(0) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var hasReferencePoint by remember { mutableStateOf(false) }
    var captureEpoch by remember { mutableStateOf(0) }
    var captureErrorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (sessionError != null) {
            SessionErrorMessage(message = sessionError.orEmpty())
        } else {
            ArPhotoCapture(
                modifier = Modifier.fillMaxSize(),
                onTrackingStatusChanged = { trackingStatus = it },
                onPlaneCountChanged = { planeCount = it },
                onSessionError = { sessionError = it },
                onReferencePointChanged = { hasReferencePoint = it },
                captureRequestEpoch = captureEpoch,
                onCaptured = { result: ArPhotoCaptureResult -> onPhotoCaptured(result.jpeg, result.pixelsPerMeter) },
                onCaptureError = { captureErrorMessage = it }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CaptureStatusBadge(
                    modifier = Modifier.padding(bottom = 12.dp),
                    status = trackingStatus,
                    planeCount = planeCount,
                    hasReferencePoint = hasReferencePoint
                )
                captureErrorMessage?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { captureEpoch += 1 },
                    modifier = Modifier
                        .padding(bottom = 40.dp)
                        .height(56.dp)
                        .fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Capture Room Photo", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        TryInSpaceTopBar(title = "Photograph your room", onBack = onBack)
    }
}

@Composable
private fun CaptureStatusBadge(
    modifier: Modifier = Modifier,
    status: ArTrackingStatus?,
    planeCount: Int,
    hasReferencePoint: Boolean
) {
    val text = if (status?.state == TrackingState.TRACKING) {
        when {
            hasReferencePoint -> "Reference point set — tap again to move it, or Capture when ready"
            planeCount == 0 -> "Move your phone to find a wall or floor"
            else -> "Tap where the product will go for accurate sizing (optional) — planes detected: $planeCount"
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

// Devices without ARCore support fall back here -- no scale reference,
// same behavior this screen had before it became measurement-aware.
@Composable
private fun FallbackCameraCapture(onBack: () -> Unit, onPhotoCaptured: (ByteArray) -> Unit) {
    val context = LocalContext.current

    // Same resolution/quality cap as CameraScreen.kt's scan capture — see
    // that file's comment: uncapped capture on a real device was several
    // MB and repeatedly caused upload/processing timeouts.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
            .setJpegQuality(90)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())
        Button(
            onClick = {
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()
                            onPhotoCaptured(bytes)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            // No error surface for this path yet — worth
                            // adding if real-device testing shows this
                            // failing silently in practice.
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .height(56.dp)
                .fillMaxWidth(0.7f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Capture Room Photo", style = MaterialTheme.typography.titleMedium)
        }
        TryInSpaceTopBar(title = "Photograph your room", onBack = onBack)
    }
}

@Composable
private fun ComposeStep(
    product: Product,
    roomPhotoJpeg: ByteArray,
    pixelsPerMeter: Float?,
    onRetakePhoto: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val roomPhoto = remember(roomPhotoJpeg) {
        BitmapFactory.decodeByteArray(roomPhotoJpeg, 0, roomPhotoJpeg.size).asImageBitmap()
    }

    // Physically-computed starting scale when a reference point was set at
    // capture time (pixelsPerMeter * the product's real-world width, as a
    // multiple of the overlay's base on-screen size); otherwise 1x, same
    // fixed-size starting point this screen always had. Either way, pinch
    // still adjusts freely on top -- the physical estimate is a starting
    // point, not a hard constraint, since product dimension data is
    // frequently missing or approximate.
    val baseSizePx = with(LocalDensity.current) { BASE_OVERLAY_SIZE.toPx() }
    val initialScale = remember(pixelsPerMeter) {
        pixelsPerMeter?.let { ppm ->
            val widthMeters = product.widthCm?.let { it / 100f } ?: DEFAULT_PRODUCT_WIDTH_METERS
            (ppm * widthMeters) / baseSizePx
        } ?: 1f
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember(initialScale) { mutableStateOf(initialScale) }
    var rotation by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = roomPhoto,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // Standard Compose pan/zoom/rotate gesture pattern: pan and
        // rotation accumulate directly, scale multiplies and is clamped
        // relative to the starting scale (not a fixed absolute range) so a
        // physically-large or -small starting size still has sensible
        // pinch headroom in both directions.
        ProductOverlayImage(
            product = product,
            modifier = Modifier
                .align(Alignment.Center)
                .size(BASE_OVERLAY_SIZE)
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        offset += pan
                        scale = (scale * zoom).coerceIn(initialScale * 0.2f, initialScale * 5f)
                        rotation += rotationChange
                    }
                }
        )

        TryInSpaceTopBar(title = "Try it out — drag, pinch, rotate", onBack = onDone)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(20.dp)
        ) {
            Text(text = product.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (pixelsPerMeter == null) {
                Text(
                    text = "No reference point was set — sized manually, not to real-world scale.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRetakePhoto, modifier = Modifier.weight(1f)) {
                    Text("Retake Photo")
                }
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.productUrl)))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("View / Buy")
                }
            }
        }
    }
}
