package com.momentoestoico.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "momento_estoico")

/** Estado persistente de la app (momentos, favoritos, pensamiento actual). Operaciones atómicas. */
class Repository(private val context: Context) {

    private val key = stringPreferencesKey("state_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun decode(raw: String?): AppState {
        val s = raw?.let { runCatching { json.decodeFromString(AppState.serializer(), it) }.getOrNull() } ?: AppState()
        // Versión 1.0: los momentos de ejemplo se guardaban con el nombre en español
        return s.copy(
            reminders = s.reminders.map {
                when (it.label) {
                    "Al despertar" -> it.copy(label = Reminder.PRESET_MORNING)
                    "Examen de la noche" -> it.copy(label = Reminder.PRESET_EVENING)
                    else -> it
                }
            },
        )
    }

    val state: Flow<AppState> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { decode(it[key]) }

    suspend fun current(): AppState = state.first()

    /** Aplica [transform] de forma atómica y devuelve el estado resultante. */
    suspend fun update(transform: (AppState) -> AppState): AppState {
        var result = AppState()
        context.dataStore.edit { prefs ->
            result = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(AppState.serializer(), result)
        }
        return result
    }

    /** Elige el siguiente pensamiento y lo deja como actual. */
    suspend fun pickNext(filter: ContentFilter, now: Long = System.currentTimeMillis()): Thought {
        var picked: Thought? = null
        update { s ->
            val (thought, next) = Selection.next(s, filter)
            picked = thought
            next.copy(currentId = thought.id, currentAt = now)
        }
        return picked!!
    }

    suspend fun setCurrent(id: String, now: Long = System.currentTimeMillis()) {
        update { it.copy(currentId = id, currentAt = now) }
    }
}
