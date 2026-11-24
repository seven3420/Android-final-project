package com.edu.sicnu.cs.stu.niuwenya.studentc.order.api

import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiResponse
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CreateOrderRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderDetail
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderGoodsRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderStatus
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderTestJson
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Product
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.Seat
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.WatchStats
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.formatMoney
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.parseServerTime
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 接口契约测试（成员 C，D1 验收：能拿到接口返回的 JSON，数据类和 JSON 字段对得上）。
 *
 * 跑法：
 *   1) 启动本地 mock：python tools/mock_server.py
 *   2) ./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest"
 *
 * 为什么用本地 JVM 单元测试而不是 androidTest：Retrofit、OkHttp、Gson 全是纯 JVM 库，
 * 不需要模拟器就能把验收跑实。这里发的是真实 HTTP 请求，走的就是 RetrofitClient 里
 * 配好的单例、鉴权拦截器与日志拦截器，所以同时验证 baseUrl、超时、Authorization 头是否真的生效。
 */
class OrderApiContractTest {

    private val api: OrderApi by lazy { RetrofitClient.orderApi }

    @Before
    fun setUp() {
        useMockServer()
    }

    /** 模拟器里要用 10.0.2.2，本地 JVM 测试直连回环地址。 */
    private fun useMockServer() {
        RetrofitClient.init(
            url = System.getProperty("order.baseUrl", "http://127.0.0.1:8000/"),
            tokenProvider = { "demo-token" }
        )
    }

    private fun <T> assertOk(resp: ApiResponse<T>) {
        assertTrue("接口应返回 code 0，实际 ${resp.code} ${resp.msgText}", resp.isSuccess)
        assertNotNull("data 不应为空", resp.data)
    }

    // ------------------------------------------------------------ 8 卖品列表

    @Test
    fun getProductList_fieldsMatchApiDoc() = runBlocking {
        val resp = api.getProductList(cinemaId = 1)
        assertOk(resp)
        val list = resp.data!!.items
        assertTrue("卖品列表不应为空", list.isNotEmpty())
        assertEquals(4, list.size)

        val first: Product = list.first { it.id == 2001 }
        assertEquals("双人套餐", first.nameText)
        assertEquals(58.00, first.price, 0.001)
        assertEquals(78.00, first.originPrice, 0.001)
        assertEquals(12, first.stock)
        assertEquals(320, first.salesCount)
        assertTrue(first.isHot)
        assertTrue("原价高于现价应判为有优惠", first.hasDiscount)
        assertEquals("立省 20.00 元", first.saveText)

        val noDiscount = list.first { it.id == 2002 }
        assertFalse("原价等于现价不应展示立省", noDiscount.hasDiscount)

        val soldOut = list.first { it.id == 2004 }
        assertTrue("库存 0 应判为售完", soldOut.soldOut)

        list.forEach { product ->
            assertTrue("每个卖品都要有 image 字段", product.imageUrl.isNotEmpty())
        }
    }

    // ------------------------------------------------------------ 9 下单

    @Test
    fun createOrder_returnsIdAmountExpire() = runBlocking {
        val resp = api.createOrder(
            scheduleId = 10086,
            seats = listOf(Seat(5, 8), Seat(5, 9))
        )
        assertOk(resp)
        val data = resp.data!!
        assertEquals(20261119001L, data.orderId)
        // 两个座位 × 42 元，金额由服务端重算
        assertEquals(84.00, data.totalAmount, 0.001)
        assertTrue("过期时间要能解析成时间戳", parseServerTime(data.expireTimeText) > 0L)
    }

    @Test
    fun createOrder_submitsIdsOnlyNoAmount() = runBlocking {
        val body = CreateOrderRequest(
            scheduleId = 10086,
            seats = listOf(Seat(5, 8), Seat(5, 9)),
            goods = listOf(OrderGoodsRequest(productId = 2001, quantity = 2))
        )
        val json = RetrofitClient.gson.toJson(body)
        println("createOrder 请求体 = $json")
        assertFalse("请求体不应出现金额字段，金额由服务端重算", json.contains("mount"))
        assertFalse("请求体不应出现单价字段", json.contains("rice"))
        assertTrue("座位与数量应正常提交", json.contains("quantity") && json.contains("seats"))

        val resp = api.createOrder(body)
        assertOk(resp)
        // 2 座 × 42 + 双人套餐 58 × 2 = 200
        assertEquals(200.00, resp.data!!.totalAmount, 0.001)
    }

