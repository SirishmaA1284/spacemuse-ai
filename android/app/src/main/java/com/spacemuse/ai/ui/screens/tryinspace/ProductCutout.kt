package com.spacemuse.ai.ui.screens.tryinspace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
private val subjectSegmenter = SubjectSegmentation.getClient(
    SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
)

@Composable
internal fun ProductOverlayImage(product: Product, modifier: Modifier) {
    val context = LocalContext.current
    val cutoutBitmap by produceState<Bitmap?>(initialValue = null, key1 = product.imageUrl) {
        value = cutOutProductPhoto(context, product.imageUrl)
    }

    val bitmap = cutoutBitmap
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
    } else {
        // Covers both "still processing" and "gave up" -- Coil loads the
        // same URL either way, so there's no separate loading state to
        // manage; this just gets swapped out once/if the cutout lands.
        AsyncImage(model = product.imageUrl, contentDescription = null, modifier = modifier)
    }
}

private suspend fun cutOutProductPhoto(context: Context, imageUrl: String?): Bitmap? {
    if (imageUrl == null) return null
    return try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            // ML Kit needs direct pixel access to build an InputImage;
            // Coil's default HARDWARE bitmap config (API 26+) doesn't allow
            // that, so request a software-backed bitmap explicitly.
            .allowHardware(false)
            .build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        val sourceBitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return null

        val inputImage = InputImage.fromBitmap(sourceBitmap, 0)
        subjectSegmenter.process(inputImage).await().foregroundBitmap
    } catch (e: Exception) {
        null
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
