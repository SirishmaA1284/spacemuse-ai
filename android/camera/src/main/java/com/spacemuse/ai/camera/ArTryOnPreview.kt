package com.spacemuse.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Milestone 5 of Phase 7 (Spatial/AR -- see
// docs/architecture/spatial-architecture.md): tap a detected plane to drop
// a real ARCore Anchor, then draw the caller-supplied product bitmap as a
// textured quad in the AR scene itself (ProductQuadRenderer), oriented to
// the surface it was placed on and scaled to its real-world size --
// replacing an earlier version that drew a flat, always-camera-facing
// Compose overlay positioned via screen-space projection. That version
// didn't tilt/foreshorten as the camera moved around it (real user
// feedback), because it was never actually IN the 3D scene. This one is,
// so GL's own perspective projection gives correct foreshortening for
// free, the same way it already does for BackgroundRenderer/PlaneRenderer.
//
// Deliberately a SEPARATE composable/renderer from ArCameraPreview
// (Milestones 1-3's tap-to-measure flow), duplicating its session-lifecycle
// scaffold rather than adding a second tap semantic to that state machine:
// ArCameraPreview is confirmed working end-to-end on a real device after
// real debugging effort, so protecting it from regression risk is worth
// the small duplication here.
//
// Touch handling is a single raw MotionEvent listener (not Compose gesture
// modifiers) so it can distinguish a single-finger tap (place/move the
// anchor) from a two-finger pinch/twist (resize/rotate the placed product)
// without fighting Compose's own gesture-consumption order on top of an
// AndroidView -- this mirrors the already-proven tap-slop pattern from
// ArCameraPreview, extended with manual two-pointer distance/angle
// tracking for pinch and rotation.
@Composable
fun ArTryOnPreview(
    modifier: Modifier = Modifier,
    getProductBitmap: () -> Bitmap? = { null },
    getProductWidthMeters: () -> Float = { 0.6f },
    onTrackingStatusChanged: (ArTrackingStatus) -> Unit = {},
    onPlaneCountChanged: (Int) -> Unit = {},
    onSessionError: (String) -> Unit = {},
    onAnchorPlaced: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionHolder = remember { mutableStateOf<Session?>(null) }
    val glViewHolder = remember { mutableStateOf<GLSurfaceView?>(null) }
    val resumeRequested = remember { AtomicBoolean(false) }
    val pendingTap = remember { AtomicReference<FloatArray?>(null) }
    val pendingPinchRotate = remember { AtomicReference<FloatArray?>(null) } // [scaleFactor, rotationDeltaRadians]

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
                        consumePinchRotate = { pendingPinchRotate.getAndSet(null) },
                        getProductBitmap = getProductBitmap,
                        getProductWidthMeters = getProductWidthMeters,
                        onTrackingStatusChanged = onTrackingStatusChanged,
                        onPlaneCountChanged = onPlaneCountChanged,
                        onRendererError = onSessionError,
                        onAnchorPlaced = onAnchorPlaced
                    )
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder.value = this
                setOnTouchListener(TapPinchRotateListener(pendingTap, pendingPinchRotate))
            }
        }
    )
}

private const val TAP_SLOP_PX = 20f

// Single-finger tap (place/move the anchor) vs two-finger pinch+twist
// (resize/rotate the placed product), from raw MotionEvents. Pinch scale
// is reported as a ratio (distance-this-move / distance-last-move) and
// rotation as a delta in radians, both meant to be applied incrementally
// by the consumer rather than treated as absolute values.
private class TapPinchRotateListener(
    private val pendingTap: AtomicReference<FloatArray?>,
    private val pendingPinchRotate: AtomicReference<FloatArray?>
) : android.view.View.OnTouchListener {
    private var downX = 0f
    private var downY = 0f
    private var isMultiTouch = false
    private var previousDistance = 0f
    private var previousAngle = 0f

    override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isMultiTouch = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    isMultiTouch = true
                    previousDistance = distanceBetween(event)
                    previousAngle = angleBetween(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isMultiTouch && event.pointerCount >= 2) {
                    val distance = distanceBetween(event)
                    val angle = angleBetween(event)
                    if (previousDistance > 0f) {
                        val scaleFactor = distance / previousDistance
                        val rotationDelta = angle - previousAngle
                        pendingPinchRotate.set(floatArrayOf(scaleFactor, rotationDelta))
                    }
                    previousDistance = distance
                    previousAngle = angle
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isMultiTouch && abs(event.x - downX) < TAP_SLOP_PX && abs(event.y - downY) < TAP_SLOP_PX) {
                    pendingTap.set(floatArrayOf(event.x, event.y))
                }
            }
        }
        return true
    }

    private fun distanceBetween(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun angleBetween(event: MotionEvent): Float {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return atan2(dy, dx)
    }
}

