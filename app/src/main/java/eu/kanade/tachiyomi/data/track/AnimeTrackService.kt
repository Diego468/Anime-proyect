package eu.kanade.tachiyomi.data.track

import android.app.Application
import eu.kanade.domain.items.episode.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.items.episode.interactor.GetEpisodeByAnimeId
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.ZoneOffset
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

private val insertTrack: InsertAnimeTrack by injectLazy()
private val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack by injectLazy()

interface AnimeTrackService {

    // Common functions
    fun getCompletionStatus(): Int

    fun getScoreList(): List<String>

    fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    // Anime specific functions
    fun getStatusListAnime(): List<Int>

    fun getWatchingStatus(): Int

    fun getRewatchingStatus(): Int

    // TODO: Store all scores as 10 point in the future maybe?
    fun get10PointScore(track: DomainAnimeTrack): Double {
        return track.score
    }

    fun displayScore(track: AnimeTrack): String

    suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean = false): AnimeTrack

    suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean = false): AnimeTrack

    suspend fun searchAnime(query: String): List<AnimeTrackSearch>

    suspend fun refresh(track: AnimeTrack): AnimeTrack

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: AnimeTrack, animeId: Long) {
        item.anime_id = animeId
        try {
            withIOContext {
                val allEpisodes = Injekt.get<GetEpisodeByAnimeId>().await(animeId)
                val hasSeenEpisodes = allEpisodes.any { it.seen }
                bind(item, hasSeenEpisodes)

                var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

                insertTrack.await(track)

                // TODO: merge into SyncChaptersWithTrackServiceTwoWay?
                // Update episode progress if newer episodes marked seen locally
                if (hasSeenEpisodes) {
                    val latestLocalSeenEpisodeNumber = allEpisodes
                        .sortedBy { it.episodeNumber }
                        .takeWhile { it.seen }
                        .lastOrNull()
                        ?.episodeNumber ?: -1.0

                    if (latestLocalSeenEpisodeNumber > track.lastEpisodeSeen) {
                        track = track.copy(
                            lastEpisodeSeen = latestLocalSeenEpisodeNumber,
                        )
                        setRemoteLastEpisodeSeen(
                            track.toDbTrack(),
                            latestLocalSeenEpisodeNumber.toInt(),
                        )
                    }

                    if (track.startDate <= 0) {
                        val firstReadChapterDate = Injekt.get<GetAnimeHistory>().await(animeId)
                            .sortedBy { it.seenAt }
                            .firstOrNull()
                            ?.seenAt

                        firstReadChapterDate?.let {
                            val startDate = firstReadChapterDate.time.convertEpochMillisZone(
                                ZoneOffset.systemDefault(),
                                ZoneOffset.UTC,
                            )
                            track = track.copy(
                                startDate = startDate,
                            )
                            setRemoteStartDate(track.toDbTrack(), startDate)
                        }
                    }
                }

                syncEpisodeProgressWithTrack.await(animeId, track, this@AnimeTrackService)
            }
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    suspend fun setRemoteAnimeStatus(track: AnimeTrack, status: Int) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_episodes != 0) {
            track.last_episode_seen = track.total_episodes.toFloat()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteLastEpisodeSeen(track: AnimeTrack, episodeNumber: Int) {
        if (track.last_episode_seen == 0f && track.last_episode_seen < episodeNumber && track.status != getRewatchingStatus()) {
            track.status = getWatchingStatus()
        }
        track.last_episode_seen = episodeNumber.toFloat()
        if (track.total_episodes != 0 && track.last_episode_seen.toInt() == track.total_episodes) {
            track.status = getCompletionStatus()
            track.finished_watching_date = System.currentTimeMillis()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteScore(track: AnimeTrack, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteStartDate(track: AnimeTrack, epochMillis: Long) {
        track.started_watching_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteFinishDate(track: AnimeTrack, epochMillis: Long) {
        track.finished_watching_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    private suspend fun updateRemote(track: AnimeTrack) {
        withIOContext {
            try {
                update(track)
                track.toDomainTrack(idRequired = false)?.let {
                    insertTrack.await(it)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to update remote track data id=${track.id}" }
                withUIContext { Injekt.get<Application>().toast(e.message) }
            }
        }
    }
}
