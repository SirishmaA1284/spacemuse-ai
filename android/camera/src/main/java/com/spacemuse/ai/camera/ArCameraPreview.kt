package com.spacemuse.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix as GraphicsMatrix
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sqrt

data class ArTrackingStatus(
    val state: TrackingState,
    val failureReason: TrackingFailureReason? = null
)

// ARCore camera-passthrough + plane-detection preview: creates/resumes/
// pauses a Session tied to the composable's lifecycle (mirrors
// CameraPreview.kt's CameraX lifecycle-binding pattern), renders the camera
// background texture, overlays detected planes as translucent colored
// polygons (Milestone 2 of Phase 7), and supports tap-to-measure plus a
// one-shot photo capture (Milestone 3) — see
// docs/architecture/spatial-architecture.md.
//
// Deliberately a separate preview from CameraPreview.kt/CameraX rather than
// combined with it: ARCore needs raw camera2 ownership via
// Session.setCameraTextureName, which conflicts with CameraX binding its
// own camera2 session on the same physical camera. Callers must have
// already confirmed ArAvailability.check() == Supported and CAMERA
// permission is granted before composing this.
// onTrackingStatusChanged is invoked from the GL render thread (once per
// frame), not the UI thread — writing to a Compose mutableStateOf from
// there is safe (the snapshot system applies cross-thread), but don't do
// anything else non-thread-safe in that callback. The same applies to every
// other on*Changed/on*Completed callback below.
//
// Milestone 3 additions (tap-to-measure + photo capture, see
// docs/architecture/spatial-architecture.md): a tap is queued from the UI
// touch thread and only ever resolved against a *fresh* Frame inside
// onDrawFrame (ARCore's Frame.hitTest is only valid against the frame that
// produced it) — same "queue on one thread, consume on the GL thread"
// shape as resumeRequested/captureRequested below. getConfirmedMeasurements
// is polled once per frame rather than pushed, so the renderer always draws
// whatever the caller's Compose state currently holds without needing its
// own copy to stay in sync.
@Composable
fun ArCameraPreview(
    modifier: Modifier = Modifier,
    onTrackingStatusChanged: (ArTrackingStatus) -> Unit = {},
    onPlaneCountChanged: (Int) -> Unit = {},
    onSessionError: (String) -> Unit = {},
    onPendingPointChanged: (Boolean) -> Unit = {},
    onMeasurementCompleted: (pointA: Pose, pointB: Pose, distanceCm: Float) -> Unit = { _, _, _ -> },
    getConfirmedMeasurements: () -> List<Pair<Pose, Pose>> = { emptyList() },
    captureRequestEpoch: Int = 0,
    onPhotoCaptured: (ByteArray) -> Unit = {},
    onCaptureError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionHolder = remember { mutableStateOf<Session?>(null) }
    val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
    // ARCore requires Session.setCameraTextureName() to be called
    // immediately before EVERY Session.resume(), or the camera capture
    // session never gets wired to that texture and the passthrough image
    // stays blank — even though 6DOF tracking keeps working fine, since
    // pose tracking uses a separate internal path independent of the
    // display texture. The texture only exists on the GL thread, so the
    // actual setCameraTextureName+resume() pairing happens there (below, in
    // ArRenderer.onDrawFrame) in strict order, never split across threads
    // racing each other. This flag just signals "a resume is due".
    val resumeRequested = remember { AtomicBoolean(false) }
    val pendingTap = remember { AtomicReference<FloatArray?>(null) }
    // Bumped by the caller (e.g. a "Finish Scan" button) to request a photo
    // capture on the next frame; a plain boolean can't distinguish "no
    // request yet" from "already consumed", so the epoch's *value* is what
    // gets compared, not just its change.
    val captureRequested = remember { AtomicBoolean(false) }
    var lastHandledCaptureEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(captureRequestEpoch) {
        if (captureRequestEpoch > 0 && captureRequestEpoch != lastHandledCaptureEpoch) {
            lastHandledCaptureEpoch = captureRequestEpoch
            captureRequested.set(true)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        val activeSession = sessionHolder.value ?: Session(context).also { newSession ->
                            val config = Config(newSession).apply {
                                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            }
                            newSession.configure(config)
                        }
                        sessionHolder.value = activeSession
                        resumeRequested.set(true)
                        glViewHolder.value?.onResume()
                    } catch (e: Exception) {
                        onSessionError(e.message ?: "Failed to start the AR session.")
                        sessionHolder.value?.close()
                        sessionHolder.value = null
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    glViewHolder.value?.onPause()
                    sessionHolder.value?.pause()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionHolder.value?.close()
            sessionHolder.value = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                preserveEGLContextOnPause = true
                setEGLContextClientVersion(2)
                setRenderer(
                    ArRenderer(
                        context = ctx,
                        getSession = { sessionHolder.value },
                        consumeResumeRequest = { resumeRequested.compareAndSet(true, false) },
                        consumeTap = { pendingTap.getAndSet(null) },
                        consumeCaptureRequest = { captureRequested.compareAndSet(true, false) },
                        getConfirmedMeasurements = getConfirmedMeasurements,
                        onTrackingStatusChanged = onTrackingStatusChanged,
                        onPlaneCountChanged = onPlaneCountChanged,
                        onRendererError = onSessionError,
                        onPendingPointChanged = onPendingPointChanged,
                        onMeasurementCompleted = onMeasurementCompleted,
                        onPhotoCaptured = onPhotoCaptured,
                        onCaptureError = onCaptureError
                    )
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder.value = this

                // Simple tap (not drag/pinch) detection: record the ACTION_DOWN
                // position and only queue a tap on ACTION_UP if the pointer
                // barely moved. Consumed on the GL thread inside
                // ArRenderer.onDrawFrame, never here — see pendingTap above.
                var downX = 0f
                var downY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                        }
                        MotionEvent.ACTION_UP -> {
                            if (abs(event.x - downX) < TAP_SLOP_PX && abs(event.y - downY) < TAP_SLOP_PX) {
                                pendingTap.set(floatArrayOf(event.x, event.y))
                            }
                        }
                    }
                    true
                }
            }
        }
    )
}

