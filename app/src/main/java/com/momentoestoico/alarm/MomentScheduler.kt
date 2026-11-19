package com.momentoestoico.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.momentoestoico.data.Reminder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Programa los momentos con AlarmManager: una alarma por momento, que al sonar
 * programa la siguiente. Con permiso de alarmas exactas suena puntual; sin él,
 * Android puede retrasarla unos minutos para ahorrar batería.
 */
class MomentScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Reprograma todos; los que no están en la lista o están desactivados se cancelan. */
    fun scheduleAll(reminders: List<Reminder>, removedIds: Collection<Int> = emptyList()) {
        removedIds.forEach(::cancel)
        reminders.forEach { if (it.enabled) schedule(it) else cancel(it.id) }
    }

    /** Programa el siguiente aviso de [reminder] posterior a [after]. Devuelve cuándo, o null. */
    fun schedule(reminder: Reminder, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val next = reminder.nextTrigger(after)
        if (next == null || !reminder.enabled) {
            cancel(reminder.id)
            return null
        }
        val at = next.toInstant().toEpochMilli()
        val pi = momentIntent(reminder.id, at)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        return next
    }

    fun cancel(id: Int) {
        alarms.cancel(momentIntent(id, 0))
    }

    /** "Más tarde": vuelve a mostrar el mismo pensamiento dentro de [minutes]. */
    fun snooze(thoughtId: String, label: String, minutes: Int = SNOOZE_MINUTES) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_SNOOZED)
            .putExtra(AlarmReceiver.EXTRA_THOUGHT_ID, thoughtId)
            .putExtra(AlarmReceiver.EXTRA_LABEL, label)
        val pi = PendingIntent.getBroadcast(context, SNOOZE_REQUEST, intent, FLAGS)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun momentIntent(id: Int, at: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_MOMENT)
            // El id va en la URI para que cada momento tenga su propia alarma
            .setData(android.net.Uri.parse("momento://reminder/$id"))
            .putExtra(AlarmReceiver.EXTRA_REMINDER_ID, id)
            .putExtra(AlarmReceiver.EXTRA_AT, at)
        return PendingIntent.getBroadcast(context, id, intent, FLAGS)
    }

    companion object {
        const val SNOOZE_MINUTES = 15
        private const val SNOOZE_REQUEST = 900_000
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun atZoned(epochMillis: Long): ZonedDateTime =
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    }
}
