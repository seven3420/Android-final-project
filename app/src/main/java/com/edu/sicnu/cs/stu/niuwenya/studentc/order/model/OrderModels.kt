package com.edu.sicnu.cs.stu.niuwenya.studentc.order.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 交易与卖品链路（成员 C：确认订单 / 卖品加购 / 支付 / 订单管理与详情 / 观影统计）的数据模型。
 *
 * 权威依据：成员 A 的《后端接口文档》（README.md）。2026-11-24 与 A 当面确认后按它全量对齐，
 * 与《课程设计说明书》不一致处以接口文档为准（已知差异：payOrder 不收 payType；
 * getProductList 的 data 是裸数组且原价字段为 origin_price；列表项主键叫 orderId；
 * 统计接口是 totalShows 一族字段；已支付订单不可取消返回 400）。
 *
 * 说明：Gson 反序列化不走 Kotlin 主构造的默认值，服务端缺字段时数字会是 0、字符串会是 null，
 * 所以来自服务端的字符串一律声明为可空，再由 xxxText 属性兜成非空文案，界面层只取 xxxText。
 */

// ---------------------------------------------------------------- 统一响应体

/**
 * 统一响应体。所有接口 HTTP 状态码恒为 200，客户端只判 body.code。
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == ApiCode.OK
    val msgText: String get() = msg.orEmpty()
}

/** 业务错误码，按 A 接口文档 2.4 错误码表（比说明书多了 400）。 */
object ApiCode {
    const val OK = 0 // 成功
    const val PARAM_INVALID = 400 // 参数不合法（含"已支付订单不可取消"）
    const val NOT_LOGIN = 1001 // 未登录或 token 失效
    const val SEAT_TAKEN = 2001 // 座位已被占用
    const val STOCK_NOT_ENOUGH = 2002 // 卖品库存不足
    const val PRICE_CHANGED = 2003 // 价格已变动，需重新确认
    const val ORDER_EXPIRED = 2004 // 订单已失效（已取消或已超时）
    const val NOT_FOUND = 2005 // 资源不存在（影片/场次/订单/用户）

    /** 直接把错误码翻成给用户看的文案；msg 有服务端原文时优先用 msgText。 */
    fun tip(code: Int): String = when (code) {
        OK -> "操作成功"
        PARAM_INVALID -> "操作不被允许，请刷新后重试"
        NOT_LOGIN -> "登录已失效，请重新登录"
        SEAT_TAKEN -> "座位已被占用，请返回重新选座"
        STOCK_NOT_ENOUGH -> "卖品库存不足，请调整加购数量"
        PRICE_CHANGED -> "价格已变动，请确认最新金额后重新提交"
        ORDER_EXPIRED -> "订单已失效（已取消或超过支付时限）"
        NOT_FOUND -> "订单不存在或已被删除"
        else -> "操作失败，请稍后重试"
    }
}

/** 列表类接口的通用返回：list + total（getOrderList 用；getProductList 是裸数组，不用它）。 */
data class PageResult<T>(
    val list: List<T>? = null,
    val total: Int = 0
) {
    val items: List<T> get() = list.orEmpty()
}

// ---------------------------------------------------------------- 卖品与购物车

/**
 * 卖品，对应 getProductList 返回项。
 * 两个坑：originPrice 的 JSON 键是下划线 origin_price（A 文档 2.3 明确列出的命名例外，
 * 且可为 null）；isHot 是数字 1/0 而不是布尔，直接声明 Boolean 会解析崩溃。
 */
data class Product(
    val id: Int = 0,
    val name: String? = null,
    val image: String? = null,
    val price: Double = 0.0, // 现价，参与结算
    @SerializedName("origin_price")
    val originPrice: Double? = null, // 原价，仅用于展示立省，可为 null
    val stock: Int = 0,
    val salesCount: Int = 0,
    val isHot: Int = 0 // 1 热销 0 普通；服务端给的是数字
) {
    val nameText: String get() = name.orEmpty()
    val imageUrl: String get() = image.orEmpty()
    val hot: Boolean get() = isHot == 1

    /** 原价高于现价才展示"立省"，差额只做展示，不进结算。 */
    val hasDiscount: Boolean get() = price > 0 && (originPrice ?: 0.0) > price
    val saveAmount: Double get() = if (hasDiscount) (originPrice ?: 0.0) - price else 0.0
    val saveText: String get() = "立省 ${formatMoney(saveAmount)} 元"

    /** 库存不足时加号禁用。 */
    val soldOut: Boolean get() = stock <= 0

    companion object {
        /** 每种卖品最多五份（A 文档 createOrder 业务约束）。 */
        const val MAX_QUANTITY_PER_PRODUCT = 5

        /** 服务端已按热销置顶、销量降序排好，客户端不要再排序打乱。 */
        val HOT_FIRST_BY_SALES: Comparator<Product> =
            compareByDescending<Product> { it.hot }.thenByDescending { it.salesCount }
    }
}

