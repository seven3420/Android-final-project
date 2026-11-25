package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/** 影片条目，兼容 A 组字段和旧 mock 的数组演员格式。 */
data class Movie(
    @SerializedName(value = "movieId", alternate = ["id"])
    val movieId: Int = 0,
    @SerializedName(value = "title", alternate = ["name"])
    val title: String = "",
    @SerializedName(value = "poster", alternate = ["posterUrl", "cover"])
    val poster: String = "",
    @SerializedName(value = "rating", alternate = ["score"])
    val rating: Double = 0.0,
    val duration: Int = 0,
    @SerializedName(value = "synopsis", alternate = ["description", "intro"])
    val synopsis: String = "",
    @field:JsonAdapter(StringListDeserializer::class)
    @SerializedName(value = "cast", alternate = ["actors", "casts"])
    val cast: List<String> = emptyList(),
    @SerializedName(value = "releaseDate", alternate = ["release_date", "showDate"])
    val releaseDate: String = ""
)

data class MoviePage(
    @SerializedName(value = "list", alternate = ["records", "items", "movies"])
    val list: List<Movie> = emptyList(),
    val total: Int = 0
)

private class StringListDeserializer : JsonDeserializer<List<String>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<String> {
        if (json == null || json.isJsonNull) return emptyList()
        val values = if (json.isJsonArray) {
            json.asJsonArray.map { element ->
                if (element.isJsonNull) "" else element.asString
            }
        } else {
            json.asString.split(',')
        }
        return values.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
