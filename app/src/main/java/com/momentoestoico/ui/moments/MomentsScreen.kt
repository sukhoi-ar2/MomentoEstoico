package com.momentoestoico.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Context
import com.momentoestoico.R
import com.momentoestoico.data.AppLanguage
import com.momentoestoico.data.displayLabel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.momentoestoico.data.ContentFilter
import com.momentoestoico.data.Reminder
import com.momentoestoico.ui.Permissions
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.theme.ZenType
import com.momentoestoico.ui.today.whenText
import java.time.ZonedDateTime

private val INTERVALS = listOf(0, 30, 60, 120, 180, 240)

private fun Context.dayLetters(): List<String> = getString(R.string.day_letters).split(',')

fun daysText(context: Context, days: Set<Int>): String = when (days) {
    Reminder.ALL_DAYS -> context.getString(R.string.days_all)
    Reminder.WEEKDAYS -> context.getString(R.string.days_weekdays)
    Reminder.WEEKEND -> context.getString(R.string.days_weekend)
    emptySet<Int>() -> context.getString(R.string.days_none)
    else -> context.dayLetters().let { letters -> days.sorted().joinToString(" ") { letters[it - 1] } }
}

fun intervalText(context: Context, minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> context.getString(R.string.every_min, minutes)
    minutes == 60 -> context.getString(R.string.every_hour)
    minutes % 60 == 0 -> context.getString(R.string.every_hours, minutes / 60)
    else -> context.getString(R.string.every_hm, minutes / 60, minutes % 60)
}

private fun intervalChoice(context: Context, minutes: Int): String = when {
    minutes <= 0 -> context.getString(R.string.interval_once)
    minutes < 60 -> context.getString(R.string.interval_min, minutes)
    minutes == 60 -> context.getString(R.string.interval_hour)
    else -> context.getString(R.string.interval_hours, minutes / 60)
}

fun contentText(context: Context, f: ContentFilter): String = context.getString(
    when (f) {
        ContentFilter.ANY -> R.string.content_any
        ContentFilter.QUOTE -> R.string.content_quotes
        ContentFilter.PRACTICE -> R.string.content_practices
    },
)


private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

@Composable
fun MomentsScreen(
    reminders: List<Reminder>,
    permissions: Permissions,
    onRequestNotifications: () -> Unit,
    onRequestExact: () -> Unit,
    onToggle: (Reminder, Boolean) -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onTest: () -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zen = LocalZen.current
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val now = ZonedDateTime.now()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Caps(stringResource(R.string.moments_kicker), color = zen.accent, modifier = Modifier.padding(bottom = 6.dp))
                Text(stringResource(R.string.tab_moments), style = ZenType.title, color = zen.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.moments_intro),
                    style = ZenType.body,
                    color = zen.muted,
                )
            }
        }
        if (!permissions.notifications) {
            item {
                Notice(
                    stringResource(R.string.notif_off_title),
                    stringResource(R.string.notif_off_text),
                    stringResource(R.string.action_enable),
                    onRequestNotifications,
                )
            }
        }
        if (!permissions.exactAlarms) {
            item {
                Notice(
                    stringResource(R.string.exact_title),
                    stringResource(R.string.exact_text),
                    stringResource(R.string.action_allow),
                    onRequestExact,
                )
            }
        }
        items(reminders, key = { it.id }) { r ->
            ReminderCard(r, r.nextTrigger(now), onClick = { editing = r }, onToggle = { onToggle(r, it) })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZenTextButton(stringResource(R.string.new_moment)) {
                    editing = Reminder(id = 0, hour = 13, minute = 0)
                }
                ZenTextButton(stringResource(R.string.test_now), onTest)
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caps(stringResource(R.string.language))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.lang_system), language == AppLanguage.SYSTEM) { onLanguage(AppLanguage.SYSTEM) }
                    Pill("Español", language == AppLanguage.ES) { onLanguage(AppLanguage.ES) }
                    Pill("Français", language == AppLanguage.FR) { onLanguage(AppLanguage.FR) }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.battery_note),
                style = ZenType.body.copy(fontSize = ZenType.body.fontSize * 0.85f),
                color = zen.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    editing?.let { r ->
        ReminderDialog(
            initial = r,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
            onDelete = if (r.id > 0) ({
                onDelete(r)
                editing = null
            }) else null,
        )
    }
}

@Composable
private fun ZenTextButton(text: String, onClick: () -> Unit) {
    val zen = LocalZen.current
    TextButton(onClick = onClick) {
        Text(text, style = ZenType.caps.copy(fontSize = ZenType.caps.fontSize * 1.1f), color = zen.accent)
    }
}

@Composable
private fun Notice(title: String, text: String, action: String, onAction: () -> Unit) {
    val zen = LocalZen.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(zen.accent.copy(alpha = 0.12f))
            .padding(16.dp),
    ) {
        Text(title, style = ZenType.author.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Normal), color = zen.ink)
        Text(text, style = ZenType.body, color = zen.inkSoft)
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
            Text(action.uppercase(), style = ZenType.caps, color = zen.accent)
        }
    }
}

