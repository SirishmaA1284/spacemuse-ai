package com.spacemuse.ai.camera

import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class ArTrackingStatus(
    val state: TrackingState,
    val failureReason: TrackingFailureReason? = null
)

// Minimal ARCore camera-passthrough preview: creates/resumes/pauses a
// Session tied to the composable's lifecycle (mirrors CameraPreview.kt's
// CameraX lifecycle-binding pattern) and renders only the camera background
// texture — no plane/point-cloud rendering yet, that's Milestone 2 (see
// docs/architecture/spatial-architecture.md, Phase 7).
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
// anything else non-thread-safe in that callback.
@Composable
fun ArCameraPreview(
    modifier: Modifier = Modifier,
    onTrackingStatusChanged: (ArTrackingStatus) -> Unit = {},
    onSessionError: (String) -> Unit = {}
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        val activeSession = sessionHolder.value ?: Session(context).also { newSession ->
                            val config = Config(newSession).apply {
                                planeFindingMode = Config.PlaneFindingMode.DISABLED
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
                        getSession = { sessionHolder.value },
                        consumeResumeRequest = { resumeRequested.compareAndSet(true, false) },
                        onTrackingStatusChanged = onTrackingStatusChanged,
                        onRendererError = onSessionError
                    )
                )
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder.value = this
            }
        }
    )
}

private class ArRenderer(
    private val getSession: () -> Session?,
    private val consumeResumeRequest: () -> Boolean,
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit,
    private val onRendererError: (String) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private var renderingDisabled = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        renderingDisabled = false
        try {
            backgroundRenderer.createOnGlThread()
        } catch (e: Exception) {
            // A shader compile/link failure previously showed up as a blank
            // solid-color screen with zero diagnostic — surface the real
            // reason instead of silently drawing nothing every frame.
            renderingDisabled = true
            onRendererError(e.message ?: "AR renderer failed to initialize.")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Full-screen quad in NDC — no viewport-relative geometry to adjust.
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

        val frame: Frame = try {
            session.update()
        } catch (e: Exception) {
            return
        }

        backgroundRenderer.draw(frame)

        val camera = frame.camera
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
}
