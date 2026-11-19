package com.momentoestoico.art

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ensō pintado como con un pincel japonés (sumi-e). Lo usan la app, la imagen y el video.
 *
 * - El trazo entra un poco más ancho, donde el pincel se apoya.
 * - Corre con el grosor apenas irregular de la mano, bordes ásperos y vetas de las cerdas.
 * - En el último tercio se seca y se abre en cerdas sueltas (kasure) que se cortan antes de cerrar el círculo.
 *
 * La forma es siempre la misma (semilla fija), así el ensō es reconocible en todas partes.
 * Cada instancia tiene sus propios Paint y Path: se puede usar una por hilo.
 */
class SumiBrush {
    private val body = Path()
    private val strand = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val xs = FloatArray(N + 1)
    private val ys = FloatArray(N + 1)
    private val cs = FloatArray(N + 1)
    private val sn = FloatArray(N + 1)

    /**
     * Pinta el ensō con centro ([cx], [cy]), radio [radius] y grosor base [stroke].
     * [painted] es la parte del trazo ya pintada (0 a 1), [rotation] gira el círculo en grados
     * y [alpha] atenúa toda la tinta. [color] es un ARGB.
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float, color: Int, painted: Float = 1f, rotation: Float = 0f, alpha: Float = 1f) {
        val last = (N * painted.coerceIn(0f, 1f)).toInt()
        if (last < 2 || alpha <= 0f) return
        val baseAlpha = (color ushr 24) / 255f * alpha
        val rgb = color and 0xFFFFFF

        for (i in 0..last) {
            val a = Math.toRadians((START + rotation + i * SWEEP / N).toDouble())
            cs[i] = cos(a).toFloat()
            sn[i] = sin(a).toFloat()
            val r = radius * RADIUS[i]
            xs[i] = cx + r * cs[i]
            ys[i] = cy + r * sn[i]
        }

        // Cuerpo del trazo: borde exterior de ida e interior de vuelta, los dos un poco ásperos
        body.rewind()
        for (i in 0..last) {
            val h = stroke * WIDTH[i] * CORE[i] / 2f * EDGE_OUT[i]
            val x = xs[i] + h * cs[i]
            val y = ys[i] + h * sn[i]
            if (i == 0) body.moveTo(x, y) else body.lineTo(x, y)
        }
        for (i in last downTo 0) {
            val h = stroke * WIDTH[i] * CORE[i] / 2f * EDGE_IN[i]
            body.lineTo(xs[i] - h * cs[i], ys[i] - h * sn[i])
        }
        body.close()
        // Puntas redondeadas: donde se apoya el pincel y donde va pintando
        body.addCircle(xs[0], ys[0], stroke * WIDTH[0] * CORE[0] / 2f * 0.92f, Path.Direction.CW)
        body.addCircle(xs[last], ys[last], stroke * WIDTH[last] * CORE[last] / 2f, Path.Direction.CW)
        fill.color = rgb or ((0.9f * baseAlpha * 255).toInt() shl 24)
        canvas.drawPath(body, fill)

        // Cerdas: vetas tenues dentro de la tinta húmeda y líneas sueltas que se cortan donde se seca
        val dryFrom = (DRY_FROM * N).toInt()
        val wetInk = rgb or ((0.32f * baseAlpha * 255).toInt() shl 24)
        val dryInk = rgb or ((0.85f * baseAlpha * 255).toInt() shl 24)
        for (k in 0 until STRANDS) {
            val end = minOf(last, (STRAND_END[k] * N).toInt())
            var i = 0
            while (i <= end) {
                if (!MASK[k][i]) {
                    i++
                    continue
                }
                strand.rewind()
                val w0 = stroke * WIDTH[i] * STRAND_WIDTH[k]
                val wet = i < dryFrom
                var n = 0
                // Cada tramo es húmedo o seco entero: se corta donde cambia
                while (i <= end && MASK[k][i] && (i < dryFrom) == wet) {
                    val off = stroke * WIDTH[i] * STRAND_OFFSET[k]
                    val x = xs[i] + off * cs[i]
                    val y = ys[i] + off * sn[i]
                    if (n == 0) strand.moveTo(x, y) else strand.lineTo(x, y)
                    n++
                    i++
                }
                if (n > 1) {
                    line.color = if (wet) wetInk else dryInk
                    line.strokeWidth = w0.coerceAtLeast(0.6f)
                    canvas.drawPath(strand, line)
                }
            }
        }
    }

    private companion object {
        const val N = 220
        const val GAP = 40f
        const val SWEEP = 360f - GAP
        const val START = -62f
        const val STRANDS = 13
        const val DRY_FROM = 0.56f

        val RADIUS = FloatArray(N + 1)
        val WIDTH = FloatArray(N + 1)

        /** Parte del grosor que sigue siendo tinta plena (el resto, en la zona seca, son cerdas). */
        val CORE = FloatArray(N + 1)
        val STRAND_OFFSET = FloatArray(STRANDS)
        val STRAND_WIDTH = FloatArray(STRANDS)
        val STRAND_END = FloatArray(STRANDS)
        val MASK = Array(STRANDS) { BooleanArray(N + 1) }

