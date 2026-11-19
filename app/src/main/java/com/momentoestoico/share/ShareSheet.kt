package com.momentoestoico.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.momentoestoico.R
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Lang
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.components.ZenBackground
import com.momentoestoico.ui.components.ZenIconButton
import com.momentoestoico.ui.quoted
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.theme.ZenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ANIMATION_MS = 3200

/** Qué se comparte. */
enum class ShareKind { VIDEO, IMAGE }

/** Graba el video del momento (respiración + tarjeta, con sonido) y abre el selector de apps. */
suspend fun shareVideo(context: Context, film: MomentFilm, sound: Boolean, t: Thought, lang: Lang, onProgress: (Float) -> Unit) {
    val file = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "momento-${t.id}-${film.card.format.name.lowercase()}.mp4")
        VideoEncoder.encode(context, film, sound, f, onProgress)
        f
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, shareText(t, lang))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_chooser)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

/** Texto plano del pensamiento (para "solo texto" y como pie de la imagen). */
fun shareText(t: Thought, lang: Lang): String = buildString {
    if (t.kind == Kind.PRACTICE && t.title != null) append(t.title[lang]).append("\n\n")
    append(if (t.kind == Kind.QUOTE) quoted(t.text[lang], lang) else t.text[lang])
    append("\n\n— ${t.author[lang]}")
    if (t.source[lang].isNotBlank()) append(", ${t.source[lang]}")
}

fun shareTextOnly(context: Context, t: Thought, lang: Lang) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(t, lang))
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser)))
}

/** Guarda la tarjeta en la caché y abre el selector de apps para compartirla. */
suspend fun shareImage(context: Context, card: QuoteCard, t: Thought, lang: Lang) {
    val file = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "momento-${t.id}-${card.format.name.lowercase()}.png")
        val bmp = card.render()
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        f
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, shareText(t, lang))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_chooser)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

private fun playBowl(context: Context) {
    runCatching {
        MediaPlayer.create(context, R.raw.cuenco)?.apply {
            setVolume(0.55f, 0.55f)
            setOnCompletionListener { it.release() }
            start()
        }
    }
}

/**
 * Pantalla para compartir: vista previa animada de la tarjeta (el ensō se pinta y aparece el texto,
 * con el sonido del cuenco), formato post o historia y tres estilos de color.
 */
@Composable
fun ShareSheet(thought: Thought, lang: Lang, sound: Boolean, onSound: (Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val zen = LocalZen.current
    val scope = rememberCoroutineScope()
    var format by rememberSaveable { mutableStateOf(CardFormat.SQUARE) }
    var style by rememberSaveable { mutableStateOf(if (zen.dark) CardStyle.NIGHT else CardStyle.PAPER) }
    var kind by rememberSaveable { mutableStateOf(ShareKind.VIDEO) }
    var busy by remember { mutableStateOf(false) }
    var making by remember { mutableFloatStateOf(-1f) }
    var replay by remember { mutableIntStateOf(0) }
    val card = remember(thought.id, lang, style, format) { QuoteCard(context, thought, lang, style, format) }
    val film = remember(card) { MomentFilm(context, card, lang) }
    val progress = remember { Animatable(0f) }

    BackHandler(onBack = onClose)

    // Vista previa: la tarjeta (3,2 s) o el video completo en tiempo real (20 s), con el cuenco
    LaunchedEffect(card, replay, kind) {
        progress.snapTo(0f)
        if (kind == ShareKind.IMAGE) {
            if (sound) playBowl(context)
            progress.animateTo(1f, tween(ANIMATION_MS, easing = LinearEasing))
        } else {
            launch {
                var elapsed = 0L
                MomentFilm.BOWLS.forEach { (at, _) ->
                    val ms = (at * 1000).toLong()
                    delay(ms - elapsed)
                    elapsed = ms
                    if (sound) playBowl(context)
                }
            }
            progress.animateTo(1f, tween((MomentFilm.DURATION * 1000).toInt(), easing = LinearEasing))
        }
    }

    ZenBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Caps(stringResource(R.string.share_title), color = zen.accent, modifier = Modifier.weight(1f).padding(start = 8.dp))
                ZenIconButton(Icons.Filled.Close, stringResource(R.string.close), onClose)
            }

            // Vista previa: tocarla repite la animación
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .aspectRatio(format.ratio, matchHeightConstraintsFirst = format == CardFormat.STORY)
                        .shadow(14.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { replay++ },
                ) {
                    val p = progress.value
                    drawIntoCanvas {
                        if (kind == ShareKind.IMAGE) {
                            card.draw(it.nativeCanvas, size.width, p)
                        } else {
                            val c = it.nativeCanvas
                            val s = size.width / card.width
                            c.save()
                            c.scale(s, s)
                            card.drawBackground(c)
                            film.drawFrame(c, p * MomentFilm.DURATION)
                            c.restore()
                        }
                    }
                }
            }
            Caps(stringResource(R.string.share_replay), color = zen.muted)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(R.string.share_kind_video), kind == ShareKind.VIDEO) { kind = ShareKind.VIDEO }
                Chip(stringResource(R.string.share_kind_image), kind == ShareKind.IMAGE) { kind = ShareKind.IMAGE }
                Spacer(Modifier.width(6.dp))
                Chip(
                    (if (sound) "♪  " else "—  ") + stringResource(R.string.share_sound),
                    sound,
                ) { onSound(!sound) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(stringResource(R.string.share_format_square), format == CardFormat.SQUARE) { format = CardFormat.SQUARE }
                Chip(stringResource(R.string.share_format_story), format == CardFormat.STORY) { format = CardFormat.STORY }
                Spacer(Modifier.width(6.dp))
                CardStyle.entries.forEach { st ->
                    Swatch(st, st == style) { style = st }
                }
            }
            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(zen.ink)
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            try {
                                if (kind == ShareKind.IMAGE) {
                                    shareImage(context, card, thought, lang)
                                } else {
                                    making = 0f
                                    shareVideo(context, film, sound, thought, lang) { making = it }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                Toast.makeText(context, R.string.share_video_error, Toast.LENGTH_LONG).show()
                            } finally {
                                busy = false
                                making = -1f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        making >= 0f -> stringResource(R.string.share_making_video, (making * 100).toInt())
                        busy -> stringResource(R.string.share_preparing)
                        kind == ShareKind.VIDEO -> stringResource(R.string.share_video)
                        else -> stringResource(R.string.share_image)
                    }.uppercase(),
                    style = ZenType.caps,
                    color = if (zen.dark) Color(0xFF15181B) else Color(0xFFF6F2EA),
                )
            }
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .clip(CircleShape)
                    .clickable { shareTextOnly(context, thought, lang) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Caps(stringResource(R.string.share_text_only), color = zen.muted)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val zen = LocalZen.current
    Box(
        Modifier
            .clip(CircleShape)
            .border(1.dp, if (selected) zen.ink else zen.line, CircleShape)
            .background(if (selected) zen.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Caps(label, color = if (selected) zen.ink else zen.muted)
    }
}

@Composable
private fun Swatch(style: CardStyle, selected: Boolean, onClick: () -> Unit) {
    val zen = LocalZen.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) zen.ink else zen.line, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color(style.top))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(style.ink)))
    }
}
