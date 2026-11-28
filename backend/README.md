# 电影票订购 App 后端接口文档

> 软件工程课程期末项目 · 后端服务
> 技术栈：Python 3.10+ / FastAPI / SQLite
> 服务地址：`http://localhost:9000`（局域网手机访问替换为电脑 IP）
> Swagger 在线文档：`http://localhost:9000/docs`

---

## 一、快速启动

### 1. 安装依赖

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
```

### 2. 注入演示数据

```bash
python seed.py
```

**演示账号**：`13800000001` / `123456`

> 注意：`seed.py` 会清空所有表并重置自增 ID，每次重跑后演示 ID 固定为 1、2、3……，方便联调。

### 3. 启动服务

```bash
uvicorn main:app --host 0.0.0.0 --port 9000 --reload
```

---

## 二、全局约定

### 2.1 统一响应格式

**所有接口**返回统一结构，HTTP 状态码**恒为 200**，客户端只判断 `body.code`：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 0=成功，非 0=业务错误，见错误码表 |
| msg | string | 提示信息，成功固定 `"ok"` |
| data | any | 业务数据，失败时为 `null` |

### 2.2 认证方式

需要登录的接口在请求头携带：

```
Authorization: Bearer {token}
```

`token` 由 `/api/login` 接口返回。服务端重启后 token 失效，客户端需重新登录。

### 2.3 命名与数据约定

| 约定 | 说明 |
|------|------|
| 字段命名 | 接口返回字段统一**小驼峰**（如 `movieId`、`totalAmount`） |
| 金额单位 | 元，保留两位小数，如 `42.00` |
| 时间格式 | `YYYY-MM-DD HH:MM:SS`，如 `2026-11-23 19:30:00` |
| 日期格式 | `YYYY-MM-DD`，如 `2026-11-23` |
| 图片地址 | 接口返回**完整 URL**，前端可直接用 Glide 加载，无需拼接 |

**字段命名例外**（其余字段均为小驼峰）：

| 接口 | 字段 | 说明 |
|------|------|------|
| `/api/getMovieList` | `release_date` | 上映日期 |
| `/api/getMovieDetail` | `release_date` | 上映日期 |
| `/api/getProductList` | `origin_price` | 原价，可为 `null` |

### 2.4 错误码表

| code | 含义 | 触发场景 |
|------|------|----------|
| 0 | 成功 | — |
| 400 | 参数不合法 | 参数缺失、格式错误、超出限制 |
| 1001 | 未登录 / token 失效 | 未携带 Authorization 或 token 不存在 |
| 2001 | 座位已被占用 | 下单时座位已售/已锁 |
| 2002 | 卖品库存不足 | 下单时卖品库存不够 |
| 2003 | 价格已变动 | 客户端传入金额与服务端重算不一致 |
| 2004 | 订单已失效 | 操作已取消或已超时的订单 |
| 2005 | 资源不存在 | 影片/场次/订单/用户不存在 |

---

## 三、接口列表

共 **14 个接口**，按模块分组：

| 模块 | 接口 | 方法 | 是否需要登录 |
|------|------|------|--------------|
| 账号 | `/api/login` | POST | 否 |
| 账号 | `/api/getUserInfo` | GET | 是 |
| 影片 | `/api/getMovieList` | GET | 否 |
| 影片 | `/api/getMovieDetail` | GET | 否 |
| 影院/场次 | `/api/getCinemaList` | GET | 否 |
| 影院/场次 | `/api/getScheduleList` | GET | 否 |
| 影院/场次 | `/api/getSeatMap` | GET | 否 |
| 卖品 | `/api/getProductList` | GET | 否 |
| 订单 | `/api/createOrder` | POST | 是 |
| 订单 | `/api/payOrder` | POST | 是 |
| 订单 | `/api/cancelOrder` | POST | 是 |
| 订单 | `/api/getOrderList` | GET | 是 |
| 订单 | `/api/getOrderDetail` | GET | 是 |
| 订单 | `/api/getWatchStats` | GET | 是 |

---

## 四、接口详情

### 1. 登录

```
POST /api/login
```

**请求体**

```json
{
  "phone": "13800000001",
  "password": "123456"
}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "token": "a1b2c3d4e5f6...",
    "userInfo": {
      "id": 1,
      "nickname": "小明",
      "avatar": null
    }
  }
}
```

**失败响应**

```json
{"code": 1001, "msg": "账号或密码错误", "data": null}
```

---

### 2. 获取用户信息

```
GET /api/getUserInfo
Authorization: Bearer {token}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "id": 1,
    "nickname": "小明",
    "avatar": null
  }
}
```

---

### 3. 影片列表

```
GET /api/getMovieList?status=now&pageNum=1&pageSize=10
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | `now`=热映（默认），`soon`=待映 |
| pageNum | int | 否 | 页码，默认 1，最小 1 |
| pageSize | int | 否 | 每页条数，默认 10，最小 1 |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "list": [
      {
        "id": 1,
        "title": "寻梦环游记",
        "poster": "http://localhost:9000/static/posters/coco.jpg",
        "score": 9.6,
        "duration": 105,
        "type": "动画/家庭/冒险",
        "release_date": "2017-11-24"
      }
    ],
    "total": 6
  }
}
```

---

### 4. 影片详情

```
GET /api/getMovieDetail?movieId=1
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| movieId | int | 是 | 影片 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "id": 1,
    "title": "寻梦环游记",
    "poster": "http://localhost:9000/static/posters/coco.jpg",
    "score": 9.6,
    "duration": 105,
    "type": "动画/家庭/冒险",
    "director": "李·昂克里奇",
    "actors": "安东尼·冈萨雷斯,本杰明·布拉特,盖尔·加西亚·贝纳尔",
    "description": "鞋匠家庭出身的米格在亡灵节误入亡灵世界……",
    "release_date": "2017-11-24"
  }
}
```

---

### 5. 影院列表

```
GET /api/getCinemaList?movieId=1&pageNum=1&pageSize=20
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| movieId | int | 是 | 影片 ID |
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页条数，默认 20 |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "list": [
      {
        "id": 1,
        "name": "星汇影城（春熙路店）",
        "address": "成都市锦江区春熙路1号",
        "rating": 4.8
      }
    ],
    "total": 3
  }
}
```

---

### 6. 场次列表

```
GET /api/getScheduleList?cinemaId=1&movieId=1&date=2026-11-23
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cinemaId | int | 是 | 影院 ID |
| movieId | int | 是 | 影片 ID |
| date | string | 是 | 日期，格式 `YYYY-MM-DD` |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": [
    {
      "id": 1,
      "startTime": "2026-11-23 19:30:00",
      "hallName": "1号厅",
      "price": 42.0,
      "langVersion": "国语 2D",
      "leftSeat": 138
    }
  ]
}
```

> `leftSeat` = 总座位数 − 已售座位数 − 有效锁定座位数，实时计算。

---

### 7. 座位图

```
GET /api/getSeatMap?scheduleId=1
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scheduleId | int | 是 | 场次 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "rows": 10,
    "cols": 14,
    "price": 42.0,
    "seats": [
      {"row": 1, "col": 1, "status": 0},
      {"row": 1, "col": 2, "status": 1},
      {"row": 1, "col": 3, "status": 2}
    ]
  }
}
```

**座位状态说明**

| status | 含义 | 颜色建议 |
|--------|------|----------|
| 0 | 可选 | 白色/灰色 |
| 1 | 已售 | 红色/不可点击 |
| 2 | 已锁 | 橙色/不可点击 |

> 座位状态**不落库**，每次请求实时推导：已支付订单→已售，未过期锁座→已锁，其余可选。

---

### 8. 卖品列表

```
GET /api/getProductList?cinemaId=1
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cinemaId | int | 是 | 影院 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": [
    {
      "id": 1,
      "name": "双人套餐",
      "image": "http://localhost:9000/static/products/double.jpg",
      "price": 58.0,
      "origin_price": 68.0,
      "stock": 120,
      "salesCount": 0,
      "isHot": 1
    }
  ]
}
```

> 热销商品（`isHot=1`）置顶，同热销状态按销量降序排列。

---

### 9. 创建订单

```
POST /api/createOrder
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**

