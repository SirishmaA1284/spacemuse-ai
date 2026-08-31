package com.spacemuse.ai.ui.screens.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.spacemuse.ai.camera.CameraPreview
import com.spacemuse.ai.core.model.Product
import com.spacemuse.ai.core.model.ProductSearchResponse
import com.spacemuse.ai.core.model.RoomAnalysis
import com.spacemuse.ai.core.model.RoomAnalyzeRequest
import com.spacemuse.ai.core.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private sealed interface ScanState {
    data object Preview : ScanState
    data object Scanning : ScanState
    data class Result(val analysis: RoomAnalysis) : ScanState
    data class Error(val message: String) : ScanState
}

private sealed interface ProductSearchState {
    val query: String

    data class Loading(override val query: String) : ProductSearchState
    data class Loaded(override val query: String, val response: ProductSearchResponse) : ProductSearchState
    data class Error(override val query: String, val message: String) : ProductSearchState
}

// Section 7 of the product spec: camera-first capture with scanning
// feedback. Captures a real photo via CameraX ImageCapture, sends it to
// POST /rooms/analyze, and renders whatever RoomAnalysis comes back —
// including the backend's demo fallback when no GEMINI_API_KEY is
// configured, so scanning always produces a visible result end to end
// rather than going silent after capture. Every state (scanning, result,
// error) offers a way out — either a Cancel/Back action or the always-on
// top-bar back button — so the user is never stuck.
@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Preview) }
    // Incremented on every new scan attempt and on cancel; a completed
    // capture/network callback only applies its result if the epoch it
    // captured still matches — this is what makes "Cancel" during Scanning
    // work without needing real coroutine/CameraX cancellation plumbing.
    var scanEpoch by remember { mutableStateOf(0) }
    var productSearch by remember { mutableStateOf<ProductSearchState?>(null) }

    // ImageCapture's in-memory callback delivers JPEG by default (its only
    // supported format prior to CameraX 1.5's RAW/Ultra HDR additions,
    // which this project doesn't opt into) — imageProxyToJpegBytes() below
    // relies on that single-plane compressed buffer.
    //
    // Bounded to ~1280x960 at JPEG quality 85 rather than the sensor's full
    // resolution/default quality: an uncapped capture on a modern phone can
    // be several MB, which base64-encodes to an even bigger request body —
    // real-device testing showed that stalling past the 60s OkHttp timeout
    // (both the upload itself and Gemini's own processing scale with image
    // size). Room-object detection doesn't need more than ~1.2MP anyway.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            .setJpegQuality(85)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    fun startScan() {
        scanEpoch += 1
        val epoch = scanEpoch
        captureAndAnalyze(context, imageCapture, coroutineScope) { newState ->
            if (epoch == scanEpoch) scanState = newState
        }
    }

    fun cancelScan() {
        scanEpoch += 1 // invalidates any in-flight capture/network callback
        scanState = ScanState.Preview
    }

    fun shopFor(query: String) {
        productSearch = ProductSearchState.Loading(query)
        coroutineScope.launch {
            try {
                val response = ApiClient.api.searchProducts(query)
                productSearch = ProductSearchState.Loaded(query, response)
            } catch (error: Exception) {
                productSearch = ProductSearchState.Error(
                    query,
                    error.message ?: "Could not reach the SpaceMuse AI backend."
                )
            }
        }
    }

    fun openProduct(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(imageCapture = imageCapture, modifier = Modifier.fillMaxSize())

            when (val state = scanState) {
                is ScanState.Preview ->
                    ScanButton(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onClick = ::startScan
                    )

                is ScanState.Scanning -> ScanningOverlay(onCancel = ::cancelScan)

                is ScanState.Result -> RoomAnalysisOverlay(
                    analysis = state.analysis,
                    onScanAnother = { scanState = ScanState.Preview },
                    onDone = onBack,
                    onShop = ::shopFor
                )

                is ScanState.Error -> ScanErrorOverlay(
                    message = state.message,
                    onRetry = ::startScan,
                    onCancel = { scanState = ScanState.Preview }
                )
            }

            // Rendered last so it stays on top and clickable in every
            // state above, including the full-screen scanning/result/error
            // overlays — previously the back button could be visually and
            // functionally covered by those overlays.
            CameraTopBar(onBack = onBack)

            productSearch?.let { state ->
                ProductSearchOverlay(
                    state = state,
                    onOpenProduct = ::openProduct,
                    onDismiss = { productSearch = null }
                )
            }
        } else {
            PermissionRequest(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
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
private fun CameraTopBar(onBack: () -> Unit) {
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
private fun ScanButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(bottom = 40.dp)
            .height(56.dp)
            .fillMaxWidth(0.7f),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text = "Scan Room", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ScanningOverlay(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analyzing your room…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ScanErrorOverlay(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
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
                Text("Couldn't analyze this room", style = MaterialTheme.typography.titleMedium)
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
private fun RoomAnalysisOverlay(
    analysis: RoomAnalysis,
    onScanAnother: () -> Unit,
    onDone: () -> Unit,
    onShop: (String) -> Unit
) {
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
                        text = "Room analysis",
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
                    text = "Detected objects",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.height(160.dp)) {
                    items(analysis.objects) { obj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = obj.type.replace('_', ' '),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = obj.classification ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Shop",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        onShop(obj.type.replace('_', ' '))
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onScanAnother, modifier = Modifier.weight(1f)) {
                        Text("Scan another")
                    }
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSearchOverlay(
    state: ProductSearchState,
    onOpenProduct: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "Shop: ${state.query}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onDismiss)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                when (state) {
                    is ProductSearchState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is ProductSearchState.Error -> {
                        Text(
                            text = "Couldn't search for products",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is ProductSearchState.Loaded -> {
                        if (!state.response.providersConfigured) {
                            Text(
                                text = "No shopping provider is configured on the backend yet. " +
                                    "Add a product API key (e.g. SERPAPI_KEY — see backend/.env.example) " +
                                    "to enable real product search.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else if (state.response.results.isEmpty()) {
                            Text(
                                text = "No products found for \"${state.query}\".",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(240.dp)) {
                                items(state.response.results) { product ->
                                    ProductRow(
                                        product = product,
                                        onClick = { onOpenProduct(product.productUrl) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.bodyMedium)
            product.brand?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatPrice(product.priceMinor, product.currency),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatPrice(priceMinor: Int, currency: String): String {
    val major = priceMinor / 100
    return if (currency == "INR") "₹$major" else "$currency $major"
}

// Not a Composable — invoked from click handlers, so it drives UI state
// purely through the onState callback rather than reading/writing Compose
// state directly.
private fun captureAndAnalyze(
    context: Context,
    imageCapture: ImageCapture,
    scope: CoroutineScope,
    onState: (ScanState) -> Unit
) {
    onState(ScanState.Scanning)

    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val jpegBytes = imageProxyToJpegBytes(image)
                image.close()
                val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                scope.launch {
                    try {
                        val analysis = ApiClient.api.analyzeRoom(
                            RoomAnalyzeRequest(imageBase64 = base64Image)
                        )
                        onState(ScanState.Result(analysis))
                    } catch (error: Exception) {
                        onState(
                            ScanState.Error(
                                error.message ?: "Could not reach the SpaceMuse AI backend."
                            )
                        )
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onState(ScanState.Error(exception.message ?: "Camera capture failed."))
            }
        }
    )
}

// ImageCapture's in-memory callback (OnImageCapturedCallback) delivers a
// single-plane JPEG-encoded buffer by default — no decode/re-encode needed.
private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}
