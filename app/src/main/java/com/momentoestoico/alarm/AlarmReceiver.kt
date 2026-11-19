package com.momentoestoico.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.momentoestoico.App
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.data.displayLabel
import com.momentoestoico.ui.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Suena un momento: elige un pensamiento, lo notifica y programa el siguiente. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as App).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MOMENT -> {
                        val id = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
                        val state = container.repository.current()
                        val reminder = state.reminders.firstOrNull { it.id == id } ?: return@launch
                        if (!reminder.enabled) return@launch
                        val lang = state.language.resolve()
                        val thought = container.repository.pickNext(reminder.content)
                        container.notifier.show(thought, reminder.displayLabel(context.localized(lang)), lang)
                        // Siguiente aviso: después de la hora prevista (o de ahora, si llegó tarde)
                        val scheduled = MomentScheduler.atZoned(intent.getLongExtra(EXTRA_AT, 0))
                        val now = ZonedDateTime.now()
                        container.scheduler.schedule(reminder, if (scheduled.isAfter(now)) scheduled else now)
                    }
                    ACTION_SNOOZED -> {
                        val thought = StoicLibrary.get(intent.getStringExtra(EXTRA_THOUGHT_ID)) ?: return@launch
                        val lang = container.repository.current().language.resolve()
                        container.notifier.show(thought, intent.getStringExtra(EXTRA_LABEL).orEmpty(), lang)
                    }
                    ACTION_SNOOZE -> {
                        val thoughtId = intent.getStringExtra(EXTRA_THOUGHT_ID) ?: return@launch
                        container.notifier.dismiss()
                        container.scheduler.snooze(thoughtId, intent.getStringExtra(EXTRA_LABEL).orEmpty())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MOMENT = "com.momentoestoico.MOMENT"
        const val ACTION_SNOOZE = "com.momentoestoico.SNOOZE"
        const val ACTION_SNOOZED = "com.momentoestoico.SNOOZED"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_AT = "at"
        const val EXTRA_THOUGHT_ID = "thought_id"
        const val EXTRA_LABEL = "label"
    }
}

/** Tras reiniciar, cambiar la hora o actualizar la app, Android borra las alarmas: se reprograman. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SYSTEM_ACTIONS) return
        val container = (context.applicationContext as App).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.scheduler.scheduleAll(container.repository.current().reminders)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