private const val TAP_SLOP_PX = 20f

private class ArRenderer(
    private val context: Context,
    private val getSession: () -> Session?,
    private val consumeResumeRequest: () -> Boolean,
    private val consumeTap: () -> FloatArray?,
    private val consumeCaptureRequest: () -> Boolean,
    private val getConfirmedMeasurements: () -> List<Pair<Pose, Pose>>,
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit,
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onRendererError: (String) -> Unit,
    private val onPendingPointChanged: (Boolean) -> Unit,
    private val onMeasurementCompleted: (pointA: Pose, pointB: Pose, distanceCm: Float) -> Unit,
    private val onPhotoCaptured: (ByteArray) -> Unit,
    private val onCaptureError: (String) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val measurementRenderer = MeasurementRenderer()
    private var renderingDisabled = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    // First point of an in-progress measurement, or null between
    // measurements. GL-thread-only state — never touched from the UI
    // thread, unlike the AtomicBoolean/AtomicReference flags above which
    // genuinely cross threads.
    private var pendingFirstPoint: Pose? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        renderingDisabled = false
        try {
            backgroundRenderer.createOnGlThread()
            planeRenderer.createOnGlThread()
            measurementRenderer.createOnGlThread()
        } catch (e: Exception) {
            // A shader compile/link failure previously showed up as a blank
            // solid-color screen with zero diagnostic — surface the real
            // reason instead of silently drawing nothing every frame.
            renderingDisabled = true
            onRendererError(e.message ?: "AR renderer failed to initialize.")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (renderingDisabled) return
        val session = getSession() ?: return

        if (consumeResumeRequest()) {
            try {
                // Must happen in exactly this order, every resume (not just
                // the first) — see the comment on resumeRequested above.
                session.setCameraTextureName(backgroundRenderer.textureId)
                session.resume()
            } catch (e: Exception) {
                onRendererError(e.message ?: "Failed to resume the AR session.")
                return
            }
        }

        // ARCore requires this before Frame.transformCoordinates2d() (used
        // in BackgroundRenderer.draw()) produces valid results — without it
        // the camera-to-display coordinate mapping is undefined, which
        // showed up as a flat, blurred, near-uniform color instead of a
        // real image (the transform sampled the wrong/degenerate region of
        // the camera texture). Cheap Session-level call, safe to repeat
        // every frame rather than tracking a "did this change" flag.
        if (viewportWidth > 0 && viewportHeight > 0) {
            session.setDisplayGeometry(displayRotation(context), viewportWidth, viewportHeight)
        }

        val frame: Frame = try {
            session.update()
        } catch (e: Exception) {
            return
        }

        backgroundRenderer.draw(frame)

        // Captured right after the clean camera-only draw, before the
        // plane/measurement overlays go on top — the room photo sent to
        // Gemini vision should match what a plain camera capture would
        // have produced, not include AR debug graphics.
        if (consumeCaptureRequest() && viewportWidth > 0 && viewportHeight > 0) {
            try {
                onPhotoCaptured(capturePhotoJpeg(viewportWidth, viewportHeight))
            } catch (e: Exception) {
                onCaptureError(e.message ?: "Failed to capture the room photo.")
            }
        }

        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
            val planeCount = planeRenderer.draw(session.getAllTrackables(Plane::class.java), viewMatrix, projectionMatrix)
            onPlaneCountChanged(planeCount)

            handleTap(frame, consumeTap())

            val confirmed = getConfirmedMeasurements()
            val markers = buildList {
                pendingFirstPoint?.let(::add)
                confirmed.forEach { (a, b) -> add(a); add(b) }
            }
            measurementRenderer.draw(markers, confirmed, viewMatrix, projectionMatrix)
        } else {
            onPlaneCountChanged(0)
        }

        onTrackingStatusChanged(
            ArTrackingStatus(
                state = camera.trackingState,
                failureReason = if (camera.trackingState == TrackingState.PAUSED) {
                    camera.trackingFailureReason
                } else {
                    null
                }
            )
        )
    }

    // Resolves a queued screen-space tap against the plane it landed on,
    // using ARCore's own hit-test (which already restricts plane hits to
    // inside the tracked polygon) rather than reimplementing that geometry.
    // The first valid tap starts a measurement; the second completes it and
    // reports the real-world distance between the two hit poses.
    private fun handleTap(frame: Frame, tap: FloatArray?) {
        if (tap == null) return
        val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { result ->
            val trackable = result.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(result.hitPose)
        } ?: return

        val first = pendingFirstPoint
        if (first == null) {
            pendingFirstPoint = hit.hitPose
            onPendingPointChanged(true)
        } else {
            val second = hit.hitPose
            val dx = second.tx() - first.tx()
            val dy = second.ty() - first.ty()
            val dz = second.tz() - first.tz()
            val distanceCm = sqrt(dx * dx + dy * dy + dz * dz) * 100f
            pendingFirstPoint = null
            onPendingPointChanged(false)
            onMeasurementCompleted(first, second, distanceCm)
        }
    }

    // Reads back the just-drawn camera passthrough frame straight from the
    // GL framebuffer instead of decoding ARCore's raw YUV_420_888 CPU image
    // ourselves — BackgroundRenderer's shader already does correct
    // YUV->RGB conversion (via the OES external texture sampler) as part of
    // normal rendering, so this reuses that instead of re-deriving
    // colorspace/stride handling that has no way to be verified without a
    // real device. GL_RGBA/GL_UNSIGNED_BYTE readback is the one format GLES
    // 2.0 guarantees glReadPixels supports regardless of the surface's
    // actual internal format.
    private fun capturePhotoJpeg(width: Int, height: Int): ByteArray {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()

        val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)

        // glReadPixels' origin is bottom-left; Bitmap's is top-left.
        val flipped = Bitmap.createBitmap(
            rawBitmap, 0, 0, width, height,
            GraphicsMatrix().apply { postScale(1f, -1f) },
            false
        )
        rawBitmap.recycle()

        val out = ByteArrayOutputStream()
        flipped.compress(Bitmap.CompressFormat.JPEG, 85, out)
        flipped.recycle()
        return out.toByteArray()
    }
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
