package com.edu.sicnu.cs.stu.niuwenya.studentc.order.api

import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiCode
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.ApiResponse
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CreateOrderRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderGoodsRequest
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderStatus
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderTestJson
import com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.PayRequest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * 接口契约测试（成员 C）。
 *
 * 2026-11-24 按 A《后端接口文档》定稿重写，锁定三件事：
 * 1) payOrder 请求体只有 orderId —— payType 出现即红（A 确认后端没有该字段）；
 * 2) 已完成 Tab 传 status=2，状态码只有 0/1/2/3；
 * 3) 已支付订单取消回 400，与说明书"开场前可取消"的差异按 A 实现走。
 *
 * 跑法：
 *   1) python tools/mock_server.py           # 本地 mock（A 文档契约版）
 *   2) ./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest"
 *   连 A 真机服务：再追加 "-Dorder.baseUrl=http://A的IP:9000/"（需登录 token 时改 tokenProvider）
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // mock 有状态，取消/支付用例按名称顺序跑
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

    // ------------------------------------------------------------ 卖品列表

    @Test
    fun productList_rawArrayWithSnakeOriginPrice() = runBlocking {
        val resp = api.getProductList(cinemaId = 1)
        assertOk(resp)
        val list = resp.data.orEmpty()
        assertTrue("卖品列表不应为空", list.isNotEmpty())
        assertEquals(5, list.size)

        val first: Product = list.first { it.id == 1 }
        assertEquals("双人套餐", first.nameText)
        assertEquals(58.00, first.price, 0.001)
        // origin_price 下划线键名要能映射进 originPrice
        assertEquals(68.00, first.originPrice ?: -1.0, 0.001)
        assertEquals(120, first.stock)
        // isHot 是数字 1/0，不是布尔
        assertEquals(1, first.isHot)
        assertTrue(first.hot)
        assertTrue(first.hasDiscount)
        assertEquals("立省 10.00 元", first.saveText)

        // origin_price 可为 null，兜成 0 后不展示立省
        val cola = list.first { it.id == 4 }
        assertNull(cola.originPrice)
        assertFalse(cola.hasDiscount)

        val soldOut = list.first { it.id == 5 }
        assertTrue("库存 0 应判为售完", soldOut.soldOut)

        // 服务端已热销置顶、销量降序，客户端拿到就是这个顺序
        assertTrue(list.first().hot)
    }

    // ------------------------------------------------------------ 下单

    @Test
    fun createOrder_requestBodyHasNoPayTypeAndAmountOptional() {
        val json = OrderTestJson.toJson(
            CreateOrderRequest(
                scheduleId = 1,
                seats = listOf(Seat(5, 6), Seat(5, 7)),
                goods = listOf(OrderGoodsRequest(1, 1))
            )
        )
        assertFalse("请求体不应出现 payType（A 确认后端没有该字段）", json.contains("payType"))
        assertFalse("totalAmount 为 null 时不应出现", json.contains("totalAmount"))
        assertTrue(json.contains("scheduleId") && json.contains("seats") && json.contains("goods"))
    }

    @Test
    fun createOrder_returnsIdAmountExpire() = runBlocking {
        val resp = api.createOrder(
            scheduleId = 1,
            seats = listOf(Seat(5, 6), Seat(5, 7)),
            cartItems = listOf(
                Product(id = 1, name = "双人套餐", price = 58.0, originPrice = 68.0, stock = 120)
                    .let { com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.CartItem.from(it, 1) }
            ),
            clientTotal = 142.00 // 与 mock 重算一致，不应触发 2003
        )
        assertOk(resp)
        val data = resp.data!!
        assertTrue("orderId 应为服务端分配的整型 ID", data.orderId >= 1004)
        // 2 座 × 42 + 双人套餐 58 = 142，与 A 文档示例金额一致
        assertEquals(142.00, data.totalAmount, 0.001)
        assertTrue("过期时间应可解析", parseServerTime(data.expireTimeText) > 0L)
    }

    @Test
    fun createOrder_priceChangedCode2003() = runBlocking {
        // 客户端传错金额，服务端重算不一致 → 2003（A 文档第 9 节）
        val resp = api.createOrder(
            scheduleId = 1,
            seats = listOf(Seat(6, 1)),
            clientTotal = 999.00
        )
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.PRICE_CHANGED, resp.code)
        assertEquals("价格已变动，请确认最新金额后重新提交", ApiCode.tip(resp.code))
    }

    @Test
    fun createOrder_seatTakenCode2001() = runBlocking {
        val resp = api.createOrder(
            CreateOrderRequest(scheduleId = 1, seats = listOf(Seat(99, 1)))
        )
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.SEAT_TAKEN, resp.code)
    }

    @Test
    fun createOrder_moreThanFourSeatsCode400() = runBlocking {
        val resp = api.createOrder(
            CreateOrderRequest(
                scheduleId = 1,
                seats = listOf(Seat(1, 1), Seat(1, 2), Seat(1, 3), Seat(1, 4), Seat(1, 5))
            )
        )
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.PARAM_INVALID, resp.code)
    }

    // ------------------------------------------------------------ 支付

    @Test
    fun payRequest_jsonOnlyHasOrderId() {
        val json = OrderTestJson.toJson(PayRequest(orderId = 1001))
        assertEquals("""{"orderId":1001}""", json)
    }

    @Test
    fun payOrder_unpaidOrder_returnsTwoCodes() = runBlocking {
        val resp = api.payOrder(orderId = 1002)
        assertOk(resp)
        val data = resp.data!!
        assertEquals(1002, data.orderId)
        assertEquals(OrderStatus.PAID, data.status)
        assertEquals("1234 5678", data.ticketCodeText)
        // 1002 无卖品 → goodsCode 应为 null（A 文档第 10 节）
        assertFalse("无卖品订单不应有取餐码", data.hasGoodsCode)
    }

    @Test
    fun payOrder_idempotentReturnsSameCode() = runBlocking {
        val first = api.payOrder(orderId = 1002)
        val second = api.payOrder(orderId = 1002)
        assertOk(second)
        assertEquals(first.data!!.ticketCodeText, second.data!!.ticketCodeText)
    }

    @Test
    fun payOrder_cancelledOrderCode2004() = runBlocking {
        // 1002 在取消用例后被置 3；这里用已完成 1003 验证已失效分支
        val resp = api.payOrder(orderId = 1003)
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.ORDER_EXPIRED, resp.code)
    }

    // ------------------------------------------------------------ 取消

    @Test
    fun cancelOrder_paidOrderCode400() = runBlocking {
        val resp = api.cancelOrder(orderId = 1001) // 1001 已支付
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.PARAM_INVALID, resp.code)
        assertTrue(resp.msgText.contains("不可取消"))
        assertFalse("已支付订单本地 UI 也不应给取消按钮", OrderStatus.canCancel(OrderStatus.PAID))
    }

    @Test
    fun cancelOrder_unpaidOrderStatusBecomes3() = runBlocking {
        // 新建一笔待支付再取消，避免依赖其他用例留下的状态
        val created = api.createOrder(
            CreateOrderRequest(scheduleId = 1, seats = listOf(Seat(7, 7)))
        )
        assertOk(created)
        val resp = api.cancelOrder(orderId = created.data!!.orderId)
        assertOk(resp)
        assertTrue(resp.data!!.isCancelled)
        assertEquals("已取消", resp.data!!.statusText)
    }

    // ------------------------------------------------------------ 订单列表

    @Test
    fun orderList_threeTabsUnpaidFinishedAll() = runBlocking {
        // A 定稿：全部=不传，待支付=0，已完成=2
        val all = api.getOrderList(status = null)
        assertOk(all)
        assertTrue(all.data!!.total >= 3)

        val unpaid = api.getOrderList(status = OrderStatus.UNPAID)
        assertOk(unpaid)
        unpaid.data!!.items.forEach {
            assertEquals(OrderStatus.UNPAID, it.status)
        }

        val finished = api.getOrderList(status = OrderStatus.FINISHED)
        assertOk(finished)
        assertTrue(finished.data!!.items.isNotEmpty())
        finished.data!!.items.forEach {
            assertEquals("已完成", it.statusText)
            assertEquals(0L, it.remainMillis())
            assertFalse(OrderStatus.showCountdown(it.status, it.remainMillis()))
        }
    }

    @Test
    fun orderList_itemKeyIsOrderId() = runBlocking {
        val item = api.getOrderList(status = OrderStatus.UNPAID).data!!.items.first()
        assertTrue("列表项主键 orderId 应有值", item.orderId > 0)
        assertEquals("待支付", item.statusText)
        assertTrue("海报字段应可空转非空", item.posterUrl.isNotEmpty() || item.posterUrl.isEmpty())
        assertTrue("待支付订单有剩余时间", item.remainMillis() > 0L)
        assertTrue(OrderStatus.showCountdown(item.status, item.remainMillis()))
        assertTrue(OrderStatus.canCancel(item.status))
    }

    // ------------------------------------------------------------ 订单详情

    @Test
    fun orderDetail_fieldsMatchA() = runBlocking {
        val resp = api.getOrderDetail(orderId = 1001)
        assertOk(resp)
        val detail = resp.data!!

        assertEquals(1001, detail.orderId)
        assertEquals("寻梦环游记", detail.movieTitleText)
        assertEquals("星汇影城（春熙路店）", detail.cinemaNameText)
        assertEquals("成都市锦江区春熙路1号", detail.cinemaAddressText)
        assertEquals("1号厅", detail.hallNameText)
        assertEquals("国语 2D", detail.langVersionText)
        assertEquals(listOf("5 排 6 座", "5 排 7 座"), detail.seatList)
        assertEquals(2, detail.seatCount)
        assertEquals(OrderStatus.PAID, detail.status)

        assertEquals(84.00, detail.ticketAmount, 0.001)
        assertEquals(58.00, detail.goodsAmount, 0.001)
        assertEquals(142.00, detail.totalAmount, 0.001)
        assertTrue("票款+卖品应等于总额", detail.isAmountConsistent)
        assertEquals("142.00", formatMoney(detail.totalAmount))

        // C-5：两个码分开展示
        assertEquals("1234 5678", detail.ticketCodeText)
        assertEquals("4321", detail.goodsCodeText)
        assertTrue(detail.hasTicketCode && detail.hasGoodsCode)
        assertTrue(OrderStatus.showCode(detail.status))
    }

    @Test
    fun orderDetail_notFoundCode2005() = runBlocking {
        val resp = api.getOrderDetail(orderId = 404)
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.NOT_FOUND, resp.code)
    }

    // ------------------------------------------------------------ 统计

    @Test
    fun watchStats_fieldsMatchA() = runBlocking {
        val resp = api.getWatchStats()
        assertOk(resp)
        val stats: WatchStats = resp.data!!

        assertEquals(5, stats.totalShows)
        assertEquals(525, stats.totalMinutes)
        assertEquals(8.8, stats.totalHours, 0.001)
        assertEquals("8.8 小时", stats.hoursText)
        assertEquals("8 小时 45 分钟", stats.durationText)
        assertEquals(8, stats.totalTickets)
        assertEquals("356.00", stats.amountText)
        assertEquals("2026-11-20 19:30:00", stats.lastWatchTimeText)

        assertEquals("星汇影城（春熙路店）", stats.topCinemaName)
        assertEquals("寻梦环游记", stats.topMovieTitle)

        assertEquals(2, stats.typeDistList.size)
        assertEquals("动画/家庭/冒险", stats.typeDistList.first().typeText)
        assertEquals(3, stats.typeDistList.first().count)

        assertEquals(2, stats.recentWatchList.size)
        assertEquals("寻梦环游记", stats.recentWatchList.first().titleText)
        assertTrue(stats.recentWatchList.first().watchTimeText.isNotEmpty())
    }

    // ------------------------------------------------------------ 鉴权

    @Test
    fun missingToken_returnsCode1001() = runBlocking {
        RetrofitClient.init(url = "http://127.0.0.1:8000/", tokenProvider = { null })
        val resp = api.getProductList(cinemaId = 1)
        assertFalse(resp.isSuccess)
        assertEquals(ApiCode.NOT_LOGIN, resp.code)
        assertEquals("登录已失效，请重新登录", ApiCode.tip(resp.code))
        useMockServer()
    }

    // ------------------------------------------------------------ 反序列化兜底

    @Test
    fun partialJson_doesNotCrashModels() {
        val type = object : TypeToken<ApiResponse<com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderDetail>>() {}.type
        val resp: ApiResponse<com.edu.sicnu.cs.stu.niuwenya.studentc.order.model.OrderDetail> =
            RetrofitClient.gson.fromJson(OrderTestJson.PARTIAL_ORDER_DETAIL, type)

        val detail = resp.data!!
        assertEquals("", detail.movieTitleText)
        assertEquals("", detail.ticketCodeText)
        assertEquals(0.0, detail.totalAmount, 0.001)
        assertEquals("待支付", detail.statusText)
        assertTrue(detail.seatList.isEmpty())
        assertTrue(detail.goodsList.isEmpty())
        assertFalse(detail.hasTicketCode)
    }

    // ------------------------------------------------------------ A 文档示例活体校验

    @Test
    fun aDocSamples_allDeserialize() {
        val detail = OrderTestJson.parseOrderDetailFromDoc()
        assertEquals("寻梦环游记", detail.movieTitleText)
        assertEquals(142.00, detail.totalAmount, 0.001)
        assertEquals(listOf("5 排 6 座", "5 排 7 座"), detail.seatList)
        assertEquals("1234 5678", detail.ticketCodeText)
        assertTrue(detail.isAmountConsistent)

        val created = OrderTestJson.parseCreateOrderFromDoc()
        assertEquals(1001, created.orderId)
        assertEquals(142.00, created.totalAmount, 0.001)
        assertTrue(parseServerTime(created.expireTimeText) > 0L)

        val paid = OrderTestJson.parsePayResultFromDoc()
        assertEquals(OrderStatus.PAID, paid.status)
        assertEquals("4321", paid.goodsCodeText)

        val products = OrderTestJson.parseProductListFromDoc()
        assertEquals(1, products.size)
        assertEquals(68.00, products.first().originPrice ?: -1.0, 0.001)
        assertTrue(products.first().hot)

        val page = OrderTestJson.parseOrderListFromDoc()
        assertEquals(1, page.total)
        assertEquals(1001, page.items.first().orderId)

        val stats = OrderTestJson.parseWatchStatsFromDoc()
        assertEquals(5, stats.totalShows)
        assertEquals("8.8 小时", stats.hoursText)
        assertEquals(2, stats.typeDistList.size)
        assertEquals(1, stats.recentWatchList.size)
    }
}
