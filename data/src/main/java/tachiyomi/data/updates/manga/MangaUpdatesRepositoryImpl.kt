package tachiyomi.data.updates.manga

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository

class MangaUpdatesRepositoryImpl(
    private val databaseHandler: MangaDatabaseHandler,
) : MangaUpdatesRepository {

    override suspend fun awaitWithRead(read: Boolean, after: Long, limit: Long): List<MangaUpdatesWithRelations> {
        return databaseHandler.awaitList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = mangaUpdateWithRelationMapper,
            )
        }
    }

    override fun subscribeAllMangaUpdates(after: Long, limit: Long): Flow<List<MangaUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getRecentUpdates(after, limit, mangaUpdateWithRelationMapper)
        }
    }

    override fun subscribeWithRead(read: Boolean, after: Long, limit: Long): Flow<List<MangaUpdatesWithRelations>> {
        return databaseHandler.subscribeToList {
            updatesViewQueries.getUpdatesByReadStatus(
                read = read,
                after = after,
                limit = limit,
                mapper = mangaUpdateWithRelationMapper,
            )
        }
    }
}