```json
{
  "scheduleId": 1,
  "seats": [
    {"row": 5, "col": 6},
    {"row": 5, "col": 7}
  ],
  "goods": [
    {"productId": 1, "quantity": 1}
  ],
  "totalAmount": 142.00
}
```

**参数说明**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scheduleId | int | 是 | 场次 ID |
| seats | array | 是 | 座位列表，至少 1 个，最多 4 个 |
| seats[].row | int | 是 | 排号 |
| seats[].col | int | 是 | 列号 |
| goods | array | 否 | 卖品列表，默认空 |
| goods[].productId | int | 是 | 卖品 ID |
| goods[].quantity | int | 是 | 数量，每种最多 5 份 |
| totalAmount | float | 否 | 客户端计算金额，仅用于校验是否变价 |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "orderId": 1001,
    "totalAmount": 142.00,
    "expireTime": "2026-11-23 20:15:00"
  }
}
```

**业务约束**

- 单次最多选 **4 个座位**
- 每种卖品最多 **5 份**
- 未支付订单 **15 分钟**后自动取消
- 订单金额由**服务端重算**，客户端金额仅用于发现变价（不一致报 2003）
- 座位锁定通过数据库唯一索引兜底，防止并发超卖

---

### 10. 模拟支付

```
POST /api/payOrder
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**

