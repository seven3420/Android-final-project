package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network

import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.CinemaPage
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.LoginRequest
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.LoginResult
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Movie
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.MoviePage
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.Schedule
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.SeatMap
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.model.UserInfo
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network.response.ApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResponse<LoginResult>>

    @GET("getUserInfo")
    suspend fun getUserInfo(): Response<ApiResponse<UserInfo>>

    @GET("getMovieList")
    suspend fun getMovieList(
        @Query("status") status: String,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int
    ): Response<ApiResponse<MoviePage>>

    @GET("getMovieDetail")
    suspend fun getMovieDetail(@Query("movieId") movieId: Int): Response<ApiResponse<Movie>>

    @GET("getCinemaList")
    suspend fun getCinemaList(
        @Query("movieId") movieId: Int,
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int
    ): Response<ApiResponse<CinemaPage>>

    @GET("getScheduleList")
    suspend fun getScheduleList(
        @Query("cinemaId") cinemaId: Int,
        @Query("movieId") movieId: Int,
        @Query("date") date: String
    ): Response<ApiResponse<List<Schedule>>>

    @GET("getSeatMap")
    suspend fun getSeatMap(@Query("scheduleId") scheduleId: Int): Response<ApiResponse<SeatMap>>

    @GET("getProductList")
    suspend fun getProductList(@Query("cinemaId") cinemaId: Int): Response<ApiResponse<Any>>
}
