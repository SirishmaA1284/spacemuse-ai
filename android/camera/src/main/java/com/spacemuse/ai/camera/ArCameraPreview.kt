package com.spacemuse.ai.camera

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
                        activeSession.resume()
                        sessionHolder.value = activeSession
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
                        onTrackingStatusChanged = onTrackingStatusChanged
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
    private val onTrackingStatusChanged: (ArTrackingStatus) -> Unit
) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private var textureBoundToSession: Session? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backgroundRenderer.createOnGlThread()
        textureBoundToSession = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Full-screen quad in NDC — no viewport-relative geometry to adjust.
    }

    override fun onDrawFrame(gl: GL10?) {
        val session = getSession() ?: return

        // Session may not exist yet when the GL surface is first created
        // (GL thread and the lifecycle observer run independently), so bind
        // the texture name once a session becomes available rather than
        // only in onSurfaceCreated.
        if (textureBoundToSession !== session) {
            session.setCameraTextureName(backgroundRenderer.textureId)
            textureBoundToSession = session
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
