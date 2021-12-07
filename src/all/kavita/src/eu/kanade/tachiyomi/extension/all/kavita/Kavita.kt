package eu.kanade.tachiyomi.extension.all.kavita

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import com.github.salomonbrys.kotson.get
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.*

class Kavita : ConfigurableSource, HttpSource() {
    private val json: Json by injectLazy()
    private val apiToken: String?
        get() = preferences.getString("apiToken", "")
    private val helper = KavitaHelper()
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body?.string().orEmpty())
    }

    override fun popularMangaRequest(page: Int): Request {
        //    return GET("$baseUrl/browse?sort=views_w&page=$page")
        println("Popular Request")
        return POST("$baseUrl/Series/recently-added?PageNumber=$page&libraryId=0", headersBuilder().build(), tokenBodyBuilder())
    }
    // Our popular manga are just our library of manga
    override fun popularMangaParse(response: Response): MangasPage {
        println("Popular Parse")
        if (response.isSuccessful.not()) {
            println("Exception")
            throw Exception("HTTP ${response.code}")
        }

        val result = response.parseAs<List<KavitaComicsDto>>()

        /** KavitaComicsDto:
         val id: Int,
         val url: String,
         @SerialName("name") val title: String,
         // Api doesnt provides thumbnail url but it will -> WIP placeholder:
         val thumbnail_url: String? = "http://192.168.0.135:5000/api/image/series-cover?seriesId=14",
         @SerialName("summary") val description: String
         **/

        /**
         * Create list of SManga with values from KavitaComicsDto
         */
        val mangaList = result.map(::popularMangaFromObject)
        // mangaList provides a proper list of SManga

        return MangasPage(mangaList, false)
    }
    private fun popularMangaFromObject(obj: KavitaComicsDto): SManga = SManga.create().apply {
        title = obj.title
        thumbnail_url = "$baseUrl/Image/series-cover?seriesId=${obj.id}"
        println("url")
        println(thumbnail_url)
        description = obj.description
        println(obj.description)
        url = "${obj.id}"
    }
    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    // Default is to just return the whole library for searching
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used") // popularMangaRequest(1)

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
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
        val results2 = mangas.map { Pair(textDistance2.distance(it.title.toLowerCase(), query), it) }
            .filter { it.first < 0.3 }.sortedBy { it.first }.map { it.second }
        val combinedResults = results.union(results2)

        // Finally return the list
        return MangasPage(combinedResults.toList(), false)
    }

    // Stub
    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga): Request {
        println("mangaDetailsRequest")
        println(baseUrl)
        println(manga.url)

        return GET("$baseUrl/Series/${manga.url}", headersBuilder().build())
    }

    // This will just return the same thing as the main library endpoint
    private fun mangaDetailsFromObject(obj: KavitaComicsDetailsDto): SManga = SManga.create().apply {
        println("mangaDetailsFromObject")
        url = "/Series/${obj.id}"
        title = obj.name
        // artist = obj.artist
        // author = obj.author
        // description = obj.summary
        description = "This is description"
        // genre = obj.genres.joinToString(", ")
        // status = obj.status
        thumbnail_url = obj.thumbnail_url
    }

    override fun mangaDetailsParse(response: Response): SManga {
        println("mangaDetailsParse")
        // val jsonResponse = response

        // println(Gson().toJson(jsonResponse.body!!.string()))
        val result = response.parseAs<KavitaComicsDetailsDto>()
        println(result.name)
        val mangaDetails = mangaDetailsFromObject(result)
        println(mangaDetails.description)
        return mangaDetails

        // helper.createManga(manga.data, fetchSimpleChapterList(manga, ""), "dexLang", "coverSuffix")
    }

    /*override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + "/api" + manga.url + "?sort=auto", headers)*/

    // The chapter url will contain how many pages the chapter contains for our page list endpoint

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/Series/volumes?seriesId=${manga.url}"

        println("chapterListRequest")
        println(url)
        return GET(url, headersBuilder().build())

        // return actualChapterListRequest(helper.getUUIDFromUrl(manga.url), 0)
    }

    private fun chapterFromObject(obj: AggregateChapter): SChapter = SChapter.create().apply {
        url = obj.id.toString()
        name = "Chapter ${obj.number}"

        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = ""
    }
    /*import java.text.SimpleDateFormat
    import java.util.Locale
    import java.util.TimeZone*/
    override fun chapterListParse(response: Response): List<SChapter> {
        println("chapterListParse")
        try {

            val result = response.parseAs<List<AggregateVolume>>()

            val allChapterList = mutableListOf<SChapter>()
            val mangaList = result.map {
                //
                it.chapters.map {
                    val res = chapterFromObject(it)
                    allChapterList.add(res)
                }
                // chapterFromObject(it.Map)
                // allChapterList.add()
            }
            return allChapterList
        } catch (e: Exception) {
            println("EXCEPTION")
            println(e)
            throw e
        }

        // val chapterListResults = mutableListOf<Map<String,AggregateChapter>>()

        // return List<SChapter>(){}

    /*
        try {
            val chapterListResponse = helper.json.decodeFromString<ChapterListDto>(response.body!!.string())

            val chapterListResults = chapterListResponse.data.toMutableList()

            val mangaId =
                response.request.url.toString().substringBefore("/feed")
                    .substringAfter("${MDConstants.apiMangaUrl}/")

            val limit = chapterListResponse.limit

            var offset = chapterListResponse.offset

            var hasMoreResults = (limit + offset) < chapterListResponse.total

            // max results that can be returned is 500 so need to make more api calls if limit+offset > total chapters
            while (hasMoreResults) {
                offset += limit
                val newResponse =
                    client.newCall(actualChapterListRequest(mangaId, offset)).execute()
                val newChapterList = helper.json.decodeFromString<ChapterListDto>(newResponse.body!!.string())
                chapterListResults.addAll(newChapterList.data)
                hasMoreResults = (limit + offset) < newChapterList.total
            }

            val now = Date().time

            return chapterListResults.mapNotNull { helper.createChapter(it) }
                .filter {
                    it.date_upload <= now
                }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter list", e)
            throw(e)
        }*/
    }

    // val response = client.newCall(   ).execute()

    /*private fun fetchSimpleChapterList(manga: KavitaComicsDetailsDto): List<String> {
        val url = "$baseUrl/Series/${manga.id}"
        val response = client.newCall(GET(url, headersBuilder().build())).execute()
        val result = response.parseAs<List<AggregateVolume>>()


        val chapters = result.map(::popularMangaFromObject)




        val chapters: AggregateDto
        try {
            chapters = helper.json.decodeFromString(response.body!!.string())
        } catch (e: SerializationException) {
            return emptyList()
        }
        if (chapters.volumes.isNullOrEmpty()) return emptyList()
        return chapters.volumes.values.flatMap { it.chapters.values }.map { it.chapter }
    }*/

    // Helper function for listing chapters and chapters in nested titles recursively
    private fun listChapters(titleObj: JsonObject): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val topChapters = titleObj.getAsJsonArray("entries")?.map { obj ->
            SChapter.create().apply {
                name = obj["display_name"].asString
                url =
                    "/page/${obj["title_id"].asString}/${obj["id"].asString}/${obj["pages"].asString}/"
                date_upload = 1000L * obj["mtime"].asLong
            }
        }
        val subChapters = titleObj.getAsJsonArray("titles")?.map { obj ->
            val name = obj["display_name"].asString
            listChapters(obj.asJsonObject).map { chp ->
                chp.name = "$name / ${chp.name}"
                chp
            }
        }?.flatten()
        if (topChapters !== null) chapters += topChapters
        if (subChapters !== null) chapters += subChapters
        return chapters
    }

    // Stub
    override fun pageListRequest(chapter: SChapter): Request =
        throw UnsupportedOperationException("Not used")

    // Overridden fetch so that we use our overloaded method instead
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val splitUrl = chapter.url.split("/").toMutableList()
        val numPages = splitUrl.removeAt(splitUrl.size - 2).toInt()
        val baseUrlChapter = splitUrl.joinToString("/")
        val pages = mutableListOf<Page>()
        for (i in 1..numPages) {
            pages.add(
                Page(
                    index = i,
                    imageUrl = "$baseUrl/api$baseUrlChapter$i"
                )
            )
        }
        return Observable.just(pages)
    }

    // Stub
    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = ""
    override fun getFilterList(): FilterList = FilterList()

    override val name = "Kavita"
    override val lang = "all"
    override val supportsLatest = false

    override val baseUrl by lazy { getPrefBaseUrl() }
    private val port by lazy { getPrefPort() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private val token by lazy { getPrefToken() }
    private val gson by lazy { Gson() }
    private var apiCookies: String = ""

    override fun headersBuilder(): Headers.Builder =
        Headers.Builder()
            .add("User-Agent", "Tachiyomi Kavita v${BuildConfig.VERSION_NAME}")
            .add("Authorization", "Bearer $token")
    private fun tokenBodyBuilder(): RequestBody {
        val jsonObject = JSONObject()
        println(apiToken)
        jsonObject.put("mangaFormat", 0)
        val body = jsonObject.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        return body
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    /*override val client: OkHttpClient =
        network.client.newBuilder()
            .addInterceptor { authIntercept(it) }
            .build()*/

    private fun authIntercept(chain: Interceptor.Chain): Response {
        println("AuthIntercept in")
        // Check that we have our username and password to login with
        val request = chain.request()
        if (username.isEmpty() || password.isEmpty()) {
            throw IOException("Missing username or password")
        }

        // Do the login if we have not gotten the cookies yet
        if (apiCookies.isEmpty() || !apiCookies.contains("kavita-sessid-$port", true)) {
            doLogin(chain)
        }

        // Append the new cookie from the api
        val authRequest = request.newBuilder()
            .addHeader("Cookie", apiCookies)
            .build()

        return chain.proceed(authRequest)
    }

    private fun doLogin(chain: Interceptor.Chain) {
        println("Try to do Login")
        // Try to login
        val formHeaders: Headers = headersBuilder()
            .add("ContentType", "application/x-www-form-urlencoded")
            .build()
        val formBody: RequestBody = FormBody.Builder()
            .addEncoded("username", username)
            .addEncoded("password", password)
            .build()

        val jsonObject = JSONObject()
        jsonObject.put("username", username)
        jsonObject.put("password", password)

        val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val loginRequest = POST("$baseUrl/Account/login", formHeaders, body)

        val response = chain.proceed(loginRequest)
        println("RESPONSE_BODY")
        // println(Gson().toJson(response.body!!.string()))

        if (response.code == 200) {
            val jsonResponse = JSONObject(Gson().toJson(response.body!!.string()))
            val token = jsonResponse.getString("token")

            /*class Foo(json: String) : JSONObject(json) {
                val id = this.optInt("id")
                val title: String? = this.optString("title")
            }
            class Response(json: String) : JSONObject(json) {
                val type: String? = this.optString("type")
                val data = this.optJSONArray("data")
                    ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } } // returns an array of JSONObject
                    ?.map { Foo(it.toString()) } // transforms each JSONObject of the array into Foo
                }*/
        }
        // Save the cookies from the response

        response.close()
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, "The URL to access your Mango instance. Please include the port number if you didn't set up a reverse proxy"))
        screen.addPreference(screen.editTextPreference(PORT_TITLE, PORT_DEFAULT, "The port number to use if it's not the default 5000"))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, "Your login username"))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, "Your login password", true))
        screen.addPreference(screen.editTextPreference(BEARERTOKEN, BEARERTOKEN_DEFAULT, "Your Token", true))
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
                    apiCookies = ""
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
    private fun getPrefPort(): String = preferences.getString(PORT_TITLE, PORT_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!
    private fun getPrefToken(): String = preferences.getString(BEARERTOKEN, BEARERTOKEN_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = "http://192.168.0.135:5000/api/"
        private const val PORT_TITLE = "Server Port Number"
        private const val PORT_DEFAULT = "5000"
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = "username"
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = "Password0"
        private const val BEARERTOKEN = "Token"
        private const val BEARERTOKEN_DEFAULT = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJuYW1laWQiOiJ1c2VybmFtZSIsInJvbGUiOiJBZG1pbiIsIm5iZiI6MTYzODcyNjU0NSwiZXhwIjoxNjM5MzMxMzQ1LCJpYXQiOjE2Mzg3MjY1NDV9.P_1YBjZacv1JX9aIi6LnzC_gC45FzCptz8vbB39Zi4_6q-5TWzOTBBaYV0lP93jVdC1G4zv9fO8EYFiSJo8KgQ"
    }
}
