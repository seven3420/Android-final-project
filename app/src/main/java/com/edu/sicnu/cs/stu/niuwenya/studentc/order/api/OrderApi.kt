package com.edu.sicnu.cs.stu.niuwenya.studentc.order.api

import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiResponse
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CancelOrderRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CancelResult
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CreateOrderRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CreateOrderResult
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderDetail
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderListItem
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.PageResult
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.PayRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.PayResult
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Product
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Seat
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.WatchStats
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 成员 C 负责的接口声明，严格照《课程设计说明书》4.2 表8 的接口名、方法、路径、入参逐条对应。
 *
 * 十七个接口全部由成员 A 实现与维护，本文件只做客户端声明、不实现服务端。
 * 表8 中 1 至 7 号（登录、用户信息、影片、影院、场次、座位）归成员 B 的浏览链路使用，
 * 本文件不重复声明，避免出现两份同名接口声明导致字段漂移。
 * 8 号 getProductList 虽在 B 的 D2 里程碑出现，但卖品弹窗归 C（C-2），故由 C 声明。
 * 15 至 17 号收藏接口是备选项，说明书 4.2 注明 D6 无剩余工时就整体不做，此处不声明。
 *
 * 约定：interface 内只放带 HTTP 注解的抽象方法，任何带函数体的便捷写法一律放到文件底部
 * 作扩展函数。Kotlin 编译器默认会让 interface 的具体方法生成 DefaultImpls，
 * 早期会让 Retrofit.create 生成代理时报 "API method must not be a default method" 一类问题，
 * 把便捷方法挪出 interface 可以从根上避开这个坑。
 *
 * 协作规则第二条：D5 之后接口字段冻结，确需变更由 A 在小组内同步后统一修改，不得单方面改字段名。
 */
interface OrderApi {

    /**
     * 8 卖品 getProductList —— GET /api/getProductList，入参 cinemaId，返回 list。
     * 价格与库存按影院维度独立（说明书 2.4 规则 3），所以必须带 cinemaId。
     * 展示顺序为热销优先、再按销量倒序（说明书 2.4 规则 7），排序在客户端做。
     */
    @GET("api/getProductList")
    suspend fun getProductList(
        @Query("cinemaId") cinemaId: Int
    ): ApiResponse<PageResult<Product>>

    /**
     * 9 下单 createOrder —— POST /api/createOrder，入参 scheduleId、seats、goods。
     * 请求体只提交 id，不提交金额；服务端按 schedule.price 与 product.price 重算（说明书 6.1）。
     * 成功返回 orderId、totalAmount、expireTime（now + 15min）。
     * 业务失败：2001 座位被占、2002 库存不足、2003 价格变动。
     */
    @POST("api/createOrder")
    suspend fun createOrder(
        @Body body: CreateOrderRequest
    ): ApiResponse<CreateOrderResult>

    /**
     * 10 支付 payOrder —— POST /api/payOrder，入参 orderId、payType。
     * 成功返回 status、ticketCode、goodsCode；含卖品时才会生成取餐码（说明书 2.4 规则 5）。
     * 业务失败：2004 订单已失效（超过 15 分钟），界面按 C-3 验收要求给出过期提示。
     */
    @POST("api/payOrder")
    suspend fun payOrder(
        @Body body: PayRequest
    ): ApiResponse<PayResult>

    /**
     * 11 取消订单 cancelOrder —— POST /api/cancelOrder，入参 orderId，返回 status。
     * 待支付订单可直接取消；已支付需满足"开场前"（说明书 6.3 表19），该判定由服务端完成。
     */
    @POST("api/cancelOrder")
    suspend fun cancelOrder(
        @Body body: CancelOrderRequest
    ): ApiResponse<CancelResult>

    /**
     * 12 订单列表 getOrderList —— GET /api/getOrderList，入参 status、pageNum、pageSize。
     * status 不传为"全部"，传 0 为"待支付"，传 2 为"已完成"，对应订单页三个 Tab（C-4）。
     */
    @GET("api/getOrderList")
    suspend fun getOrderList(
        @Query("status") status: Int? = null,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 10
    ): ApiResponse<PageResult<OrderListItem>>

    /**
     * 13 订单详情 getOrderDetail —— GET /api/getOrderDetail，入参 orderId。
     * 一次性返回全量字段与两个取货码，客户端不再二次拼接（说明书 4.3）。
     * 业务失败：2005 订单不存在。
     */
    @GET("api/getOrderDetail")
    suspend fun getOrderDetail(
        @Query("orderId") orderId: Long
    ): ApiResponse<OrderDetail>

    /**
     * 14 观影统计 getWatchStats —— GET /api/getWatchStats，入参只有 Header 里的 token。
     * 返回 totalMovie、totalDuration、typeRank、monthCount，供统计页画饼图与柱状图（C-7）。
     */
    @GET("api/getWatchStats")
    suspend fun getWatchStats(): ApiResponse<WatchStats>
}

// ---------------------------------------------------------- 页面侧便捷调用

/** 确认订单页直接拿座位与购物车提交，quantity 为 0 的条目自动剔除。 */
suspend fun OrderApi.createOrder(
    scheduleId: Int,
    seats: List<Seat>,
    cartItems: List<CartItem> = emptyList()
): ApiResponse<CreateOrderResult> = createOrder(
    CreateOrderRequest(
        scheduleId = scheduleId,
        seats = seats,
        goods = cartItems.filter { it.quantity > 0 }.map { it.toOrderGoods() }
    )
)

/** 支付弹窗选定方式后直接调用。 */
suspend fun OrderApi.payOrder(orderId: Long, payType: String): ApiResponse<PayResult> =
    payOrder(PayRequest(orderId = orderId, payType = payType))

/** 订单列表与详情页的取消按钮直接调用。 */
suspend fun OrderApi.cancelOrder(orderId: Long): ApiResponse<CancelResult> =
    cancelOrder(CancelOrderRequest(orderId = orderId))
