package com.momentoestoico.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import com.momentoestoico.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Graba [MomentFilm] como MP4 (H.264 + AAC) sin pantalla: cada cuadro se dibuja con Canvas en
 * un Bitmap, se sube como textura OpenGL a la superficie del codificador y se marca con su tiempo exacto.
 */
object VideoEncoder {
    private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val SAMPLE_RATE = 44100
    private const val TIMEOUT_US = 10_000L

    private class Sample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    suspend fun encode(context: Context, film: MomentFilm, sound: Boolean, out: File, onProgress: (Float) -> Unit) {
        val (vw, vh) = pickSize(film.card.format)
        val audio = if (sound) encodeAudio(context) else null
        out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
        var egl: Egl? = null
        var muxerStarted = false
        try {
            val format = MediaFormat.createVideoFormat(VIDEO_MIME, vw, vh).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, if (vw * vh > 1_500_000) 9_000_000 else 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, MomentFilm.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()
            egl = Egl(surface, vw, vh)

            val background = film.card.backgroundBitmap(vw)
            val frame = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            val scale = vw / film.card.width

            var videoTrack = -1
            val info = MediaCodec.BufferInfo()
            fun drain(endOfStream: Boolean) {
                var waits = 0
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
                    when {
                        // Al final se espera el cierre del codificador, pero no más de unos segundos
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || ++waits > 500) return
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = muxer.addTrack(codec.outputFormat)
                            val audioTrack = audio?.let { muxer.addTrack(it.first) }
                            muxer.start()
                            muxerStarted = true
                            // El audio ya está codificado: se escribe entero al empezar
                            if (audio != null && audioTrack != null) {
                                val ai = MediaCodec.BufferInfo()
                                audio.second.forEach { s ->
                                    ai.set(0, s.data.size, s.ptsUs, s.flags)
                                    muxer.writeSampleData(audioTrack, ByteBuffer.wrap(s.data), ai)
                                }
                            }
                        }
                        index >= 0 -> {
                            val buf = codec.getOutputBuffer(index)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && muxerStarted) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                muxer.writeSampleData(videoTrack, buf, info)
                            }
                            codec.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            val frames = (MomentFilm.DURATION * MomentFilm.FPS).roundToInt()
            for (i in 0 until frames) {
                currentCoroutineContext().ensureActive()
                val t = i / MomentFilm.FPS.toFloat()
                canvas.drawBitmap(background, 0f, 0f, null)
                canvas.save()
                canvas.scale(scale, scale)
                film.drawFrame(canvas, t)
                canvas.restore()
                egl.draw(frame, i * 1_000_000_000L / MomentFilm.FPS)
                drain(false)
                onProgress((i + 1f) / frames)
            }
            codec.signalEndOfInputStream()
            drain(true)
            background.recycle()
            frame.recycle()
        } finally {
            egl?.release()
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    /** Tamaño del video: el de la tarjeta si el codificador lo admite; si no, más chico. */
    private fun pickSize(format: CardFormat): Pair<Int, Int> {
        val caps = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(VIDEO_MIME, true) } }
            .mapNotNull { runCatching { it.getCapabilitiesForType(VIDEO_MIME).videoCapabilities }.getOrNull() }
        for (f in listOf(1f, 2f / 3f, 0.5f)) {
            val w = (format.width * f / 2).roundToInt() * 2
            val h = (format.height * f / 2).roundToInt() * 2
            if (caps.isEmpty() || caps.any { it.isSizeSupported(w, h) }) return w to h
        }
        return 540 to (540 * format.height / format.width / 2) * 2
    }

    // ── Audio: el cuenco suena al empezar y cuando aparece la tarjeta ──

    private fun bowl(context: Context): ShortArray {
        val bytes = context.resources.openRawResource(R.raw.cuenco).use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 22050
        var channels = 1
        var pos = 12
        var data: ShortArray? = null
        while (pos + 8 <= bytes.size && data == null) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            if (id == "fmt ") {
                channels = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
            } else if (id == "data") {
                val n = min(size, bytes.size - pos - 8) / 2 / channels
                data = ShortArray(n) { bb.getShort(pos + 8 + it * 2 * channels) }
            }
            pos += 8 + size + (size and 1)
        }
        val src = data ?: return ShortArray(0)
        // Remuestreo lineal a 44,1 kHz
        val n = (src.size.toLong() * SAMPLE_RATE / rate).toInt()
        return ShortArray(n) { i ->
            val x = i * rate.toDouble() / SAMPLE_RATE
            val a = x.toInt().coerceAtMost(src.size - 1)
            val b = (a + 1).coerceAtMost(src.size - 1)
            (src[a] + (src[b] - src[a]) * (x - a)).toInt().toShort()
        }
    }

    private fun mix(context: Context): ShortArray {
        val total = (MomentFilm.DURATION * SAMPLE_RATE).toInt()
        val acc = FloatArray(total)
        val b = bowl(context)
        MomentFilm.BOWLS.forEach { (at, gain) ->
            val start = (at * SAMPLE_RATE).toInt()
            for (i in b.indices) {
                val j = start + i
                if (j >= total) break
                acc[j] += b[i] * gain
            }
        }
        // Se apaga suave en el último segundo
        val fade = SAMPLE_RATE
        return ShortArray(total) { i ->
            val g = if (i > total - fade) (total - i) / fade.toFloat() else 1f
            (acc[i] * g).coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }

    private fun encodeAudio(context: Context): Pair<MediaFormat, List<Sample>>? = runCatching {
        val pcm = mix(context)
        val format = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(AUDIO_MIME)
        val samples = mutableListOf<Sample>()
        var outFormat: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var read = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = min(buf.remaining() / 2, pcm.size - read)
                        val pts = read * 1_000_000L / SAMPLE_RATE
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, read, n)
                            codec.queueInputBuffer(i, 0, n * 2, pts, 0)
                            read += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bytes = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(bytes)
                            samples += Sample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        outFormat?.let { it to samples }
    }.getOrNull()

    // ── OpenGL: dibuja el Bitmap del cuadro en la superficie del codificador ──

    private class Egl(surface: Surface, private val width: Int, private val height: Int) {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: EGLContext
        private val eglSurface: EGLSurface
        private val program: Int
        private val texture: Int
        private var uploaded = false
        private val quad: FloatBuffer

        init {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent" }

            program = link(
                """
                attribute vec4 aPos;
                attribute vec2 aTex;
                varying vec2 vTex;
                void main() { gl_Position = aPos; vTex = aTex; }
                """.trimIndent(),
                """
                precision mediump float;
                varying vec2 vTex;
                uniform sampler2D uTex;
                void main() { gl_FragColor = texture2D(uTex, vTex); }
                """.trimIndent(),
            )
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            texture = tex[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            // x, y, u, v. La fila 0 del Bitmap es la de arriba: v = 0 arriba.
            val v = floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
            quad = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }
        }

        fun draw(bitmap: Bitmap, ptsNs: Long) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            if (uploaded) {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                uploaded = true
            }
            val pos = GLES20.glGetAttribLocation(program, "aPos")
            val tex = GLES20.glGetAttribLocation(program, "aTex")
            quad.position(0)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(pos)
            quad.position(2)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(tex)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, ptsNs)
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }

        private fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
        }

        private fun link(vs: String, fs: String): Int = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }

        companion object {
            const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }
}
