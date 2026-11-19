package com.momentoestoico.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.momentoestoico.R
import com.momentoestoico.ui.LocalLang
import com.momentoestoico.data.Kind
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.data.Thought
import com.momentoestoico.ui.components.Caps
import com.momentoestoico.ui.components.ZenIconButton
import com.momentoestoico.ui.moments.Pill
import com.momentoestoico.ui.theme.LocalZen
import com.momentoestoico.ui.theme.ZenType

private enum class LibraryFilter(val label: Int) {
    ALL(R.string.filter_all), QUOTES(R.string.content_quotes), PRACTICES(R.string.content_practices), FAVORITES(R.string.filter_favorites)
}

@Composable
fun LibraryScreen(
    favorites: Set<String>,
    onOpen: (Thought) -> Unit,
    onFavorite: (Thought) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zen = LocalZen.current
    val lang = LocalLang.current
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var author by rememberSaveable { mutableStateOf<String?>(null) }
    // Autores por número de pensamientos
    val authors = StoicLibrary.all.groupBy { it.authorKey }.values
        .sortedByDescending { it.size }
        .map { it.first().authorKey to it.first().author[lang] }
    val items = StoicLibrary.all.filter { t ->
        when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.QUOTES -> t.kind == Kind.QUOTE
            LibraryFilter.PRACTICES -> t.kind == Kind.PRACTICE
            LibraryFilter.FAVORITES -> t.id in favorites
        } && (author == null || t.authorKey == author)
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Caps(stringResource(R.string.library_count, StoicLibrary.all.size), color = zen.accent, modifier = Modifier.padding(bottom = 6.dp))
                Text(stringResource(R.string.tab_library), style = ZenType.title, color = zen.ink)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryFilter.entries.forEach { f -> Pill(stringResource(f.label), filter == f) { filter = f } }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.all_authors), author == null) { author = null }
                    authors.forEach { (key, name) -> Pill(name, author == key) { author = if (author == key) null else key } }
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    stringResource(if (filter == LibraryFilter.FAVORITES) R.string.no_favorites else R.string.nothing_filter),
                    style = ZenType.body,
                    color = zen.muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(items, key = { it.id }) { t ->
            LibraryItem(t, t.id in favorites, onClick = { onOpen(t) }, onFavorite = { onFavorite(t) })
        }
    }
}

@Composable
private fun LibraryItem(t: Thought, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val zen = LocalZen.current
    val lang = LocalLang.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(zen.card)
            .border(1.dp, zen.line, shape)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (t.kind == Kind.PRACTICE) {
                Caps(stringResource(R.string.practice_prefix, t.title?.get(lang).orEmpty()), color = zen.accent)
                Spacer(Modifier.height(6.dp))
            }
            Text(t.text[lang], style = ZenType.quoteSmall, color = zen.ink, maxLines = 6)
            Spacer(Modifier.height(10.dp))
            Text(t.author[lang], style = ZenType.author.copy(fontSize = ZenType.author.fontSize * 0.85f), color = zen.inkSoft)
            Caps(t.source[lang])
        }
        ZenIconButton(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            stringResource(if (favorite) R.string.fav_remove else R.string.fav_add),
            onFavorite,
            tint = if (favorite) zen.accent else zen.muted,
        )
    }
}

