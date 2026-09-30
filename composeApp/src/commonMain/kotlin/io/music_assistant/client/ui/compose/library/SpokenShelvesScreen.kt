package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.TopBarLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpokenShelvesScreen(
    viewModel: SpokenShelvesViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOriginalList: () -> Unit,
    onOpen: (String, MediaType, String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }
    val goBack = { if (!viewModel.back()) onBack() }
    BackHandler(enabled = !state.query.isHome) { goBack() }
    val open: (SpokenCard) -> Unit = { card ->
        when (card.kind) {
            "group" -> viewModel.group(card)
            "book" -> onOpen(card.id, MediaType.AUDIOBOOK, card.provider)
            "podcast" -> onOpen(card.id, MediaType.PODCAST, card.provider)
        }
    }
    val title = state.page?.title?.takeIf { it.isNotBlank() }
        ?: state.page?.categories?.firstOrNull { it.id == state.query.category }?.name ?: "有声书"
    TopBarLayout(topBar = {
        TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "刷新书架") }
                TextButton(onClick = onOriginalList) { Text("列表") }
            })
    }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(value = state.query.search, onValueChange = viewModel::search,
                    label = { Text("搜索书名、作者、节目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (state.query.group.isEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("" to "全部", "book" to "有声书", "commentary" to "精读讲书", "podcast" to "播客")) { (kind, name) ->
                        FilterChip(selected = state.query.kind == kind, onClick = { viewModel.kind(kind) }, label = { Text(name) })
                    }
                }
            }
            state.page?.let { page ->
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(page.categories, key = { it.id }) { category ->
                            FilterChip(selected = state.query.category == category.id,
                                onClick = { viewModel.category(category.id) },
                                label = { Text("${categorySymbol(category.id)} ${category.name} ${category.count}") })
                        }
                    }
                }
                if (state.query.isHome) {
                    if (page.continueListening.isNotEmpty()) {
                        item { Text("继续收听", style = MaterialTheme.typography.titleLarge) }
                        item { HorizontalBooks(page.continueListening, viewModel, open) }
                    }
                    items(page.sections, key = { "section:${it.id}" }) { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${categorySymbol(section.id)} ${section.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.category(section.id) }) { Text("查看全部") }
                            }
                            HorizontalBooks(section.items, viewModel, open)
                        }
                    }
                    item { Text("${page.workCount} 个作品入口 · ${page.libraryCount} 个库条目", style = MaterialTheme.typography.bodySmall) }
                    item { Button(onClick = { viewModel.kind("book") }) { Text("浏览全部有声书") } }
                } else {
                    item { Text("${page.total} 个结果", style = MaterialTheme.typography.bodySmall) }
                    items(page.items.chunked(2), key = { it.first().identity }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { card ->
                                SpokenBookCard(card, viewModel.image(card), { open(card) }, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (page.hasMore) item {
                        Button(onClick = viewModel::more, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                    }
                    if (page.items.isEmpty()) item { Text("没有找到匹配内容，试试其他分类或关键词。") }
                }
            }
            state.error?.let { message -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) { Text("重试") }
                }
            } }
            if (state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun HorizontalBooks(cards: List<SpokenCard>, vm: SpokenShelvesViewModel, open: (SpokenCard) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.identity }) { card ->
            SpokenBookCard(card, vm.image(card), { open(card) }, Modifier.width(170.dp))
        }
    }
}

@Composable
private fun SpokenBookCard(card: SpokenCard, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(if (card.kind == "group") "▤" else "书", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            AsyncImage(model = image, contentDescription = "${card.name}封面", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(when {
                card.kind == "group" -> "${card.count} 个分卷 / 版本"
                card.version.isNotBlank() -> card.version
                card.authors.isNotEmpty() -> card.authors.joinToString("、")
                else -> card.contentKind
            }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            if (card.description.isNotBlank()) Text(card.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (card.resumePositionMs > 0) Text("已听 ${card.resumePositionMs / 60_000} 分钟", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun categorySymbol(id: String) = when (id) {
    "children" -> "🧸"
    "literature" -> "📖"
    "history" -> "🏛"
    "mystery" -> "🔍"
    "scifi" -> "🌌"
    "science" -> "🔬"
    "english" -> "🌐"
    "storytelling" -> "🎙"
    "commentary" -> "📝"
    "life" -> "☕"
    else -> "📚"
}
