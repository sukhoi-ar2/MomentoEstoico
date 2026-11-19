package com.momentoestoico.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.momentoestoico.R
import com.momentoestoico.art.SumiBrush
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Lang
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.localized
import com.momentoestoico.ui.quoted
import kotlin.random.Random

/** Proporción de la imagen: post cuadrado o historia vertical. */
enum class CardFormat(val width: Int, val height: Int) {
    SQUARE(1080, 1080),
    STORY(1080, 1920),
    ;

    val ratio: Float get() = width.toFloat() / height
}

/** Estilos de color de la tarjeta. */
enum class CardStyle(val top: Int, val bottom: Int, val ink: Int, val soft: Int, val accent: Int) {
    PAPER(0xFFF6F2EA.toInt(), 0xFFE6DCCB.toInt(), 0xFF2F2B27.toInt(), 0xFF6E655B.toInt(), 0xFF7C8B6F.toInt()),
    NIGHT(0xFF15181B.toInt(), 0xFF262B30.toInt(), 0xFFEAE3D6.toInt(), 0xFFA39B8E.toInt(), 0xFFB9A77E.toInt()),
    SAGE(0xFF8A9A7C.toInt(), 0xFF5F6E55.toInt(), 0xFFF7F3EA.toInt(), 0xFFE3DED0.toInt(), 0xFFF1E6C8.toInt()),
}

/**
 * Dibuja el pensamiento como una tarjeta para redes, con el mismo código para la vista previa
 * animada y para la imagen final. Todo se dibuja en un lienzo de 1080 de ancho y se escala.
 * [progress] 0→1: el ensō se pinta de un trazo y después aparecen el texto y el autor.
 */
