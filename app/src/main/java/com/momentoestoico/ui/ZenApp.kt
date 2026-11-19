package com.momentoestoico.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.momentoestoico.R
import com.momentoestoico.data.AppLanguage
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Lang
import androidx.compose.ui.res.stringResource
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.data.Thought
import com.momentoestoico.share.ShareSheet
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.components.ZenBackground
import com.momentoestoico.ui.library.LibraryScreen
import com.momentoestoico.ui.moment.MomentScreen
import com.momentoestoico.ui.moments.MomentsScreen
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.today.TodayScreen

private val TABS = listOf(R.string.tab_today, R.string.tab_moments, R.string.tab_library)

/** Comillas según el idioma: «texto» en español, « texto » en francés. */
fun quoted(text: String, lang: Lang): String = when (lang) {
    Lang.ES -> "«$text»"
    Lang.FR -> "«\u00a0$text\u00a0»"
}

@Composable
fun ZenApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = (state?.language ?: AppLanguage.SYSTEM).resolve()
    LocalizedApp(lang) { ZenAppContent(vm, lang) }
}

@Composable
private fun ZenAppContent(vm: MainViewModel, lang: Lang) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val moment by vm.moment.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sharing by remember { mutableStateOf<Thought?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshPermissions()
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    // La primera vez se piden las notificaciones: sin ellas no hay momentos
    LaunchedEffect(state?.notificationsAsked) {
        val s = state ?: return@LaunchedEffect
        if (!s.notificationsAsked && !permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vm.markNotificationsAsked()
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }

    val s = state
    ZenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "tab",
            ) { current ->
                if (s == null) {
                    Box(Modifier.fillMaxSize())
                    return@AnimatedContent
                }
                when (current) {
                    0 -> {
                        val thought = StoicLibrary.get(s.currentId)
                        TodayScreen(
                            thought = thought,
                            favorite = thought?.id in s.favorites,
                            next = vm.nextOverall(s.reminders),
                            onAnother = vm::another,
                            onFavorite = { thought?.let { vm.toggleFavorite(it.id) } },
                            onShare = { thought?.let { sharing = it } },
                            onOpenMoments = { tab = 1 },
                        )
                    }
                    1 -> MomentsScreen(
                        reminders = s.reminders,
                        permissions = permissions,
                        onRequestNotifications = ::requestNotifications,
                        onRequestExact = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        },
                        onToggle = { r, on -> vm.setEnabled(r.id, on) },
                        onSave = vm::saveReminder,
                        onDelete = { vm.deleteReminder(it.id) },
                        onTest = { vm.testNow(s.reminders.firstOrNull { it.enabled }, context) },
                        language = s.language,
                        onLanguage = vm::setLanguage,
                    )
                    else -> LibraryScreen(
                        favorites = s.favorites,
                        onOpen = { vm.openMoment(it, breathe = false) },
                        onFavorite = { vm.toggleFavorite(it.id) },
                    )
                }
            }
            BottomTabs(tab) { tab = it }
        }
    }

    AnimatedContent(
        targetState = moment,
        transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) },
        label = "moment",
    ) { m ->
        if (m != null) {
            MomentScreen(
                thought = m.thought,
                breathe = m.breathe,
                favorite = m.thought.id in s?.favorites.orEmpty(),
                onFavorite = { vm.toggleFavorite(m.thought.id) },
                onShare = { sharing = m.thought },
                onClose = vm::closeMoment,
            )
        }
    }

    AnimatedContent(
        targetState = sharing,
        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
        label = "share",
    ) { t ->
        if (t != null) {
            ShareSheet(
                thought = t,
                lang = lang,
                sound = s?.shareSound ?: true,
                onSound = vm::setShareSound,
                onClose = { sharing = null },
            )
        }
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val zen = LocalZen.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TABS.forEachIndexed { i, labelRes ->
            val label = stringResource(labelRes)
            val on = i == selected
            Column(
                Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Caps(label, color = if (on) zen.ink else zen.muted)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (on) zen.accent else Color.Transparent),
                )
            }
        }
    }
}
