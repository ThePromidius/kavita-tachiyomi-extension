package eu.kanade.tachiyomi.extension.all.kavita

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.widget.Toast
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.all.kavita.dto.AuthenticationDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.KavitaComicsSearch
import eu.kanade.tachiyomi.extension.all.kavita.dto.MangaFormat
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataAgeRatings
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataGenres
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataLanguages
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataLibrary
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataPayload
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataPeople
import eu.kanade.tachiyomi.extension.all.kavita.dto.MetadataTags
import eu.kanade.tachiyomi.extension.all.kavita.dto.PersonRole
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesMetadataDto
import eu.kanade.tachiyomi.extension.all.kavita.dto.VolumeDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
import okio.Buffer
import org.json.JSONObject
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLEncoder

class Kavita : ConfigurableSource, HttpSource() {
    override val name = "Kavita"
    override val lang = "all"
    override val supportsLatest = true
    override val baseUrl by lazy { getPrefBaseUrl() } // Base URL is the API address of the Kavita Server. Should end with /api
    private val address by lazy { getPrefAddress() } // Address for the Kavita OPDS url. Should be http(s)://host:(port)/api/opds/api-key
    private var jwtToken = "" // * JWT Token for authentication with the server. Stored in memory.
    private val apiKey by lazy { getPrefapiKey() } // API Key of the USer. This is parsed from Address
    private var isLoged =
        false // Used to know if login was correct and not send login requests anymore

    private val json: Json by injectLazy()
    private val helper = KavitaHelper()
    private inline fun <reified T> Response.parseAs(): T =
        use { json.decodeFromString(it.body?.string().orEmpty()) }
    inline fun <reified T : kotlin.Enum<T>> safeValueOf(type: String): T {
        return java.lang.Enum.valueOf(T::class.java, type)
    }
    private var libraries = emptyList<MetadataLibrary>()
    private var series = emptyList<SeriesDto>() // Acts as a cache

