package eu.kanade.tachiyomi.extension.all.kavita

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import com.google.gson.Gson
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.all.kavita.dto.AuthenticationDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.KavitaComicsSearch
import eu.kanade.tachiyomi.extension.all.kavita.dto.LibraryDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.MangaFormat
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesMetadataDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.VolumeDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLEncoder

class Kavita : ConfigurableSource, HttpSource() {

    private var libraries = emptyList<LibraryDto>()
    private var series = emptyList<SeriesDto>()

    override fun popularMangaRequest(page: Int): Request {
        println("popularMangaRequest Page: $page")
        val pageNum = helper.convertPagination(page)

        return POST(
            "$baseUrl/series/all?pageNumber=$pageNum&libraryId=0&pageSize=20",
            headersBuilder().build(),
            buildFilterBody()
        )
    }

    // Our popular manga are just our library of manga
    override fun popularMangaParse(response: Response): MangasPage {
        if (response.isSuccessful.not()) {
            println("Exception")
            throw Exception("HTTP ${response.code}")
        }

        val result = response.parseAs<List<SeriesDto>>()
        series = result
        val mangaList = result.map { item -> helper.createSeriesDto(item, baseUrl) }
        return MangasPage(mangaList, helper.hasNextPage(response))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        println("latestUpdatesRequest Page: $page")
        val pageNum = helper.convertPagination(page)

        return POST(
            "$baseUrl/series/recently-added?pageNumber=$pageNum&libraryId=0&pageSize=20",
            headersBuilder().build(),
            buildFilterBody()
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (response.isSuccessful.not()) {
            println("Exception")
            throw Exception("HTTP ${response.code}")
        }

        val result = response.parseAs<List<SeriesDto>>()
        series = result
        val mangaList = result.map { item -> helper.createSeriesDto(item, baseUrl) }
        return MangasPage(mangaList, helper.hasNextPage(response))
    }

    /**
     * SEARCH MANGA
     * **/

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/Library/search?queryString=$query", headers)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<KavitaComicsSearch>>()
        val mangaList = result.map(::searchMangaFromObject)
        return MangasPage(mangaList, false)
    }
    private fun searchMangaFromObject(obj: KavitaComicsSearch): SManga = SManga.create().apply {
        title = obj.name
        thumbnail_url = "$baseUrl/Image/series-cover?seriesId=${obj.seriesId}"
        println("url")
        println(thumbnail_url)
        description = "None"
        println(description)
        url = "${obj.seriesId}"
    }
    /**
     * MANGA DETAILS (metadata about series)
     * **/
    override fun mangaDetailsRequest(manga: SManga): Request {
        println("mangaDetailsRequest")
        println(manga.url)

        return GET("$baseUrl/series/metadata?seriesId=${helper.getIdFromUrl(manga.url)}", headersBuilder().build())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        println("mangaDetailsParse")
        // This is metadata
        val result = response.parseAs<SeriesMetadataDto>()
        val existingSeries = series.find { dto -> dto.id == result.seriesId }

        if (existingSeries != null) {
            println("Found existing series")
            val manga = helper.createSeriesDto(existingSeries, baseUrl)
            manga.artist = result.artists.joinToString { ", " }
            manga.author = result.writers.joinToString { ", " }
            manga.genre = result.genres.joinToString { ", " }
            return manga
        }

        return SManga.create().apply {
            url = "$baseUrl/Series/${result.seriesId}"
            artist = result.artists.joinToString { ", " }
            author = result.writers.joinToString { ", " }
            genre = result.genres.joinToString { ", " }
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${result.seriesId}"
        }
    }

