package com.momentoestoico.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.momentoestoico.data.Lang

/** Idioma del contenido visible (citas, autores, fuentes). */
val LocalLang = staticCompositionLocalOf { Lang.ES }

/** Recursos en otro idioma sin perder la actividad (startActivity sigue funcionando). */
private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}

/** Contexto con los textos del idioma indicado (para notificaciones y receptores). */
fun Context.localized(lang: Lang): Context {
    val config = Configuration(resources.configuration).apply { setLocale(lang.locale) }
    return createConfigurationContext(config)
}

/** Aplica el idioma elegido en la app a toda la interfaz Compose que contiene. */
@Composable
fun LocalizedApp(lang: Lang, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val context = remember(base, lang) { LocalizedContext(base, base.localized(lang).resources) }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalResources provides context.resources,
        LocalConfiguration provides context.resources.configuration,
        LocalLang provides lang,
        content = content,
    )
}