/**
 * 加购项：确认订单页本地持有的卖品条目，带名称与单价快照。
 * 用不可变方式增减数量，便于列表刷新时数据一致。
 */
data class CartItem(
    val productId: Int = 0,
    val name: String = "",
    val image: String = "",
    val price: Double = 0.0,
    val originPrice: Double = 0.0,
    val stock: Int = 0,
    val quantity: Int = 0
) {
    /** 小计 = 现价 × 数量。 */
    val amount: Double get() = price * quantity
    val hasDiscount: Boolean get() = price > 0 && originPrice > price
    val saveAmount: Double get() = if (hasDiscount) (originPrice - price) * quantity else 0.0

    /** 加号是否可用：受单品五份上限与实际库存共同约束。 */
    val canIncrease: Boolean get() = quantity < Product.MAX_QUANTITY_PER_PRODUCT && quantity < stock
    val canDecrease: Boolean get() = quantity > 0

    fun increased(): CartItem = if (canIncrease) copy(quantity = quantity + 1) else this
    fun decreased(): CartItem = if (canDecrease) copy(quantity = quantity - 1) else this

    fun toOrderGoods(): OrderGoodsRequest = OrderGoodsRequest(productId = productId, quantity = quantity)

    companion object {
        /** 由卖品列表项生成加购项，初始数量为 0。 */
        fun from(product: Product, quantity: Int = 0): CartItem = CartItem(
            productId = product.id,
            name = product.nameText,
            image = product.imageUrl,
            price = product.price,
            originPrice = product.originPrice ?: 0.0,
            stock = product.stock,
            quantity = quantity
        )
    }
}

/** 提交订单时的卖品项。 */
data class OrderGoodsRequest(
    val productId: Int = 0,
    val quantity: Int = 0
)

/** 订单详情里的卖品明细项：下单时的名称快照 + 数量 + 小计。 */
data class OrderGoodsItem(
    val name: String? = null,
    val quantity: Int = 0,
    val amount: Double = 0.0
) {
    val nameText: String get() = name.orEmpty()
}

// ---------------------------------------------------------------- 座位

/** 座位坐标，对应 createOrder 请求体的 seats。 */
data class Seat(
    val row: Int = 0,
    val col: Int = 0
) {
    /** 界面展示用，如"5 排 6 座"，与服务端返回的座位文案格式一致。 */
    val label: String get() = "${row} 排 ${col} 座"
}

// ---------------------------------------------------------------- 下单与支付

/**
 * POST /api/createOrder 请求体。
 * totalAmount 按 A 文档是可选的：传过去仅供服务端发现"展示后改价"（不一致报 2003），
 * 结算金额一律以服务端重算为准。为 null 时不会出现在 JSON 里。
 */
data class CreateOrderRequest(
    val scheduleId: Int = 0,
    val seats: List<Seat> = emptyList(),
    val goods: List<OrderGoodsRequest> = emptyList(),
    val totalAmount: Double? = null
) {
    companion object {
        /** 单次最多 4 个座位（A 文档 createOrder 业务约束）。 */
        const val MAX_SEATS = 4
    }
}

/** createOrder 返回的 data。 */
data class CreateOrderResult(
    val orderId: Int = 0, // A 文档示例 orderId=1001，整型自增 ID
    val totalAmount: Double = 0.0,
    val expireTime: String? = null // 下单后 15 分钟，倒计时以此为准
) {
    val expireTimeText: String get() = expireTime.orEmpty()
}

/**
 * POST /api/payOrder 请求体。
 * 2026-11-24 与 A 确认：后端没有 payType 字段，支付接口只收 orderId。
 * 支付方式（支付宝/微信/云闪付）只是本地 UI 单选，用于界面展示与埋点，不往接口传。
 */
data class PayRequest(
    val orderId: Int = 0
)

/** 支付方式的本地 UI 常量，仅供支付弹窗单选与展示使用，不参与任何请求体。 */
object PayType {
    const val ALIPAY = "alipay"
    const val WECHAT = "wechat"
    const val UNIONPAY = "unionpay"

    fun label(payType: String): String = when (payType) {
        ALIPAY -> "支付宝"
        WECHAT -> "微信支付"
        UNIONPAY -> "云闪付"
        else -> "余额支付"
    }
}

