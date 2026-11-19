package com.momentoestoico.share

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.content.Context
import com.momentoestoico.R
import com.momentoestoico.data.Lang
import com.momentoestoico.ui.localized
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * El video para compartir, cuadro por cuadro (20 s):
 *
 * - 0,0–8,0 s: una respiración guiada. El ensō se pinta de un trazo y crece al inhalar
 *   (3,4 s); después se achica al exhalar (4,1 s).
 * - 8,0–10,0 s: el ensō se desliza y se achica hasta su lugar en la tarjeta, frenando
 *   suavemente como un resorte amortiguado.
 * - 9,2–20,0 s: aparecen la cita y el autor. Quedan más de 10 s para leerlos, mientras
 *   el ensō sigue respirando despacio.
 */
class MomentFilm(context: Context, val card: QuoteCard, lang: Lang) {
    private val texts = context.localized(lang)
    private val inhale = texts.getString(R.string.inhale)
    private val exhale = texts.getString(R.string.exhale)
    private val w = card.width
    private val h = card.height

    private val breathY = h * (if (card.isStory) 0.45f else 0.46f)
    private val breathR = if (card.isStory) 330f else 270f
    private val labelY = breathY + breathR + (if (card.isStory) 170f else 120f)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.cormorant_italic) ?: Typeface.SERIF
        textSize = if (card.isStory) 72f else 60f
        textAlign = Paint.Align.CENTER
        color = card.palette.soft
        fontVariationSettings = "'wght' 500"
    }

    /** Estado del ensō en el instante [t] (segundos). */
    data class Enso(val x: Float, val y: Float, val r: Float, val painted: Float, val rotation: Float)

    fun enso(t: Float): Enso {
        val painted = card.ease(span(t, 0.3f, 2.0f))
        return when {
            t < MORPH_START -> {
                val s = breathScale(t)
                Enso(w / 2, breathY, breathR * s, painted, 0f)
            }
            t < MORPH_END -> {
                val u = damped(t - MORPH_START)
                val r0 = breathR * MIN_SCALE
                Enso(
                    x = w / 2 + sin(u.coerceIn(0f, 1f) * PI).toFloat() * w * 0.025f,
                    y = lerp(breathY, card.ensoCenterY, u),
                    r = lerp(r0, card.ensoCardRadius, u),
                    painted = 1f,
                    rotation = ROTATION * u,
                )
            }
            else -> {
                // Sigue respirando despacio mientras se lee la tarjeta
                val k = t - MORPH_END
                val ramp = span(t, MORPH_END, MORPH_END + 1.2f)
                val breath = 1f + 0.035f * ramp * sin(2 * PI * k / 6.0).toFloat()
                Enso(w / 2, card.ensoCenterY, card.ensoCardRadius * breath, 1f, ROTATION + 3f * k)
            }
        }
    }

    /** Tamaño del ensō durante la respiración (0,55 a 1). */
    private fun breathScale(t: Float): Float = when {
        t < INHALE_START -> MIN_SCALE
        t < EXHALE_START -> lerp(MIN_SCALE, 1f, smooth(span(t, INHALE_START, EXHALE_START)))
        else -> lerp(1f, MIN_SCALE, smooth(span(t, EXHALE_START, MORPH_START)))
    }

    /** Dibuja el cuadro del instante [t] en coordenadas de 1080 de ancho (el fondo ya debe estar pintado). */
    fun drawFrame(canvas: Canvas, t: Float) {
        val e = enso(t)
        card.drawEnso(canvas, e.x, e.y, e.r, card.strokeFor(e.r), e.painted, e.rotation)

        // «Inhala» y «Exhala»
        val inA = span(t, 0.6f, 1.4f) * (1f - span(t, EXHALE_START - 0.3f, EXHALE_START + 0.2f))
        val exA = span(t, EXHALE_START, EXHALE_START + 0.6f) * (1f - span(t, MORPH_START - 0.6f, MORPH_START + 0.2f))
        label(canvas, inhale, inA)
        label(canvas, exhale, exA)

        // La tarjeta
        card.drawTexts(canvas, card.ease(span(t, TEXT_START, TEXT_START + 1.0f)), card.ease(span(t, TEXT_START + 0.5f, TEXT_START + 1.6f)))
    }

    private fun label(canvas: Canvas, text: String, a: Float) {
        if (a <= 0f) return
        labelPaint.alpha = (255 * a).toInt()
        canvas.drawText(text, w / 2, labelY + (1f - a) * 14f, labelPaint)
    }

    companion object {
        const val DURATION = 20f
        const val FPS = 30
        const val INHALE_START = 0.5f
        const val EXHALE_START = 3.9f
        const val MORPH_START = 8.0f
        const val MORPH_END = 10.0f
        const val TEXT_START = 9.2f
        const val MIN_SCALE = 0.55f

        /** Giro leve del ensō mientras se desplaza (grados). */
        const val ROTATION = 40f

        // Resorte amortiguado: casi sin rebote y asentado en unos 2 s
        private const val DAMPING = 0.9f
        private const val OMEGA = 3.6f

        /** Momentos en que suena el cuenco (segundos) y su volumen. */
        val BOWLS = listOf(0.0f to 0.8f, MORPH_START to 0.6f)

        fun span(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
        fun smooth(u: Float) = u * u * (3 - 2 * u)
        /** Respuesta de un resorte amortiguado que parte quieto de 0 y se asienta en 1. */
        fun damped(s: Float): Float {
            if (s <= 0f) return 0f
            val wd = OMEGA * sqrt(1 - DAMPING * DAMPING)
            val decay = exp(-DAMPING * OMEGA * s)
            return 1f - decay * (cos(wd * s) + DAMPING / sqrt(1 - DAMPING * DAMPING) * sin(wd * s))
        }
    }
}
