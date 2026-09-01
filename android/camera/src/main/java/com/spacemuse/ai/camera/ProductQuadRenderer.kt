package com.spacemuse.ai.camera

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

// Draws a product's (already background-cut) photo as a textured quad
// anchored to a real-world point and oriented to the surface it was
// placed on -- Milestone 5 of Phase 7 (Spatial/AR -- see
// docs/architecture/spatial-architecture.md), replacing an earlier
// Compose-overlay version that always faced the camera flat and never
// tilted/foreshortened with the actual wall or floor (real user feedback:
// "if I change the camera angle... not appropriate"). The quad lives in
// true 3D world space and is drawn with the same view/projection pipeline
// as everything else in this AR scene, so perspective foreshortening is
// exactly what GL already gives BackgroundRenderer/PlaneRenderer -- no
// screen-space math to re-derive.
internal class ProductQuadRenderer {
    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var mvpUniform = 0
    private var textureUniform = 0
    private var textureId = -1
    private var uploadedBitmapKey: Int = 0

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

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
            throw IllegalStateException("Product quad shader program failed to link: $log")
        }

        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
    }

    // Re-uploads the texture only when the bitmap instance actually
    // changed (System.identityHashCode as a cheap "is this the same
    // object" check) -- called once per frame from draw(), so this must
    // stay cheap when nothing changed.
    private fun uploadTextureIfChanged(bitmap: Bitmap) {
        val key = System.identityHashCode(bitmap)
        if (key == uploadedBitmapKey) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        uploadedBitmapKey = key
    }

    // centerWorld/right/up/normal are all world-space; right and up are
    // expected to already be unit length and mutually orthogonal (see
    // ArTryOnPreview's computePlacementBasis) -- widthMeters/heightMeters
    // scale them per call rather than requiring the caller to pre-scale.
    fun draw(
        bitmap: Bitmap,
        centerWorld: FloatArray,
        right: FloatArray,
        up: FloatArray,
        widthMeters: Float,
        heightMeters: Float,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        uploadTextureIfChanged(bitmap)

        val halfWidth = widthMeters / 2f
        val halfHeight = heightMeters / 2f

        // Four corners of the quad in world space: center offset by
        // +/-halfWidth along `right` and +/-halfHeight along `up`. Texture
        // V is 0 at the top corners -- GLUtils.texImage2D uploads bitmap
        // row 0 (the image's own top row) as texture row 0, so v=0
        // sampling the top row matches with no flip needed. (If this ships
        // upside-down on-device, swap the two v values below -- that's
        // the one-line fix for that specific symptom.)
        val vertices = floatArrayOf(
            // position (x,y,z),                                                    u,    v
            centerWorld[0] - right[0] * halfWidth + up[0] * halfHeight, centerWorld[1] - right[1] * halfWidth + up[1] * halfHeight, centerWorld[2] - right[2] * halfWidth + up[2] * halfHeight, 0f, 0f,
            centerWorld[0] + right[0] * halfWidth + up[0] * halfHeight, centerWorld[1] + right[1] * halfWidth + up[1] * halfHeight, centerWorld[2] + right[2] * halfWidth + up[2] * halfHeight, 1f, 0f,
            centerWorld[0] - right[0] * halfWidth - up[0] * halfHeight, centerWorld[1] - right[1] * halfWidth - up[1] * halfHeight, centerWorld[2] - right[2] * halfWidth - up[2] * halfHeight, 0f, 1f,
            centerWorld[0] + right[0] * halfWidth - up[0] * halfHeight, centerWorld[1] + right[1] * halfWidth - up[1] * halfHeight, centerWorld[2] + right[2] * halfWidth - up[2] * halfHeight, 1f, 1f
        )

        // Vertices are already in world space (no separate model matrix,
        // same as MeasurementRenderer's line drawing) -- just view * proj.
        val viewProjectionMatrix = FloatArray(16)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureUniform, 0)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, viewProjectionMatrix, 0)

        val vertexBuffer = toFloatBuffer(vertices)
        val stride = 5 * 4 // 5 floats per vertex, 4 bytes each

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
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
            throw IllegalStateException("Product quad $kind shader failed to compile: $log")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 u_ModelViewProjection;
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = u_ModelViewProjection * a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """

        fun toFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(data); position(0) }
    }
}

// Small Vec3 helpers -- android.opengl.Matrix only operates on 4x4/vec4,
// nothing lighter-weight ships for plain 3-vectors.
internal object Vec3 {
    fun dot(a: FloatArray, b: FloatArray): Float = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun cross(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

    fun subtract(a: FloatArray, b: FloatArray): FloatArray =
        floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

    fun scale(a: FloatArray, s: Float): FloatArray = floatArrayOf(a[0] * s, a[1] * s, a[2] * s)

    fun length(a: FloatArray): Float = sqrt(dot(a, a))

    // Falls back to `fallback` when `a` is too close to zero-length to
    // normalize safely (the degenerate case this guards against: the
    // reference-up snapshot ends up nearly parallel to the surface
    // normal, which the caller works around by choosing a
    // plane-appropriate reference in the first place -- this is a last-
    // resort guard, not the primary defense).
    fun normalizeOrFallback(a: FloatArray, fallback: FloatArray): FloatArray {
        val len = length(a)
        if (len < 0.0001f) return fallback
        return scale(a, 1f / len)
    }
}
