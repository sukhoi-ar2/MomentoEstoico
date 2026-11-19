package com.momentoestoico.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** Idiomas del contenido y de la interfaz. */
enum class Lang(val code: String, val locale: Locale) {
    ES("es", Locale.forLanguageTag("es-ES")),
    FR("fr", Locale.FRANCE),
}

/** Preferencia guardada: seguir al sistema o fijar un idioma. */
@Serializable
enum class AppLanguage {
    SYSTEM, ES, FR;

    fun resolve(system: Locale = Locale.getDefault()): Lang = when (this) {
        ES -> Lang.ES
        FR -> Lang.FR
        SYSTEM -> if (system.language == "fr") Lang.FR else Lang.ES
    }
}

/** Texto en los idiomas disponibles. */
@Serializable
data class L(val es: String, val fr: String) {
    operator fun get(lang: Lang): String = when (lang) {
        Lang.ES -> es
        Lang.FR -> fr.ifBlank { es }
    }
}

@Serializable
enum class Kind { QUOTE, PRACTICE }

/**
 * Un pensamiento: cita con su fuente o práctica (ejercicio) con su origen.
 * Las traducciones son versiones libres de los textos clásicos.
 */
data class Thought(
    val id: String,
    val kind: Kind,
    val authorKey: String,
    val author: L,
    val source: L,
    val text: L,
    /** Nombre del ejercicio, solo en prácticas. */
    val title: L? = null,
)

@Serializable
private data class RawThought(
    val id: String,
    val kind: Kind,
    val author: String,
    val work: String,
    val ref: String = "",
    val text: L,
    val title: L? = null,
)

@Serializable
private data class RawLibrary(
    val authors: Map<String, L>,
    val works: Map<String, L>,
    val thoughts: List<RawThought>,
)

/** Biblioteca cargada desde assets/thoughts.json (generado con tools/build_thoughts.py). */
object StoicLibrary {

    @Volatile
    var all: List<Thought> = emptyList()
        private set

    @Volatile
    private var byId: Map<String, Thought> = emptyMap()

    private val json = Json { ignoreUnknownKeys = true }

    fun load(source: String) {
        val raw = json.decodeFromString(RawLibrary.serializer(), source)
        all = raw.thoughts.map { t ->
            val work = raw.works.getValue(t.work)
            Thought(
                id = t.id,
                kind = t.kind,
                authorKey = t.author,
                author = raw.authors.getValue(t.author),
                source = if (t.ref.isBlank()) work else L("${work.es}, ${t.ref}", "${work.fr}, ${t.ref}"),
                text = t.text,
                title = t.title,
            )
        }
        byId = all.associateBy { it.id }
    }

    fun get(id: String?): Thought? = id?.let(byId::get)
}
