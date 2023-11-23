package eu.kanade.domain.track.anime.interactor

import android.content.Context
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.service.DelayedAnimeTrackingUpdateJob
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.util.system.isOnline
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack

class TrackEpisode(
    private val getTracks: GetAnimeTracks,
    private val trackManager: TrackManager,
    private val insertTrack: InsertAnimeTrack,
    private val delayedTrackingStore: DelayedAnimeTrackingStore,
) {

    suspend fun await(context: Context, animeId: Long, episodeNumber: Double) = coroutineScope {
        launchNonCancellable {
            val tracks = getTracks.await(animeId)

            if (tracks.isEmpty()) return@launchNonCancellable

            tracks.mapNotNull { track ->
                val service = trackManager.getService(track.syncId)
                if (service != null && service.isLoggedIn && episodeNumber > track.lastEpisodeSeen) {
                    val updatedTrack = track.copy(lastEpisodeSeen = episodeNumber)

                    async {
                        runCatching {
                            if (context.isOnline()) {
                                service.animeService.update(updatedTrack.toDbTrack(), true)
                                insertTrack.await(updatedTrack)
                            } else {
                                delayedTrackingStore.addAnimeItem(updatedTrack)
                                DelayedAnimeTrackingUpdateJob.setupTask(context)
                            }
                        }
                    }
                } else {
                    null
                }
            }
                .awaitAll()
                .mapNotNull { it.exceptionOrNull() }
                .forEach { logcat(LogPriority.INFO, it) }
        }
    }
}
