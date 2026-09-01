package com.spacemuse.ai.camera

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sqrt

// Screen-space projection of a live ARCore Anchor, in pixels: where it
// currently projects to, and how many screen pixels correspond to one
// real-world metre at the anchor's current depth -- so a caller can size a
// 2D product overlay to the product's real dimensions. Reported as null
// (via ArTryOnPreview's onAnchorProjectionChanged) when there is no anchor
// or its trackingState isn't TRACKING (e.g. tracking lost).
data class AnchorProjection(val screenX: Float, val screenY: Float, val pixelsPerMeter: Float)

// Milestone 5 of Phase 7 (Spatial/AR -- see
// docs/architecture/spatial-architecture.md): tap a detected plane to drop
// a single ARCore Anchor, then report its live screen-space projection
// every frame so a caller can position/scale a 2D product photo overlay in
// Compose to visually track it (ADR-006's Level 4 -- real-world placement
// via ARCore, still 2D image compositing rather than a rendered 3D asset).
//
// Deliberately a SEPARATE composable/renderer from ArCameraPreview
// (Milestones 1-3's tap-to-measure flow), duplicating its session-lifecycle
// scaffold rather than adding a second tap semantic to that state machine:
// ArCameraPreview is confirmed working end-to-end on a real device after
// real debugging effort (4 rounds for Milestone 1 alone), so protecting it
// from regression risk is worth the small duplication here.
@Composable
fun ArTryOnPreview(
    modifier: Modifier = Modifier,
    onTrackingStatusChanged: (ArTrackingStatus) -> Unit = {},
    onPlaneCountChanged: (Int) -> Unit = {},
    onSessionError: (String) -> Unit = {},
    onAnchorProjectionChanged: (AnchorProjection?) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionHolder = remember { mutableStateOf<Session?>(null) }
    val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
    val resumeRequested = remember { AtomicBoolean(false) }
    val pendingTap = remember { AtomicReference<FloatArray?>(null) }

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
                    ArTryOnRenderer(
                        context = ctx,
                        getSession = { sessionHolder.value },
                        consumeResumeRequest = { resumeRequested.compareAndSet(true, false) },
                        consumeTap = { pendingTap.getAndSet(null) },
                        onTrackingStatusChanged = onTrackingStatusChanged,
                        onPlaneCountChanged = onPlaneCountChanged,
                        onRendererError = onSessionError,
                        onAnchorProjectionChanged = onAnchorProjectionChanged
                    )
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder.value = this

                // Same tap-vs-drag slop detection as ArCameraPreview.
                var downX = 0f
                var downY = 0f
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                        }
                        MotionEvent.ACTION_UP -> {
                            if (abs(event.x - downX) < TRYON_TAP_SLOP_PX && abs(event.y - downY) < TRYON_TAP_SLOP_PX) {
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

private const val TRYON_TAP_SLOP_PX = 20f

private class ArTryOnRenderer(
    private val context: Context,
    private val getSession: () -> Session?,
    private val consumeResumeRequest: () -> Boolean,
    private val consumeTap: () -> FloatArray?,
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit,
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onRendererError: (String) -> Unit,
    private val onAnchorProjectionChanged: (AnchorProjection?) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private var renderingDisabled = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    // The single currently-placed anchor, or null before the user has
    // tapped a plane. Re-tapping detaches the previous one and creates a
    // new one -- only one try-on placement at a time in this milestone.
    private var anchor: Anchor? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        renderingDisabled = false
        try {
            backgroundRenderer.createOnGlThread()
            planeRenderer.createOnGlThread()
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

        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
            val planeCount = planeRenderer.draw(session.getAllTrackables(Plane::class.java), viewMatrix, projectionMatrix)
            onPlaneCountChanged(planeCount)

            handleTap(session, frame)
            updateAnchorProjection(camera, viewMatrix, projectionMatrix)
        } else {
            onPlaneCountChanged(0)
            onAnchorProjectionChanged(null)
        }

        onTrackingStatusChanged(
            ArTrackingStatus(
                state = camera.trackingState,
                failureReason = if (camera.trackingState == TrackingState.PAUSED) camera.trackingFailureReason else null
            )
        )
    }

    private fun handleTap(session: Session, frame: Frame) {
        val tap = consumeTap() ?: return
        val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { result ->
            val trackable = result.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(result.hitPose)
        } ?: return

        anchor?.detach()
        anchor = session.createAnchor(hit.hitPose)
    }

    private fun updateAnchorProjection(camera: Camera, viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        val currentAnchor = anchor
        if (currentAnchor == null || currentAnchor.trackingState != TrackingState.TRACKING ||
            viewportWidth == 0 || viewportHeight == 0
        ) {
            onAnchorProjectionChanged(null)
            return
        }

        val viewProjection = FloatArray(16)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

        val anchorPose = currentAnchor.pose
        val center = projectToScreen(
            floatArrayOf(anchorPose.tx(), anchorPose.ty(), anchorPose.tz()),
            viewProjection, viewportWidth, viewportHeight
        )

        // A second point one real-world metre away along the camera's own
        // right axis (so it's always facing the viewer, regardless of the
        // anchor's own orientation) -- the on-screen pixel distance between
        // the two projected points is exactly "pixels per metre" at this
        // anchor's current depth.
        val right = camera.pose.xAxis
        val edge = projectToScreen(
            floatArrayOf(anchorPose.tx() + right[0], anchorPose.ty() + right[1], anchorPose.tz() + right[2]),
            viewProjection, viewportWidth, viewportHeight
        )

        if (center == null || edge == null) {
            onAnchorProjectionChanged(null)
            return
        }

        val dx = edge[0] - center[0]
        val dy = edge[1] - center[1]
        onAnchorProjectionChanged(AnchorProjection(center[0], center[1], sqrt(dx * dx + dy * dy)))
    }

    // Standard perspective projection: clip = viewProjection * worldPos,
    // then perspective-divide by w, then map NDC [-1,1] to pixel
    // coordinates (flipping Y since NDC is bottom-up but screen space is
    // top-down). Null for a point behind the camera (w <= 0).
    private fun projectToScreen(worldPos: FloatArray, viewProjection: FloatArray, width: Int, height: Int): FloatArray? {
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, viewProjection, 0, floatArrayOf(worldPos[0], worldPos[1], worldPos[2], 1f), 0)
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        return floatArrayOf(
            (ndcX * 0.5f + 0.5f) * width,
            (1f - (ndcY * 0.5f + 0.5f)) * height
        )
    }
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
