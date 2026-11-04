package com.streamplay.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun HomeScreen(vm: CatalogViewModel, onPlay: (Channel) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(vm.playlistUrl.isBlank()) }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Rechercher") }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showDialog = true }) { Text("Playlist") }
            }

            TabRow(selectedTabIndex = tab) {
                val titles = listOf(
                    "Chaînes (${vm.live.size})",
                    "Films (${vm.movies.size})",
                    "Séries (${vm.series.size})"
                )
                titles.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i; openedSeries = null },
                        text = { Text(t, maxLines = 1) }
                    )
                }
            }

            when {
                vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                vm.errorMsg != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(vm.errorMsg.orEmpty())
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { vm.load() }) { Text("Réessayer") }
                }
                vm.playlistUrl.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("Ajoutez l'adresse de votre playlist M3U avec le bouton « Playlist ».")
                }
                else -> when (tab) {
                    0 -> ChannelList(vm.live, onPlay)
                    1 -> PosterGrid(vm.movies, onPlay)
                    else -> SeriesTab(vm.series, openedSeries, { openedSeries = it }, onPlay)
                }
            }
        }
    }

    if (showDialog) {
        PlaylistDialog(
            current = vm.playlistUrl,
            onDismiss = { showDialog = false },
            onSave = { vm.savePlaylist(it); showDialog = false }
        )
    }
}

@Composable
fun PlaylistDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist M3U") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("https://…/playlist.m3u") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("Charger") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun ChannelList(channels: List<Channel>, onClick: (Channel) -> Unit) {
    val grouped = channels.groupBy { it.group ?: "Autres" }
    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (group, items) ->
            item {
                Text(
                    group,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            items(items) { ch ->
                Row(
                    Modifier.fillMaxWidth().clickable { onClick(ch) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ch.logo,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PosterGrid(items: List<Channel>, onClick: (Channel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { m ->
            Column(Modifier.padding(4.dp).clickable { onClick(m) }) {
                AsyncImage(
                    model = m.logo,
                    contentDescription = m.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Text(m.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SeriesTab(
    series: Map<String, List<Channel>>,
    opened: String?,
    onOpen: (String?) -> Unit,
    onPlay: (Channel) -> Unit
) {
    if (opened == null || series[opened] == null) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(series.keys.sorted()) { name ->
                val first = series[name]!!.first()
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(name) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = first.logo,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${series[name]!!.size} épisode(s)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        BackHandler { onOpen(null) }
        val bySeason = series[opened].orEmpty().groupBy { it.season ?: 1 }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                TextButton(onClick = { onOpen(null) }) { Text("← $opened") }
            }
            bySeason.forEach { (season, eps) ->
                item {
                    Text(
                        "Saison $season",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                items(eps) { ep ->
                    Text(
                        "Épisode ${ep.episode ?: "?"}",
                        modifier = Modifier.fillMaxWidth().clickable { onPlay(ep) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
