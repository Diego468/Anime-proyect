package eu.kanade.tachiyomi.ui.browse.migration.search

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.anime.model.AnimeUpdate
import eu.kanade.domain.anime.model.hasCustomCover
import eu.kanade.domain.anime.model.toDbAnime
import eu.kanade.domain.animetrack.interactor.GetAnimeTracks
import eu.kanade.domain.animetrack.interactor.InsertAnimeTrack
import eu.kanade.domain.category.interactor.GetCategoriesAnime
import eu.kanade.domain.category.interactor.MoveAnimeToCategories
import eu.kanade.domain.episode.interactor.GetEpisodeByAnimeId
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.episode.interactor.UpdateEpisode
import eu.kanade.domain.episode.model.toEpisodeUpdate
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.toSEpisode
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.toAnimeInfo
import eu.kanade.tachiyomi.data.database.models.toDomainAnime
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchCardItem
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchItem
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchPresenter
import eu.kanade.tachiyomi.ui.browse.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date

class AnimeSearchPresenter(
    initialQuery: String? = "",
    private val anime: Anime,
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val getEpisodeByAnimeId: GetEpisodeByAnimeId = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getCategories: GetCategoriesAnime = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val insertTrack: InsertAnimeTrack = Injekt.get(),
    private val moveAnimeToCategories: MoveAnimeToCategories = Injekt.get(),
) : GlobalAnimeSearchPresenter(initialQuery) {

    private val replacingAnimeRelay = BehaviorRelay.create<Pair<Boolean, Anime?>>()
    private val coverCache: AnimeCoverCache by injectLazy()
    private val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        replacingAnimeRelay.subscribeLatestCache(
            { controller, (isReplacingAnime, newAnime) ->
                (controller as? AnimeSearchController)?.renderIsReplacingAnime(isReplacingAnime, newAnime)
            },
        )
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        // Put the source of the selected anime at the top
        return super.getEnabledSources()
            .sortedByDescending { it.id == anime.source }
    }

    override fun createCatalogueSearchItem(source: AnimeCatalogueSource, results: List<GlobalAnimeSearchCardItem>?): GlobalAnimeSearchItem {
        // Set the catalogue search item as highlighted if the source matches that of the selected anime
        return GlobalAnimeSearchItem(source, results, source.id == anime.source)
    }

    override fun networkToLocalAnime(sAnime: SAnime, sourceId: Long): Anime {
        val localAnime = super.networkToLocalAnime(sAnime, sourceId)
        // For migration, displayed title should always match source rather than local DB
        localAnime.title = sAnime.title
        return localAnime
    }

    fun migrateAnime(prevAnime: Anime, anime: Anime, replace: Boolean) {
        val source = sourceManager.get(anime.source) ?: return
        val prevSource = sourceManager.get(prevAnime.source)

        replacingAnimeRelay.call(Pair(true, null))

        presenterScope.launchIO {
            try {
                val episodes = source.getEpisodeList(anime.toAnimeInfo())
                    .map { it.toSEpisode() }

                migrateAnimeInternal(prevSource, source, episodes, prevAnime, anime, replace)
            } catch (e: Throwable) {
                withUIContext { view?.applicationContext?.toast(e.message) }
            }

            presenterScope.launchUI { replacingAnimeRelay.call(Pair(false, anime)) }
        }
    }

    private suspend fun migrateAnimeInternal(
        prevSource: AnimeSource?,
        source: AnimeSource,
        sourceEpisodes: List<SEpisode>,
        prevAnime: Anime,
        anime: Anime,
        replace: Boolean,
    ) {
        val flags = preferences.migrateFlags().get()

        val migrateEpisodes = AnimeMigrationFlags.hasEpisodes(flags)
        val migrateCategories = AnimeMigrationFlags.hasCategories(flags)
        val migrateTracks = AnimeMigrationFlags.hasTracks(flags)
        val migrateCustomCover = AnimeMigrationFlags.hasCustomCover(flags)

        val prevDomainAnime = prevAnime.toDomainAnime() ?: return
        val domainAnime = anime.toDomainAnime() ?: return

        try {
            syncEpisodesWithSource.await(sourceEpisodes, domainAnime, source)
        } catch (e: Exception) {
            // Worst case, episodes won't be synced
        }

        // Update episodes seen, bookmark and dateFetch
        if (migrateEpisodes) {
            val prevAnimeEpisodes = getEpisodeByAnimeId.await(prevDomainAnime.id)
            val animeEpisodes = getEpisodeByAnimeId.await(domainAnime.id)

            val maxEpisodeSeen = prevAnimeEpisodes
                .filter { it.seen }
                .maxOfOrNull { it.episodeNumber }

            val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                var updatedEpisode = animeEpisode
                if (updatedEpisode.isRecognizedNumber) {
                    val prevEpisode = prevAnimeEpisodes
                        .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                    if (prevEpisode != null) {
                        updatedEpisode = updatedEpisode.copy(
                            dateFetch = prevEpisode.dateFetch,
                            bookmark = prevEpisode.bookmark,
                        )
                    }

                    if (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen) {
                        updatedEpisode = updatedEpisode.copy(seen = true)
                    }
                }

                updatedEpisode
            }

            val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
            updateEpisode.awaitAll(episodeUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(prevDomainAnime.id).map { it.id }
            moveAnimeToCategories.await(domainAnime.id, categoryIds)
        }

        // Update track
        if (migrateTracks) {
            val tracks = getTracks.await(prevDomainAnime.id).mapNotNull { track ->
                val updatedTrack = track.copy(animeId = domainAnime.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, prevDomainAnime, prevSource) }

                if (service != null) service.migrateTrack(updatedTrack, domainAnime, source)
                else updatedTrack
            }
            insertTrack.awaitAll(tracks)
        }

        if (replace) {
            updateAnime.await(AnimeUpdate(prevDomainAnime.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && prevDomainAnime.hasCustomCover()) {
            @Suppress("BlockingMethodInNonBlockingContext")
            coverCache.setCustomCoverToCache(domainAnime.toDbAnime(), coverCache.getCustomCoverFile(prevDomainAnime.id).inputStream())
        }

        updateAnime.await(
            AnimeUpdate(
                id = domainAnime.id,
                favorite = true,
                episodeFlags = prevDomainAnime.episodeFlags,
                viewerFlags = prevDomainAnime.viewerFlags,
                dateAdded = if (replace) prevDomainAnime.dateAdded else Date().time,
            ),
        )
    }
}
