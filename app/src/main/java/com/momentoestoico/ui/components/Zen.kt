package com.momentoestoico.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.momentoestoico.art.SumiBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Thought
import com.momentoestoico.R
import com.momentoestoico.ui.LocalLang
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.theme.ZenType

/** Fondo de toda la app: degradado suave como papel al amanecer (o piedra de noche). */
@Composable
fun ZenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val zen = LocalZen.current
    Box(
        modifier
            .fillMaxSize()
            .background(zen.sky),
        content = content,
    )
}

/** Trazo de ensō: un círculo abierto de pincel que se adelgaza al final. */
fun DrawScope.drawEnso(color: Color, strokeWidth: Float, gapDegrees: Float = 50f) =
    drawEnsoAt(center, (size.minDimension - strokeWidth) / 2f, color, strokeWidth, gapDegrees)

private val sumi = ThreadLocal.withInitial { SumiBrush() }

/** Ensō de pincel con centro y radio explícitos; [rotation] gira el trazo en grados y [painted] es cuánto está pintado. */
@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawEnsoAt(center: Offset, r: Float, color: Color, strokeWidth: Float, gapDegrees: Float = 50f, rotation: Float = 0f, painted: Float = 1f) {
    drawIntoCanvas { sumi.get()!!.draw(it.nativeCanvas, center.x, center.y, r, strokeWidth, color.toArgb(), painted, rotation) }
}

/** Ensō que respira despacio (escala) y gira casi imperceptiblemente. */
@Composable
fun BreathingEnso(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stroke: Dp = 7.dp,
    periodMs: Int = 5000,
) {
    val zen = LocalZen.current
    val transition = rememberInfiniteTransition(label = "enso")
    val breath by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(180_000, easing = LinearEasing)),
        label = "turn",
    )
    Canvas(
        modifier
            .size(size)
            .scale(breath)
            .rotate(turn),
    ) {
        drawEnso(zen.ink.copy(alpha = if (zen.dark) 0.85f else 0.9f), stroke.toPx())
    }
}

@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = LocalZen.current.muted) {
    Text(text.uppercase(), style = ZenType.caps, color = color, modifier = modifier, textAlign = TextAlign.Center)
}

/** Línea corta de acento, como un trazo de pincel horizontal. */
@Composable
fun InkLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(28.dp)
            .height(1.5.dp)
            .background(LocalZen.current.accent.copy(alpha = 0.8f)),
    )
}

/** Presentación de un pensamiento: título (si es práctica), texto, autor y fuente. */
@Composable
fun ThoughtView(thought: Thought, modifier: Modifier = Modifier, compact: Boolean = false) {
    val zen = LocalZen.current
    val lang = LocalLang.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (thought.kind == Kind.PRACTICE) {
            Caps(stringResource(R.string.practice_prefix, thought.title?.get(lang).orEmpty()), color = zen.accent)
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        }
        Text(
            thought.text[lang],
            style = if (compact) ZenType.quoteSmall else ZenType.quote,
            color = zen.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
        InkLine()
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(thought.author[lang], style = ZenType.author, color = zen.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Caps(thought.source[lang])
    }
}

/** Botón circular discreto con icono. */
@Composable
fun ZenIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalZen.current.inkSoft,
) {
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}
