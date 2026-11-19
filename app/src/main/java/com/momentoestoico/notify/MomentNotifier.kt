package com.momentoestoico.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.momentoestoico.MainActivity
import com.momentoestoico.R
import com.momentoestoico.alarm.AlarmReceiver
import com.momentoestoico.alarm.MomentScheduler
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Lang
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.localized

/** Notificación del momento estoico, con sonido de cuenco. */
class MomentNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Crea o actualiza el canal (el nombre se traduce al idioma elegido). */
    fun createChannel(lang: Lang) {
        val texts = context.localized(lang)
        val sound = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.cuenco}")
        val channel = NotificationChannel(CHANNEL_ID, texts.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = texts.getString(R.string.channel_desc)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 180, 120)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    /** [label]: nombre ya traducido del momento (vacío si no tiene). */
    fun show(thought: Thought, label: String, lang: Lang) {
        if (!canNotify()) return
        val texts = context.localized(lang)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_THOUGHT_ID, thought.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_SNOOZE)
                .putExtra(AlarmReceiver.EXTRA_THOUGHT_ID, thought.id)
                .putExtra(AlarmReceiver.EXTRA_LABEL, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when {
            thought.kind == Kind.PRACTICE && thought.title != null -> thought.title[lang]
            label.isNotBlank() -> label
            else -> texts.getString(R.string.brand)
        }
        val text = thought.text[lang]
        val body = if (thought.kind == Kind.QUOTE) com.momentoestoico.ui.quoted(text, lang) else text
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText(thought.author[lang]))
            .setSubText(thought.author[lang])
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, texts.getString(R.string.snooze, MomentScheduler.SNOOZE_MINUTES), snooze)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permiso retirado entre la comprobación y el aviso
        }
    }

    fun dismiss() = manager.cancel(NOTIFICATION_ID)

    companion object {
        const val CHANNEL_ID = "momentos_v1"
        private const val NOTIFICATION_ID = 1
    }
}
