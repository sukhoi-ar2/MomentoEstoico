package com.momentoestoico.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

@Serializable
enum class ContentFilter {
    ANY,
    QUOTE,
    PRACTICE;

    fun accepts(t: Thought) = when (this) {
        ANY -> true
        QUOTE -> t.kind == Kind.QUOTE
        PRACTICE -> t.kind == Kind.PRACTICE
    }
}

/**
 * Un momento programado.
 *
 * - Una vez al día: a las [hour]:[minute] los [days] indicados.
 * - Con frecuencia: si [intervalMinutes] > 0, se repite cada intervalo desde la hora de
 *   inicio hasta [endHour]:[endMinute] (incluida) en esos días.
 *
 * [days] usa los valores ISO de [DayOfWeek]: 1 = lunes … 7 = domingo.
 */
@Serializable
data class Reminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = ALL_DAYS,
    val enabled: Boolean = true,
    val label: String = "",
    val content: ContentFilter = ContentFilter.ANY,
    val intervalMinutes: Int = 0,
    val endHour: Int = 21,
    val endMinute: Int = 0,
) {
    val start: LocalTime get() = LocalTime.of(hour, minute)
    val end: LocalTime get() = LocalTime.of(endHour, endMinute)
    val repeats: Boolean get() = intervalMinutes > 0 && end.isAfter(start)

    /** Horas del día en que suena. */
    fun timesOfDay(): List<LocalTime> {
        if (!repeats) return listOf(start)
        val result = mutableListOf<LocalTime>()
        var minutes = hour * 60 + minute
        val last = endHour * 60 + endMinute
        while (minutes <= last) {
            result += LocalTime.of(minutes / 60, minutes % 60)
            minutes += intervalMinutes
        }
        return result
    }

    /** Siguiente aviso estrictamente posterior a [after], o null si no hay días elegidos. */
    fun nextTrigger(after: ZonedDateTime): ZonedDateTime? {
        if (days.isEmpty()) return null
        val times = timesOfDay()
        for (offset in 0L..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (date.dayOfWeek.value !in days) continue
            for (t in times) {
                val candidate = ZonedDateTime.of(date, t, after.zone)
                if (candidate.isAfter(after)) return candidate
            }
        }
        return null
    }

    companion object {
        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)

        /** Nombres de los momentos de ejemplo: se traducen al mostrarlos. */
        const val PRESET_MORNING = "@morning"
        const val PRESET_EVENING = "@evening"
    }
}

/** Lo que se guarda en el dispositivo. */
@Serializable
data class AppState(
    val reminders: List<Reminder> = listOf(
        Reminder(id = 1, hour = 8, minute = 0, label = Reminder.PRESET_MORNING, content = ContentFilter.ANY),
        Reminder(id = 2, hour = 22, minute = 0, label = Reminder.PRESET_EVENING, content = ContentFilter.PRACTICE),
    ),
    val nextReminderId: Int = 3,
    val favorites: Set<String> = emptySet(),
    /** Ids pendientes de mostrar; al vaciarse se vuelve a barajar toda la biblioteca. */
    val bag: List<String> = emptyList(),
    val currentId: String? = null,
    val currentAt: Long = 0,
    val notificationsAsked: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Sonido del cuenco al preparar una imagen para compartir. */
    val shareSound: Boolean = true,
)

/** Elige el siguiente pensamiento sin repetir hasta haber pasado por toda la biblioteca. */
object Selection {

    fun next(
        state: AppState,
        filter: ContentFilter,
        library: List<Thought> = StoicLibrary.all,
        random: Random = Random.Default,
    ): Pair<Thought, AppState> {
        val valid = library.map { it.id }.toSet()
        var bag = state.bag.filter { it in valid }
        var pick = bag.firstOrNull { id -> library.first { it.id == id }.let(filter::accepts) }
        if (pick == null) {
            // Nueva vuelta: se baraja todo, evitando repetir el actual justo al empezar
            bag = library.map { it.id }.shuffled(random)
            if (bag.size > 1 && bag.first() == state.currentId) bag = bag.drop(1) + bag.first()
            pick = bag.firstOrNull { id -> library.first { it.id == id }.let(filter::accepts) }
                ?: bag.first()
        }
        val thought = library.first { it.id == pick }
        return thought to state.copy(bag = bag - pick)
    }
}