    @Test
    fun createOrder_seatTakenCode2001() = runBlocking {
        val resp = api.createOrder(CreateOrderRequest(scheduleId = 10086, seats = emptyList()))
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.SEAT_TAKEN, resp.code)
        assertEquals("座位已被占用，请返回重新选座", ApiCode.tip(resp.code))
    }

    // ------------------------------------------------------------ 10 支付

    @Test
    fun payOrder_returnsStatusAndTwoCodes() = runBlocking {
        val resp = api.payOrder(orderId = 20261119001L, payType = "alipay")
        assertOk(resp)
        val data = resp.data!!
        assertEquals(OrderStatus.PAID, data.status)
        assertEquals("8823 6619", data.ticketCodeText)
        assertEquals("6620", data.goodsCodeText)
        assertTrue(data.hasGoodsCode)
    }

    @Test
    fun payOrder_orderExpiredCode2004() = runBlocking {
        val resp = api.payOrder(orderId = 404L, payType = "alipay")
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.ORDER_EXPIRED, resp.code)
        assertTrue(ApiCode.tip(resp.code).contains("重新下单"))
    }

    // ------------------------------------------------------------ 11 取消

    @Test
    fun cancelOrder_statusBecomesCancelled() = runBlocking {
        val resp = api.cancelOrder(orderId = 20261124002L)
        assertOk(resp)
        assertTrue(resp.data!!.isCancelled)
        assertEquals(OrderStatus.CANCELLED, resp.data!!.status)
        assertEquals("已取消", resp.data!!.statusText)
    }

    // ------------------------------------------------------------ 12 列表

    @Test
    fun getOrderList_threeTabs() = runBlocking {
        val all = api.getOrderList(status = null)
        assertOk(all)
        assertEquals(3, all.data!!.total)

        val unpaid = api.getOrderList(status = OrderStatus.UNPAID)
        assertOk(unpaid)
        assertEquals(1, unpaid.data!!.items.size)
        val item = unpaid.data!!.items.first()
        assertEquals("深夜闭环", item.movieTitleText)
        assertEquals("待支付", item.statusText)
        assertTrue("待支付订单应有剩余支付时间", item.remainMillis() > 0L)
        assertTrue(
            "待支付状态应展示倒计时并可取消",
            OrderStatus.showCountdown(item.status, item.remainMillis()) && OrderStatus.canCancel(item.status)
        )

        val finished = api.getOrderList(status = OrderStatus.FINISHED)
        assertOk(finished)
        val done = finished.data!!.items.first()
        assertEquals("已完成", done.statusText)
        assertEquals(0L, done.remainMillis())
        assertFalse("已完成订单不再展示倒计时", OrderStatus.showCountdown(done.status, done.remainMillis()))
    }

    @Test
    fun orderList_amountBreakdownConsistent() = runBlocking {
        api.getOrderList().data!!.items.forEach { order ->
            assertEquals(
                "订单 ${order.id} 金额分项应等于总额",
                order.totalAmount,
                order.ticketAmount + order.goodsAmount,
                0.011
            )
        }
    }

    // ------------------------------------------------------------ 13 详情

    @Test
    fun getOrderDetail_fieldsMatchApiDoc() = runBlocking {
        val resp = api.getOrderDetail(orderId = 20261119001L)
        assertOk(resp)
        val detail = resp.data!!

        assertEquals("星际远征", detail.movieTitleText)
        assertEquals("星汇影城（春熙路店）", detail.cinemaNameText)
        assertEquals("3 号厅", detail.hallNameText)
        assertEquals("2026-11-20 19:30", detail.startTimeText)
        assertEquals(OrderStatus.PAID, detail.status)
        assertEquals("已支付", detail.statusText)

        // 接口返回的 seats 是"5 排 8 座"文案数组，不是坐标
        assertEquals(listOf("5 排 8 座", "5 排 9 座"), detail.seatList)

        assertTrue(detail.hasGoods)
        val goods = detail.goodsList.first()
        assertEquals("双人套餐", goods.nameText)
        assertEquals(2, goods.quantity)
        assertEquals(116.00, goods.amount, 0.001)

        assertEquals(84.00, detail.ticketAmount, 0.001)
        assertEquals(116.00, detail.goodsAmount, 0.001)
        assertEquals(200.00, detail.totalAmount, 0.001)
        // C-1 验收：票款 + 卖品 = 总额
        assertTrue("金额分项应与总额一致", detail.isAmountConsistent)
        assertEquals("200.00", formatMoney(detail.totalAmount))

        // C-5 验收：两个码分开展示
        assertEquals("8823 6619", detail.ticketCodeText)
        assertEquals("6620", detail.goodsCodeText)
        assertTrue(detail.hasTicketCode && detail.hasGoodsCode)
        assertTrue(OrderStatus.showCode(detail.status))
    }

    @Test
    fun getOrderDetail_notFoundCode2005() = runBlocking {
        val resp = api.getOrderDetail(orderId = 404L)
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.ORDER_NOT_FOUND, resp.code)
        assertEquals("订单不存在或已被删除", ApiCode.tip(resp.code))
    }

    // ------------------------------------------------------------ 14 统计

    @Test
    fun getWatchStats_fourSectionsReadyForChart() = runBlocking {
        val resp = api.getWatchStats()
        assertOk(resp)
        val stats: WatchStats = resp.data!!

        assertEquals(12, stats.totalMovie)
        assertEquals(1575, stats.totalDuration)
        assertEquals("26 小时 15 分钟", stats.durationText)

        assertEquals(4, stats.typeRankList.size)
        assertEquals("科幻", stats.typeRankList.first().typeText)
        assertEquals(5, stats.typeRankList.first().count)
        val rank = stats.typeRankList.sortedByDescending { it.count }
        assertEquals("科幻", rank.first().typeText)
        assertEquals("喜剧", rank.last().typeText)

        assertEquals(6, stats.monthCountList.size)
        stats.monthCountList.forEach { month ->
            assertTrue("月份格式应为 yyyy-MM", month.monthText.matches(Regex("\\d{4}-\\d{2}")))
        }
    }

    // ------------------------------------------------------------ 鉴权与兜底

    @Test
    fun missingToken_returnsCode1001() = runBlocking {
        RetrofitClient.init(url = "http://127.0.0.1:8000/", tokenProvider = { null })
        val resp = api.getProductList(cinemaId = 1)
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.NOT_LOGIN, resp.code)
        assertEquals("登录已失效，请重新登录", ApiCode.tip(resp.code))
        useMockServer() // 还原，避免影响同批次其他用例
    }

    @Test
    fun partialJson_doesNotCrashModels() {
        // Gson 不走 Kotlin 主构造默认值，缺字段时数字为 0、字符串为 null，
        // 模型里来自服务端的字符串因此全部可空并由 xxxText 兜底，这条规则用测试钉死。
        val type = object : TypeToken<ApiResponse<OrderDetail>>() {}.type
        val resp: ApiResponse<OrderDetail> =
            RetrofitClient.gson.fromJson(OrderTestJson.PARTIAL_ORDER_DETAIL, type)

        val detail = resp.data!!
        assertEquals("", detail.movieTitleText)
        assertEquals("", detail.ticketCodeText)
        assertEquals(0.0, detail.totalAmount, 0.001)
        assertEquals("待支付", detail.statusText) // status 缺省为 0
        assertTrue(detail.seatList.isEmpty())
        assertTrue(detail.goodsList.isEmpty())
        assertFalse(detail.hasTicketCode)
    }

    @Test
    fun docSampleJson_deserializesIntoModels() {
        val detail = OrderTestJson.parseOrderDetailFromDoc()
        assertEquals("星际远征", detail.movieTitleText)
        assertEquals(200.00, detail.totalAmount, 0.001)
        assertEquals(listOf("5 排 8 座", "5 排 9 座"), detail.seatList)
        assertEquals(116.00, detail.goodsList.first().amount, 0.001)
        // 文档示例里没有 id 与时间字段，兜底后应为默认值而不是抛异常
        assertEquals(0L, detail.id)
        assertTrue(detail.createTime.isNullOrEmpty())
    }

    @Test
    fun docCreateOrderSample_deserializes() {
        val result = OrderTestJson.parseCreateOrderFromDoc()
        assertEquals(20261119001L, result.orderId)
        assertEquals(122.00, result.totalAmount, 0.001)
        assertTrue(parseServerTime(result.expireTimeText) > 0L)
    }
}
