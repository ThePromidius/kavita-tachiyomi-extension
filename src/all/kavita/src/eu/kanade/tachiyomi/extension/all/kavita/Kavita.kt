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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Kavita : ConfigurableSource, HttpSource() {

    /**
     * POPULAR MANGA
     * **/
    override fun popularMangaRequest(page: Int): Request {
        //    return GET("$baseUrl/browse?sort=views_w&page=$page")
        println("Popular Request")
        println()
        return POST(
            "$baseUrl/Series/recently-added?PageNumber=$page&libraryId=0",
            headersBuilder().build(),
            tokenBodyBuilder()
        )
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

    /**
     * LATES UPDATES UNUSED MANGA
     * **/
    override fun latestUpdatesRequest(page: Int): Request =
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
        println(baseUrl)
        println(manga.url)

        return GET("$baseUrl/Series/${manga.url}", headersBuilder().build())
    }

    // This will just return the same thing as the main library endpoint
    private fun mangaDetailsFromObject(obj: KavitaComicsDetailsDto): SManga =
        SManga.create().apply {
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
    /**
     * CHAPTER LIST
     * **/
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/Series/volumes?seriesId=${manga.url}"

        println("chapterListRequest")
        println(url)
        return GET(url, headersBuilder().build())

        // return actualChapterListRequest(helper.getUUIDFromUrl(manga.url), 0)
    }

    private fun chapterFromObject(obj: AggregateChapter): SChapter = SChapter.create().apply {
        url = obj.id.toString()
        name = "Chapter ${obj.range}"
        date_upload = helper.parseDate(obj.created)
        chapter_number = obj.number.toFloat()
        scanlator = obj.pages.toString()
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
                    val pattern = """00?\.""".toRegex()
                    if (pattern.containsMatchIn(it.title) || it.pages> 5) {
                        val res = chapterFromObject(it)
                        allChapterList.add(res)
                    }
                }
                // chapterFromObject(it.Map)
                // allChapterList.add()
            }
            println(allChapterList)
            return allChapterList
        } catch (e: Exception) {
            println("EXCEPTION")
            println(e)
            throw e
        }
    }

    // val chapterListResults = mutableListOf<Map<String,AggregateChapter>>()

    // return List<SChapter>(){}

    /**
     * ACTUAL IMAGE OF PAGES REQUEST
     * **/
    override fun pageListRequest(chapter: SChapter): Request =
        throw UnsupportedOperationException("Not used")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
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

    override val name = "Kavita"
    override val lang = "all"
    override val supportsLatest = false

    /**
     * SOME USEFUL VARS
     * **/
    override val baseUrl by lazy { getPrefBaseUrl() }
    private val port by lazy { getPrefPort() }
    private val username by lazy { getPrefUsername() }
    private val password by lazy { getPrefPassword() }
    private val JWTtoken by lazy { getPrefToken() }
    private val apiKey by lazy { getPrefApiKey() }
    private val gson by lazy { Gson() }
    private val json: Json by injectLazy()

    private val helper = KavitaHelper()
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body?.string().orEmpty())
    }

    override fun headersBuilder(): Headers.Builder {
        /** Remember to add .build() at the end of headersBuilder()**/
        println("headersBuilder")
        println(JWTtoken)
        println("------")
        return Headers.Builder()
            .add("User-Agent", "Tachiyomi Kavita v${BuildConfig.VERSION_NAME}")
            .add("Authorization", "Bearer $JWTtoken")
    }

    private fun tokenBodyBuilder(): RequestBody {

        val jsonObject = JSONObject()
        jsonObject.put("mangaFormat", 0)
        return jsonObject.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }

    /**
     * PREFERENCES SETUP
     * **/

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .addInterceptor { authIntercept(it) }
            .build()
    private fun isValidToken(chain: Interceptor.Chain): Boolean {
        val jsonObject = JSONObject()
            .put("Authorization", "Bearer $JWTtoken")
        // ("""{"Authorization":"Bearer $JWTtoken"}""")

        val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        println("$baseUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=Tachiyomi-Kavita")
        val request = POST("$baseUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=Tachiyomi-Kavita", headersBuilder().build(), body)

        val response = chain.proceed(request)
        val requestSuccess = response.code == 200
        println(response.code)
        println("requestSuccess: " + requestSuccess)
        response.close()
        return requestSuccess
    }
    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (username.isEmpty() || password.isEmpty()) { // If there is no password or username in preferences:
            val res = preferences.edit().putString(APIKEY, "").commit()
            val res2 = preferences.edit().putString(BEARERTOKEN, "").commit()

            throw IOException("Token deleted:Missing username or password")
        }

        if (JWTtoken.isEmpty()) { // If there is no token stored in preferences:
            doLogin(chain)
        }
        if (isValidToken(chain)) {
            println("True valid token")
            return chain.proceed(request)
        } else {
            doLogin(chain)
        }

        return chain.proceed(request)
    }

    private fun doLogin(chain: Interceptor.Chain) {
        val formHeaders: Headers = headersBuilder()
            .add("ContentType", "application/x-www-form-urlencoded")
            .build()
        val jsonObject = JSONObject() // Create JSON to send in a POST
        jsonObject.put("username", username)
        jsonObject.put("password", password)

        val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val loginRequest = POST("$baseUrl/Account/login", formHeaders, body)
        val response = chain.proceed(loginRequest)
        if (response.code == 200) {
            val result = response.parseAs<Login>() // Serialize Login Response

            if (result.token.isNotEmpty()) {
                /*println(result.token)
                println(result.apiKey)*/
                val res = preferences.edit().putString(BEARERTOKEN, result.token).commit()
                val res2 = preferences.edit().putString(APIKEY, result.apiKey).commit()
                // Need to throw Exception. IDK how to change preference without restarting the app
                throw IOException("Login Successful. Token Saved. Please restart Tachiyomi.")
            }
        }
        // Save the cookies from the response

        response.close()
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(screen.editTextPreference(ADDRESS_TITLE, ADDRESS_DEFAULT, "The URL to access your Mango instance. Please include the port number if you didn't set up a reverse proxy"))
        screen.addPreference(screen.editTextPreference(PORT_TITLE, PORT_DEFAULT, "The port number to use if it's not the default 5000"))
        screen.addPreference(screen.editTextPreference(USERNAME_TITLE, USERNAME_DEFAULT, "Your login username"))
        screen.addPreference(screen.editTextPreference(PASSWORD_TITLE, PASSWORD_DEFAULT, "Your login password", true))
        screen.addPreference(screen.editTextPreference(BEARERTOKEN, BEARERTOKEN_DEFAULT, "Your Token (Don't touch unless necessary)", true))
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
    private fun getPrefPort(): String = preferences.getString(PORT_TITLE, PORT_DEFAULT)!!
    private fun getPrefUsername(): String = preferences.getString(USERNAME_TITLE, USERNAME_DEFAULT)!!
    private fun getPrefPassword(): String = preferences.getString(PASSWORD_TITLE, PASSWORD_DEFAULT)!!
    private fun getPrefApiKey(): String = preferences.getString(APIKEY, APIKEY_DEFAULT)!!
    private fun getPrefToken(): String = preferences.getString(BEARERTOKEN, BEARERTOKEN_DEFAULT)!!

    companion object {
        private const val ADDRESS_TITLE = "Address"
        private const val ADDRESS_DEFAULT = "http://192.168.0.135:5000/api/"
        private const val PORT_TITLE = "Server Port Number"
        private const val PORT_DEFAULT = "5000"
        private const val USERNAME_TITLE = "Username"
        private const val USERNAME_DEFAULT = ""
        private const val PASSWORD_TITLE = "Password"
        private const val PASSWORD_DEFAULT = ""
        private const val APIKEY = "apiKey"
        private const val APIKEY_DEFAULT = ""
        private const val BEARERTOKEN = "Token"
        private const val BEARERTOKEN_DEFAULT = ""
        // private const val BEARERTOKEN_DEFAULT = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJuYW1laWQiOiJ1c2VybmFtZSIsInJvbGUiOiJBZG1pbiIsIm5iZiI6MTYzODcyNjU0NSwiZXhwIjoxNjM5MzMxMzQ1LCJpYXQiOjE2Mzg3MjY1NDV9.P_1YBjZacv1JX9aIi6LnzC_gC45FzCptz8vbB39Zi4_6q-5TWzOTBBaYV0lP93jVdC1G4zv9fO8EYFiSJo8KgQ"
    }
}
