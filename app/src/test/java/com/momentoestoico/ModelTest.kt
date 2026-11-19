package com.momentoestoico

import com.momentoestoico.data.AppLanguage
import com.momentoestoico.data.AppState
import com.momentoestoico.data.Lang
import com.momentoestoico.data.ContentFilter
import com.momentoestoico.data.Kind
import com.momentoestoico.data.Reminder
import com.momentoestoico.data.Selection
import com.momentoestoico.data.StoicLibrary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Locale

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class ReminderTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun dailyAtTime() {
        val r = Reminder(1, 8, 0)
        // Miércoles 18/11/2026 a las 07:00 → hoy a las 08:00; a las 08:00 → mañana
        assertEquals(at(2026, 11, 18, 8, 0), r.nextTrigger(at(2026, 11, 18, 7, 0)))
        assertEquals(at(2026, 11, 19, 8, 0), r.nextTrigger(at(2026, 11, 18, 8, 0)))
    }

    @Test fun onlyChosenDays() {
        val r = Reminder(1, 9, 30, days = Reminder.WEEKEND)
        // Desde el miércoles, el siguiente es el sábado 21
        assertEquals(at(2026, 11, 21, 9, 30), r.nextTrigger(at(2026, 11, 18, 12, 0)))
        assertNull(r.copy(days = emptySet()).nextTrigger(at(2026, 11, 18, 12, 0)))
    }

    @Test fun repeatsWithInterval() {
        val r = Reminder(1, 9, 0, intervalMinutes = 150, endHour = 14, endMinute = 0)
        assertEquals(listOf("09:00", "11:30", "14:00"), r.timesOfDay().map { it.toString() })
        assertEquals(at(2026, 11, 18, 11, 30), r.nextTrigger(at(2026, 11, 18, 9, 0)))
        assertEquals(at(2026, 11, 19, 9, 0), r.nextTrigger(at(2026, 11, 18, 14, 0)))
        // Final anterior al inicio: se comporta como una vez al día
        assertEquals(1, r.copy(endHour = 8).timesOfDay().size)
    }

    @Test fun daylightSavingChange() {
        // 29/03/2026 en Madrid: de 02:00 se salta a 03:00
        val r = Reminder(1, 2, 30)
        val next = r.nextTrigger(at(2026, 3, 28, 23, 0))!!
        assertEquals(29, next.dayOfMonth)
        assertEquals(3, next.hour)
    }
}

class SelectionTest {
    companion object {
        @JvmStatic @BeforeClass fun load() = TestLibrary.ensure()
    }

    @Test fun noRepeatsUntilWholeLibraryShown() {
        var state = AppState()
        val seen = mutableListOf<String>()
        val random = Random(7)
        repeat(StoicLibrary.all.size) {
            val (t, next) = Selection.next(state, ContentFilter.ANY, random = random)
            seen += t.id
            state = next.copy(currentId = t.id)
        }
        assertEquals(StoicLibrary.all.size, seen.toSet().size)
        // Nueva vuelta: no repite el último justo al empezar
        val (again, _) = Selection.next(state, ContentFilter.ANY, random = random)
        assertTrue(again.id != state.currentId)
    }

    @Test fun respectsFilter() {
        var state = AppState()
        repeat(40) {
            val (t, next) = Selection.next(state, ContentFilter.PRACTICE)
            assertEquals(Kind.PRACTICE, t.kind)
            state = next
        }
    }
}

class LibraryTest {
    companion object {
        @JvmStatic @BeforeClass fun load() = TestLibrary.ensure()
    }

    @Test fun libraryIsLargeAndBilingual() {
        assertTrue(StoicLibrary.all.size >= 190)
        StoicLibrary.all.forEach { t ->
            for (lang in Lang.entries) {
                assertTrue("${t.id} sin texto ${lang.code}", t.text[lang].isNotBlank())
                assertTrue("${t.id} sin autor ${lang.code}", t.author[lang].isNotBlank())
                assertTrue("${t.id} sin fuente ${lang.code}", t.source[lang].isNotBlank())
                if (t.kind == Kind.PRACTICE) assertTrue("${t.id} sin título ${lang.code}", !t.title?.get(lang).isNullOrBlank())
            }
        }
        // La traducción francesa no es una copia del español (salvo frases muy cortas o latinas)
        val copies = StoicLibrary.all.filter { it.text.es == it.text.fr }
        assertTrue("Sin traducir: ${copies.map { it.id }}", copies.isEmpty())
        // Los favoritos de la versión 1.0 siguen existiendo
        listOf("ma-2-1", "se-ep-1", "ep-ench-5", "pr-control", "pr-examen").forEach { assertTrue(it, StoicLibrary.get(it) != null) }
    }

    @Test fun languageResolution() {
        assertEquals(Lang.FR, AppLanguage.SYSTEM.resolve(Locale.FRANCE))
        assertEquals(Lang.FR, AppLanguage.SYSTEM.resolve(Locale.CANADA_FRENCH))
        assertEquals(Lang.ES, AppLanguage.SYSTEM.resolve(Locale.forLanguageTag("es-AR")))
        assertEquals(Lang.ES, AppLanguage.SYSTEM.resolve(Locale.GERMANY)) // otros idiomas: español
        assertEquals(Lang.FR, AppLanguage.FR.resolve(Locale.forLanguageTag("es-ES")))
    }

    @Test fun libraryIsConsistent() {
        val ids = StoicLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        StoicLibrary.all.forEach {
            assertTrue(it.text.es.isNotBlank())
        }
        assertTrue(StoicLibrary.all.count { it.kind == Kind.PRACTICE } >= 30)
    }

    @Test fun stateRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = AppState(favorites = setOf("ma-2-1"), bag = listOf("se-ep-1"))
        assertEquals(s, json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), s)))
    }
}

/** Carga la biblioteca real (assets/thoughts.json) una sola vez. */
object TestLibrary {
    private var loaded = false
    fun ensure() {
        if (!loaded) {
            StoicLibrary.load(File("src/main/assets/thoughts.json").readText())
            loaded = true
        }
    }
}
