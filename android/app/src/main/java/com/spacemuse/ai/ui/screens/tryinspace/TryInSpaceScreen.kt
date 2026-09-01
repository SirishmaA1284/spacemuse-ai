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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.spacemuse.ai.camera.CameraPreview
import com.spacemuse.ai.core.model.Product

private sealed interface TryInSpaceStep {
    data object SearchProduct : TryInSpaceStep
    data class CaptureRoomPhoto(val product: Product) : TryInSpaceStep
    data class Compose(val product: Product, val roomPhotoJpeg: ByteArray) : TryInSpaceStep
}

// Milestone 4 of Phase 7 (Spatial/AR — see
// docs/architecture/spatial-architecture.md): product image overlay
// compositing on a static room photo, deliberately built and testable
// independently of the AR scan flow (no anchoring, no live AR scene) —
// search a product, take a photo of your room, then drag/pinch/rotate the
// product's photo on top of it. AR-anchored placement (using the room's
// real-world measurements from Milestone 3) is Milestone 5
// (ArTryOnScreen.kt); persisting the placement plus a dedicated "Buy" flow
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
                onPhotoCaptured = { jpeg -> step = TryInSpaceStep.Compose(current.product, jpeg) }
            )

        is TryInSpaceStep.Compose ->
            ComposeStep(
                product = current.product,
                roomPhotoJpeg = current.roomPhotoJpeg,
                onRetakePhoto = { step = TryInSpaceStep.CaptureRoomPhoto(current.product) },
                onDone = onBack
            )
    }
}

@Composable
private fun CaptureRoomPhotoStep(onBack: () -> Unit, onPhotoCaptured: (ByteArray) -> Unit) {
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
        if (hasCameraPermission) {
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
                                // No error surface for this step yet — worth
                                // adding if real-device testing shows this
                                // path failing silently in practice.
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
        } else {
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
        }

        TryInSpaceTopBar(title = "Photograph your room", onBack = onBack)
    }
}

@Composable
private fun ComposeStep(
    product: Product,
    roomPhotoJpeg: ByteArray,
    onRetakePhoto: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val roomPhoto = remember(roomPhotoJpeg) {
        BitmapFactory.decodeByteArray(roomPhotoJpeg, 0, roomPhotoJpeg.size).asImageBitmap()
    }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = roomPhoto,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // Standard Compose pan/zoom/rotate gesture pattern: pan and
        // rotation accumulate directly, scale multiplies and is clamped so
        // the product photo can't be pinched away to nothing or off-screen
        // huge.
        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp)
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
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
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