class QuoteCard(
    context: Context,
    private val thought: Thought,
    private val lang: Lang,
    val palette: CardStyle,
    val format: CardFormat,
) {
    private val texts = context.localized(lang)
    private val regular: Typeface = ResourcesCompat.getFont(context, R.font.cormorant) ?: Typeface.SERIF
    private val italic: Typeface = ResourcesCompat.getFont(context, R.font.cormorant_italic) ?: Typeface.SERIF
    private val w = format.width.toFloat()
    private val h = format.height.toFloat()
    private val story = format == CardFormat.STORY

    private val ensoRadius = if (story) 150f else 96f
    private val ensoY = if (story) h * 0.2f else 190f
    private val margin = if (story) 120f else 110f
    private val textWidth = (w - 2 * margin).toInt()
    private val footerY = h - (if (story) 150f else 80f)

    private val grain: List<Triple<Float, Float, Float>> = Random(thought.id.hashCode()).let { r ->
        List(1600) { Triple(r.nextFloat() * w, r.nextFloat() * h, 0.6f + r.nextFloat() * 1.8f) }
    }

    private fun paint(tf: Typeface, size: Float, color: Int, weight: Int, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size
        this.color = color
        letterSpacing = spacing
        fontVariationSettings = "'wght' $weight"
    }

    private fun layout(text: String, p: TextPaint, width: Int = textWidth, spacingMult: Float = 1.12f): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, spacingMult)
            .setIncludePad(false)
            .build()

    private val titleLayout: StaticLayout? = thought.title?.takeIf { thought.kind == Kind.PRACTICE }?.let {
        layout(it[lang].uppercase(), paint(regular, 30f, palette.accent, 600, 0.22f))
    }

    private val body = thought.text[lang].let { if (thought.kind == Kind.QUOTE) quoted(it, lang) else it }

    private val authorLayout = layout(thought.author[lang].uppercase(), paint(regular, 32f, palette.ink, 600, 0.2f))
    private val sourceLayout = thought.source[lang].takeIf { it.isNotBlank() }?.let { layout(it, paint(italic, 32f, palette.soft, 400)) }

    /** Ajusta el tamaño de letra de la cita para que entre en el espacio disponible. */
    private val quoteLayout: StaticLayout = run {
        val top = ensoY + ensoRadius + (if (story) 110f else 70f)
        val available = footerY - 90f - top
        val fixed = (titleLayout?.height?.plus(36) ?: 0) + 50 + 4 + 40 + authorLayout.height + (sourceLayout?.height?.plus(8) ?: 0)
        var size = if (story) 76f else 62f
        var l: StaticLayout
        while (true) {
            val weight = if (thought.kind == Kind.QUOTE) 500 else 450
            l = layout(body, paint(regular, size, palette.ink, weight), spacingMult = 1.14f)
            if (l.height + fixed <= available || size <= 30f) break
            size -= 2f
        }
        l
    }

    private val blockTop: Float = run {
        val top = ensoY + ensoRadius + (if (story) 110f else 70f)
        val available = footerY - 90f - top
        val block = (titleLayout?.height?.plus(36) ?: 0) + quoteLayout.height + 50 + 4 + 40 + authorLayout.height + (sourceLayout?.height?.plus(8) ?: 0)
        top + ((available - block) / 2f).coerceAtLeast(0f)
    }

    private val brand = texts.getString(R.string.brand).uppercase()
    private val brandPaint = paint(regular, 26f, palette.soft, 600, 0.32f).apply { textAlign = Paint.Align.CENTER }

    fun ease(t: Float) = (1f - (1f - t) * (1f - t) * (1f - t))
    fun phase(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

    // ── Geometría pública (la usa también el video) ──
    val width: Float get() = w
    val height: Float get() = h
    val isStory: Boolean get() = story
    val ensoCenterY: Float get() = ensoY
    val ensoCardRadius: Float get() = ensoRadius
    val ensoCardStroke: Float get() = if (story) 30f else 22f

    /** Grosor del trazo para un ensō de radio [r]: crece un poco con el tamaño. */
    fun strokeFor(r: Float): Float = ensoCardStroke * (0.7f + 0.3f * r / ensoRadius)

    /** Dibuja la tarjeta en [canvas] con ancho [width] (el alto sale de la proporción). */
    fun draw(canvas: Canvas, width: Float, progress: Float) {
        val s = width / w
        canvas.save()
        canvas.scale(s, s)
        drawBackground(canvas)
        // Ensō: un solo trazo de pincel
        drawEnso(canvas, w / 2, ensoY, ensoRadius, ensoCardStroke, ease(phase(progress, 0f, 0.45f)))
        drawTexts(canvas, ease(phase(progress, 0.38f, 0.78f)), ease(phase(progress, 0.62f, 1f)))
        canvas.restore()
    }

    /** Fondo con degradado y grano de papel, en coordenadas de 1080 de ancho. */
    fun drawBackground(canvas: Canvas) {
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w * 0.3f, h, palette.top, palette.bottom, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, w, h, bg)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.ink; alpha = 10 }
        grain.forEach { (x, y, r) -> canvas.drawCircle(x, y, r, dot) }
    }

    /** El fondo ya pintado a [widthPx] píxeles de ancho, para no redibujarlo en cada cuadro. */
    fun backgroundBitmap(widthPx: Int): Bitmap {
        val s = widthPx / w
        val bmp = Bitmap.createBitmap(widthPx, (h * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        drawBackground(c)
        return bmp
    }

    /** Título, cita, línea, autor, obra y firma. [tQuote] y [tAuthor] van de 0 (ocultos) a 1. */
    fun drawTexts(canvas: Canvas, tQuote: Float, tAuthor: Float) {
        var y = blockTop
        titleLayout?.let {
            drawLayout(canvas, it, y + (1 - tQuote) * 24f, tQuote)
            y += it.height + 36
        }
        drawLayout(canvas, quoteLayout, y + (1 - tQuote) * 24f, tQuote)
        y += quoteLayout.height + 50
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; alpha = (255 * tAuthor).toInt(); strokeWidth = 3f }
        val half = 40f * tAuthor
        if (tAuthor > 0f) canvas.drawLine(w / 2 - half, y, w / 2 + half, y, line)
        y += 4 + 40
        drawLayout(canvas, authorLayout, y + (1 - tAuthor) * 16f, tAuthor)
        y += authorLayout.height + 8
        sourceLayout?.let { drawLayout(canvas, it, y + (1 - tAuthor) * 16f, tAuthor) }

        brandPaint.alpha = (255 * 0.75f * tAuthor).toInt()
        canvas.drawText(brand, w / 2, footerY, brandPaint)
    }

    private fun drawLayout(canvas: Canvas, l: StaticLayout, y: Float, alpha: Float) {
        if (alpha <= 0f) return
        val base = l.paint.alpha
        canvas.save()
        canvas.translate(margin, y)
        l.paint.alpha = (255 * alpha).toInt()
        l.draw(canvas)
        l.paint.alpha = base
        canvas.restore()
    }

    private val brush = SumiBrush()

    /**
     * Ensō de pincel con centro ([cx], [cy]) y radio [radius]. [t] es cuánto del trazo está
     * pintado (0 a 1), [rotation] gira el círculo en grados y [alpha] lo atenúa.
     */
    fun drawEnso(canvas: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float, t: Float, rotation: Float = 0f, alpha: Float = 1f) =
        brush.draw(canvas, cx, cy, radius, stroke, palette.ink, t, rotation, alpha)

    /** La imagen final, a tamaño completo. */
    fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), w, 1f)
        return bmp
    }
}
