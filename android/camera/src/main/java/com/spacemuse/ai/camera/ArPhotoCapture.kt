package com.spacemuse.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix as GraphicsMatrix
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// Result of an AR-assisted room-photo capture: the JPEG bytes, plus a
// real-world scale reference -- screen pixels per real-world metre, at the
// depth of whatever the user tapped before capturing -- so a later static-
// image compositing step (TryInSpaceScreen's ComposeStep) can size a
// product overlay correctly by default instead of an arbitrary fixed size.
// pixelsPerMeter is null when the user never tapped a reference point (the
// tap is optional -- capture still works, just without a physical-scale
// default, matching this screen's original behavior).
data class ArPhotoCaptureResult(val jpeg: ByteArray, val pixelsPerMeter: Float?)

// Milestone 4 becoming measurement-aware (see
// docs/architecture/spatial-architecture.md): unlike Milestone 5's
// ArTryOnPreview (which renders the product live, continuously, inside the
// 3D scene), this only needs a scale reference at the single moment of
// capture -- a plain world-space Pose from a hit-test is enough, no
// ARCore Anchor (with its own tracking/drift-correction machinery) is
// needed for a value only used once, immediately. Reuses
// MeasurementRenderer to draw the tapped reference point (no new marker
// shader) and the same glReadPixels capture technique already proven in
// ArCameraPreview (Milestone 3) -- reads back the already-rendered camera
// passthrough rather than decoding ARCore's raw YUV image.
//
// Deliberately its own composable/renderer rather than extending
// ArCameraPreview or ArTryOnPreview -- both of those are confirmed working
// end-to-end already; this repo's established pattern for new AR tap
// semantics is a new file, not a mode flag on a working one.
@Composable
fun ArPhotoCapture(
    modifier: Modifier = Modifier,
    onTrackingStatusChanged: (ArTrackingStatus) -> Unit = {},
    onPlaneCountChanged: (Int) -> Unit = {},
    onSessionError: (String) -> Unit = {},
    onReferencePointChanged: (Boolean) -> Unit = {},
    captureRequestEpoch: Int = 0,
    onCaptured: (ArPhotoCaptureResult) -> Unit = {},
    onCaptureError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionHolder = remember { mutableStateOf<Session?>(null) }
    val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
    val resumeRequested = remember { AtomicBoolean(false) }
    val pendingTap = remember { AtomicReference<FloatArray?>(null) }
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
                    ArPhotoCaptureRenderer(
                        context = ctx,
                        getSession = { sessionHolder.value },
                        consumeResumeRequest = { resumeRequested.compareAndSet(true, false) },
                        consumeTap = { pendingTap.getAndSet(null) },
                        consumeCaptureRequest = { captureRequested.compareAndSet(true, false) },
                        onTrackingStatusChanged = onTrackingStatusChanged,
                        onPlaneCountChanged = onPlaneCountChanged,
                        onRendererError = onSessionError,
                        onReferencePointChanged = onReferencePointChanged,
                        onCaptured = onCaptured,
                        onCaptureError = onCaptureError
                    )
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder.value = this

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

private class ArPhotoCaptureRenderer(
    private val context: Context,
    private val getSession: () -> Session?,
    private val consumeResumeRequest: () -> Boolean,
    private val consumeTap: () -> FloatArray?,
    private val consumeCaptureRequest: () -> Boolean,
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit,
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onRendererError: (String) -> Unit,
    private val onReferencePointChanged: (Boolean) -> Unit,
    private val onCaptured: (ArPhotoCaptureResult) -> Unit,
    private val onCaptureError: (String) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val measurementRenderer = MeasurementRenderer()
    private var renderingDisabled = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var referencePose: Pose? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        renderingDisabled = false
        try {
            backgroundRenderer.createOnGlThread()
            planeRenderer.createOnGlThread()
            measurementRenderer.createOnGlThread()
        } catch (e: Exception) {
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
                session.setCameraTextureName(backgroundRenderer.textureId)
                session.resume()
            } catch (e: Exception) {
                onRendererError(e.message ?: "Failed to resume the AR session.")
                return
            }
        }

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
        // plane/marker overlays go on top -- same reasoning as
        // ArCameraPreview's capture: the room photo used for compositing
        // shouldn't include AR debug graphics.
        if (consumeCaptureRequest() && viewportWidth > 0 && viewportHeight > 0) {
            try {
                val jpeg = capturePhotoJpeg(viewportWidth, viewportHeight)
                val pixelsPerMeter = if (frame.camera.trackingState == TrackingState.TRACKING) {
                    referencePose?.let { pose -> computePixelsPerMeter(pose, frame, viewportWidth, viewportHeight) }
                } else {
                    null
                }
                onCaptured(ArPhotoCaptureResult(jpeg, pixelsPerMeter))
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

            handleTap(frame)
            referencePose?.let { pose ->
                measurementRenderer.draw(listOf(pose), emptyList(), viewMatrix, projectionMatrix)
            }
        } else {
            onPlaneCountChanged(0)
        }

        onTrackingStatusChanged(
            ArTrackingStatus(
                state = camera.trackingState,
                failureReason = if (camera.trackingState == TrackingState.PAUSED) camera.trackingFailureReason else null
            )
        )
    }

    // Tapping again moves the reference point rather than accumulating --
    // only one reference (and one resulting scale) makes sense per photo.
    private fun handleTap(frame: Frame) {
        val tap = consumeTap() ?: return
        val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { result ->
            val trackable = result.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(result.hitPose)
        } ?: return

        referencePose = hit.hitPose
        onReferencePointChanged(true)
    }

    // Screen pixels per real-world metre at the reference point's depth,
    // in the frame being captured right now: project the reference point
    // and a second point one metre away (along the camera's own right
    // axis) to screen space, and measure the pixel distance between them
    // -- same technique the pre-3D-rework version of Milestone 5 used for
    // its live overlay, reused here for a one-shot static value instead.
    private fun computePixelsPerMeter(pose: Pose, frame: Frame, width: Int, height: Int): Float? {
        val camera = frame.camera
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        val center = projectToScreen(floatArrayOf(pose.tx(), pose.ty(), pose.tz()), viewProjection, width, height)
        val right = camera.pose.xAxis
        val edge = projectToScreen(
            floatArrayOf(pose.tx() + right[0], pose.ty() + right[1], pose.tz() + right[2]),
            viewProjection, width, height
        )
        if (center == null || edge == null) return null

        val dx = edge[0] - center[0]
        val dy = edge[1] - center[1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun projectToScreen(worldPos: FloatArray, viewProjection: FloatArray, width: Int, height: Int): FloatArray? {
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, viewProjection, 0, floatArrayOf(worldPos[0], worldPos[1], worldPos[2], 1f), 0)
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return floatArrayOf((ndcX * 0.5f + 0.5f) * width, (1f - (ndcY * 0.5f + 0.5f)) * height)
    }

    // Same technique as ArCameraPreview's capturePhotoJpeg (Milestone 3):
    // reads back the just-drawn camera passthrough framebuffer rather than
    // decoding ARCore's raw YUV_420_888 CPU image, reusing
    // BackgroundRenderer's already-correct YUV->RGB conversion.
    private fun capturePhotoJpeg(width: Int, height: Int): ByteArray {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()

        val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)

        val flipped = Bitmap.createBitmap(
            rawBitmap, 0, 0, width, height,
            GraphicsMatrix().apply { postScale(1f, -1f) },
            false
        )
        rawBitmap.recycle()

        val out = ByteArrayOutputStream()
        flipped.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
