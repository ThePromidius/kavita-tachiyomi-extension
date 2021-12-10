package eu.kanade.tachiyomi.extension.all.kavita

import eu.kanade.tachiyomi.extension.all.kavita.dto.PaginationInfo
import eu.kanade.tachiyomi.extension.all.kavita.dto.SeriesDto
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response

class KavitaHelper {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
        useArrayPolymorphism = true
        prettyPrint = true
    } /*
    fun parseDate(dateAsString: String): Long =
        MDConstants.dateFormatter.parse(dateAsString)?.time ?: 0
*/
    fun parseDate(dateAsString: String): Long =
        MDConstants.dateFormatter.parse(dateAsString)?.time ?: 0

    fun convertPagination(page: Int): Int {
        var pageNum = page - 1
        if (pageNum < 0) pageNum = 0
        return pageNum
    }

    fun hasNextPage(response: Response): Boolean {
        var paginationHeader = response.header("Pagination")
        var hasNextPage = false
        if (!paginationHeader.isNullOrEmpty()) {
            var paginationInfo = json.decodeFromString<PaginationInfo>(paginationHeader)
            hasNextPage = paginationInfo.currentPage < paginationInfo.totalPages
        }
        return hasNextPage
    }

    fun getIdFromUrl(url: String): Int {
        return url.split("/").last().toInt()
    }

    fun createSeriesDto(obj: SeriesDto, baseUrl: String): SManga =
        SManga.create().apply {
//            println("createSeriesDto")
//            println(obj)
            url = "$baseUrl/Series/${obj.id}"
            title = obj.name
            // artist = obj.artist
            // author = obj.author
            description = obj.summary
            // genre = obj.genres.joinToString(", ")
            // status = obj.status
            thumbnail_url = "$baseUrl/image/series-cover?seriesId=${obj.id}"
        }
}
/**
 * Remove bbcode tags as well as parses any html characters in description or
 * chapter name to actual characters for example &hearts; will show ♥
 */
    /*
    fun cleanString(string: String): String {
        val bbRegex =
            """\[(\w+)[^]]*](.*?)\[/\1]""".toRegex()
        var intermediate = string
            .replace("[list]", "")
            .replace("[/list]", "")
            .replace("[*]", "")
        // Recursively remove nested bbcode
        while (bbRegex.containsMatchIn(intermediate)) {
            intermediate = intermediate.replace(bbRegex, "$2")
        }
        return Parser.unescapeEntities(intermediate, false)
    }
*/
/**
 * create an SManga from json element only basic elements
 */
    /*
    fun createBasicManga(
        mangaDataDto: MangaDataDto,
        coverFileName: String?,
        coverSuffix: String?,
        lang: String
    ): SManga {
        return SManga.create().apply {
            url = "/${mangaDataDto.id}"
            val titleMap = mangaDataDto.attributes.name.asMdMap()
            val dirtyTitle = titleMap["en"]
                ?: titleMap["en"]
                ?: mangaDataDto.attributes.altTitles.jsonArray
                    .find {
                        val altTitle = it.asMdMap()
                        altTitle[lang] ?: altTitle["en"] != null
                    }?.asMdMap()?.values?.singleOrNull()
                ?: titleMap["ja"] // romaji titles are sometimes ja (and are not altTitles)
                ?: titleMap.values.firstOrNull() // use literally anything from title as a last resort
            title = cleanString(dirtyTitle ?: "")

            coverFileName?.let {
                thumbnail_url = "http://192.168.0.135:5000/api/image/series-cover?seriesId=14"
            }
        }
    }*/

/**
 * Create an SManga from json element with all details
 *//*
    fun createManga(mangaDataDto: MangaDataDto, chapters: List<String>, lang: String, coverSuffix: String?): SManga {
        try {
            val attr = mangaDataDto.attributes

            // things that will go with the genre tags but aren't actually genre

            val tempContentRating = attr.contentRating
            val contentRating =
                if (tempContentRating == null || tempContentRating.equals("safe", true)) {
                    null
                } else {
                    "Content rating: " + tempContentRating.capitalize(Locale.US)
                }

            val dexLocale = Locale.forLanguageTag(lang)

            val nonGenres = listOf(
                (attr.publicationDemographic ?: "").capitalize(Locale.US),
                contentRating,
                Locale(attr.originalLanguage ?: "")
                    .getDisplayLanguage(dexLocale)
                    .capitalize(dexLocale)
            )

            val authors = mangaDataDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(MDConstants.author, true)
            }.mapNotNull { it.attributes!!.name }.distinct()

            val artists = mangaDataDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(MDConstants.artist, true)
            }.mapNotNull { it.attributes!!.name }.distinct()

            val coverFileName = mangaDataDto.relationships.firstOrNull { relationshipDto ->
                relationshipDto.type.equals(MDConstants.coverArt, true)
            }?.attributes?.fileName

            // get tag list
            // val tags = mdFilters.getTags()

            // map ids to tag names
            /*val genreList = (
                attr.tags
                    .map { it.id }
                    .map { dexId ->
                        tags.firstOrNull { it.id == dexId }
                    }
                    .map { it?.name } +
                    nonGenres
                )
                .filter { it.isNullOrBlank().not() }*/

            val desc = attr.summary.asMdMap()
            return createBasicManga(mangaDataDto, coverFileName, coverSuffix, lang).apply {
                description = cleanString(desc[lang] ?: desc["en"] ?: "")
                author = authors.joinToString(", ")
                artist = artists.joinToString(", ")
                // status = getPublicationStatus(attr, chapters)
                // genre = genreList.joinToString(", ")
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing manga", e)
            throw(e)
        }
    }*/

/**
 * create the SChapter from json
 */
/*
    fun createChapter(chapterDataDto: ChapterDataDto): SChapter? {
        try {
            val attr = chapterDataDto.attributes

            val groups = chapterDataDto.relationships.filter { relationshipDto ->
                relationshipDto.type.equals(
                    MDConstants.scanlator,
                    true
                )
            }.mapNotNull { it.attributes!!.name }
                .joinToString(" & ")
                .replace("no group", "No Group")
                .ifEmpty { "No Group" }

            val chapterName = mutableListOf<String>()
            // Build chapter name

            attr.volume?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Vol.$it")
                }
            }

            attr.chapter?.let {
                if (it.isNotEmpty()) {
                    chapterName.add("Ch.$it")
                }
            }

            attr.title?.let {
                if (it.isNotEmpty()) {
                    if (chapterName.isNotEmpty()) {
                        chapterName.add("-")
                    }
                    chapterName.add(it)
                }
            }

            if (attr.externalUrl != null && attr.data.isEmpty()) {
                return null
            }

            // if volume, chapter and title is empty its a oneshot
            if (chapterName.isEmpty()) {
                chapterName.add("Oneshot")
            }

            // In future calculate [END] if non mvp api doesnt provide it

            return SChapter.create().apply {
                url = "/chapter/${chapterDataDto.id}"
                name = cleanString(chapterName.joinToString(" "))
                date_upload = parseDate(attr.publishAt)
                scanlator = groups
            }
        } catch (e: Exception) {
            Log.e("MangaDex", "error parsing chapter", e)
            throw(e)
        }
    }
}
*/