// One placed anchor plus the world-space "up" reference frozen at the
// moment it was placed -- see computePlacementBasis for why this needs to
// be frozen rather than recomputed every frame.
private class PlacedAnchor(val anchor: Anchor, val referenceUp: FloatArray)

private class ArTryOnRenderer(
    private val context: Context,
    private val getSession: () -> Session?,
    private val consumeResumeRequest: () -> Boolean,
    private val consumeTap: () -> FloatArray?,
    private val consumePinchRotate: () -> FloatArray?,
    private val getProductBitmap: () -> Bitmap?,
    private val getProductWidthMeters: () -> Float,
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit,
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onRendererError: (String) -> Unit,
    private val onAnchorPlaced: (Boolean) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val productQuadRenderer = ProductQuadRenderer()
    private var renderingDisabled = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private var placedAnchor: PlacedAnchor? = null

    // Gesture-driven adjustments on top of the physically-computed size.
    // Written only from the touch/UI thread (via consumePinchRotate,
    // itself only ever called from this GL thread -- see below), read
    // here on the GL thread each frame; @Volatile makes that write
    // visible without needing a lock for this simple single-value case.
    @Volatile private var userScale = 1f
    @Volatile private var userRotation = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        renderingDisabled = false
        try {
            backgroundRenderer.createOnGlThread()
            planeRenderer.createOnGlThread()
            productQuadRenderer.createOnGlThread()
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

            // Pinch/rotate is consumed here rather than in the touch
            // listener itself, so every write to userScale/userRotation
            // happens on this one GL thread -- @Volatile only needs to
            // handle a single writer for its visibility guarantee to be
            // enough, and this keeps that true.
            consumePinchRotate()?.let { (scaleFactor, rotationDelta) ->
                userScale = (userScale * scaleFactor).coerceIn(0.3f, 3f)
                userRotation += rotationDelta
            }

            handleTap(session, frame, camera)
            drawPlacedProduct(camera, viewMatrix, projectionMatrix)
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

    private fun handleTap(session: Session, frame: Frame, camera: Camera) {
        val tap = consumeTap() ?: return
        val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { result ->
            val trackable = result.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(result.hitPose)
        } ?: return

        placedAnchor?.anchor?.detach()
        val normal = hit.hitPose.yAxis
        val referenceUp = computeReferenceUp(normal, camera)
        placedAnchor = PlacedAnchor(session.createAnchor(hit.hitPose), referenceUp)
        onAnchorPlaced(true)
    }

    // World-space "up" reference for the product quad's in-plane rotation,
    // frozen at placement time (not recomputed every frame):
    // - Walls (normal roughly horizontal): world-up (0,1,0) directly --
    //   never parallel to a horizontal normal, so it always gives a
    //   stable, genuinely-vertical "up" for the image (a picture/mirror
    //   hangs upright), and needs no snapshot since it never changes.
    // - Floors/ceilings (normal roughly vertical): world-up IS parallel to
    //   the normal here (degenerate for the cross product below), so
    //   there's no inherent "up" within a horizontal plane -- fall back to
    //   the camera's own forward direction at the moment of the tap,
    //   projected onto the plane, so the product faces roughly away from
    //   whoever placed it (like setting down a real rug) rather than
    //   rotating as the camera moves around it afterward.
    private fun computeReferenceUp(normal: FloatArray, camera: Camera): FloatArray {
        val worldUp = floatArrayOf(0f, 1f, 0f)
        val isHorizontalSurface = abs(Vec3.dot(normal, worldUp)) > 0.9f
        if (!isHorizontalSurface) return worldUp

        val cameraZAxis = camera.pose.zAxis
        val cameraForward = floatArrayOf(-cameraZAxis[0], -cameraZAxis[1], -cameraZAxis[2])
        val projected = Vec3.subtract(cameraForward, Vec3.scale(normal, Vec3.dot(cameraForward, normal)))
        return Vec3.normalizeOrFallback(projected, fallback = floatArrayOf(0f, 0f, 1f))
    }

    private fun drawPlacedProduct(camera: Camera, viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        val current = placedAnchor ?: return
        if (current.anchor.trackingState != TrackingState.TRACKING) return
        val bitmap = getProductBitmap() ?: return

        // Wrapped in try/catch (kept permanently, unlike the debug logging
        // that used to live here -- see git history if this needs
        // re-instrumenting) so a bad frame's placement math can't take
        // down the whole render loop.
        try {
            val normal = Vec3.normalizeOrFallback(current.anchor.pose.yAxis, fallback = floatArrayOf(0f, 1f, 0f))
            // Re-derived each frame from the live (possibly ARCore-refined)
            // normal and the frozen reference, Gram-Schmidt style, so right/up
            // stay a valid orthonormal basis even if the anchor's exact tilt
            // shifts slightly over time.
            var right = Vec3.cross(current.referenceUp, normal)
            if (Vec3.length(right) < 0.0001f) right = floatArrayOf(1f, 0f, 0f) // reference nearly parallel to normal; arbitrary stable fallback
            right = Vec3.normalizeOrFallback(right, fallback = floatArrayOf(1f, 0f, 0f))
            val up = Vec3.cross(normal, right)

            // User pinch/rotate applied on top of the physical width: scale
            // both right/up uniformly (keeps the image's own aspect ratio,
            // which is what determines height -- see ArTryOnScreen), and spin
            // the right/up basis around the surface normal for the twist
            // gesture.
            val (rotatedRight, rotatedUp) = rotateAroundAxis(right, up, normal, userRotation)

            val widthMeters = getProductWidthMeters() * userScale
            val heightMeters = widthMeters * (bitmap.height.toFloat() / bitmap.width.toFloat())

            val center = floatArrayOf(current.anchor.pose.tx(), current.anchor.pose.ty(), current.anchor.pose.tz())
            productQuadRenderer.draw(bitmap, center, rotatedRight, rotatedUp, widthMeters, heightMeters, viewMatrix, projectionMatrix)
        } catch (e: Exception) {
            onRendererError(e.message ?: "Failed to draw the placed product.")
        }
    }

    // Rotates the right/up basis vectors around `axis` (the surface
    // normal) by `angleRadians` -- Rodrigues' rotation formula specialized
    // to a unit axis, applied to two vectors that share that axis as their
    // rotation plane's normal.
    private fun rotateAroundAxis(right: FloatArray, up: FloatArray, axis: FloatArray, angleRadians: Float): Pair<FloatArray, FloatArray> {
        val cosAngle = cos(angleRadians)
        val sinAngle = sin(angleRadians)
        fun rotate(v: FloatArray): FloatArray {
            val cross = Vec3.cross(axis, v)
            val dot = Vec3.dot(axis, v)
            return floatArrayOf(
                v[0] * cosAngle + cross[0] * sinAngle + axis[0] * dot * (1 - cosAngle),
                v[1] * cosAngle + cross[1] * sinAngle + axis[1] * dot * (1 - cosAngle),
                v[2] * cosAngle + cross[2] * sinAngle + axis[2] * dot * (1 - cosAngle)
            )
        }
        return rotate(right) to rotate(up)
    }
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
