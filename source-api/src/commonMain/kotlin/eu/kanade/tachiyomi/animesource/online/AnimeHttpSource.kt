package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.core.util.lang.awaitSingle
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * A simple implementation for sources from a website.
 */
@Suppress("unused")
abstract class AnimeHttpSource : AnimeCatalogueSource {

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId = 1

    /**
     * ID of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string `"${name.lowercase()}/$lang/$versionId"`.
     *
     * The ID is generated by the [generateId] function, which can be reused if needed
     * to generate outdated IDs for cases where the source name or language needs to
     * be changed but migrations can be avoided.
     *
     * Note: the generated ID sets the sign bit to `0`.
     */
    override val id by lazy { generateId(name, lang, versionId) }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Generates a unique ID for the source based on the provided [name], [lang] and
     * [versionId]. It will use the first 16 characters (64 bits) of the MD5 of the string
     * `"${name.lowercase()}/$lang/$versionId"`.
     *
     * Note: the generated ID sets the sign bit to `0`.
     *
     * Can be used to generate outdated IDs, such as when the source name or language
     * needs to be changed but migrations can be avoided.
     *
     * @since extensions-lib 1.5
     * @param name [String] the name of the source
     * @param lang [String] the language of the source
     * @param versionId [Int] the version ID of the source
     * @return a unique ID for the source
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.uppercase()})"

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return client.newCall(popularAnimeRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularAnimeParse(response)
            }
    }

    /**
     * Returns the request for the popular anime given the page.
     *
     * @param page the page number to retrieve.
     */
    protected abstract fun popularAnimeRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return Observable.defer {
            try {
                client.newCall(searchAnimeRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                // RxJava doesn't handle Errors, which tends to happen during global searches
                // if an old extension using non-existent classes is still around
                throw RuntimeException(e)
            }
        }
            .map { response ->
                searchAnimeParse(response)
            }
    }

    /**
     * Returns the request for the search anime given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    /**
     * Returns an observable containing a page with a list of latest anime updates.
     *
     * @param page the page number to retrieve.
     */
    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    /**
     * Returns the request for latest anime given the page.
     *
     * @param page the page number to retrieve.
     */
    protected abstract fun latestUpdatesRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    /**
     * Get the updated details for a anime.
     * Normally it's not needed to override this method.
     *
     * @param anime the anime to be updated.
     * @return the updated anime.
     */
    @Suppress("DEPRECATION")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return fetchAnimeDetails(anime).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getAnimeDetails"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asObservableSuccess()
            .map { response ->
                animeDetailsParse(response).apply { initialized = true }
            }
    }

    /**
     * Returns the request for the details of an anime. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param anime the anime to be updated.
     */
    open fun animeDetailsRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    /**
     * Parses the response from the site and returns the details of an anime.
     *
     * @param response the response from the site.
     */
    protected abstract fun animeDetailsParse(response: Response): SAnime

    /**
     * Get all the available episodes for an anime.
     * Normally it's not needed to override this method.
     *
     * @param anime the anime to update.
     * @return the chapters for the manga.
     * @throws LicensedEntryItemsException if a anime is licensed and therefore no episodes are available.
     */
    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        if (anime.status == SAnime.LICENSED) {
            throw LicensedEntryItemsException()
        }

        return fetchEpisodeList(anime).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return if (anime.status != SAnime.LICENSED) {
            client.newCall(episodeListRequest(anime))
                .asObservableSuccess()
                .map { response ->
                    episodeListParse(response)
                }
        } else {
            Observable.error(LicensedEntryItemsException())
        }
    }

    /**
     * Returns the request for updating the episode list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param anime the anime to look for episodes.
     */
    protected open fun episodeListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of episodes.
     *
     * @param response the response from the site.
     */
    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    /**
     * Parses the response from the site and returns a SEpisode Object.
     *
     * @param response the response from the site.
     */
    protected abstract fun episodeVideoParse(response: Response): SEpisode

    /**
     * Get the list of videos a episode has. Videos should be returned
     * in the expected order; the index is ignored.
     *
     * @param episode the episode.
     * @return the videos for the episode.
     */
    @Suppress("DEPRECATION")
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return fetchVideoList(episode).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        return client.newCall(videoListRequest(episode))
            .asObservableSuccess()
            .map { response ->
                videoListParse(response).sort()
            }
    }

    /**
     * Returns the request for getting the episode link. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param episode the episode to look for links.
     */
    protected open fun videoListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    protected abstract fun videoListParse(response: Response): List<Video>

    /**
     * Sorts the video list. Override this according to the user's preference.
     */
    protected open fun List<Video>.sort(): List<Video> {
        return this
    }

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @since extensions-lib 1.5
     * @param video the video whose source image has to be fetched.
     */
    @Suppress("DEPRECATION")
    open suspend fun getVideoUrl(video: Video): String {
        return fetchVideoUrl(video).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl"))
    open fun fetchVideoUrl(video: Video): Observable<String> {
        return client.newCall(videoUrlRequest(video))
            .asObservableSuccess()
            .map { videoUrlParse(it) }
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param video the chapter whose page list has to be fetched
     */
    protected open fun videoUrlRequest(video: Video): Request {
        return GET(video.url, headers)
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    protected abstract fun videoUrlParse(response: Response): String

    /**
     * Returns the response of the source video.
     * Typically does not need to be overridden.
     *
     * @since extensions-lib 1.5
     * @param video the video whose source video has to be downloaded.
     */
    open suspend fun getVideo(video: Video): Response {
        return client.newCachelessCallWithProgress(videoRequest(video), video)
            .awaitSuccess()
    }

    suspend fun getVideoChunk(video: Video, start: Long, end: Long, listener: ProgressListener): Response {
        val animeDownloadClient = client.newBuilder()
            .callTimeout(30, TimeUnit.MINUTES)
            .build();

        return animeDownloadClient.newCachelessCallWithProgress(chunkRequest(video, start, end), listener)
            .awaitSuccess();
    }

    private fun chunkRequest(video: Video, start: Long, end: Long): Request {
        val headers = video.headers ?: headers;
        val newHeaders = if (end - start > 0L) {
            Headers.Builder().addAll(headers).add("range", "bytes=$start-$end").build();
        } else {
//            logcat(LogPriority.ERROR) { "Error: end-start is less than 0" }
            null;
        }
        return GET(video.videoUrl!!, newHeaders ?: headers);
    }

   suspend fun getVideoSize(video: Video, tries: Int): Long {
        val animeDownloadClient = client.newBuilder()
            .callTimeout(30, TimeUnit.MINUTES)
            .build();
        val headers = Headers.Builder().addAll(video.headers ?: headers).add("Range", "bytes=0-1").build();
        val request = GET(video.videoUrl!!, headers);
        val response = animeDownloadClient.newCall(request).awaitSuccess();
        // parse the response headers to get the size of the video, in particular the content-range header
        val contentRange = response.header("Content-Range");
        if (contentRange != null) {
            return contentRange.split("/")[1].toLong();
        }
        if (tries > 0) {
            return getVideoSize(video, tries - 1);
        }
//        logcat(LogPriority.ERROR) { "Error: Content-Range header not found and exhausted tries" }
//        logcat { "Response headers: ${response.headers}" }
//        logcat { "Request headers: ${request.headers}" }
        return -1L
    }


    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param video the video whose link has to be fetched
     */
    protected open fun videoRequest(video: Video, bytes: Long = 0L): Request {
        val headers = video.headers ?: headers
        val newHeaders = if (bytes > 0L) {
            Headers.Builder().addAll(headers).add("Range", "bytes=$bytes-").build()
        } else {
            null
        }
        return GET(video.videoUrl!!, newHeaders ?: headers)
    }

    /**
     * Assigns the url of the episode without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the episode.
     */
    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Assigns the url of the anime without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the anime.
     */
    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Returns the url of the given string without the scheme and domain.
     *
     * @param orig the full url.
     */
    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    /**
     * Returns the url of the provided anime
     *
     * @since extensions-lib 14
     * @param anime the anime
     * @return url of the anime
     */
    open fun getAnimeUrl(anime: SAnime): String {
        return animeDetailsRequest(anime).url.toString()
    }

    /**
     * Returns the url of the provided episode
     *
     * @since extensions-lib 14
     * @param episode the episode
     * @return url of the episode
     */
    open fun getEpisodeUrl(episode: SEpisode): String {
        return episode.url.toString()
    }

    /**
     * Called before inserting a new episode into database. Use it if you need to override episode
     * fields, like the title or the episode number. Do not change anything to [anime].
     *
     * @param episode the episode to be added.
     * @param anime the anime of the episode.
     */
    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = AnimeFilterList()
}

class LicensedEntryItemsException : Exception("Licensed - No items to show")
