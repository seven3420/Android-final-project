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
 * 成员 C 负责的接口声明，按成员 A 的《后端接口文档》（README.md）逐条对齐（2026-11-24 定稿）。
 *
 * 与课程说明书的已知差异，全部以 A 的文档为准：
 * - getProductList 的 data 是裸数组，不套 {list,total}；
 * - payOrder 只收 orderId，没有 payType（支付方式留在本地 UI 层，见 PayType 注释）；
 * - getOrderList 已完成 Tab 传 status=2；
 * - 已支付订单不可取消（服务端回 400）。
 *
 * 登录、用户信息、影片、影院、场次、座位 6 个接口归成员 B 声明，本文件不重复。
 * 接口冻结后确需变更，由 A 在小组内同步后统一修改，不得单方面改字段名。
 */
interface OrderApi {

    /**
     * 卖品列表 —— GET /api/getProductList，入参 cinemaId。
     * 返回裸数组；服务端已按热销置顶、销量降序排好，客户端不要再排序打乱
     * （Product.HOT_FIRST_BY_SALES 仅做本地兜底）。原价 JSON 键为 origin_price。
     */
    @GET("api/getProductList")
    suspend fun getProductList(
        @Query("cinemaId") cinemaId: Int
    ): ApiResponse<List<Product>>

    /**
     * 下单 —— POST /api/createOrder，入参 scheduleId、seats、goods、totalAmount（可选）。
     * totalAmount 仅供服务端发现变价（不一致报 2003），结算金额以服务端重算为准。
     * 约束：最多 4 座、每种卖品最多 5 份、15 分钟未支付自动取消。
     * 业务失败：2001 座位被占、2002 库存不足、2003 价格变动、400 参数不合法。
     */
    @POST("api/createOrder")
    suspend fun createOrder(
        @Body body: CreateOrderRequest
    ): ApiResponse<CreateOrderResult>

    /**
     * 模拟支付 —— POST /api/payOrder，请求体只有 orderId。
     * A 确认后端没有 payType 字段，支付方式仅本地 UI 展示，不往接口传。
     * 成功返回 orderId、status、ticketCode、goodsCode（无卖品时 goodsCode 为 null）。
     * 幂等：重复支付返回原取票码，不报错。
     */
    @POST("api/payOrder")
    suspend fun payOrder(
        @Body body: PayRequest
    ): ApiResponse<PayResult>

    /**
     * 取消订单 —— POST /api/cancelOrder，入参 orderId。
     * 只有待支付可取消；已支付回 400，已取消/超时回 2004，不存在回 2005。
     */
    @POST("api/cancelOrder")
    suspend fun cancelOrder(
        @Body body: CancelOrderRequest
    ): ApiResponse<CancelResult>

    /**
     * 订单列表 —— GET /api/getOrderList，入参 status、pageNum、pageSize。
     * 三个 Tab 的传法（A 定稿）：全部=不传 status，待支付=0，已完成=2。
     * 状态码只有 0/1/2/3 四种。
     */
    @GET("api/getOrderList")
    suspend fun getOrderList(
        @Query("status") status: Int? = null,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 10
    ): ApiResponse<PageResult<OrderListItem>>

    /**
     * 订单详情 —— GET /api/getOrderDetail，入参 orderId。
     * 一次性返回全量字段与两个码，客户端不再二次拼接。业务失败：2005 订单不存在。
     */
    @GET("api/getOrderDetail")
    suspend fun getOrderDetail(
        @Query("orderId") orderId: Int
    ): ApiResponse<OrderDetail>

    /**
     * 观影统计 —— GET /api/getWatchStats，入参只有 Header 里的 token。
     * 返回 totalShows / totalMinutes / totalHours / totalTickets / totalAmount /
     * lastWatchTime / topCinema / topMovie / typeDist / recentList。
     */
    @GET("api/getWatchStats")
    suspend fun getWatchStats(): ApiResponse<WatchStats>
}

// ---------------------------------------------------------- 页面侧便捷调用

/** 确认订单页直接拿座位与购物车提交，quantity 为 0 的条目自动剔除。 */
suspend fun OrderApi.createOrder(
    scheduleId: Int,
    seats: List<Seat>,
    cartItems: List<CartItem> = emptyList(),
    clientTotal: Double? = null
): ApiResponse<CreateOrderResult> = createOrder(
    CreateOrderRequest(
        scheduleId = scheduleId,
        seats = seats,
        goods = cartItems.filter { it.quantity > 0 }.map { it.toOrderGoods() },
        totalAmount = clientTotal
    )
)

/**
 * 支付弹窗确认后直接调用。payType 参数只为兼容旧调用点保留，
 * A 已确认接口不收该字段，默认实现直接忽略它——UI 层想记就记在埋点里，别塞进请求体。
 */
@Deprecated("后端只收 orderId；payType 仅供本地 UI 展示，不会随请求提交", ReplaceWith("payOrder(orderId)"))
suspend fun OrderApi.payOrder(orderId: Int, payType: String): ApiResponse<PayResult> =
    payOrder(PayRequest(orderId = orderId))

/** 支付弹窗确认后调用（定稿版）。 */
suspend fun OrderApi.payOrder(orderId: Int): ApiResponse<PayResult> =
    payOrder(PayRequest(orderId = orderId))

/** 订单列表与详情页的取消按钮直接调用。 */
suspend fun OrderApi.cancelOrder(orderId: Int): ApiResponse<CancelResult> =
    cancelOrder(CancelOrderRequest(orderId = orderId))
