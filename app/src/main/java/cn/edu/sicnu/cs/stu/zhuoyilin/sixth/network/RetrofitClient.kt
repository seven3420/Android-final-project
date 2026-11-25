package cn.edu.sicnu.cs.stu.zhuoyilin.sixth.network

import android.content.Context
import cn.edu.sicnu.cs.stu.zhuoyilin.sixth.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val apiOrigin = BuildConfig.API_ORIGIN.toHttpUrl()

    /** 将后端相对图片路径补全，并避免 localhost 指向手机自身。 */
    fun resolveUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null

        val absoluteUrl = path.toHttpUrlOrNull()
        if (absoluteUrl != null) {
            if (absoluteUrl.host != "localhost" && absoluteUrl.host != "127.0.0.1") return path
            return absoluteUrl.newBuilder()
                .scheme(apiOrigin.scheme)
                .host(apiOrigin.host)
                .port(apiOrigin.port)
                .build()
                .toString()
        }

        return apiOrigin.resolve(path)?.toString()
            ?: apiOrigin.toString().trimEnd('/') + "/" + path.trimStart('/')
    }

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
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }
}
