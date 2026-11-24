package com.edu.sicnu.cs.stu.niuwenya.studentc.order.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 契约测试用的 JSON 样本（成员 C）。
 *
 * DOC_ORDER_DETAIL 直接抄《课程设计说明书》4.3 订单详情示例原文，一字未改。
 * 用它反序列化能证明"数据类和文档示例的 JSON 字段对得上"这条验收成立——
 * 这份样本就是 A 出接口文档时的依据，A 的真实返回若与它不符，本测试会先红。
 */
object OrderTestJson {

    /** 说明书 4.3 订单详情示例原文（示例里本身没有 id、createTime、payTime 等字段）。 */
    const val DOC_ORDER_DETAIL = """
    { "code": 0, "msg": "ok",
      "data": { "status": 1, "movieTitle": "星际远征",
        "cinemaName": "星汇影城（春熙路店）", "hallName": "3 号厅",
        "startTime": "2026-11-20 19:30",
        "seats": ["5 排 8 座", "5 排 9 座"],
        "goods": [ {"name": "双人套餐", "quantity": 2, "amount": 116.00} ],
        "ticketAmount": 84.00, "goodsAmount": 116.00, "totalAmount": 200.00,
        "ticketCode": "8823 6619", "goodsCode": "6620" } }
    """

    /** 说明书 4.3 下单示例的响应原文。 */
    const val DOC_CREATE_ORDER = """
    { "code": 0, "msg": "ok",
      "data": { "orderId": 20261119001,
                "totalAmount": 122.00,
                "expireTime": "2026-11-19 20:15:00" } }
    """

    /** 故意只给一半字段，用于验证服务端漏字段时客户端不崩。 */
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
}
