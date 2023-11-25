package eu.kanade.tachiyomi.data.track.kavita

import android.graphics.Color
import androidx.annotation.StringRes
import com.google.common.base.Strings.isNullOrEmpty
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

class Kavita(id: Long) : BaseTracker(id, "Kavita"), EnhancedMangaTracker, MangaTracker {

    companion object {
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
    }

    var authentications: OAuth? = null

    private val interceptor by lazy { KavitaInterceptor(this) }
    val api by lazy { KavitaApi(client, interceptor) }

    private val sourceManager: MangaSourceManager by injectLazy()

    override fun getLogo(): Int = R.drawable.ic_tracker_kavita

    override fun getLogoColor() = Color.rgb(74, 198, 148)

    override fun getStatusListManga() = listOf(UNREAD, READING, COMPLETED)

    @StringRes
    override fun getStatus(status: Int): Int? = when (status) {
        UNREAD -> R.string.unread
        READING -> R.string.reading
        COMPLETED -> R.string.completed
        else -> null
    }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = -1

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> = emptyList()

    override fun displayScore(track: MangaTrack): String = ""

    override suspend fun update(track: MangaTrack, didReadChapter: Boolean): MangaTrack {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }
        return api.updateProgress(track)
    }

    override suspend fun bind(track: MangaTrack, hasReadChapters: Boolean): MangaTrack {
        return track
    }

    override suspend fun searchManga(query: String): List<MangaTrackSearch> {
        TODO("Not yet implemented: search")
    }

    override suspend fun refresh(track: MangaTrack): MangaTrack {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    // [Tracker].isLogged works by checking that credentials are saved.
    // By saving dummy, unused credentials, we can activate the tracker simply by login/logout
    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.extension.all.kavita.Kavita")

    override suspend fun match(manga: Manga): MangaTrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (e: Exception) {
            null
        }

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: MangaSource?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: MangaSource): DomainTrack? =
        if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }

    fun loadOAuth() {
        val oauth = OAuth()
        for (id in 1..3) {
            val authentication = oauth.authentications[id - 1]
            val sourceId by lazy {
                val key = "kavita_$id/all/1" // Hardcoded versionID to 1
                val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
                (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                    .reduce(Long::or) and Long.MAX_VALUE
            }
            val preferences = (sourceManager.get(sourceId) as ConfigurableSource).sourcePreferences()

            val prefApiUrl = preferences.getString("APIURL", "")
            val prefApiKey = preferences.getString("APIKEY", "")
            if (prefApiUrl.isNullOrEmpty() || prefApiKey.isNullOrEmpty()) {
                // Source not configured. Skip
                continue
            }

            val token = api.getNewToken(apiUrl = prefApiUrl, apiKey = prefApiKey)
            if (token.isNullOrEmpty()) {
                // Source is not accessible. Skip
                continue
            }

            authentication.apiUrl = prefApiUrl
            authentication.jwtToken = token.toString()
        }
        authentications = oauth
    }
}
