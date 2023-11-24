package eu.kanade.tachiyomi.data.track

import android.app.Application
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.toast
import logcat.LogPriority
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.items.chapter.interactor.GetChapterByMangaId
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.ZoneOffset
import tachiyomi.domain.track.manga.model.MangaTrack as DomainTrack

private val insertTrack: InsertMangaTrack by injectLazy()
private val syncChapterProgressWithTrack: SyncChapterProgressWithTrack by injectLazy()

interface MangaTracker {

    // Common functions
    fun getCompletionStatus(): Int

    fun getScoreList(): List<String>

    fun indexToScore(index: Int): Float {
        return index.toFloat()
    }

    // Manga specific functions
    fun getStatusListManga(): List<Int>

    fun getReadingStatus(): Int

    fun getRereadingStatus(): Int

    // TODO: Store all scores as 10 point in the future maybe?
    fun get10PointScore(track: DomainTrack): Double {
        return track.score
    }

    fun displayScore(track: MangaTrack): String

    suspend fun update(track: MangaTrack, didReadChapter: Boolean = false): MangaTrack

    suspend fun bind(track: MangaTrack, hasReadChapters: Boolean = false): MangaTrack

    suspend fun searchManga(query: String): List<MangaTrackSearch>

    suspend fun refresh(track: MangaTrack): MangaTrack

    // TODO: move this to an interactor, and update all trackers based on common data
    suspend fun register(item: MangaTrack, mangaId: Long) {
        item.manga_id = mangaId
        try {
            withIOContext {
                val allChapters = Injekt.get<GetChapterByMangaId>().await(mangaId)
                val hasReadChapters = allChapters.any { it.read }
                bind(item, hasReadChapters)

                var track = item.toDomainTrack(idRequired = false) ?: return@withIOContext

                insertTrack.await(track)

                // TODO: merge into SyncChaptersWithTrackServiceTwoWay?
                // Update chapter progress if newer chapters marked read locally
                if (hasReadChapters) {
                    val latestLocalReadChapterNumber = allChapters
                        .sortedBy { it.chapterNumber }
                        .takeWhile { it.read }
                        .lastOrNull()
                        ?.chapterNumber ?: -1.0

                    if (latestLocalReadChapterNumber > track.lastChapterRead) {
                        track = track.copy(
                            lastChapterRead = latestLocalReadChapterNumber,
                        )
                        setRemoteLastChapterRead(
                            track.toDbTrack(),
                            latestLocalReadChapterNumber.toInt(),
                        )
                    }

                    if (track.startDate <= 0) {
                        val firstReadChapterDate = Injekt.get<GetMangaHistory>().await(mangaId)
                            .sortedBy { it.readAt }
                            .firstOrNull()
                            ?.readAt

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

                syncChapterProgressWithTrack.await(mangaId, track, this@MangaTracker)
            }
        } catch (e: Throwable) {
            withUIContext { Injekt.get<Application>().toast(e.message) }
        }
    }

    suspend fun setRemoteMangaStatus(track: MangaTrack, status: Int) {
        track.status = status
        if (track.status == getCompletionStatus() && track.total_chapters != 0) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteLastChapterRead(track: MangaTrack, chapterNumber: Int) {
        if (track.last_chapter_read == 0f && track.last_chapter_read < chapterNumber && track.status != getRereadingStatus()) {
            track.status = getReadingStatus()
        }
        track.last_chapter_read = chapterNumber.toFloat()
        if (track.total_chapters != 0 && track.last_chapter_read.toInt() == track.total_chapters) {
            track.status = getCompletionStatus()
            track.finished_reading_date = System.currentTimeMillis()
        }
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteScore(track: MangaTrack, scoreString: String) {
        track.score = indexToScore(getScoreList().indexOf(scoreString))
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteStartDate(track: MangaTrack, epochMillis: Long) {
        track.started_reading_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    suspend fun setRemoteFinishDate(track: MangaTrack, epochMillis: Long) {
        track.finished_reading_date = epochMillis
        withIOContext { updateRemote(track) }
    }

    private suspend fun updateRemote(track: MangaTrack) {
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