```json
{"orderId": 1001}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "orderId": 1001,
    "status": 1,
    "ticketCode": "1234 5678",
    "goodsCode": "4321"
  }
}
```

> 若订单包含卖品，`goodsCode` 为取餐码；无卖品则为 `null`。
> 已支付/已完成的订单重复调用支付接口会幂等返回原取票码，不会报错。

---

### 11. 取消订单

```
POST /api/cancelOrder
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**

```json
{"orderId": 1001}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "orderId": 1001,
    "status": 3
  }
}
```

**失败场景**

- 已支付订单：`{"code": 400, "msg": "已支付订单不可取消"}`
- 已取消/已超时：`{"code": 2004, "msg": "订单已失效"}`
- 订单不存在：`{"code": 2005, "msg": "订单不存在"}`

---

### 12. 订单列表

```
GET /api/getOrderList?status=1&pageNum=1&pageSize=10
Authorization: Bearer {token}
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | int | 否 | 0=待支付，1=已支付，2=已完成，3=已取消；不传则查全部 |
| pageNum | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页条数，默认 10 |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "list": [
      {
        "orderId": 1001,
        "movieTitle": "寻梦环游记",
        "poster": "http://localhost:9000/static/posters/coco.jpg",
        "cinemaName": "星汇影城（春熙路店）",
        "hallName": "1号厅",
        "startTime": "2026-11-23 19:30:00",
        "seatCount": 2,
        "seats": ["5 排 6 座", "5 排 7 座"],
        "totalAmount": 142.00,
        "status": 1,
        "createTime": "2026-11-23 18:00:00",
        "expireTime": "2026-11-23 18:15:00"
      }
    ],
    "total": 1
  }
}
```

---

### 13. 订单详情

```
GET /api/getOrderDetail?orderId=1001
Authorization: Bearer {token}
```

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderId | int | 是 | 订单 ID |

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "orderId": 1001,
    "status": 1,
    "movieTitle": "寻梦环游记",
    "poster": "http://localhost:9000/static/posters/coco.jpg",
    "cinemaName": "星汇影城（春熙路店）",
    "cinemaAddress": "成都市锦江区春熙路1号",
    "hallName": "1号厅",
    "startTime": "2026-11-23 19:30:00",
    "langVersion": "国语 2D",
    "seats": ["5 排 6 座", "5 排 7 座"],
    "seatCount": 2,
    "goods": [
      {"name": "双人套餐", "quantity": 1, "amount": 58.00}
    ],
    "ticketAmount": 84.00,
    "goodsAmount": 58.00,
    "totalAmount": 142.00,
    "ticketCode": "1234 5678",
    "goodsCode": "4321",
    "createTime": "2026-11-23 18:00:00",
    "expireTime": "2026-11-23 18:15:00"
  }
}
```

---

### 14. 观影统计

```
GET /api/getWatchStats
Authorization: Bearer {token}
```

**成功响应**

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "totalShows": 5,
    "totalMinutes": 525,
    "totalHours": 8.8,
    "totalTickets": 8,
    "totalAmount": 356.00,
    "lastWatchTime": "2026-11-20 19:30:00",
    "topCinema": {
      "name": "星汇影城（春熙路店）",
      "count": 3
    },
    "topMovie": {
      "title": "寻梦环游记",
      "count": 2
    },
    "typeDist": [
      {"type": "动画/家庭/冒险", "count": 3},
      {"type": "喜剧/动作/悬疑", "count": 2}
    ],
    "recentList": [
      {
        "title": "寻梦环游记",
        "poster": "http://localhost:9000/static/posters/coco.jpg",
        "type": "动画/家庭/冒险",
        "watchTime": "2026-11-20 19:30:00",
        "cinemaName": "星汇影城（春熙路店）"
      }
    ]
  }
}
```

**字段说明**

| 字段 | 说明 |
|------|------|
| totalShows | 有效订单数（已支付+已完成） |
| totalMinutes | 累计观影时长（分钟） |
| totalHours | 累计观影时长（小时，保留 1 位小数） |
| totalTickets | 累计购票张数 |
| totalAmount | 累计消费金额 |
| lastWatchTime | 最近一次观影时间 |
| topCinema | 最常去的影院及次数 |
| topMovie | 最常看的电影及次数 |
| typeDist | 类型分布 |
| recentList | 最近 5 部看过的电影 |

---

## 五、订单状态机

```
0 待支付 ──支付──> 1 已支付 ──客户端展示──> 2 已完成
    │
    ├──用户主动取消──────────> 3 已取消
    └──超过 15 分钟未支付────> 3 已取消（系统自动释放座位与库存）
