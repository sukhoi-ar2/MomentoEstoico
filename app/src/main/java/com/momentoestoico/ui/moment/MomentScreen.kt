package com.momentoestoico.ui.moment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.momentoestoico.ui.components.drawEnsoAt
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.momentoestoico.R
import androidx.compose.ui.unit.dp
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.components.ThoughtView
import com.momentoestoico.ui.components.ZenBackground
import com.momentoestoico.ui.components.ZenIconButton
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.theme.ZenType

private const val INHALE_MS = 4000
private const val EXHALE_MS = 6000
private const val BREATHS = 2
/** Cuánto tarda, más o menos, el ensō en asentarse en su lugar. */
private const val FLIGHT_MS = 2000
private val BREATH_SIZE = 240.dp
private val BREATH_STROKE = 9.dp
private val ICON_SIZE = 72.dp
private val ICON_STROKE = 5.dp

/**
 * El momento estoico a pantalla completa. Si [breathe], antes del pensamiento guía dos
 * respiraciones lentas (inhala 4 s, exhala 6 s). El ensō se pinta de un trazo al empezar, crece
 * y mengua; al terminar, el mismo ensō se desliza y se achica hasta su lugar sobre el pensamiento,
 * frenando como un resorte amortiguado.
 */
@Composable
fun MomentScreen(
    thought: Thought,
    breathe: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val zen = LocalZen.current
    var breathing by remember(thought.id) { mutableStateOf(breathe) }
    var phase by remember(thought.id) { mutableStateOf(R.string.inhale) }
    val scale = remember(thought.id) { Animatable(0.55f) }
    // 0 = el ensō está en la respiración, 1 = ya llegó a su lugar sobre el pensamiento
    val flight = remember(thought.id) { Animatable(if (breathe) 0f else 1f) }
    // Cuánto del trazo está pintado: al empezar la respiración el pincel dibuja el círculo
    val paint = remember(thought.id) { Animatable(if (breathe) 0f else 1f) }

    // Dónde está cada lugar del ensō, relativo a la pantalla
    var origin by remember { mutableStateOf(Offset.Zero) }
    var from by remember(thought.id) { mutableStateOf<Rect?>(null) }
    var to by remember(thought.id) { mutableStateOf<Rect?>(null) }

    BackHandler(onBack = onClose)

    LaunchedEffect(thought.id, breathing) {
        if (!breathing) {
            if (paint.value < 1f) launch { paint.animateTo(1f, tween(500)) }
            if (flight.value < 1f) flight.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 13f))
            return@LaunchedEffect
        }
        launch { paint.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }
        repeat(BREATHS) {
            phase = R.string.inhale
            scale.animateTo(1f, tween(INHALE_MS, easing = FastOutSlowInEasing))
            phase = R.string.exhale
            scale.animateTo(0.55f, tween(EXHALE_MS, easing = FastOutSlowInEasing))
        }
        breathing = false
    }

    // Respiración lenta y giro casi imperceptible, una vez que el ensō llegó
    val ambient = rememberInfiniteTransition(label = "ambient")
    val idle by ambient.animateFloat(0.9f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "idle")
    val turn by ambient.animateFloat(0f, 360f, infiniteRepeatable(tween(180_000, easing = LinearEasing)), label = "turn")

    ZenBackground(Modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Caps(stringResource(R.string.brand), color = zen.accent, modifier = Modifier.weight(1f).padding(start = 48.dp))
                TextButton(onClick = { if (breathing) breathing = false else onClose() }) {
                    Text(stringResource(if (breathing) R.string.skip else R.string.close).uppercase(), style = ZenType.caps, color = zen.muted)
                }
            }

            AnimatedContent(
                targetState = breathing,
                // El texto aparece cuando el ensō ya está llegando
                transitionSpec = { fadeIn(tween(1400, delayMillis = FLIGHT_MS / 2)) togetherWith fadeOut(tween(700)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "moment",
            ) { isBreathing ->
                if (isBreathing) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Lugar del ensō grande (se dibuja encima, en la capa de abajo)
                        Spacer(Modifier.size(BREATH_SIZE).onGloballyPositioned { from = it.boundsInRoot() })
                        Spacer(Modifier.height(36.dp))
                        AnimatedContent(
                            targetState = phase,
                            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(600)) },
                            label = "phase",
                        ) { p ->
                            Text(stringResource(p), style = ZenType.title, color = zen.inkSoft, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        // Lugar final del ensō: lo sigue aunque se desplace el texto
                        Spacer(Modifier.size(ICON_SIZE).onGloballyPositioned { to = it.boundsInRoot() })
                        Spacer(Modifier.height(36.dp))
                        ThoughtView(thought, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            AnimatedVisibility(!breathing, enter = fadeIn(tween(1200, delayMillis = FLIGHT_MS)), exit = fadeOut()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    ZenIconButton(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        stringResource(if (favorite) R.string.fav_remove else R.string.fav_add),
                        onFavorite,
                        tint = if (favorite) zen.accent else zen.inkSoft,
                    )
                    ZenIconButton(Icons.Filled.Share, stringResource(R.string.share), onShare)
                }
            }
        }

        // El ensō vive en su propia capa, por encima de las dos pantallas, para poder viajar entre ellas
        Canvas(Modifier.fillMaxSize()) {
            val u = flight.value
            val start = from
            val end = to
            val bigStroke = BREATH_STROKE.toPx()
            val smallStroke = ICON_STROKE.toPx()
            fun state(v: Float): Triple<Offset, Float, Float>? {
                val ambientScale = 1f + (idle - 1f) * v
                val endR = end?.let { (it.width - smallStroke) / 2f * ambientScale }
                val startR = start?.let { (it.width - bigStroke) / 2f * scale.value }
                return when {
                    start == null || startR == null -> end?.let { Triple(it.center - origin, endR!!, smallStroke) }
                    end == null || endR == null || v <= 0f -> Triple(start.center - origin, startR, bigStroke)
                    else -> {
                        val a = start.center - origin
                        val b = end.center - origin
                        // Apenas una curva hacia un costado, no una línea recta
                        val bow = sin(v.coerceIn(0f, 1f) * PI).toFloat() * 14.dp.toPx()
                        Triple(Offset(lerp(a.x, b.x, v) + bow, lerp(a.y, b.y, v)), lerp(startR, endR, v), lerp(bigStroke, smallStroke, v))
                    }
                }
            }
            val ink = zen.ink.copy(alpha = if (zen.dark) 0.85f else 0.9f)
            val (c, r, sw) = state(u) ?: return@Canvas
            // Un giro leve mientras se desliza; después, el giro lentísimo de siempre
            drawEnsoAt(c, r, ink, sw, rotation = 40f * u + turn * u.coerceIn(0f, 1f), painted = paint.value)
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
