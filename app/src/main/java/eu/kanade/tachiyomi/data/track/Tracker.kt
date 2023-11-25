package eu.kanade.tachiyomi.data.track

import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import okhttp3.OkHttpClient

interface Tracker {

    val id: Long

    val name: String

    val client: OkHttpClient

    // Application and remote support for reading dates
    val supportsReadingDates: Boolean

    @DrawableRes
    fun getLogo(): Int

    @ColorInt
    fun getLogoColor(): Int

    @StringRes
    fun getStatus(status: Int): Int?
    fun getCompletionStatus(): Int
    fun getScoreList(): List<String>
    suspend fun login(username: String, password: String)

    @CallSuper
    fun logout()

    val isLoggedIn: Boolean

    fun getUsername(): String

    fun getPassword(): String

    fun saveCredentials(username: String, password: String)

    val animeService: AnimeTracker
        get() = this as AnimeTracker

    val mangaService: MangaTracker
        get() = this as MangaTracker
}
