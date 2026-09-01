package com.spacemuse.ai.ui.screens.tryinspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.spacemuse.ai.core.model.Product
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val LOG_TAG = "ProductCutout"

// Real user feedback on Milestone 4: overlaying a product's raw retailer
// photo as-is rendered as a floating white rectangle, since most product
// photography is shot on a plain white/studio background -- not a usable
// "try it in your room" look. Runs the photo through ML Kit's on-device
// Subject Segmentation (its model downloads lazily via Play Services on
// first use -- there is no bundled-at-install-time path for a sideloaded,
// non-Play-Store APK, so the very first cutout on a fresh install may be
// slow or briefly fail while the module downloads) to remove the
// background before compositing. Falls back to the plain AsyncImage (the
// old white-card look) on ANY failure -- no network, decode failure, model
// not yet downloaded -- so a segmentation problem degrades the feature
// instead of breaking it outright.
//
// The fallback used to be silent, which made a real-device failure
// undiagnosable from here (no logcat access in this environment) -- it
// stayed broken across two pushes with no way to tell why. Now the
// specific failure step is tracked and surfaced as a small on-screen
// caption so a screenshot alone is enough to diagnose it.
internal sealed interface CutoutResult {
    data class Ready(val bitmap: Bitmap) : CutoutResult
    data object Loading : CutoutResult
    data class Unavailable(val reason: String) : CutoutResult
}

private val subjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
)

// Reusable hook for callers that need the raw cutout Bitmap without
// Compose rendering it themselves -- e.g. ArTryOnScreen (Milestone 5)
// hands the bitmap down into the GL layer instead of drawing it in
// Compose. ProductOverlayImage below is the Compose-rendering wrapper for
// callers (Milestone 4) that do want it drawn directly.
@Composable
internal fun rememberProductCutout(product: Product): CutoutResult {
    val context = LocalContext.current
    val cutoutResult by produceState<CutoutResult>(initialValue = CutoutResult.Loading, key1 = product.imageUrl) {
        value = cutOutProductPhoto(context, product.imageUrl)
    }
    return cutoutResult
}

@Composable
internal fun ProductOverlayImage(product: Product, modifier: Modifier) {
    val cutoutResult = rememberProductCutout(product)

    // The caller's full modifier chain (size, graphicsLayer transform,
    // pointerInput gesture) goes on this Box; the image/caption inside
    // just fill it, so both visually transform together.
    Box(modifier = modifier) {
        when (val result = cutoutResult) {
            is CutoutResult.Ready ->
                Image(bitmap = result.bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())

            CutoutResult.Loading, is CutoutResult.Unavailable ->
                // Covers "still processing" and "gave up" the same way --
                // Coil loads the same URL either way, so there's no
                // separate loading placeholder to manage; this just gets
                // swapped out if/when the cutout lands.
                AsyncImage(model = product.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }

        (cutoutResult as? CutoutResult.Unavailable)?.let { unavailable ->
            Text(
                text = "cutout: ${unavailable.reason}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(2.dp),
                color = Color.Red,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private suspend fun cutOutProductPhoto(context: Context, imageUrl: String?): CutoutResult {
    if (imageUrl == null) return CutoutResult.Unavailable("no image URL")
    try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            // ML Kit needs direct pixel access to build an InputImage;
            // Coil's default HARDWARE bitmap config (API 26+) doesn't allow
            // that, so request a software-backed bitmap explicitly.
            .allowHardware(false)
            .build()
        val result = loader.execute(request) as? SuccessResult
        if (result == null) {
            Log.w(LOG_TAG, "Coil load did not return SuccessResult for $imageUrl")
            return CutoutResult.Unavailable("photo download failed")
        }

        val sourceBitmap = (result.drawable as? BitmapDrawable)?.bitmap
        if (sourceBitmap == null) {
            Log.w(LOG_TAG, "Loaded drawable was not a BitmapDrawable: ${result.drawable::class.simpleName}")
            return CutoutResult.Unavailable("unexpected image format")
        }

        val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
        val segmentationResult = subjectSegmenter.process(inputImage).await()
        val foreground = segmentationResult.foregroundBitmap
        if (foreground == null) {
            return CutoutResult.Unavailable("no subject detected")
        }
        return CutoutResult.Ready(foreground)
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Subject segmentation failed for $imageUrl", e)
        return CutoutResult.Unavailable(e::class.simpleName + ": " + (e.message ?: "no message"))
    }
}

// ML Kit's Task API predates kotlinx-coroutines-play-services being worth
// adding as a dependency just for this one call site -- a plain
// suspendCancellableCoroutine bridge is a handful of lines and avoids
// pulling in and version-pinning a whole new interop artifact.
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