/** payOrder 返回的 data。重复支付会幂等返回原取票码，不报错。 */
data class PayResult(
    val orderId: Int = 0,
    val status: Int = 0,
    val ticketCode: String? = null, // 取票码
    val goodsCode: String? = null // 取餐码；订单无卖品时为 null
) {
    val ticketCodeText: String get() = ticketCode.orEmpty()
    val goodsCodeText: String get() = goodsCode.orEmpty()
    val hasGoodsCode: Boolean get() = !goodsCode.isNullOrBlank()
}

/** POST /api/cancelOrder 请求体。 */
data class CancelOrderRequest(
    val orderId: Int = 0
)

/** cancelOrder 返回的 data：取消成功后 status 为 3。 */
data class CancelResult(
    val orderId: Int = 0,
    val status: Int = 0
) {
    val statusText: String get() = OrderStatus.label(status)
    val isCancelled: Boolean get() = status == OrderStatus.CANCELLED
}

// ---------------------------------------------------------------- 订单

/**
 * 订单状态码：0 待支付 / 1 已支付 / 2 已完成 / 3 已取消（A 文档第五节状态机）。
 * 界面按状态值渲染按钮，不在本地另设状态变量。
 */
object OrderStatus {
    const val UNPAID = 0 // 待支付
    const val PAID = 1 // 已支付
    const val FINISHED = 2 // 已完成
    const val CANCELLED = 3 // 已取消（用户取消或超 15 分钟未支付）

    fun label(status: Int): String = when (status) {
        UNPAID -> "待支付"
        PAID -> "已支付"
        FINISHED -> "已完成"
        CANCELLED -> "已取消"
        else -> "未知状态"
    }

    /** 待支付才显示"去支付"。 */
    fun canPay(status: Int): Boolean = status == UNPAID

    /**
     * 只有待支付可取消。A 的实现里已支付订单取消会返回 code 400"已支付订单不可取消"，
     * 与说明书"开场前可取消"不一致，以 A 的实现为准，UI 不给已支付订单出取消按钮。
     */
    fun canCancel(status: Int): Boolean = status == UNPAID

    /** 已支付与已完成才展示取票码 / 取餐码。 */
    fun showCode(status: Int): Boolean = status == PAID || status == FINISHED

    /** 待支付且未过期时才需要倒计时与取消按钮。 */
    fun showCountdown(status: Int, remainMillis: Long): Boolean = status == UNPAID && remainMillis > 0L
}

/** 订单列表项，对应 getOrderList 返回项（A 文档第 12 节：主键字段是 orderId，不是 id）。 */
data class OrderListItem(
    val orderId: Int = 0,
    val movieTitle: String? = null,
    val poster: String? = null,
    val cinemaName: String? = null,
    val hallName: String? = null,
    val startTime: String? = null,
    val seatCount: Int = 0,
    val seats: List<String>? = null,
    val totalAmount: Double = 0.0,
    val status: Int = OrderStatus.UNPAID,
    val createTime: String? = null,
    val expireTime: String? = null
) {
    val movieTitleText: String get() = movieTitle.orEmpty()
    val cinemaNameText: String get() = cinemaName.orEmpty()
    val hallNameText: String get() = hallName.orEmpty()
    val startTimeText: String get() = startTime.orEmpty()
    val posterUrl: String get() = poster.orEmpty()
    val seatList: List<String> get() = seats.orEmpty()
    val statusText: String get() = OrderStatus.label(status)

    /** 剩余支付毫秒数，倒计时结束后前端自动显示为已取消。 */
    fun remainMillis(now: Long = System.currentTimeMillis()): Long =
        (parseServerTime(expireTime) - now).coerceAtLeast(0L)
}

/**
 * 订单详情，对应 getOrderDetail（A 文档第 13 节）。
 * 一次性返回展示所需全部字段；seats 是"5 排 6 座"文案数组，不是坐标。
 */