```

---

## 六、数据库表结构（9 张表）

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| movie | 影片表 |
| cinema | 影院表 |
| hall | 影厅表 |
| schedule | 场次表 |
| product | 卖品表 |
| order_info | 订单主表 |
| order_goods | 订单卖品明细表 |
| seat_lock | 座位锁表（唯一索引防并发） |

**关键索引**

```sql
UNIQUE (schedule_id, row_num, col_num)  -- seat_lock 表，防止同一座位被并发锁定
```

---

## 七、项目结构

```
backend/
├── main.py              # FastAPI 入口，注册路由、CORS、异常处理
├── database.py          # SQLite 连接管理 + 9 张表结构
├── utils.py             # 统一响应体、图片 URL 拼接
├── seed.py              # 演示数据注入脚本
├── requirements.txt     # Python 依赖
├── movies.db            # SQLite 数据库文件
├── static/              # 静态资源（海报、卖品图）
│   ├── posters/
│   └── products/
├── routers/             # 路由层（参数校验、转发）
│   ├── auth.py
│   ├── movies.py
│   ├── cinemas.py
│   ├── products.py
│   └── orders.py
└── services/            # 业务逻辑层
    └── order_service.py
```

---

## 八、常见问题

**Q: 手机访问不到接口？**
A: 把 `localhost` 替换为电脑的局域网 IP（如 `192.168.1.100`），并确保手机和电脑在同一 WiFi 下。启动命令已使用 `--host 0.0.0.0` 监听所有网卡。

**Q: 接口返回 1001？**
A: token 失效或未携带。重新调用 `/api/login` 获取新 token。

**Q: 座位图状态不对？**
A: 座位状态实时推导，已支付订单的座位为已售（1），未过期锁座为已锁（2），取消/超时后自动恢复可选（0）。

**Q: 金额对不上？**
A: 订单金额由服务端重算，客户端传入的 `totalAmount` 仅用于校验是否变价。若不一致会报 2003。

---

## 九、演示数据说明

当前演示数据包含：

- **影片**：热映 + 待映，具体片目由 `data/movies.json` 决定（未提供时使用内置演示数据）
- **影院**（3 家）：星汇影城（春熙路店）、锦江国际影城、天府艺术影城
- **影厅**：每家 2 个厅，均为 10 排 × 14 列
- **场次**：未来 7 天，每厅每天 4 场，**所有影片均参与排片**（待映片从第 4 天开始排）
- **卖品**：每家影院 5 种（单人套餐、双人套餐、大桶爆米花、可乐大杯、香草冰淇淋）

### 影片数据来源

海报复用本地文件，全部存储在 `static/posters/`，断网环境也能正常显示。

**方式一：内置演示数据（默认）**
直接运行 `python seed.py`，使用脚本内置的影片清单。

**方式二：从 TMDB 同步真实数据**

```powershell
# 1. 注册免费 API Key：https://www.themoviedb.org/settings/api
# 2. 设置凭证（本次终端有效）
$env:TMDB_API_KEY="你的 v3 key"
# 或使用 v4 读访问令牌
$env:TMDB_READ_TOKEN="你的 v4 令牌"

# 3. 拉取数据 + 下载海报（只在开发时联网一次）
python fetch_tmdb.py

# 4. 注入数据库
python seed.py
```

同步结果写入 `data/movies.json`，海报下载为 `static/posters/tmdb_{id}.jpg`。后端运行时**不依赖** TMDB，断网演示不受影响。

> 数据来源：TMDB（The Movie Database）。此产品使用 TMDB API，但未获得 TMDB 认可或认证。
> This product uses the TMDB API but is not endorsed or certified by TMDB.

