package eu.kanade.tachiyomi.extension.all.kavita

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import com.google.gson.Gson
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.all.kavita.dto.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import info.debatty.java.stringsimilarity.JaroWinkler
import info.debatty.java.stringsimilarity.Levenshtein
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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
        var pageNum = helper.convertPagination(page)

        return POST(
            "$baseUrl/series/all?pageNumber=$pageNum&libraryId=0&pageSize=20",
            headersBuilder().build(),
            buildFilterBody()
        )
    }

    // Our popular manga are just our library of manga
    override fun popularMangaParse(response: Response): MangasPage {
        println("popularMangaParse")
        if (response.isSuccessful.not()) {
            println("Exception")
            throw Exception("HTTP ${response.code}")
        }

        val result = response.parseAs<List<SeriesDto>>()
        series = result
        var mangaList = result.map { item -> helper.createSeriesDto(item, baseUrl) }
        return MangasPage(mangaList, helper.hasNextPage(response))
    }

    /**
     * LATES UPDATES UNUSED MANGA
     * **/
    override fun latestUpdatesRequest(page: Int): Request =
        // TODO: Recently-added
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    /**
     * SEARCH MANGA NOT IMPLEMENTED YET SURELY THROWS EXCEPTION
     * **/
    // Default is to just return the whole library for searching
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException("Not used") // popularMangaRequest(1)

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList
    ): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    // Here the best we can do is just match manga based on their titles
    private fun searchMangaParse(response: Response, query: String): MangasPage {

        val queryLower = query.toLowerCase()
        val mangas = popularMangaParse(response).mangas
        val exactMatch = mangas.firstOrNull { it.title.toLowerCase() == queryLower }
        if (exactMatch != null) {
            return MangasPage(listOf(exactMatch), false)
        }

        // Text distance algorithms
        val textDistance = Levenshtein()
        val textDistance2 = JaroWinkler()

        // Take results that potentially start the same
        val results = mangas.filter {
            val title = it.title.toLowerCase()
            val query2 = queryLower.take(7)
            (title.startsWith(query2, true) || title.contains(query2, true))
        }.sortedBy { textDistance.distance(queryLower, it.title.toLowerCase()) }

        // Take similar results
        val results2 =
            mangas.map { Pair(textDistance2.distance(it.title.toLowerCase(), query), it) }
                .filter { it.first < 0.3 }.sortedBy { it.first }.map { it.second }
        val combinedResults = results.union(results2)

        // Finally return the list
        return MangasPage(combinedResults.toList(), false)
    }

    // Stub
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    /**
     * MANGA DETAILS
     * **/
    override fun mangaDetailsRequest(manga: SManga): Request {
        println("mangaDetailsRequest")
        println(manga.url)

        return GET("$baseUrl/series/metadata?seriesId=${getIdFromImageUrl(manga.url)}", headersBuilder().build())
    }

    private fun getIdFromImageUrl(url: String): Int {
        return url.split("/").last().toInt()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        println("mangaDetailsParse")
        // This is metadata
        val result = response.parseAs<SeriesMetadataDto>()
        var existingSeries = series.find { dto -> dto.id == result.seriesId }

        if (existingSeries != null) {
            println("Found existing series")
            var manga = helper.createSeriesDto(existingSeries, baseUrl)
            manga.artist = result.artists.joinToString { ", " }
            manga.author = result.writers.joinToString { ", " }
            manga.genre = result.genres.joinToString { ", " }
            return manga
        }

        return SManga.create().apply {
            url = "$baseUrl/Series/${result.id}"
            artist = result.artists.joinToString { ", " }
            author = result.writers.joinToString { ", " }
            genre = result.genres.joinToString { ", " }
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${result.id}"
        }
    }

    // The chapter url will contain how many pages the chapter contains for our page list endpoint
    /**
     * CHAPTER LIST
     * **/
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/Series/volumes?seriesId=${getIdFromImageUrl(manga.url)}"
        return GET(url, headersBuilder().build())
    }

    private fun chapterFromObject(obj: AggregateChapter): SChapter = SChapter.create().apply {
        url = obj.id.toString()
        name = "Chapter ${obj.range}"
        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = obj.pages.toString()
    }

    private fun chapterFromVolume(obj: AggregateChapter): SChapter = SChapter.create().apply {
        url = obj.id.toString()
        if (obj.number == "0" && obj.isSpecial) {
            name = obj.range
        } else {
            name = "Volume ${obj.number}"
        }

        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = obj.pages.toString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        println("chapterListParse")
        try {

            val volumes = response.parseAs<List<VolumeDto>>()

            val allChapterList = mutableListOf<SChapter>()

            // Goal: Flatten volumes into Chapters, sort specials, volumes, regular chapters
            // To accomplish flattening, volumes will have their individual chapters labelled Volume Number.Chapter Number

            val mangaList = volumes.map {

                print("Transforming volume from ")
                println(it)
                if (it.name == "0") {
                    // These are just chapters
                    it.chapters.map {
                        allChapterList.add(chapterFromVolume(it))
                    }
                } else {
                    it.chapters.map {
                        allChapterList.add(chapterFromObject(it))
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
    override val supportsLatest = false
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
            put("mangaFormat", MangaFormat.Archive.ordinal)
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
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, "The URL to access your Kavita instance. Please include the port number if you didn't set up a reverse proxy"))
        screen.addPreference(screen.editTextPreference(APIKEY, "", "The API KEY copied from User Settings", true))
        screen.addPreference(screen.editTextPreference(BEARERTOKEN, BEARERTOKEN_DEFAULT, "Your Token (Don't touch unless necessary)", true)) // TODO: Remove
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
