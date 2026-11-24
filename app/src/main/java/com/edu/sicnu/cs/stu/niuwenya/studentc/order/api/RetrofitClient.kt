package com.edu.sicnu.cs.stu.niuwenya.studentc.order.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 交易链路的 Retrofit 单例（成员 C）。
 *
 * 为什么不放在公共 net 包：说明书 7.2 协作规则第三条写明"公共文件（网络层、主题、公共组件）
 * 由 B 负责，其他人只读不改"。所以本类只服务 C 的交易与卖品链路，放在 C 独占的 order/api 包内，
 * D1 每日合并时不会与 B 的公共网络层产生文件冲突；等 B 的公共单例就绪后，把这里的
 * create() 调用点替换过去即可，页面与接口声明都不用动。
 *
 * baseUrl 说明：说明书 8.2 风险预案要求"服务端提前在局域网跑起来，客户端地址做成可配置"，
 * 因此地址不写死在代码里，通过 [init] 在 Application 或联调页传入，默认值取 A 演示机地址。
 * Android 模拟器访问宿主机要用 10.0.2.2，真机要用局域网 IP，两种情况都靠 [init] 切换。
 */
object RetrofitClient {

    /** A 的 FastAPI 按 README 用 uvicorn --port 9000 启动，路径统一 /api/{接口名}。 */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:9000/"

    /** 模拟器访问宿主机时的地址，真机联调请换成 A 的局域网 IP。 */
    const val LOCAL_HOST_BASE_URL = "http://127.0.0.1:9000/"

    /** 本地 mock（tools/mock_server.py）默认端口，与 A 的 9000 错开，可同机并跑。 */
    const val MOCK_BASE_URL = "http://127.0.0.1:8000/"

    private const val TIMEOUT_SECONDS = 15L

    /** 运行时可配置的服务端地址，默认指向 A 的机器。 */
    @Volatile
    private var baseUrl: String = DEFAULT_BASE_URL

    /**
     * token 取值口子。登录态由 B 的登录页写入本地存储，这里只读不写，
     * 用回调注入而不是直接依赖 B 的存储类，避免两个客户端模块互相依赖。
     */
    @Volatile
    private var tokenProvider: (() -> String?)? = null

    /** 联调时改服务端地址；传入地址需以 / 结尾，否则 Retrofit 拼路径会丢一段。 */
    fun init(url: String, tokenProvider: (() -> String?)? = null) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        this.tokenProvider = tokenProvider
    }

    val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .serializeNulls()
            .create()
    }

    /** 自动带上 Authorization 头；token 为空时不加，让服务端回 1001 由界面统一跳登录。 */
    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
        tokenProvider?.invoke()?.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }
        chain.proceed(builder.build())
    }

    /** 日志级别 BODY 会把请求体与响应体全打出来，联调阶段看字段最方便。 */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /** 页面通过这里拿接口实例，不在页面里自建 Retrofit。 */
    val orderApi: OrderApi by lazy { retrofit.create(OrderApi::class.java) }
}
