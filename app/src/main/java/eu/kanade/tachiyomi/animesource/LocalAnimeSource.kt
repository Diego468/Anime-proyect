package eu.kanade.tachiyomi.animesource

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.episode.EpisodeRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.logcat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalAnimeSource(private val context: Context) : AnimeCatalogueSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://tachiyomi.org/help/guides/local-anime/"

        private const val COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        fun updateCover(context: Context, anime: SAnime, input: InputStream): File? {
            val dir = getBaseDirectories(context).firstOrNull()
            if (dir == null) {
                input.close()
                return null
            }
            var cover = getCoverFile(File("${dir.absolutePath}/${anime.url}"))
            if (cover == null) {
                cover = File("${dir.absolutePath}/${anime.url}", COVER_NAME)
            }
            if (!cover.exists()) {
                // It might not exist if using the external SD card
                cover.parentFile?.mkdirs()
                input.use {
                    cover.outputStream().use {
                        input.copyTo(it)
                    }
                }
            }
            return cover
        }

        /**
         * Returns valid cover file inside [parent] directory.
         */
        private fun getCoverFile(parent: File): File? {
            return parent.listFiles()?.find { it.nameWithoutExtension == "cover" }?.takeIf {
                it.isFile && ImageUtil.isImage(it.name) { it.inputStream() }
            }
        }

        private fun getBaseDirectories(context: Context): List<File> {
            val c = context.getString(R.string.app_name) + File.separator + "localanime"
            return DiskUtil.getExternalStorages(context).map { File(it.absolutePath, c) }
        }
    }

    private val json: Json by injectLazy()

    override val id = ID
    override val name = context.getString(R.string.local_source)
    override val lang = "other"
    override val supportsLatest = true

    override fun toString() = name

    override fun fetchPopularAnime(page: Int) = fetchSearchAnime(page, "", POPULAR_FILTERS)

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        val baseDirs = getBaseDirectories(context)

        val time = if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L
        var animeDirs = baseDirs
            .asSequence()
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory }
            .filterNot { it.name.startsWith('.') }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) POPULAR_FILTERS else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                animeDirs = if (state.ascending) {
                    animeDirs.sortedBy { it.name.lowercase(Locale.ENGLISH) }
                } else {
                    animeDirs.sortedByDescending { it.name.lowercase(Locale.ENGLISH) }
                }
            }
            1 -> {
                animeDirs = if (state.ascending) {
                    animeDirs.sortedBy(File::lastModified)
                } else {
                    animeDirs.sortedByDescending(File::lastModified)
                }
            }
        }

        val animes = animeDirs.map { animeDir ->
            SAnime.create().apply {
                title = animeDir.name
                url = animeDir.name

                // Try to find the cover
                for (dir in baseDirs) {
                    val cover = getCoverFile(File("${dir.absolutePath}/$url"))
                    if (cover != null && cover.exists()) {
                        thumbnail_url = cover.absolutePath
                        break
                    }
                }

                val episodes = fetchEpisodeList(this).toBlocking().first()
                if (episodes.isNotEmpty()) {
                    val episode = episodes.last()
                    /*val format = getFormat(episode)
                    if (format is Format.Anime) {
                        AnimeFile(format.file).use { epub ->
                            epub.fillAnimeMetadata(this)
                        }
                    }*/

                    // Copy the cover from the first episode found.
                    if (thumbnail_url == null) {
                        try {
                            val dest = updateCover(episode, this)
                            thumbnail_url = dest?.absolutePath
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e)
                        }
                    }
                }
            }
        }

        return Observable.just(AnimesPage(animes.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchAnime(page, "", LATEST_FILTERS)

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        getBaseDirectories(context)
            .asSequence()
            .mapNotNull { File(it, anime.url).listFiles()?.toList() }
            .flatten()
            .firstOrNull { it.extension.lowercase() == "json" }
            ?.apply {
                val obj = json.decodeFromStream<JsonObject>(inputStream())

                anime.title = obj["title"]?.jsonPrimitive?.contentOrNull ?: anime.title
                anime.author = obj["author"]?.jsonPrimitive?.contentOrNull ?: anime.author
                anime.artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: anime.artist
                anime.description = obj["description"]?.jsonPrimitive?.contentOrNull ?: anime.description
                anime.genre = obj["genre"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content }
                    ?: anime.genre
                anime.status = obj["status"]?.jsonPrimitive?.intOrNull ?: anime.status
            }

        return Observable.just(anime)
    }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        val episodes = getBaseDirectories(context)
            .asSequence()
            .mapNotNull { file -> File(file, anime.url).listFiles()?.filter { isSupportedFile(it.extension) } }
            .flatten()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { episodeFile ->
                SEpisode.create().apply {
                    url = episodeFile.absolutePath
                    name = if (episodeFile.isDirectory) {
                        episodeFile.name
                    } else {
                        episodeFile.nameWithoutExtension
                    }
                    date_upload = episodeFile.lastModified()

                    /*val format = getFormat(this)
                    if (format is Format.Anime) {
                        AnimeFile(format.file).use { epub ->
                            epub.fillEpisodeMetadata(this)
                        }
                    }*/

                    name = getCleanEpisodeTitle(name, anime.title)
                    EpisodeRecognition.parseEpisodeNumber(this, anime)
                }
            }
            .sortedWith { e1, e2 ->
                val e = e2.episode_number.compareTo(e1.episode_number)
                if (e == 0) e2.name.compareToCaseInsensitiveNaturalOrder(e1.name) else e
            }
            .toList()

        return Observable.just(episodes)
    }

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        val video = mutableListOf(Video(episode.url, "local", episode.url, Uri.parse(episode.url)))
        return Observable.just(video)
    }

    /**
     * Strips the anime title from a episode name and trim whitespace/delimiter characters.
     */
    private fun getCleanEpisodeTitle(episodeName: String, animeTitle: String): String {
        return episodeName
            .replace(animeTitle, "")
            .trim(*WHITESPACE_CHARS.toCharArray(), '-', '_', ',', ':')
    }

    private fun isSupportedFile(extension: String): Boolean {
        return extension.lowercase(Locale.ROOT) in SUPPORTED_FILE_TYPES
    }

    fun getFormat(episode: SEpisode): Format {
        val baseDirs = getBaseDirectories(context)

        for (dir in baseDirs) {
            val episodeFile = File(dir, episode.url)
            if (!episodeFile.exists()) continue

            return getFormat(episodeFile)
        }
        throw Exception(context.getString(R.string.episode_not_found))
    }

    private fun getFormat(file: File) = with(file) {
        when {
            isDirectory -> Format.Directory(this)
            SUPPORTED_FILE_TYPES.contains(extension.lowercase(Locale.ENGLISH)) -> Format.Anime(this)
            else -> throw Exception(context.getString(R.string.local_invalid_episode_format))
        }
    }

    private fun updateCover(episode: SEpisode, anime: SAnime): File? {
        return when (val format = getFormat(episode)) {
            is Format.Directory -> {
                val entry = format.file.listFiles()
                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(context, anime, it.inputStream()) }
            }
            is Format.Anime -> {
                val entry = format.file.listFiles()
                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(context, anime, it.inputStream()) }
            }
        }
    }

    override fun getFilterList() = POPULAR_FILTERS

    private val POPULAR_FILTERS = AnimeFilterList(OrderBy(context))
    private val LATEST_FILTERS = AnimeFilterList(OrderBy(context).apply { state = AnimeFilter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : AnimeFilter.Sort(
        context.getString(R.string.local_filter_order_by),
        arrayOf(context.getString(R.string.title), context.getString(R.string.date)),
        Selection(0, true)
    )

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Anime(val file: File) : Format()
    }
}

private val SUPPORTED_FILE_TYPES = listOf("mp4", "mkv")

private val WHITESPACE_CHARS = arrayOf(
    ' ',
    '\u0009',
    '\u000A',
    '\u000B',
    '\u000C',
    '\u000D',
    '\u0020',
    '\u0085',
    '\u00A0',
    '\u1680',
    '\u2000',
    '\u2001',
    '\u2002',
    '\u2003',
    '\u2004',
    '\u2005',
    '\u2006',
    '\u2007',
    '\u2008',
    '\u2009',
    '\u200A',
    '\u2028',
    '\u2029',
    '\u202F',
    '\u205F',
    '\u3000',
)
