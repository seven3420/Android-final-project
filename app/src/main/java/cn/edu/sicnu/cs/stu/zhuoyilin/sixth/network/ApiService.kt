package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network

import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Cinema
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.LoginRequest
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.LoginResult
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.MoviePage
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.SeatMap
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.response.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    // A-2 账号内容域接口，参数等 A 组文档确认后再补
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<LoginResult>>

    @GET("getUserInfo")
    suspend fun getUserInfo(): Response<ApiResponse<Any>>

    @GET("getMovieList")
    suspend fun getMovieList(
        @Query("page") page: Int,
        @Query("type") type: Int
    ): Response<ApiResponse<MoviePage>>

    @GET("getMovieDetail")
    suspend fun getMovieDetail(@Query("movieId") movieId: Int): Response<ApiResponse<Movie>>

    @GET("getCinemaList")
    suspend fun getCinemaList(): Response<ApiResponse<List<Cinema>>>

    @GET("getScheduleList")
    suspend fun getScheduleList(
        @Query("cinemaId") cinemaId: Int,
        @Query("movieId") movieId: Int,
        @Query("date") date: String
    ): Response<ApiResponse<List<Schedule>>>

    @GET("getSeatMap")
    suspend fun getSeatMap(@Query("scheduleId") scheduleId: Int): Response<ApiResponse<SeatMap>>

    @GET("getProductList")
    suspend fun getProductList(): Response<ApiResponse<Any>>
}
