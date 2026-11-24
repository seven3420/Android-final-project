package com.edu.sicnu.cs.stu.niuwenya.studentc.order.model

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 交易与卖品链路（成员 C：确认订单 / 卖品加购 / 支付 / 订单管理与详情 / 观影统计）的数据模型。
 *
 * 字段命名与《电影票订购App课程设计说明书》4.2 接口总表、4.3 关键接口示例、5.2 字段定义保持一致，
 * 统一小驼峰。按小组协作规则第一条，字段名由 A 的接口文档定稿，任何人不得单方面改动；
 * D5 之后接口冻结，确需变更须由 A 在小组内同步后统一修改。
 *
 * 说明：Gson 反序列化不走 Kotlin 主构造的默认值，服务端漏字段时数字会是 0、字符串会是 null，
 * 所以来自服务端的字符串一律声明为可空，再由 xxxText 属性兜成非空文案，界面层只取 xxxText。
 */

// ---------------------------------------------------------------- 统一响应体

/**
 * 统一响应体，对应说明书 4.1：{ "code": 0, "msg": "ok", "data": { } }
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == ApiCode.OK
    val msgText: String get() = msg.orEmpty()
}

/** 业务错误码，对应说明书 4.4 表9。 */
object ApiCode {
    const val OK = 0 // 成功
    const val NOT_LOGIN = 1001 // 未登录或 token 失效
    const val SEAT_TAKEN = 2001 // 座位已被占用
    const val STOCK_NOT_ENOUGH = 2002 // 卖品库存不足
    const val PRICE_CHANGED = 2003 // 价格已变动，需重新确认
    const val ORDER_EXPIRED = 2004 // 订单已失效
    const val ORDER_NOT_FOUND = 2005 // 订单不存在

    /** 直接把错误码翻成给用户看的文案，用于支付失败与过期提示。 */
    fun tip(code: Int): String = when (code) {
        OK -> "操作成功"
        NOT_LOGIN -> "登录已失效，请重新登录"
        SEAT_TAKEN -> "座位已被占用，请返回重新选座"
        STOCK_NOT_ENOUGH -> "卖品库存不足，请调整加购数量"
        PRICE_CHANGED -> "价格已变动，请确认最新金额后重新提交"
        ORDER_EXPIRED -> "订单已超过支付时限，请重新下单"
        ORDER_NOT_FOUND -> "订单不存在或已被删除"
        else -> "操作失败，请稍后重试"
    }
}

/** 列表类接口的通用返回：list + total。 */
data class PageResult<T>(
    val list: List<T>? = null,
    val total: Int = 0
) {
    val items: List<T> get() = list.orEmpty()
}

// ---------------------------------------------------------------- 卖品与购物车

/**
 * 卖品，对应 product 表与 getProductList 返回项（id、name、image、price、originPrice、stock、salesCount、isHot）。
 * 价格与库存按影院维度独立（说明书 2.4 规则 3）。
 */
data class Product(
    val id: Int = 0,
    val cinemaId: Int = 0,
    val name: String? = null,
    val image: String? = null,
    val price: Double = 0.0, // 现价，参与结算
    val originPrice: Double = 0.0, // 原价，仅用于展示立省，不参与结算
    val stock: Int = 0,
    val salesCount: Int = 0,
    val isHot: Boolean = false
) {
    val nameText: String get() = name.orEmpty()
    val imageUrl: String get() = image.orEmpty()

    /** 原价高于现价才展示"立省"，差额只做展示，不进结算（说明书 6.5）。 */
    val hasDiscount: Boolean get() = price > 0 && originPrice > price
    val saveAmount: Double get() = if (hasDiscount) originPrice - price else 0.0
    val saveText: String get() = "立省 ${formatMoney(saveAmount)} 元"

    /** 库存不足时加号禁用（说明书 2.4 规则 4）。 */
    val soldOut: Boolean get() = stock <= 0

    companion object {
        /** 单笔订单每种卖品最多五份（说明书 2.4 规则 6）。 */
        const val MAX_QUANTITY_PER_PRODUCT = 5
    }
}

