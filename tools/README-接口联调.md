# 交易链路接口联调（成员 C）

**2026-11-24 定稿**：以成员 A 的《后端接口文档》（README.md）为唯一依据，
与课程说明书不一致处全部按 A 的文档走。已确认要点：

| 事项 | 定稿结论 |
|------|----------|
| payOrder 请求体 | 只有 `orderId`，**没有 payType**；支付宝/微信等只是本地 UI 单选，不往接口传 |
| 订单列表三 Tab | 全部=不传 status；待支付=`status=0`；已完成=`status=2`（状态码只有 0/1/2/3） |
| 取消订单 | 仅待支付可取消；已支付回 `400`"已支付订单不可取消"（说明书的"开场前可取消"作废） |
| getProductList | `data` 是裸数组；原价键名 `origin_price`（可 null）；`isHot` 是 1/0 |
| createOrder | 可选传 `totalAmount` 供服务端查变价（不一致回 2003）；最多 4 座；orderId 是 int |
| 统计接口 | 字段是 totalShows / totalMinutes / totalHours / typeDist / recentList 一族，无月度趋势 |
| 服务地址 | A 真实服务 `http://A的IP:9000`（Swagger 在 /docs）；本地 mock 用 8000 错开 |

## 一、本地 mock（A 的服务没起来时用）

```bash
python tools/mock_server.py            # 8000 端口，契约与 A 文档一致
```

含错误分支触发器：座位 row=99 回 2001；totalAmount 不符回 2003；
orderId=404 回 2005；不传 token 回 1001。

## 二、跑契约测试（20 个用例）

```bash
./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest"
```

真实 HTTP 请求，同时验证 RetrofitClient 的单例、鉴权头、超时。
关键回归点：`payRequest_jsonOnlyHasOrderId` 钉死请求体必须是 `{"orderId":...}`；
`orderList_threeTabsUnpaidFinishedAll` 钉死 Tab 传值；
`cancelOrder_paidOrderCode400` 钉死已支付不可取消。

连 A 的真实服务（需先 login 拿真 token，改测试里的 tokenProvider）：

```bash
./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest" "-Dorder.baseUrl=http://A的IP:9000/"
```

## 三、Postman / 浏览器

导入 `tools/postman_collection.json`（14 个请求），改环境变量 `baseUrl` 切换 mock / 真实服务。
mock 已开 CORS，URL 也可直接粘浏览器验证。

## 四、客户端接法

```kotlin
// 模拟器 10.0.2.2:9000，真机用 A 的局域网 IP:9000；本地顶位用 RetrofitClient.MOCK_BASE_URL
RetrofitClient.init(
    url = "http://10.0.2.2:9000/",
    tokenProvider = { 本地存的 token }   // 登录态由 B 写入，这里只读
)

val resp = RetrofitClient.orderApi.getProductList(cinemaId = 1)   // data 是 List<Product>
if (resp.isSuccess) showList(resp.data.orEmpty())
else toast(resp.msgText.ifBlank { ApiCode.tip(resp.code) })
```
