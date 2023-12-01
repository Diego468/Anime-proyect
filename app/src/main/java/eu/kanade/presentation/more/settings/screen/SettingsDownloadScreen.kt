package eu.kanade.presentation.more.settings.screen

import dev.icerock.moko.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import tachiyomi.i18n.MR
import tachiyomi.core.i18n.localize
import tachiyomi.presentation.core.i18n.localize

import kotlinx.coroutines.runBlocking
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.presentation.core.i18n.localizePlural
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetMangaCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(
            initial = runBlocking { getCategories.await() },
        )
        val getAnimeCategories = remember { Injekt.get<GetAnimeCategories>() }
        val allAnimeCategories by getAnimeCategories.subscribe().collectAsState(
            initial = runBlocking { getAnimeCategories.await() },
        )

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.downloadOnlyOverWifi(),
                title = localize(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.saveChaptersAsCBZ(),
                title = localize(MR.strings.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.splitTallImages(),
                title = localize(MR.strings.split_tall_images),
                subtitle = localize(MR.strings.split_tall_images_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                pref = downloadPreferences.numberOfDownloads(),
                title = localize(MR.strings.pref_download_slots),
                entries = (1..5).associateWith { it.toString() },
            ),
            Preference.PreferenceItem.InfoPreference(localize(MR.strings.download_slots_info)),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
                allAnimeCategories = allAnimeCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = localize(MR.strings.pref_category_delete_chapters),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeAfterMarkedAsRead(),
                    title = localize(MR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.removeAfterReadSlots(),
                    title = localize(MR.strings.pref_remove_after_read),
                    entries = mapOf(
                        -1 to localize(MR.strings.disabled),
                        0 to localize(MR.strings.last_read_chapter),
                        1 to localize(MR.strings.second_to_last),
                        2 to localize(MR.strings.third_to_last),
                        3 to localize(MR.strings.fourth_to_last),
                        4 to localize(MR.strings.fifth_to_last),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeBookmarkedChapters(),
                    title = localize(MR.strings.pref_remove_bookmarked_chapters),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            pref = downloadPreferences.removeExcludeCategories(),
            title = localize(MR.strings.pref_remove_exclude_categories_manga),
            entries = categories().associate { it.id.toString() to it.visualName },
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
        allAnimeCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewEpisodesPref = downloadPreferences.downloadNewEpisodes()
        val downloadNewEpisodeCategoriesPref = downloadPreferences.downloadNewEpisodeCategories()
        val downloadNewEpisodeCategoriesExcludePref = downloadPreferences.downloadNewEpisodeCategoriesExclude()

        val downloadNewEpisodes by downloadNewEpisodesPref.collectAsState()

        val includedAnime by downloadNewEpisodeCategoriesPref.collectAsState()
        val excludedAnime by downloadNewEpisodeCategoriesExcludePref.collectAsState()
        var showAnimeDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeDialog) {
            TriStateListDialog(
                title = localize(MR.strings.anime_categories),
                message = localize(MR.strings.pref_download_new_categories_details),
                items = allAnimeCategories,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                initialInversed = excludedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewEpisodeCategoriesPref.set(
                        newIncluded.fastMap { it.id.toString() }.toSet(),
                    )
                    downloadNewEpisodeCategoriesExcludePref.set(
                        newExcluded.fastMap { it.id.toString() }.toSet(),
                    )
                    showAnimeDialog = false
                },
            )
        }

        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = localize(MR.strings.manga_categories),
                message = localize(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(
                        newIncluded.fastMap { it.id.toString() }.toSet(),
                    )
                    downloadNewChapterCategoriesExcludePref.set(
                        newExcluded.fastMap { it.id.toString() }.toSet(),
                    )
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = localize(MR.strings.pref_category_auto_download),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewEpisodesPref,
                    title = localize(MR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = localize(MR.strings.anime_categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allAnimeCategories,
                        included = includedAnime,
                        excluded = excludedAnime,
                    ),
                    onClick = { showAnimeDialog = true },
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewChaptersPref,
                    title = localize(MR.strings.pref_download_new),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = localize(MR.strings.manga_categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showDialog = true },
                    enabled = downloadNewChapters,
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = localize(MR.strings.download_ahead),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.autoDownloadWhileReading(),
                    title = localize(MR.strings.auto_download_while_reading),
                    entries = listOf(0, 2, 3, 5, 10).associateWith {
                        if (it == 0) {
                            localize(MR.strings.disabled)
                        } else {
                            localizePlural(
                                MR.plurals.next_unread_chapters,
                                count = it,
                                it,
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.autoDownloadWhileWatching(),
                    title = localize(MR.strings.auto_download_while_watching),
                    entries = listOf(0, 2, 3, 5, 10).associateWith {
                        if (it == 0) {
                            localize(MR.strings.disabled)
                        } else {
                            localizePlural(
                                MR.plurals.next_unseen_episodes,
                                count = it,
                                it,
                            )
                        }
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    localize(MR.strings.download_ahead_info),
                ),
            ),
        )
    }

    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: DownloadPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val useExternalDownloader = downloadPreferences.useExternalDownloader()
        val externalDownloaderPreference = downloadPreferences.externalDownloaderSelection()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedDownloaders = installedPackages.filter {
            when (it.packageName) {
                "idm.internet.download.manager" -> true
                "idm.internet.download.manager.plus" -> true
                "idm.internet.download.manager.adm.lite" -> true
                "com.dv.adm" -> true
                else -> false
            }
        }
        val packageNames = supportedDownloaders.map { it.packageName }
        val packageNamesReadable = supportedDownloaders
            .map { pm.getApplicationLabel(it.applicationInfo).toString() }

        val packageNamesMap: Map<String, String> =
            packageNames.zip(packageNamesReadable)
                .toMap()

        return Preference.PreferenceGroup(
            title = localize(MR.strings.pref_category_external_downloader),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = useExternalDownloader,
                    title = localize(MR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = externalDownloaderPreference,
                    title = localize(MR.strings.pref_external_downloader_selection),
                    entries = mapOf("" to "None") + packageNamesMap,
                ),
            ),
        )
    }
}
