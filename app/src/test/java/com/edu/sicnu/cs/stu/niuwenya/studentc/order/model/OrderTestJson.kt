package com.edu.sicnu.cs.stu.niuwenya.studentc.order.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 契约测试用的 JSON 样本（成员 C）。
 *
 * 2026-11-24 起样本换源：不再抄课程说明书示例，直接内嵌成员 A《后端接口文档》（README.md）
 * 各接口"成功响应"示例原文。A 的真实返回若与他自己文档不符，这些测试会先红，
 * 正好充当接口文档的活体校验。
 */
object OrderTestJson {

    /** A 文档第 13 节订单详情示例原文（data 部分）。 */
    const val DOC_ORDER_DETAIL = """
    { "code": 0, "msg": "ok",
      "data": {
        "orderId": 1001, "status": 1, "movieTitle": "寻梦环游记",
        "poster": "http://localhost:9000/static/posters/coco.jpg",
        "cinemaName": "星汇影城（春熙路店）",
        "cinemaAddress": "成都市锦江区春熙路1号",
        "hallName": "1号厅",
        "startTime": "2026-11-23 19:30:00",
        "langVersion": "国语 2D",
        "seats": ["5 排 6 座", "5 排 7 座"],
        "seatCount": 2,
        "goods": [ {"name": "双人套餐", "quantity": 1, "amount": 58.00} ],
        "ticketAmount": 84.00, "goodsAmount": 58.00, "totalAmount": 142.00,
        "ticketCode": "1234 5678", "goodsCode": "4321",
        "createTime": "2026-11-23 18:00:00",
        "expireTime": "2026-11-23 18:15:00" } }
    """

    /** A 文档第 9 节下单成功响应示例原文。 */
    const val DOC_CREATE_ORDER = """
    { "code": 0, "msg": "ok",
      "data": { "orderId": 1001, "totalAmount": 142.00,
                "expireTime": "2026-11-23 20:15:00" } }
    """

    /** A 文档第 10 节支付成功响应示例原文（注意没有 payType 相关字段）。 */
    const val DOC_PAY_RESULT = """
    { "code": 0, "msg": "ok",
      "data": { "orderId": 1001, "status": 1,
                "ticketCode": "1234 5678", "goodsCode": "4321" } }
    """

    /** A 文档第 8 节卖品示例原文（裸数组 data，origin_price 下划线、isHot 数字）。 */
    const val DOC_PRODUCT_LIST = """
    { "code": 0, "msg": "ok",
      "data": [ { "id": 1, "name": "双人套餐",
        "image": "http://localhost:9000/static/products/double.jpg",
        "price": 58.0, "origin_price": 68.0, "stock": 120,
        "salesCount": 0, "isHot": 1 } ] }
    """

    /** A 文档第 12 节订单列表示例原文（主键 orderId、含 poster 与 seats 文案数组）。 */
    const val DOC_ORDER_LIST = """
    { "code": 0, "msg": "ok",
      "data": { "list": [ {
        "orderId": 1001, "movieTitle": "寻梦环游记",
        "poster": "http://localhost:9000/static/posters/coco.jpg",
        "cinemaName": "星汇影城（春熙路店）", "hallName": "1号厅",
        "startTime": "2026-11-23 19:30:00", "seatCount": 2,
        "seats": ["5 排 6 座", "5 排 7 座"],
        "totalAmount": 142.00, "status": 1,
        "createTime": "2026-11-23 18:00:00",
        "expireTime": "2026-11-23 18:15:00" } ], "total": 1 } }
    """

    /** A 文档第 14 节统计示例原文（data 部分拆出，包上响应壳）。 */
    const val DOC_WATCH_STATS = """
    { "code": 0, "msg": "ok",
      "data": { "totalShows": 5, "totalMinutes": 525, "totalHours": 8.8,
        "totalTickets": 8, "totalAmount": 356.00,
        "lastWatchTime": "2026-11-20 19:30:00",
        "topCinema": { "name": "星汇影城（春熙路店）", "count": 3 },
        "topMovie": { "title": "寻梦环游记", "count": 2 },
        "typeDist": [ { "type": "动画/家庭/冒险", "count": 3 },
                      { "type": "喜剧/动作/悬疑", "count": 2 } ],
        "recentList": [ { "title": "寻梦环游记",
          "poster": "http://localhost:9000/static/posters/coco.jpg",
          "type": "动画/家庭/冒险", "watchTime": "2026-11-20 19:30:00",
          "cinemaName": "星汇影城（春熙路店）" } ] } }
    """

    /** 故意只给一半字段，验证服务端漏字段时客户端不崩。 */
    const val PARTIAL_ORDER_DETAIL = """
    { "code": 0, "msg": "ok", "data": { "cinemaName": "只剩影院名" } }
    """

    private val gson = Gson()

    fun parseOrderDetailFromDoc(): OrderDetail {
        val type = object : TypeToken<ApiResponse<OrderDetail>>() {}.type
        return gson.fromJson<ApiResponse<OrderDetail>>(DOC_ORDER_DETAIL, type).data!!
    }

    fun parseCreateOrderFromDoc(): CreateOrderResult {
        val type = object : TypeToken<ApiResponse<CreateOrderResult>>() {}.type
        return gson.fromJson<ApiResponse<CreateOrderResult>>(DOC_CREATE_ORDER, type).data!!
    }

    fun parsePayResultFromDoc(): PayResult {
        val type = object : TypeToken<ApiResponse<PayResult>>() {}.type
        return gson.fromJson<ApiResponse<PayResult>>(DOC_PAY_RESULT, type).data!!
    }

    fun parseProductListFromDoc(): List<Product> {
        val type = object : TypeToken<ApiResponse<List<Product>>>() {}.type
        return gson.fromJson<ApiResponse<List<Product>>>(DOC_PRODUCT_LIST, type).data!!.orEmpty()
    }

    fun parseOrderListFromDoc(): PageResult<OrderListItem> {
        val type = object : TypeToken<ApiResponse<PageResult<OrderListItem>>>() {}.type
        return gson.fromJson<ApiResponse<PageResult<OrderListItem>>>(DOC_ORDER_LIST, type).data!!
    }

    fun parseWatchStatsFromDoc(): WatchStats {
        val type = object : TypeToken<ApiResponse<WatchStats>>() {}.type
        return gson.fromJson<ApiResponse<WatchStats>>(DOC_WATCH_STATS, type).data!!
    }

    fun toJson(value: Any): String = gson.toJson(value)
}
