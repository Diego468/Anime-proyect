package eu.kanade.domain.category.repository

import eu.kanade.domain.category.model.Category
import eu.kanade.domain.category.model.CategoryUpdate
import kotlinx.coroutines.flow.Flow

interface CategoryRepositoryAnime {

    fun getAll(): Flow<List<Category>>

    @Throws(DuplicateNameException::class)
    suspend fun insert(name: String, order: Long)

    @Throws(DuplicateNameException::class)
    suspend fun update(payload: CategoryUpdate)

    suspend fun delete(categoryId: Long)

    suspend fun getCategoriesForAnime(animeId: Long): List<Category>

    suspend fun checkDuplicateName(name: String): Boolean
}