    override fun popularMangaRequest(page: Int): Request {
        if (!isLoged) { checkLogin() }

        return POST(
            "$baseUrl/series/all?pageNumber=$page&libraryId=0&pageSize=20",
            headersBuilder().build(),
            buildFilterBody()
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<SeriesDto>>()
        series = result
        val mangaList = result.map { item -> helper.createSeriesDto(item, baseUrl) }
        return MangasPage(mangaList, helper.hasNextPage(response))
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return POST(
            "$baseUrl/series/recently-added?pageNumber=$page&libraryId=0&pageSize=20",
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
    var isFilterOn = false // If any filter option is enabled this is true
    var toFilter = MetadataPayload()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        toFilter = MetadataPayload() // need to reset it or will double
        filters.forEach { filter ->
            when (filter) {
                is StatusFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.readStatus.add(content.name)
                            isFilterOn = true
                        }
                    }
                }
                is GenreFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.genres.add(genresListMeta.find { it.title == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is TagFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.tags.add(tagsListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is AgeRatingFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.ageRating.add(ageRatingsListMeta.find { it.title == content.name }!!.value)
                            isFilterOn = true
                        }
                    }
                }
                is FormatsFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.formats.add(content.name)
                            isFilterOn = true
                        }
                    }
                }

                is LanguageFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            val DtoObj =
                                toFilter.language.add(languagesListMeta.find { it.title == content.name }!!.isoCode)
                            isFilterOn = true
                        }
                    }
                }
                is LibrariesFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            val DtoObj =
                                toFilter.libraries.add(libraryListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }

                is OtherPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peopleOther.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is WriterPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peoplewriters.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is PencillerPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peoplepenciller.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is InkerPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peopleinker.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is ColoristPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peoplepeoplecolorist.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is LettererPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peopleletterer.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is CoverArtistPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peoplecoverArtist.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is EditorPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peopleeditor.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }
                is PublisherPeopleFilterGroup -> {
                    filter.state.forEach { content ->
                        if (content.state) {
                            toFilter.peoplepublisher.add(peopleListMeta.find { it.name == content.name }!!.id)
                            isFilterOn = true
                        }
                    }
                }

                else -> isFilterOn = false
            }
        }

        if (isFilterOn || query.isEmpty()) {
            return popularMangaRequest(page)
        } else {
            return GET("$baseUrl/Library/search?queryString=$query", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (isFilterOn) {
            isFilterOn = false
            return popularMangaParse(response)
        } else {
            if (response.request.url.toString().contains("api/series/all"))
                return popularMangaParse(response)

            val result = response.parseAs<List<KavitaComicsSearch>>()
            val mangaList = result.map(::searchMangaFromObject)
            return MangasPage(mangaList, false)
        }
    }

    private fun searchMangaFromObject(obj: KavitaComicsSearch): SManga = SManga.create().apply {
        title = obj.name
        thumbnail_url = "$baseUrl/Image/series-cover?seriesId=${obj.seriesId}"
        description = "None"
        url = "${obj.seriesId}"
    }

    /**
     * MANGA DETAILS (metadata about series)
     * **/
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(
            "$baseUrl/series/metadata?seriesId=${helper.getIdFromUrl(manga.url)}",
            headersBuilder().build()
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesMetadataDto>()
        val existingSeries = series.find { dto -> dto.id == result.seriesId }

        if (existingSeries != null) {
            val manga = helper.createSeriesDto(existingSeries, baseUrl)
            manga.artist = result.artists.joinToString { "${it.name}" }
            manga.description = result.summary
            manga.author = result.writers.joinToString { "${it.name}" }
            manga.genre = result.genres.joinToString { "${it.title}" }

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

    /**
     * CHAPTER LIST
     * **/
    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/Series/volumes?seriesId=${helper.getIdFromUrl(manga.url)}"
        return GET(url, headersBuilder().build())
    }

    private fun chapterFromObject(obj: ChapterDto): SChapter = SChapter.create().apply {
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

    private fun chapterFromVolume(obj: ChapterDto, volume: VolumeDto): SChapter =
        SChapter.create().apply {
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
                    name = "Volume ${volume.number}"
                }
            } else {
                name = "Unhandled Else Volume ${volume.number}"
            }

            url = obj.id.toString()
            date_upload = helper.parseDate(obj.created)
            chapter_number = obj.number.toFloat()
            scanlator = obj.pages.toString()
        }
    override fun chapterListParse(response: Response): List<SChapter> {
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
            allChapterList.reverse()
            return allChapterList
        } catch (e: Exception) {
            println("EXCEPTION")
            println(e)
            throw IOException(e)
        }
    }

    /**
     * Fetches the "url" of each page from the chapter
     * **/
    override fun pageListRequest(chapter: SChapter): Request {

        return GET("${chapter.url}/Reader/chapter-info")
    }
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterId = chapter.url
        val numPages = chapter.scanlator?.toInt()
        val numPages2 = "$numPages".toInt() - 1
        val pages = mutableListOf<Page>()
        for (i in 0..numPages2) {
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
     *          FILTERING
     * **/
    private fun fetchMetadataFiltering() {

        /**
         * Fetchs all user defined metadata (genres, writers, tags, languages, age rating)
         * Called upon opening extenstion after token loged in
         * **/
        if (genresListMeta.isEmpty()) {
            val request = GET("$baseUrl/Metadata/genres", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                genresListMeta = response.parseAs<List<MetadataGenres>>()
            }
            response.close()
        }
        // Todo: var otherPeople = emptyList<>()
        if (peopleListMeta.isEmpty()) {
            val request = GET("$baseUrl/Metadata/people", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200

            if (requestSuccess) { // peopleListMeta
                peopleListMeta = response.parseAs<List<MetadataPeople>>()

                    /*

                var testMetaJson = buildJsonObject() {
                    result.map {
                        when (it.role) {
                            1 -> put("Writer",a)
                            //1 -> peopleOtherListMeta.add(it.role)
                            3 -> peopleWritersListMeta.add(it.role)
                            4 -> peoplePencillerListMeta.add(it.role)
                            5 -> peopleInkerListMeta.add(it.role)
                            6 -> peopleColoristListMeta.add(it.role)
                            7 -> peopleLettererListMeta.add(it.role)
                            8 -> peopleCoverArtistListMeta.add(it.role)
                            9 -> peopleEditorListMeta.add(it.role)
                            10 -> peoplePublisherListMeta.add(it.role)
                            else -> throw IOException("Filtering error")
                        }
                    }
                }*/
            }
            response.close()
        }
        if (tagsListMeta.isEmpty()) {
            val request = GET("$baseUrl/Metadata/tags", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                tagsListMeta = response.parseAs<List<MetadataTags>>()
            }
            response.close()
        }
        if (ageRatingsListMeta.isEmpty()) {
            val request = GET("$baseUrl/Metadata/age-ratings", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                ageRatingsListMeta = response.parseAs<List<MetadataAgeRatings>>()
            }
            response.close()
        }
        if (languagesListMeta.isEmpty()) {
            val request = GET("$baseUrl/Metadata/languages", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                languagesListMeta = response.parseAs<List<MetadataLanguages>>()
            }
            response.close()
        }
        if (libraries.isEmpty()) {
            val request = GET("$baseUrl/Library", headersBuilder().build())
            val response = client.newCall(request).execute()
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                libraryListMeta = response.parseAs<List<MetadataLibrary>>()
            }
            response.close()
        }
    }

    /** Some variable names already exist. im not good at naming add Meta suffix */
    var genresListMeta = emptyList<MetadataGenres>()
    var tagsListMeta = emptyList<MetadataTags>()
    var ageRatingsListMeta = emptyList<MetadataAgeRatings>()
    var peopleListMeta = emptyList<MetadataPeople>()
    var languagesListMeta = emptyList<MetadataLanguages>()
    var libraryListMeta = emptyList<MetadataLibrary>()
    val personRoles = listOf<String>(
        "Other",
        "Writer",
        "Penciller",
        "Inker",
        "Colorist",
        "Letterer",
        "CoverArtist",
        "Editor",
        "Publisher"
    )
    /*var peopleOtherListMeta = mutableListOf<Int>()
    var peopleWritersListMeta = mutableListOf<Int>()
    var peoplePencillerListMeta = mutableListOf<Int>()
    var peopleInkerListMeta = mutableListOf<Int>()
    var peopleColoristListMeta = mutableListOf<Int>()
    var peopleLettererListMeta = mutableListOf<Int>()
    var peopleCoverArtistListMeta = mutableListOf<Int>()
    var peopleEditorListMeta = mutableListOf<Int>()
    var peoplePublisherListMeta = mutableListOf<Int>()*/

    private class StatusFilter(name: String) : Filter.CheckBox(name, false)
    private class StatusFilterGroup(filters: List<StatusFilter>) :
        Filter.Group<StatusFilter>("Status", filters)

    private class GenreFilter(genre: String) : Filter.CheckBox(genre, false)
    private class GenreFilterGroup(genres: List<GenreFilter>) :
        Filter.Group<GenreFilter>("Genres", genres)

    private class TagFilter(tag: String) : Filter.CheckBox(tag, false)
    private class TagFilterGroup(tags: List<TagFilter>) : Filter.Group<TagFilter>("Tags", tags)

    private class AgeRatingFilter(ageRating: String) : Filter.CheckBox(ageRating, false)
    private class AgeRatingFilterGroup(ageRatings: List<AgeRatingFilter>) :
        Filter.Group<AgeRatingFilter>("Age-Rating", ageRatings)

    private class FormatFilter(name: String) : Filter.CheckBox(name, false)
    private class FormatsFilterGroup(formats: List<FormatFilter>) :
        Filter.Group<FormatFilter>("Formats", formats)

    private class LanguageFilter(language: String) : Filter.CheckBox(language, false)
    private class LanguageFilterGroup(languages: List<LanguageFilter>) :
        Filter.Group<LanguageFilter>("Language", languages)
    private class LibraryFilter(library: String) : Filter.CheckBox(library, false)
    private class LibrariesFilterGroup(libraries: List<LibraryFilter>) :
        Filter.Group<LibraryFilter>("Libraries", libraries)

    private class OtherPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class OtherPeopleFilterGroup(peoples: List<OtherPeopleFilter>) :
        Filter.Group<OtherPeopleFilter>("Other", peoples)

    private class WriterPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class WriterPeopleFilterGroup(peoples: List<WriterPeopleFilter>) :
        Filter.Group<WriterPeopleFilter>("Writer", peoples)

    private class PencillerPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class PencillerPeopleFilterGroup(peoples: List<PencillerPeopleFilter>) :
        Filter.Group<PencillerPeopleFilter>("Penciller", peoples)

    private class InkerPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class InkerPeopleFilterGroup(peoples: List<InkerPeopleFilter>) :
        Filter.Group<InkerPeopleFilter>("Inker", peoples)

    private class ColoristPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class ColoristPeopleFilterGroup(peoples: List<ColoristPeopleFilter>) :
        Filter.Group<ColoristPeopleFilter>("Colorist", peoples)

    private class LettererPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class LettererPeopleFilterGroup(peoples: List<LettererPeopleFilter>) :
        Filter.Group<LettererPeopleFilter>("Letterer", peoples)

    private class CoverArtistPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class CoverArtistPeopleFilterGroup(peoples: List<CoverArtistPeopleFilter>) :
        Filter.Group<CoverArtistPeopleFilter>("CoverArtist", peoples)

    private class EditorPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class EditorPeopleFilterGroup(peoples: List<EditorPeopleFilter>) :
        Filter.Group<EditorPeopleFilter>("Editor", peoples)

    private class PublisherPeopleFilter(people: String) : Filter.CheckBox(people, false)
    private class PublisherPeopleFilterGroup(peoples: List<PublisherPeopleFilter>) :
        Filter.Group<PublisherPeopleFilter>("Publisher", peoples)

    override fun getFilterList(): FilterList {
        // fetchMetadataFiltering()
        val filters = try {
            val peopleInRoles = mutableListOf<List<MetadataPeople>>()
            personRoles.map { role ->
                var peoplesWithRole = mutableListOf<MetadataPeople>()
                peopleListMeta.map {
                    if (it.role == safeValueOf<PersonRole>(role)!!.role) {
                        peoplesWithRole.add(it)
                    }
                }
                peopleInRoles.add(peoplesWithRole)
            }
            try {
                mutableListOf<Filter<*>>(
                    StatusFilterGroup(listOf("notRead", "inProgress", "read").map { StatusFilter(it) }),
                    GenreFilterGroup(genresListMeta.map { GenreFilter(it.title) }),
                    TagFilterGroup(tagsListMeta.map { TagFilter(it.name) }),
                    AgeRatingFilterGroup(ageRatingsListMeta.map { AgeRatingFilter(it.title) }),
                    FormatsFilterGroup(listOf("Manga", "Archive", "Unknown", "Epub", "Pdf").map { FormatFilter(it) }),

                    LanguageFilterGroup(languagesListMeta.map { LanguageFilter(it.title) }),
                    LibrariesFilterGroup(libraryListMeta.map { LibraryFilter(it.name) }),
                    OtherPeopleFilterGroup(peopleInRoles.get(0).map { OtherPeopleFilter(it.name) }),
                    WriterPeopleFilterGroup(peopleInRoles.get(1).map { WriterPeopleFilter(it.name) }),
                    PencillerPeopleFilterGroup(peopleInRoles.get(2).map { PencillerPeopleFilter(it.name) }),
                    InkerPeopleFilterGroup(peopleInRoles.get(3).map { InkerPeopleFilter(it.name) }),
                    ColoristPeopleFilterGroup(peopleInRoles.get(4).map { ColoristPeopleFilter(it.name) }),
                    LettererPeopleFilterGroup(peopleInRoles.get(5).map { LettererPeopleFilter(it.name) }),
                    CoverArtistPeopleFilterGroup(peopleInRoles.get(6).map { CoverArtistPeopleFilter(it.name) }),
                    EditorPeopleFilterGroup(peopleInRoles.get(7).map { EditorPeopleFilter(it.name) }),
                    PublisherPeopleFilterGroup(peopleInRoles.get(8).map { PublisherPeopleFilter(it.name) }),
                )
            } catch (e: Exception) {
                println("Error on filtering creation")
                mutableListOf<Filter<*>>(
                    StatusFilterGroup(listOf("notRead", "inProgress", "read").map { StatusFilter(it) }),
                    GenreFilterGroup(genresListMeta.map { GenreFilter(it.title) }),
                    TagFilterGroup(tagsListMeta.map { TagFilter(it.name) }),
                    AgeRatingFilterGroup(ageRatingsListMeta.map { AgeRatingFilter(it.title) }),
                    FormatsFilterGroup(listOf("Manga", "Archive", "Unknown", "Epub", "Pdf").map { FormatFilter(it) }),
                )
            }
        } catch (e: Exception) {
            println("Exception:\n$e")
            emptyList()
        }
        return FilterList(filters)
    }

    /**
     *
     * Finished filtering
     *
     * */

    override fun headersBuilder(): Headers.Builder {
        return Headers.Builder()
            .add("User-Agent", "Tachiyomi Kavita v${BuildConfig.VERSION_NAME}")
            .add("Content-Type", "application/json")
            .add("Authorization", "Bearer $jwtToken")
    }

    private fun buildFilterBody(filter: MetadataPayload = toFilter): RequestBody {
        var filter = filter
        if (!isFilterOn) {
            filter = MetadataPayload()
        }

        val formats = buildJsonArray {
            // TODO: Add formats here. the rest is done. filter.formats can be used. List<String> Ref: Line465 in getFilterList
            add(MangaFormat.Archive.ordinal)
            add(MangaFormat.Image.ordinal)
            add(MangaFormat.Pdf.ordinal)
        }

        val payload = buildJsonObject {
            put("formats", formats)
            put("libraries", buildJsonArray { filter.libraries.map { add(it) } })
            put(
                "readStatus",
                buildJsonObject {
                    if (filter.readStatus.isNotEmpty() and isFilterOn) {
                        filter.readStatus.forEach { status ->
                            if (status in listOf("notRead", "inProgress", "read")) {
                                put(status, JsonPrimitive(true))
                            } else {
                                put(status, JsonPrimitive(false))
                            }
                        }
                    } else {
                        put("notRead", JsonPrimitive(true))
                        put("inProgress", JsonPrimitive(true))
                        put("read", JsonPrimitive(true))
                    }
                }
            )
            put("genres", buildJsonArray { filter.genres.map { add(it) } })
            put("writers", buildJsonArray { filter.peoplewriters.map { add(it) } })
            put("penciller", buildJsonArray { filter.peoplepenciller.map { add(it) } })
            put("inker", buildJsonArray { filter.peopleinker.map { add(it) } })
            put("colorist", buildJsonArray { filter.peoplepeoplecolorist.map { add(it) } })
            put("letterer", buildJsonArray { filter.peopleletterer.map { add(it) } })
            put("coverArtist", buildJsonArray { filter.peoplecoverArtist.map { add(it) } })
            put("editor", buildJsonArray { filter.peopleeditor.map { add(it) } })
            put("publisher", buildJsonArray { filter.peoplepublisher.map { add(it) } })
            put("character", buildJsonArray {})
            put("translators", buildJsonArray {})
            put("collectionTags", buildJsonArray {})
            put("languages", buildJsonArray { filter.language.map { add(it) } })
            put("tags", buildJsonArray { filter.tags.map { add(it) } })
            put("rating", 0)
            put("ageRating", buildJsonArray { filter.ageRating.map { add(it) } })
            // put("sortOptions", JSONObject.NULL)
        }
        return payload.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .addInterceptor {
                authIntercept(it)
            }
            .build()

    private fun authenticateAndSetToken(chain: Interceptor.Chain): Boolean {
        if (!isLoged) {
            println("Performing Authentication...")
            val jsonObject = JSONObject()
            val body = jsonObject.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = POST(
                "$baseUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=${
                URLEncoder.encode(
                    "Tachiyomi-Kavita",
                    "utf-8"
                )
                }",
                headersBuilder().build(), body
            )

            val response = chain.proceed(request)
            val requestSuccess = response.code == 200
            if (requestSuccess) {
                println("Authentication successful")
                val result = response.parseAs<AuthenticationDto>()
                if (result.token.isNotEmpty()) {
                    jwtToken = result.token
                }
            }
            response.close()
            fetchMetadataFiltering()

            return requestSuccess
        } else { return true }
    }

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (jwtToken.isEmpty()) {
            authenticateAndSetToken(chain)
        }
        // Re-attach the Authorization header
        val authRequest = request.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $jwtToken")
            .build()
        return chain.proceed(authRequest)
    }

    /**
     * Debug method to pring a Request body
     */
    private fun bodyToString(request: Request): String? {
        return try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body!!.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "did not work"
        }
    }

    private fun setupVariablesFromAddress() {
        if (address.isEmpty()) {
            throw IOException("You must setup the Address to communicate with Kavita")
        }

        val tokens = address.split("/opds/")
        if (tokens.size != 2) {
            throw IOException("The Address is not correct. Please copy from User settings -> OPDS Url")
        }
        val apiKey = tokens[1]
        val baseUrl = tokens[0]
        preferences.edit().putString("APIKEY", apiKey).commit()
        preferences.edit().putString("BASEURL", baseUrl).commit()
    }

    private fun checkLogin() {
        val jsonObject = JSONObject()
        val body = jsonObject.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = POST(
            "$baseUrl/Plugin/authenticate?apiKey=$apiKey&pluginName=${
            URLEncoder.encode(
                "Tachiyomi-Kavita",
                "utf-8"
            )
            }",
            headersBuilder().build(), body
        )
        client.newCall(request).execute().run {
            if (!isSuccessful) {
                println(this.code)
                println(this.body.toString())
                close()
                throw IOException("Login failed. Your API key is not correct. Please check and try again.")
            }
        }
    }

    // Preference code
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        screen.addPreference(
            screen.editTextPreference(
                ADDRESS_TITLE,
                "",
                "The OPDS url copied from User Settings. This should include address and the api key on end."
            )
        )
    }

    private fun androidx.preference.PreferenceScreen.editTextPreference(
        title: String,
        default: String,
        summary: String,
        isPassword: Boolean = false
    ): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            val input = preferences.getString(title, null)
            this.summary = if (input == null || input.isEmpty()) summary else input
            this.setDefaultValue(default)
            dialogTitle = title

            if (isPassword) {
                setOnBindEditTextListener {
                    it.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    setupVariablesFromAddress()
                    Toast.makeText(
                        context,
                        "Restart Tachiyomi to apply new setting.",
                        Toast.LENGTH_LONG
                    ).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    private fun getPrefapiKey(): String = preferences.getString("APIKEY", "")!!
    private fun getPrefBaseUrl(): String = preferences.getString("BASEURL", "")!!

    // We strip the last slash since we will append it above
    private fun getPrefAddress(): String {
        var path = preferences.getString(ADDRESS_TITLE, "")!!
        if (path.isNotEmpty() && path.last() == '/') {
            path = path.substring(0, path.length - 1)
        }
        return path
    }
    companion object {
        private const val ADDRESS_TITLE = "Address"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
