package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.GlobalAnimeSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen

class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
    val extensionFilter: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            GlobalAnimeSearchScreenModel(
                initialQuery = searchQuery,
                initialExtensionFilter = extensionFilter,
            )
        }
        val state by screenModel.state.collectAsState()

        GlobalAnimeSearchScreen(
            state = state,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = screenModel::search,
            getAnime = { screenModel.getAnime(it) },
            onClickSource = {
                if (!screenModel.incognitoMode.get()) {
                    screenModel.lastUsedSourceId.set(it.id)
                }
                navigator.push(BrowseAnimeSourceScreen(it.id, state.searchQuery))
            },
            onClickItem = { navigator.push(AnimeScreen(it.id, true)) },
            onLongClickItem = { navigator.push(AnimeScreen(it.id, true)) },
        )
    }
}
