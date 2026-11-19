package com.momentoestoico.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import com.momentoestoico.R
import com.momentoestoico.data.Reminder
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.components.BreathingEnso
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.components.ThoughtView
import com.momentoestoico.ui.LocalLang
import com.momentoestoico.ui.components.ZenIconButton
import com.momentoestoico.data.displayLabel
import com.momentoestoico.ui.theme.LocalZen
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** «hoy a las 08:00», «demain à 08:00», «el lunes a las 22:00»… */
fun whenText(context: Context, at: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val locale = context.resources.configuration.locales[0]
    val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
    return when (days) {
        0L -> context.getString(R.string.when_today, time)
        1L -> context.getString(R.string.when_tomorrow, time)
        else -> context.getString(R.string.when_day, at.format(DateTimeFormatter.ofPattern("EEEE", locale)), time)
    }
}

@Composable
fun TodayScreen(
    thought: Thought?,
    favorite: Boolean,
    next: Pair<Reminder, ZonedDateTime>?,
    onAnother: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenMoments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zen = LocalZen.current
    val context = LocalContext.current
    val pattern = stringResource(R.string.date_pattern)
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, LocalLang.current.locale))
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Caps(stringResource(R.string.brand), color = zen.accent)
        Spacer(Modifier.height(6.dp))
        Caps(date)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BreathingEnso(size = 92.dp, stroke = 6.dp)
                Spacer(Modifier.height(36.dp))
                AnimatedContent(
                    targetState = thought,
                    transitionSpec = { fadeIn(tween(900, delayMillis = 300)) togetherWith fadeOut(tween(400)) },
                    label = "thought",
                ) { t ->
                    if (t != null) ThoughtView(t, Modifier.fillMaxWidth())
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ZenIconButton(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(if (favorite) R.string.fav_remove else R.string.fav_add),
                onFavorite,
                tint = if (favorite) zen.accent else zen.inkSoft,
            )
            ZenIconButton(Icons.Filled.Refresh, stringResource(R.string.another), onAnother)
            ZenIconButton(Icons.Filled.Share, stringResource(R.string.share), onShare)
        }
        Spacer(Modifier.height(10.dp))
        Caps(
            next?.let { (r, at) ->
                val label = r.displayLabel(context)
                stringResource(R.string.next_moment, whenText(context, at)) + if (label.isNotBlank()) " · $label" else ""
            } ?: stringResource(R.string.no_moments),
            modifier = Modifier
                .clickable(onClick = onOpenMoments)
                .padding(8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
