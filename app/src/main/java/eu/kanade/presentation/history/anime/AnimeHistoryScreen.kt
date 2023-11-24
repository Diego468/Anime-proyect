package eu.kanade.presentation.history.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import tachiyomi.core.preference.InMemoryPreferenceStore
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.ThemePreviews
import java.util.Date

@Composable
fun AnimeHistoryScreen(
    state: AnimeHistoryScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String? = null,
    snackbarHostState: SnackbarHostState,
    onClickCover: (animeId: Long) -> Unit,
    onClickResume: (animeId: Long, episodeId: Long) -> Unit,
    onDialogChange: (AnimeHistoryScreenModel.Dialog?) -> Unit,
    preferences: UiPreferences,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { _ ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!searchQuery.isNullOrEmpty()) {
                    R.string.no_results_found
                } else {
                    R.string.information_no_recent_anime
                }
                EmptyScreen(
                    textResource = msg,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                AnimeHistoryContent(
                    history = it,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.animeId) },
                    onClickResume = { history -> onClickResume(history.animeId, history.episodeId) },
                    onClickDelete = { item ->
                        onDialogChange(
                            AnimeHistoryScreenModel.Dialog.Delete(item),
                        )
                    },
                    preferences = preferences,
                )
            }
        }
    }
}

sealed interface AnimeHistoryUiModel {
    data class Header(val date: Date) : AnimeHistoryUiModel
    data class Item(val item: AnimeHistoryWithRelations) : AnimeHistoryUiModel
}

@ThemePreviews
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(AnimeHistoryScreenModelStateProvider::class)
    historyState: AnimeHistoryScreenModel.State,
) {
    TachiyomiTheme {
        AnimeHistoryScreen(
            state = historyState,
            contentPadding = topSmallPaddingValues,
            snackbarHostState = SnackbarHostState(),
            searchQuery = null,
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            preferences = UiPreferences(
                InMemoryPreferenceStore(
                    sequenceOf(
                        InMemoryPreferenceStore.InMemoryPreference(
                            key = "relative_time_v2",
                            data = false,
                            defaultValue = false,
                        ),
                    ),
                ),
            ),
        )
    }
}
