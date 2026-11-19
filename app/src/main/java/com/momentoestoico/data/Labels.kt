package com.momentoestoico.data

import android.content.Context
import com.momentoestoico.R

/** Nombre visible: los momentos de ejemplo se traducen; los del usuario, tal cual. */
fun Reminder.displayLabel(context: Context): String = when (label) {
    Reminder.PRESET_MORNING -> context.getString(R.string.preset_morning)
    Reminder.PRESET_EVENING -> context.getString(R.string.preset_evening)
    else -> label
}
