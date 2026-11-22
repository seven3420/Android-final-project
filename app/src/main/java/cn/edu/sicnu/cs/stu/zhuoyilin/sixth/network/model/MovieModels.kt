package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model

import com.google.gson.annotations.SerializedName

/**
 * 影片条目，字段名以 A 组接口为准；这里用 alternate 兼容常见的几种命名。
 */
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
    @SerializedName(value = "cast", alternate = ["actors", "casts"])
    val cast: List<String> = emptyList(),
    @SerializedName(value = "releaseDate", alternate = ["showDate"])
    val releaseDate: String = ""
)

/**
 * 分页返回结构，兼容 list / records / items 以及直接返回数组的情况（数组需后端调整）。
 */
data class MoviePage(
    @SerializedName(value = "list", alternate = ["records", "items", "movies"])
    val list: List<Movie> = emptyList(),
    val total: Int = 0
)