/**
 * 加购项：确认订单页本地持有的卖品条目，带下单时的名称与单价快照。
 * 用不可变方式增减数量，便于列表 DiffUtil 与回填时数据一致。
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
    /** 小计 = 现价 × 数量，对应 order_goods.amount。 */
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
            originPrice = product.originPrice,
            stock = product.stock,
            quantity = quantity
        )
    }
}

/** 提交订单时的卖品项，对应 order_goods 表与 createOrder 的 goods。 */
data class OrderGoodsRequest(
    val productId: Int = 0,
    val quantity: Int = 0
)

/** 订单详情里的卖品明细项，对应 getOrderDetail 返回的 goods。 */
data class OrderGoodsItem(
    val productId: Int = 0,
    val name: String? = null,
    val price: Double = 0.0,
    val quantity: Int = 0,
    val amount: Double = 0.0
) {
    val nameText: String get() = name.orEmpty()
}

// ---------------------------------------------------------------- 座位

/** 座位坐标，对应 createOrder 请求体的 seats 与 seat_lock 表。 */
data class Seat(
    val row: Int = 0,
    val col: Int = 0
) {
    /** 界面展示用，如"5 排 8 座"，与订单详情返回的座位文案格式一致。 */
    val label: String get() = "${row} 排 ${col} 座"
}

// ---------------------------------------------------------------- 下单与支付

/** POST /api/createOrder 请求体：场次 + 座位 + 卖品。金额一律由服务端重算，客户端不提交金额。 */
data class CreateOrderRequest(
    val scheduleId: Int = 0,
    val seats: List<Seat> = emptyList(),
    val goods: List<OrderGoodsRequest> = emptyList()
)

/** createOrder 返回的 data。 */
data class CreateOrderResult(
    val orderId: Long = 0L,
    val totalAmount: Double = 0.0,
    val expireTime: String? = null // 下单后 15 分钟，倒计时以此为准
) {
    val expireTimeText: String get() = expireTime.orEmpty()
}

/** POST /api/payOrder 请求体。 */
data class PayRequest(
    val orderId: Long = 0L,
    val payType: String = PayType.ALIPAY
)

/** POST /api/cancelOrder 请求体。 */
data class CancelOrderRequest(
    val orderId: Long = 0L
)

/** cancelOrder 返回的 data：接口只回 status，取消成功后为 3。 */
data class CancelResult(
    val status: Int = 0
) {
    val statusText: String get() = OrderStatus.label(status)
    val isCancelled: Boolean get() = status == OrderStatus.CANCELLED
}

/** payOrder / cancelOrder 返回的 data：状态迁移结果与两个取货码。 */
data class PayResult(
    val status: Int = 0,
    val ticketCode: String? = null, // 取票码
    val goodsCode: String? = null // 取餐码，含卖品时才生成
) {
    val ticketCodeText: String get() = ticketCode.orEmpty()
    val goodsCodeText: String get() = goodsCode.orEmpty()
    val hasGoodsCode: Boolean get() = !goodsCode.isNullOrBlank()
}

/** 支付方式。说明书未给出取值枚举，此处常量需与 A 的接口文档对齐后再定稿。 */
object PayType {
    const val ALIPAY = "alipay"
    const val WECHAT = "wechat"
    const val UNIONPAY = "unionpay"
}

// ---------------------------------------------------------------- 订单

/**
 * 订单状态码，对应 order_info.status 与说明书 6.3 表19 状态机。
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

    /** 待支付可直接取消；已支付为"开场前可取消"。 */
    fun canCancel(status: Int): Boolean = status == UNPAID || status == PAID

    /** 已支付与已完成才展示取票码 / 取餐码。 */
    fun showCode(status: Int): Boolean = status == PAID || status == FINISHED

    /** 待支付且未过期时才需要倒计时与取消按钮。 */
    fun showCountdown(status: Int, remainMillis: Long): Boolean = status == UNPAID && remainMillis > 0L
}

