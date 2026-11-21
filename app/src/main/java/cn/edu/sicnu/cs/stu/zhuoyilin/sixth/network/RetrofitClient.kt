package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    /**
     * 开发阶段地址：10.0.2.2 是 Android 模拟器访问电脑本机的别名。
     * 真机调试时改为电脑局域网 IP，且结尾必须带 "/"。
     */
    private const val BASE_URL = "http://10.0.2.2:8080/"

    lateinit var apiService: ApiService
        private set

    fun init(context: Context) {
        if (::apiService.isInitialized) return

        val appContext = context.applicationContext

        val logInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logInterceptor)
            .addInterceptor(TokenInterceptor(appContext))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}
