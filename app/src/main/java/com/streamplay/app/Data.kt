package com.streamplay.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ContentType { LIVE, MOVIE, SERIES }

data class Channel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val type: ContentType = ContentType.LIVE,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null
)

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(content: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = ""
        var logo: String? = null
        var group: String? = null

        content.removePrefix("\uFEFF").lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"]?.ifBlank { null }
                    name = line.substringAfterLast(",").trim()
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    result += Channel(name.ifBlank { line.substringAfterLast("/") }, line, logo, group)
                    name = ""; logo = null; group = null
                }
            }
        }
        return result
    }
}

object Classifier {
    private val episodeRegex = Regex("""(.*?)[\s._-]*[Ss](\d{1,2})\s*[Ee](\d{1,3})""")
    private val altEpisodeRegex = Regex("""(.*?)[\s._-]*(\d{1,2})x(\d{1,3})""")

    private fun String.hasAny(vararg k: String) = k.any { contains(it) }

    fun classify(ch: Channel): Channel {
        val group = ch.group.orEmpty().lowercase()
        val url = ch.url.lowercase().substringBefore("?")

        val m = episodeRegex.find(ch.name) ?: altEpisodeRegex.find(ch.name)
        if (m != null) {
            return ch.copy(
                type = ContentType.SERIES,
                seriesName = m.groupValues[1].trim().ifEmpty { ch.name },
                season = m.groupValues[2].toInt(),
                episode = m.groupValues[3].toInt()
            )
        }
        if (group.hasAny("série", "serie", "series", "saison")) {
            return ch.copy(type = ContentType.SERIES, seriesName = ch.name)
        }
        if (group.hasAny("film", "movie", "vod", "cinéma", "cinema") ||
            url.hasAny("/movie/", "/movies/", "/vod/") ||
            url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi")
        ) {
            return ch.copy(type = ContentType.MOVIE)
        }
        return ch.copy(type = ContentType.LIVE)
    }
}

class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("streamplay", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    var playlistUrl by mutableStateOf(prefs.getString("url", "") ?: "")
        private set
    var all by mutableStateOf<List<Channel>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set
    var query by mutableStateOf("")

    init {
        if (playlistUrl.isNotBlank()) load()
    }

    fun savePlaylist(url: String) {
        playlistUrl = url.trim()
        prefs.edit().putString("url", playlistUrl).apply()
        load()
    }

    fun load() {
        if (playlistUrl.isBlank()) return
        viewModelScope.launch {
            loading = true
            errorMsg = null
            try {
                val items = withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(playlistUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                        .build()
                    client.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                        M3uParser.parse(r.body!!.string()).map(Classifier::classify)
                    }
                }
                all = items
                if (items.isEmpty()) errorMsg = "Aucune entrée trouvée dans la playlist"
            } catch (e: Exception) {
                errorMsg = "Chargement impossible : ${e.message}"
            }
            loading = false
        }
    }

    private fun filtered(type: ContentType): List<Channel> {
        val q = query.trim().lowercase()
        return all.filter {
            it.type == type &&
                (q.isEmpty() || it.name.lowercase().contains(q) ||
                    it.seriesName.orEmpty().lowercase().contains(q))
        }
    }

    val live: List<Channel> get() = filtered(ContentType.LIVE)
    val movies: List<Channel> get() = filtered(ContentType.MOVIE)
    val series: Map<String, List<Channel>>
        get() = filtered(ContentType.SERIES)
            .groupBy { it.seriesName ?: it.name }
            .mapValues { (_, eps) -> eps.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 })) }
}
