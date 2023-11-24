package tachiyomi.data.source.manga

import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.model.StubMangaSource

val mangaSourceMapper: (eu.kanade.tachiyomi.source.MangaSource) -> Source = { source ->
    Source(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}

val mangaSourceDataMapper: (Long, String, String) -> StubMangaSource = { id, lang, name ->
    StubMangaSource(id, lang, name)
}