    // The chapter url will contain how many pages the chapter contains for our page list endpoint
    /**
     * CHAPTER LIST
     * **/
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/Series/volumes?seriesId=${helper.getIdFromUrl(manga.url)}"
        return GET(url, headersBuilder().build())
    }

    private fun chapterFromObject(obj: ChapterDto): SChapter = SChapter.create().apply {
        println("Chapter")
        println(obj)
        url = obj.id.toString()
        if (obj.number == "0" && obj.isSpecial) {
            name = obj.range
        } else {
            name = "Chapter ${obj.number}"
        }
        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = obj.pages.toString()
    }

    private fun chapterFromVolume(obj: ChapterDto, volume: VolumeDto): SChapter = SChapter.create().apply {
//        println("Volume")
//        println(volume)
//        println("Chapter")
//        println(obj)

        // If there are multiple chapters to this volume, then prefix with Volume number
        if (volume.chapters.isNotEmpty() && obj.number != "0") {
            name = "Volume ${volume.number} Chapter ${obj.number}"
        } else if (obj.number == "0") {
            // This chapter is solely on volume
            if (volume.number == 0) {
                // Treat as special
                if (obj.range == "") {
                    name = "Chapter 0"
                } else {
                    name = obj.range
                }
            } else {
                name = "Volume ${obj.number}"
            }
        } else {
            name = "Unhandled Else Volume ${obj.number}"
        }

        url = obj.id.toString()
        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = obj.pages.toString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        println("chapterListParse")
        try {

            val volumes = response.parseAs<List<VolumeDto>>()

            val allChapterList = mutableListOf<SChapter>()

            volumes.forEach { volume ->
                run {
                    if (volume.number == 0) {
                        // Regular chapters
                        volume.chapters.map {
                            allChapterList.add(chapterFromObject(it))
                        }
                    } else {
                        // Volume chapter
                        volume.chapters.map {
                            allChapterList.add(chapterFromVolume(it, volume))
                        }
                    }
                }
            }

            allChapterList.sortBy { chapter -> chapter.chapter_number }

            println(allChapterList)
            return allChapterList
        } catch (e: Exception) {
            println("EXCEPTION")
            println(e)
            throw e
        }
    }

    /**
     * ACTUAL IMAGE OF PAGES REQUEST
     * **/
    override fun pageListRequest(chapter: SChapter): Request {
        println("pageListRequest")
        println(chapter)
        return GET("${chapter.url}/Reader/chapter-info")
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        println("fetchPageList")
        println(chapter)
        val chapterId = chapter.url
        val numPages = chapter.scanlator?.toInt()
        val pages = mutableListOf<Page>()
        for (i in 0..numPages!!) {
            pages.add(
                Page(
                    index = i,
                    imageUrl = "$baseUrl/Reader/image?chapterId=$chapterId&page=$i"
                )
            )
        }
        return Observable.just(pages)
    }

    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = ""

    /**
     * UNUSED FILTER
     * **/
    override fun getFilterList(): FilterList = FilterList()

    private val LOG_TAG = "extension.all.kavita"
    override val name = "Kavita"
    override val lang = "all"
    override val supportsLatest = true
    override val baseUrl by lazy { getPrefBaseUrl() }
    private val jwtToken by lazy { getPrefToken() }
    private val apiKey by lazy { getPrefApiKey() }
    private val gson by lazy { Gson() }
    private val json: Json by injectLazy()

    private val helper = KavitaHelper()
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body?.string().orEmpty())
    }

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Tachiyomi Kavita v${BuildConfig.VERSION_NAME}")
            .add("Content-Type", "application/json")
            .add("Authorization", "Bearer $jwtToken")
    }

    private fun buildFilterBody(): RequestBody {
        val payload = buildJsonObject {
            put("mangaFormat", MangaFormat.Image.ordinal)
        }
        return payload.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .addInterceptor { authIntercept(it) }
            .build()

    private fun authenticateAndSetToken(chain: Interceptor.Chain): Boolean {
        val jsonObject = JSONObject()
            .put("Authorization", "Bearer $jwtToken")
        println("Performing Authentication...")
        val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = POST("$baseUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=${URLEncoder.encode("Tachiyomi-Kavita", "utf-8")}", headersBuilder().build(), body)

        val response = chain.proceed(request)
        val requestSuccess = response.code == 200
        if (requestSuccess) {
            println("Authentication successful")
            val result = response.parseAs<AuthenticationDto>()
            if (result.token.isNotEmpty()) {
                preferences.edit().putString(BEARERTOKEN, result.token).commit()
                throw IOException("Restart Tachiyomi")
            }
        }
        response.close()
        return requestSuccess
    }
    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (apiKey.isEmpty()) {
            throw IOException("An API KEY is required to authenticate with Kavita")
        }
        if (jwtToken.isEmpty()) {
            authenticateAndSetToken(chain)
        }

        return chain.proceed(request)
    }

    // Preference code
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, "The URL to access your Kavita instance. Please include the port number if you didn't set up a reverse proxy")) // TODO("Check address. so support only domain/ip. Port could be on different user preference")
        screen.addPreference(screen.editTextPreference(APIKEY, "", "The API KEY copied from User Settings", true))
        // screen.addPreference(screen.editTextPreference(BEARERTOKEN, "", "The API KEY copied from User Settings", true))
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(title: String, default: String, summary: String, isPassword: Boolean = false): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            val input = preferences.getString(title, null)
            this.summary = if (input == null || input.isEmpty()) summary else input
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()

                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    // We strip the last slash since we will append it above
    private fun getPrefBaseUrl(): String {
        var path = preferences.getString(ADDRESS_TITLE, ADDRESS_DEFAULT)!!
        if (path.isNotEmpty() && path.last() == '/') {
            path = path.substring(0, path.length - 1)
        }
        return path
    }
    private fun getPrefApiKey(): String = preferences.getString(APIKEY, APIKEY_DEFAULT)!!
    private fun getPrefToken(): String = preferences.getString(BEARERTOKEN, BEARERTOKEN_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = "https://demo.kavitareader.com/api"
        private const val APIKEY = "API KEY"
        private const val APIKEY_DEFAULT = ""
        private const val BEARERTOKEN = "Token"
        private const val BEARERTOKEN_DEFAULT = ""

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