@Composable
private fun ReminderCard(r: Reminder, next: ZonedDateTime?, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val zen = LocalZen.current
    val shape = RoundedCornerShape(22.dp)
    val alpha = if (r.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(zen.card)
            .border(1.dp, zen.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(hm(r.hour, r.minute), style = ZenType.time, color = zen.ink.copy(alpha = alpha))
                if (r.repeats) {
                    Text(
                        "  –  " + hm(r.endHour, r.endMinute),
                        style = ZenType.quoteSmall,
                        color = zen.inkSoft.copy(alpha = alpha),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            val label = r.displayLabel(LocalContext.current)
            if (label.isNotBlank()) {
                Text(label, style = ZenType.author, color = zen.inkSoft.copy(alpha = alpha))
            }
            Spacer(Modifier.height(4.dp))
            Caps(
                listOf(daysText(LocalContext.current, r.days), intervalText(LocalContext.current, r.intervalMinutes), contentText(LocalContext.current, r.content))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = zen.muted.copy(alpha = alpha),
            )
            if (r.enabled && next != null) {
                Text(stringResource(R.string.next_short, whenText(LocalContext.current, next)), style = ZenType.body.copy(fontSize = ZenType.body.fontSize * 0.85f), color = zen.muted)
            }
        }
        Switch(
            checked = r.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = zen.accent,
                checkedThumbColor = if (zen.dark) Color(0xFF1A1A17) else Color.White,
                uncheckedTrackColor = Color.Transparent,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(
    initial: Reminder,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zen = LocalZen.current
    val context = LocalContext.current
    var label by remember { mutableStateOf(initial.displayLabel(context)) }
    var days by remember { mutableStateOf(initial.days) }
    var content by remember { mutableStateOf(initial.content) }
    var interval by remember { mutableIntStateOf(initial.intervalMinutes) }
    val start = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    val end = rememberTimePickerState(initial.endHour, initial.endMinute, is24Hour = true)

    val endAfterStart = end.hour * 60 + end.minute > start.hour * 60 + start.minute
    val error = when {
        days.isEmpty() -> stringResource(R.string.err_days)
        interval > 0 && !endAfterStart -> stringResource(R.string.err_end)
        else -> null
    }
    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = zen.accent.copy(alpha = 0.10f),
        selectorColor = zen.accent,
        timeSelectorSelectedContainerColor = zen.accent.copy(alpha = 0.22f),
        timeSelectorUnselectedContainerColor = zen.line,
        timeSelectorSelectedContentColor = zen.ink,
        timeSelectorUnselectedContentColor = zen.inkSoft,
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (zen.dark) Color(0xFF1B1E21) else Color(0xFFF7F3EC),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(if (initial.id > 0) R.string.edit_moment else R.string.new_moment_title), style = ZenType.title.copy(fontSize = ZenType.title.fontSize * 0.8f), color = zen.ink)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.name_optional)) },
                    placeholder = { Text(stringResource(R.string.name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Caps(stringResource(if (interval > 0) R.string.label_from else R.string.label_time), modifier = Modifier.align(Alignment.Start))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = start, colors = pickerColors)
                }

                Caps(stringResource(R.string.label_frequency), modifier = Modifier.align(Alignment.Start))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    INTERVALS.forEach { minutes ->
                        Pill(intervalChoice(context, minutes), interval == minutes) { interval = minutes }
                    }
                }
                if (interval > 0) {
                    Caps(stringResource(R.string.label_until), modifier = Modifier.align(Alignment.Start))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimeInput(state = end, colors = pickerColors)
                    }
                }

                Caps(stringResource(R.string.label_days), modifier = Modifier.align(Alignment.Start))
                val letters = context.dayLetters()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (1..7).forEach { d ->
                        val on = d in days
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (on) zen.accent else Color.Transparent)
                                .border(1.dp, if (on) zen.accent else zen.line, CircleShape)
                                .clickable { days = if (on) days - d else days + d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letters[d - 1],
                                fontWeight = FontWeight.Medium,
                                color = if (on) (if (zen.dark) Color(0xFF1A1A17) else Color.White) else zen.inkSoft,
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.quick_all), days == Reminder.ALL_DAYS) { days = Reminder.ALL_DAYS }
                    Pill(stringResource(R.string.quick_weekdays), days == Reminder.WEEKDAYS) { days = Reminder.WEEKDAYS }
                    Pill(stringResource(R.string.quick_weekend), days == Reminder.WEEKEND) { days = Reminder.WEEKEND }
                }

                Caps(stringResource(R.string.label_content), modifier = Modifier.align(Alignment.Start))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentFilter.entries.forEach { f -> Pill(contentText(context, f), content == f) { content = f } }
                }

                error?.let { Text(it, style = ZenType.body, color = Color(0xFFB4533C)) }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete).uppercase(), style = ZenType.caps, color = Color(0xFFB4533C)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel).uppercase(), style = ZenType.caps, color = zen.muted) }
                    TextButton(
                        enabled = error == null,
                        onClick = {
                            onSave(
                                initial.copy(
                                    hour = start.hour,
                                    minute = start.minute,
                                    days = days,
                                    // Si no se ha tocado el nombre de un momento de ejemplo, sigue traduciéndose
                                    label = label.trim().let { if (it == initial.displayLabel(context)) initial.label else it },
                                    content = content,
                                    intervalMinutes = interval,
                                    endHour = end.hour,
                                    endMinute = end.minute,
                                    enabled = true,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.save).uppercase(), style = ZenType.caps, color = if (error == null) zen.accent else zen.muted) }
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val zen = LocalZen.current
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = ZenType.body.copy(fontSize = ZenType.body.fontSize * 0.9f),
        color = if (selected) (if (zen.dark) Color(0xFF1A1A17) else Color.White) else zen.inkSoft,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) zen.accent else Color.Transparent)
            .border(1.dp, if (selected) zen.accent else zen.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
