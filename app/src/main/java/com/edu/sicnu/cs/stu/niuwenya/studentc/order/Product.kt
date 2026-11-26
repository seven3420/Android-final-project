package com.edu.sicnu.cs.stu.niuwenya.studentc.order

/**
 * 确认订单页里随票加购的卖品（D1 静态阶段先用本地假数据）。
 * 字段与 A 接口文档 getProductList 的返回项保持同名，D2 换成真实接口时直接替换列表来源即可。
 */
data class Product(
    val id: Int,
    val name: String,
    val price: Double, // 现价，参与合计
    val count: Int // 加购数量，单笔订单每种最多 5 份
) {
    /** 小计 = 现价 × 数量。 */
    val amount: Double get() = price * count
}
