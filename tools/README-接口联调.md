# 交易链路接口联调（成员 C）

D1 产出：`RetrofitClient` 单例 + `OrderApi` 接口声明 + 契约测试，验收为
「能拿到接口返回的 JSON，数据类和 JSON 字段对得上」。

## 一、本地 mock（A 的后端没起来时用这个）

```bash
python tools/mock_server.py            # 默认 8000 端口
python tools/mock_server.py 8899       # 换端口
```

返回的 JSON 严格照《课程设计说明书》4.2 表8 与 4.3 示例逐字段构造，含
`code 1001 / 2001 / 2004 / 2005` 四个错误分支，可提前把页面和错误提示全跑通。

## 二、跑契约测试（16 个用例）

```bash
./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest"
```

这些是真实 HTTP 请求，走的就是 `RetrofitClient` 里配好的单例、鉴权拦截器与日志拦截器，
所以 baseUrl、超时、Authorization 头是否生效会一并验证。

切到 A 的真实服务端：

```bash
./gradlew :app:testDebugUnitTest --tests "*OrderApiContractTest" "-Dorder.baseUrl=http://A的局域网IP:8000/"
```

## 三、Postman / 浏览器

`tools/postman_collection.json` 导入 Postman，13 个请求（7 个接口正常用例 + 4 个错误码用例 + 列表三 Tab）。
集合已配 Bearer 鉴权，改右上角环境变量 `baseUrl` 即可切换服务端；
每个请求也能直接复制 URL 到浏览器地址栏看 JSON（mock 已开 CORS）。

## 四、客户端里怎么接

```kotlin
// 地址不写死，模拟器用 10.0.2.2，真机用 A 的局域网 IP
RetrofitClient.init(
    url = "http://10.0.2.2:8000/",
    tokenProvider = { 本地存的 token }   // 登录态由成员 B 写入，这里只读
)

// 页面 / ViewModel 里
val resp = RetrofitClient.orderApi.getProductList(cinemaId = 1)
if (resp.isSuccess) showList(resp.data!!.items)
else Toast.makeText(ctx, ApiCode.tip(resp.code), LENGTH_SHORT).show()
```

## 五、两个待确认项（阻塞 D2 联调）

1. `payType` 的合法取值说明书未给枚举，代码里暂用 `alipay / wechat / unionpay`，需 A 定稿。
2. `getOrderList` 的 `status` 参数：三 Tab 用不传 / 0 / 2，"已完成"取 2 还是 1|2 需与 A 对齐。
