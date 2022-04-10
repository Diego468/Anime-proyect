package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.animesource.AnimeSourceManager
import eu.kanade.tachiyomi.animesource.LocalAnimeSource
import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.combineLatest
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.Collator
import java.util.Collections
import java.util.Locale

class MigrationSourcesPresenter(
    private val sourceManager: SourceManager = Injekt.get(),
    private val animesourceManager: AnimeSourceManager = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get(),
    private val animedb: AnimeDatabaseHelper = Injekt.get(),
) : BasePresenter<MigrationSourcesController>() {

    private val preferences: PreferencesHelper by injectLazy()

    private val sortRelay = BehaviorRelay.create(Unit)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        db.getFavoriteMangas()
            .asRxObservable()
            .combineLatest(sortRelay.observeOn(Schedulers.io())) { sources, _ -> sources }
            .observeOn(AndroidSchedulers.mainThread())
            .map { findSourcesWithManga(it) }
            .subscribeLatestCache(MigrationSourcesController::setSources)
        animedb.getFavoriteAnimes()
            .asRxObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .map { findSourcesWithAnime(it) }
            .subscribeLatestCache(MigrationSourcesController::setAnimeSources)
    }

    fun requestSortUpdate() {
        sortRelay.call(Unit)
    }

    private fun findSourcesWithManga(library: List<Manga>): List<SourceItem> {
        val header = SelectionHeader()
        return library
            .groupBy { it.source }
            .filterKeys { it != LocalSource.ID }
            .map {
                val source = sourceManager.getOrStub(it.key)
                SourceItem(source, it.value.size, header)
            }
            .sortedWith(sortFn())
            .toList()
    }

    private fun findSourcesWithAnime(library: List<Anime>): List<AnimeSourceItem> {
        return library
            .groupBy { it.source }
            .filterKeys { it != LocalAnimeSource.ID }
            .map {
                val source = animesourceManager.getOrStub(it.key)
                AnimeSourceItem(source, it.value.size)
            }
            .sortedWith(sortFnAnime())
            .toList()
    }

    private fun sortFn(): java.util.Comparator<SourceItem> {
        val sort by lazy {
            preferences.migrationSortingMode().get()
        }
        val direction by lazy {
            preferences.migrationSortingDirection().get()
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (SourceItem, SourceItem) -> Int = { a, b ->
            when (sort) {
                MigrationSourcesController.SortSetting.ALPHABETICAL -> collator.compare(a.source.name.lowercase(locale), b.source.name.lowercase(locale))
                MigrationSourcesController.SortSetting.TOTAL -> a.mangaCount.compareTo(b.mangaCount)
            }
        }

        return when (direction) {
            MigrationSourcesController.DirectionSetting.ASCENDING -> Comparator(sortFn)
            MigrationSourcesController.DirectionSetting.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }

    private fun sortFnAnime(): java.util.Comparator<AnimeSourceItem> {
        val sort by lazy {
            preferences.migrationSortingMode().get()
        }
        val direction by lazy {
            preferences.migrationSortingDirection().get()
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (AnimeSourceItem, AnimeSourceItem) -> Int = { a, b ->
            when (sort) {
                MigrationSourcesController.SortSetting.ALPHABETICAL -> collator.compare(a.source.name.lowercase(locale), b.source.name.lowercase(locale))
                MigrationSourcesController.SortSetting.TOTAL -> a.animeCount.compareTo(b.animeCount)
            }
        }

        return when (direction) {
            MigrationSourcesController.DirectionSetting.ASCENDING -> Comparator(sortFn)
            MigrationSourcesController.DirectionSetting.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }
}
