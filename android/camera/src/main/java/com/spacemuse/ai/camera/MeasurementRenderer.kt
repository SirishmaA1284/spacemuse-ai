package com.spacemuse.ai.camera

import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Draws tap-to-measure feedback for Milestone 3 of Phase 7 (Spatial/AR —
// see docs/architecture/spatial-architecture.md): a small flat marker at
// each placed point, plus a line between the two points of every completed
// measurement (including the in-progress one, before the user has confirmed
// its label). Reuses PlaneRenderer's shader-validation and MVP-matrix
// pattern rather than introducing a third variant.
internal class MeasurementRenderer {
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
            throw IllegalStateException("AR measurement shader program failed to link: $log")
        }

        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        colorUniform = GLES20.glGetUniformLocation(program, "u_Color")
    }

    // markerPoses: every placed point that still needs drawing (pending
    // point(s) plus every confirmed measurement's two endpoints).
    // lines: pairs of world-space points (each a Pose) to connect.
    fun draw(
        markerPoses: List<Pose>,
        lines: List<Pair<Pose, Pose>>,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (markerPoses.isEmpty() && lines.isEmpty()) return

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glDepthMask(false)

        val viewProjectionMatrix = FloatArray(16)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        for (pose in markerPoses) {
            drawMarker(pose, viewMatrix, projectionMatrix, MARKER_COLOR)
        }
        for ((a, b) in lines) {
            drawLine(a, b, viewProjectionMatrix, LINE_COLOR)
        }

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDepthMask(true)
    }

    // A small flat square lying in the pose's own local XZ plane (the same
    // orientation ARCore's hit-test pose already returns for a plane hit —
    // see Frame.hitTest — so no extra billboarding math is needed).
    private fun drawMarker(pose: Pose, viewMatrix: FloatArray, projectionMatrix: FloatArray, color: FloatArray) {
        val modelMatrix = FloatArray(16)
        val modelViewMatrix = FloatArray(16)
        val modelViewProjectionMatrix = FloatArray(16)
        pose.toMatrix(modelMatrix, 0)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, modelViewProjectionMatrix, 0)
        GLES20.glUniform4f(colorUniform, color[0], color[1], color[2], color[3])

        val vertexBuffer = toFloatBuffer(MARKER_QUAD)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }

    // World-space line segment: vertex positions are already in world
    // coordinates, so the "model" matrix is identity and u_ModelViewProjection
    // is just the caller's precomputed view-projection matrix.
    private fun drawLine(a: Pose, b: Pose, viewProjectionMatrix: FloatArray, color: FloatArray) {
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, viewProjectionMatrix, 0)
        GLES20.glUniform4f(colorUniform, color[0], color[1], color[2], color[3])

        val vertices = floatArrayOf(a.tx(), a.ty(), a.tz(), b.tx(), b.ty(), b.tz())
        val vertexBuffer = toFloatBuffer(vertices)
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glLineWidth(4f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
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
            throw IllegalStateException("AR measurement $kind shader failed to compile: $log")
        }
        return shader
    }

    private companion object {
        const val MARKER_HALF_SIZE = 0.03f // 3cm half-extent -> 6cm marker, small enough not to obscure taps

        val MARKER_QUAD = floatArrayOf(
            -MARKER_HALF_SIZE, 0f, -MARKER_HALF_SIZE,
            MARKER_HALF_SIZE, 0f, -MARKER_HALF_SIZE,
            MARKER_HALF_SIZE, 0f, MARKER_HALF_SIZE,
            -MARKER_HALF_SIZE, 0f, MARKER_HALF_SIZE
        )

        val MARKER_COLOR = floatArrayOf(1f, 0.25f, 0.25f, 0.9f) // red, high-contrast against plane overlays
        val LINE_COLOR = floatArrayOf(1f, 0.9f, 0.1f, 1f) // yellow

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
