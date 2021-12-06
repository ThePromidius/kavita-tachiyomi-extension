package eu.kanade.tachiyomi.extension.all.kavita.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KavitaComicsDto(
    val id: Int,
    @SerialName("name") val title: String,
    val thumbnail_url: String? = "",
    @SerialName("summary") val description: String = ""

)

@Serializable
data class TaoSectContentDto(
    val rendered: String = ""
)

@Serializable
data class TaoSectTagDto(
    @SerialName("nome") val name: String = ""
)

@Serializable
data class TaoSectVolumeDto(
    @SerialName("capitulos") val chapters: List<TaoSectChapterDto> = emptyList()
)

@Serializable
data class TaoSectChapterDto(
    @SerialName("data_insercao") val date: String = "",
    @SerialName("id_capitulo") val id: String = "",
    @SerialName("nome_capitulo") val name: String = "",
    @SerialName("paginas") val pages: List<String> = emptyList(),
    @SerialName("post_id") val projectId: String? = "",
    val slug: String = ""
)