/** 订单列表项，对应 getOrderList 返回项。 */
data class OrderListItem(
    val id: Long = 0L,
    val status: Int = OrderStatus.UNPAID,
    val movieTitle: String? = null,
    val cinemaName: String? = null,
    val hallName: String? = null,
    val startTime: String? = null,
    val seatCount: Int = 0,
    val ticketAmount: Double = 0.0,
    val goodsAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val ticketCode: String? = null,
    val goodsCode: String? = null,
    val createTime: String? = null,
    val expireTime: String? = null
) {
    val movieTitleText: String get() = movieTitle.orEmpty()
    val cinemaNameText: String get() = cinemaName.orEmpty()
    val startTimeText: String get() = startTime.orEmpty()
    val statusText: String get() = OrderStatus.label(status)
    val hasGoods: Boolean get() = goodsAmount > 0.0

    /** 剩余支付毫秒数，倒计时结束前端自动显示为已取消。 */
    fun remainMillis(now: Long = System.currentTimeMillis()): Long =
        (parseServerTime(expireTime) - now).coerceAtLeast(0L)
}

/**
 * 订单详情，对应 getOrderDetail：一次性返回展示所需全量字段，客户端不再二次拼接。
 * 注意接口返回的 seats 是"5 排 8 座"这种文案数组，不是坐标，所以这里用 List<String>。
 */
data class OrderDetail(
    val id: Long = 0L,
    val status: Int = OrderStatus.UNPAID,
    val movieTitle: String? = null,
    val cinemaName: String? = null,
    val hallName: String? = null,
    val startTime: String? = null,
    val seats: List<String>? = null,
    val goods: List<OrderGoodsItem>? = null,
    val ticketAmount: Double = 0.0,
    val goodsAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val ticketCode: String? = null,
    val goodsCode: String? = null,
    val createTime: String? = null,
    val payTime: String? = null,
    val expireTime: String? = null
) {
    val movieTitleText: String get() = movieTitle.orEmpty()
    val cinemaNameText: String get() = cinemaName.orEmpty()
    val hallNameText: String get() = hallName.orEmpty()
    val startTimeText: String get() = startTime.orEmpty()
    val statusText: String get() = OrderStatus.label(status)
    val seatList: List<String> get() = seats.orEmpty()
    val goodsList: List<OrderGoodsItem> get() = goods.orEmpty()
    val hasGoods: Boolean get() = goodsList.isNotEmpty()
    val ticketCodeText: String get() = ticketCode.orEmpty()
    val goodsCodeText: String get() = goodsCode.orEmpty()
    val hasTicketCode: Boolean get() = !ticketCode.isNullOrBlank()
    val hasGoodsCode: Boolean get() = !goodsCode.isNullOrBlank()

    /** 票款加卖品应等于总额，用于确认订单页的分项一致性自检。 */
    val isAmountConsistent: Boolean get() = totalAmount - (ticketAmount + goodsAmount) < 0.01
}

// ---------------------------------------------------------------- 观影统计

/** 类型分布项，对应 getWatchStats 的 typeRank。 */
data class TypeCount(
    val type: String? = null,
    val count: Int = 0
) {
    val typeText: String get() = type.orEmpty()
}

/** 月度趋势项，对应 getWatchStats 的 monthCount。 */
data class MonthCount(
    val month: String? = null,
    val count: Int = 0
) {
    val monthText: String get() = month.orEmpty()
}

/** 观影统计，对应 getWatchStats：片数、总时长、类型排名、月度趋势。 */
data class WatchStats(
    val totalMovie: Int = 0,
    val totalDuration: Int = 0, // 单位分钟
    val typeRank: List<TypeCount>? = null,
    val monthCount: List<MonthCount>? = null
) {
    val typeRankList: List<TypeCount> get() = typeRank.orEmpty()
    val monthCountList: List<MonthCount> get() = monthCount.orEmpty()

    /** 时长卡片文案，如"2 小时 45 分钟"。 */
    val durationText: String
        get() {
            val h = totalDuration / 60
            val m = totalDuration % 60
            return if (h > 0) "$h 小时 $m 分钟" else "$m 分钟"
        }
}

// ---------------------------------------------------------------- 通用工具

/** 金额统一展示为两位小数，单位元（说明书 5.2）。 */
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
