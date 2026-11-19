package com.momentoestoico.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.momentoestoico.App
import com.momentoestoico.AppContainer
import com.momentoestoico.data.AppLanguage
import com.momentoestoico.data.AppState
import com.momentoestoico.data.displayLabel
import com.momentoestoico.data.ContentFilter
import com.momentoestoico.data.Reminder
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.data.Thought
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class Permissions(val notifications: Boolean = true, val exactAlarms: Boolean = true)

class MainViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<AppState?> = c.repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permissions = MutableStateFlow(Permissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    /** Pensamiento abierto a pantalla completa (desde la notificación o la biblioteca). */
    private val _moment = MutableStateFlow<MomentRequest?>(null)
    val moment: StateFlow<MomentRequest?> = _moment.asStateFlow()

    init {
        viewModelScope.launch {
            val s = c.repository.current()
            // Un pensamiento nuevo cada día al abrir la app
            if (StoicLibrary.get(s.currentId) == null || !isToday(s.currentAt)) {
                c.repository.pickNext(ContentFilter.ANY)
            }
            c.scheduler.scheduleAll(s.reminders)
            c.notifier.createChannel(s.language.resolve())
        }
        refreshPermissions()
    }

    private fun isToday(epochMillis: Long): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    fun refreshPermissions() {
        _permissions.value = Permissions(c.notifier.canNotify(), c.scheduler.canScheduleExact())
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        c.repository.update { it.copy(language = language) }
        c.notifier.createChannel(language.resolve())
    }

    fun setShareSound(on: Boolean) = viewModelScope.launch {
        c.repository.update { it.copy(shareSound = on) }
    }

    fun markNotificationsAsked() = viewModelScope.launch {
        c.repository.update { it.copy(notificationsAsked = true) }
    }

    // ---- Pensamientos ----

    fun another() = viewModelScope.launch { c.repository.pickNext(ContentFilter.ANY) }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        c.repository.update { s ->
            s.copy(favorites = if (id in s.favorites) s.favorites - id else s.favorites + id)
        }
    }

    fun openMoment(thought: Thought, breathe: Boolean) {
        _moment.value = MomentRequest(thought, breathe)
        if (breathe) viewModelScope.launch { c.repository.setCurrent(thought.id) }
    }

    fun closeMoment() {
        _moment.value = null
    }

    // ---- Momentos programados ----

    fun saveReminder(reminder: Reminder) = viewModelScope.launch {
        val s = c.repository.update { st ->
            if (reminder.id <= 0) {
                st.copy(reminders = st.reminders + reminder.copy(id = st.nextReminderId), nextReminderId = st.nextReminderId + 1)
            } else {
                st.copy(reminders = st.reminders.map { if (it.id == reminder.id) reminder else it })
            }
        }
        c.scheduler.scheduleAll(s.reminders)
    }

    fun setEnabled(id: Int, enabled: Boolean) = viewModelScope.launch {
        val s = c.repository.update { st ->
            st.copy(reminders = st.reminders.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
        c.scheduler.scheduleAll(s.reminders)
    }

    fun deleteReminder(id: Int) = viewModelScope.launch {
        val s = c.repository.update { st -> st.copy(reminders = st.reminders.filterNot { it.id == id }) }
        c.scheduler.scheduleAll(s.reminders, removedIds = listOf(id))
    }

    /** Muestra ahora mismo la notificación de un momento, para probar sonido y aspecto. */
    fun testNow(reminder: Reminder?, context: android.content.Context) = viewModelScope.launch {
        val lang = c.repository.current().language.resolve()
        val thought = c.repository.pickNext(reminder?.content ?: ContentFilter.ANY)
        c.notifier.show(thought, reminder?.displayLabel(context).orEmpty(), lang)
    }

    /** El próximo aviso de todos los momentos activos. */
    fun nextOverall(reminders: List<Reminder>, now: ZonedDateTime = ZonedDateTime.now()): Pair<Reminder, ZonedDateTime>? =
        reminders.filter { it.enabled }
            .mapNotNull { r -> r.nextTrigger(now)?.let { r to it } }
            .minByOrNull { it.second }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                MainViewModel(app.container)
            }
        }
    }
}

data class MomentRequest(val thought: Thought, val breathe: Boolean)
