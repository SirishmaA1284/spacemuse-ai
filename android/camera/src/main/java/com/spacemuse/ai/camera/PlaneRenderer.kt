package com.spacemuse.ai.camera

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Draws each currently-tracked ARCore Plane as a semi-transparent filled
// polygon, so plane detection is visually confirmable on-device (Milestone
// 2 of Phase 7 — see docs/architecture/spatial-architecture.md). ARCore
// guarantees Plane.getPolygon() is fan-triangulable around the plane's own
// centerPose origin, so a single GL_TRIANGLE_FAN per plane is sufficient —
// no separate triangulation step needed. Color hints at plane orientation
// (floor/ceiling/wall) but this is purely visual — Milestone 3 is where
// planes actually become real-world measurements.
internal class PlaneRenderer {
    private var program = 0
    private var positionAttrib = 0
    private var mvpUniform = 0
    private var colorUniform = 0

    fun createOnGlThread() {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("AR plane shader program failed to link: $log")
        }

        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color")
    }

    // Draws every TRACKING, non-subsumed plane as a translucent colored fan.
    // Returns the count drawn, so the caller can surface "planes detected: N".
    fun draw(planes: Collection<Plane>, viewMatrix: FloatArray, projectionMatrix: FloatArray): Int {
        val trackedPlanes = planes.filter {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null
        }
        if (trackedPlanes.isEmpty()) return 0

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionAttrib)

        val modelMatrix = FloatArray(16)
        val modelViewMatrix = FloatArray(16)
        val modelViewProjectionMatrix = FloatArray(16)

        for (plane in trackedPlanes) {
            val polygon = plane.polygon // FloatBuffer of x,z pairs, plane-local
            val pointCount = polygon.remaining() / 2
            if (pointCount < 3) continue

            val vertices = FloatArray(pointCount * 3)
            var i = 0
            while (polygon.hasRemaining()) {
                vertices[i * 3] = polygon.get()
                vertices[i * 3 + 1] = 0f
                vertices[i * 3 + 2] = polygon.get()
                i++
            }
            val vertexBuffer = toFloatBuffer(vertices)

            plane.centerPose.toMatrix(modelMatrix, 0)
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

            GLES20.glUniformMatrix4fv(mvpUniform, 1, false, modelViewProjectionMatrix, 0)
            val color = colorForPlaneType(plane.type)
            GLES20.glUniform4f(colorUniform, color[0], color[1], color[2], color[3])

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, pointCount)
        }

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        return trackedPlanes.size
    }

    private fun colorForPlaneType(type: Plane.Type): FloatArray = when (type) {
        Plane.Type.HORIZONTAL_UPWARD_FACING -> floatArrayOf(0.2f, 0.8f, 0.4f, 0.35f) // floor-like, green
        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> floatArrayOf(0.8f, 0.6f, 0.2f, 0.35f) // ceiling-like, amber
        Plane.Type.VERTICAL -> floatArrayOf(0.3f, 0.5f, 0.9f, 0.35f) // wall-like, blue
        else -> floatArrayOf(0.7f, 0.7f, 0.7f, 0.35f)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            val kind = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw IllegalStateException("AR plane $kind shader failed to compile: $log")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 u_ModelViewProjection;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_ModelViewProjection * a_Position;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """

        fun toFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(data); position(0) }
    }
}