data class OrderDetail(
    val orderId: Int = 0,
    val status: Int = OrderStatus.UNPAID,
    val movieTitle: String? = null,
    val poster: String? = null,
    val cinemaName: String? = null,
    val cinemaAddress: String? = null,
    val hallName: String? = null,
    val startTime: String? = null,
    val langVersion: String? = null,
    val seats: List<String>? = null,
    val seatCount: Int = 0,
    val goods: List<OrderGoodsItem>? = null,
    val ticketAmount: Double = 0.0,
    val goodsAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val ticketCode: String? = null,
    val goodsCode: String? = null,
    val createTime: String? = null,
    val expireTime: String? = null
) {
    val orderIdText: String get() = orderId.toString()
    val movieTitleText: String get() = movieTitle.orEmpty()
    val cinemaNameText: String get() = cinemaName.orEmpty()
    val cinemaAddressText: String get() = cinemaAddress.orEmpty()
    val hallNameText: String get() = hallName.orEmpty()
    val startTimeText: String get() = startTime.orEmpty()
    val langVersionText: String get() = langVersion.orEmpty()
    val posterUrl: String get() = poster.orEmpty()
    val statusText: String get() = OrderStatus.label(status)
    val seatList: List<String> get() = seats.orEmpty()
    val goodsList: List<OrderGoodsItem> get() = goods.orEmpty()
    val hasGoods: Boolean get() = goodsList.isNotEmpty()
    val ticketCodeText: String get() = ticketCode.orEmpty()
    val goodsCodeText: String get() = goodsCode.orEmpty()
    val hasTicketCode: Boolean get() = !ticketCode.isNullOrBlank()
    val hasGoodsCode: Boolean get() = !goodsCode.isNullOrBlank()
    val createTimeText: String get() = createTime.orEmpty()
    val expireTimeText: String get() = expireTime.orEmpty()

    /** 票款加卖品应等于总额，用于详情页的分项一致性自检（C-1 验收同款口径）。 */
    val isAmountConsistent: Boolean get() = kotlin.math.abs(totalAmount - (ticketAmount + goodsAmount)) < 0.01
}

// ---------------------------------------------------------------- 观影统计

/** 类型分布项，对应 getWatchStats 的 typeDist 数组项。 */
data class TypeCount(
    val type: String? = null,
    val count: Int = 0
) {
    val typeText: String get() = type.orEmpty()
}

/** 最常去影院 / 最常看电影。影院用 name，影片用 title，另一个为 null。 */
data class TopCount(
    val name: String? = null,
    val title: String? = null,
    val count: Int = 0
) {
    val displayText: String get() = name ?: title ?: ""
}

/** 最近观看记录，对应 recentList 数组项。 */
data class RecentWatch(
    val title: String? = null,
    val poster: String? = null,
    val type: String? = null,
    val watchTime: String? = null,
    val cinemaName: String? = null
) {
    val titleText: String get() = title.orEmpty()
    val posterUrl: String get() = poster.orEmpty()
    val typeText: String get() = type.orEmpty()
    val watchTimeText: String get() = watchTime.orEmpty()
    val cinemaNameText: String get() = cinemaName.orEmpty()
}

/**
 * 观影统计，对应 getWatchStats（A 文档第 14 节）。
 * 注意与说明书差异：没有月度趋势字段，柱状图区域改用 recentList 最近 5 部呈现；
 * 片数口径是 totalShows（已支付+已完成的有效订单数）。
 */
data class WatchStats(
    val totalShows: Int = 0, // 有效订单数（已支付+已完成）
    val totalMinutes: Int = 0, // 累计观影时长（分钟）
    val totalHours: Double = 0.0, // 累计观影时长（小时，1 位小数）
    val totalTickets: Int = 0, // 累计购票张数
    val totalAmount: Double = 0.0, // 累计消费金额
    val lastWatchTime: String? = null,
    val topCinema: TopCount? = null,
    val topMovie: TopCount? = null,
    val typeDist: List<TypeCount>? = null,
    val recentList: List<RecentWatch>? = null
) {
    val lastWatchTimeText: String get() = lastWatchTime.orEmpty()
    val typeDistList: List<TypeCount> get() = typeDist.orEmpty()
    val recentWatchList: List<RecentWatch> get() = recentList.orEmpty()
    val topCinemaName: String get() = topCinema?.displayText.orEmpty()
    val topMovieTitle: String get() = topMovie?.displayText.orEmpty()
    val amountText: String get() = formatMoney(totalAmount)

    /** 时长卡片文案，优先用服务端 totalHours，如"8.8 小时"。 */
    val hoursText: String get() = String.format(Locale.CHINA, "%.1f 小时", totalHours)

    /** 分钟口径兜底文案，如"8 小时 45 分钟"。 */
    val durationText: String
        get() {
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return if (h > 0) "$h 小时 $m 分钟" else "$m 分钟"
        }
}

// ---------------------------------------------------------------- 通用工具

/** 金额统一展示为两位小数，单位元。 */
fun formatMoney(value: Double): String = String.format(Locale.CHINA, "%.2f", value)

/** 倒计时展示为 mm:ss，超过一小时补小时位。 */
fun formatCountdown(remainMillis: Long): String {
    val totalSeconds = (remainMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }
}

/** 服务端时间为 yyyy-MM-dd HH:mm:ss 字符串，解析失败返回 0，避免解析异常打崩页面。 */
fun parseServerTime(text: String?): Long {
    if (text.isNullOrBlank()) return 0L
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(text)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}