        val EDGE_OUT = FloatArray(N + 1)
        val EDGE_IN = FloatArray(N + 1)

        fun smooth(u: Float) = u * u * (3 - 2 * u)

        /** Ruido suave: interpola valores al azar puestos cada [step] muestras. */
        fun noise(knots: FloatArray, i: Int, step: Float): Float {
            val x = i / step
            val a = knots[x.toInt()]
            val b = knots[x.toInt() + 1]
            return a + (b - a) * smooth(x - x.toInt())
        }

        init {
            val rnd = Random(7)
            val tau = (2 * PI).toFloat()
            val edgeOut = FloatArray(N / 3 + 2) { rnd.nextFloat() }
            val edgeIn = FloatArray(N / 3 + 2) { rnd.nextFloat() }
            for (i in 0..N) {
                val f = i / N.toFloat()
                // La mano no es un compás: el radio ondula apenas y al final se abre hacia afuera
                RADIUS[i] = 1f + 0.011f * sin(tau * 2.3f * f + 0.7f) + 0.006f * sin(tau * 6.1f * f + 2f) + 0.04f * f.pow(3)
                // Grosor: charco al apoyar, cuerpo parejo y afinamiento al levantar el pincel
                var w = when {
                    f < 0.05f -> 0.92f + 0.26f * smooth(f / 0.05f)
                    f < 0.22f -> 1.18f - 0.16f * smooth((f - 0.05f) / 0.17f)
                    else -> 1.02f + 0.06f * sin(tau * 3.7f * f)
                }
                val taper = ((f - 0.6f) / 0.4f).coerceIn(0f, 1f)
                w *= 1f - 0.86f * taper.pow(1.25f)
                WIDTH[i] = w
                val dry = ((f - DRY_FROM - 0.04f) / (1f - DRY_FROM)).coerceIn(0f, 1f)
                CORE[i] = 1f - 0.62f * smooth(dry)
                // Bordes ásperos: más cuanto más seco está el pincel
                val rough = 0.05f + 0.09f * ((f - 0.4f) / 0.6f).coerceIn(0f, 1f)
                EDGE_OUT[i] = 1f + rough * (noise(edgeOut, i, 3f) - 0.5f) * 2f
                EDGE_IN[i] = 1f + rough * (noise(edgeIn, i, 3f) - 0.5f) * 2f
            }
            for (k in 0 until STRANDS) {
                STRAND_OFFSET[k] = -0.44f + 0.88f * k / (STRANDS - 1) + (rnd.nextFloat() - 0.5f) * 0.04f
                STRAND_WIDTH[k] = 0.04f + rnd.nextFloat() * 0.05f
                // Las cerdas de los bordes se secan antes
                val edge = kotlin.math.abs(STRAND_OFFSET[k]) * 2f
                STRAND_END[k] = 1f - 0.1f * edge - rnd.nextFloat() * 0.05f
                // Ruido suave por cerda: decide dónde la tinta se corta
                val knots = FloatArray(N / 8 + 2) { rnd.nextFloat() }
                for (i in 0..N) {
                    val f = i / N.toFloat()
                    // En la parte húmeda, vetas sueltas; en la seca, cada vez más cortes
                    val threshold = if (f < DRY_FROM) 0.55f else 0.12f + 0.72f * ((f - DRY_FROM) / (1f - DRY_FROM)) + 0.12f * edge
                    MASK[k][i] = f > 0.03f && noise(knots, i, 8f) > threshold
                }
            }
        }
    }
}
