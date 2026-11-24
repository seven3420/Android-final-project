#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交易链路接口 Mock 服务（成员 C 联调用）。

2026-11-24 按成员 A 的《后端接口文档》（README.md）全量对齐：
  - payOrder 只收 orderId（无 payType），幂等返回原码
  - getOrderList 已完成 Tab = status=2；列表项主键叫 orderId，含 poster 与 seats 文案数组
  - getProductList 返回裸数组；原价键名 origin_price（可 null）；isHot 是 1/0
  - createOrder 可选带 totalAmount，与重算不一致回 2003；orderId 是 int
  - 已支付订单取消回 400；错误码表含 400
用途：A 的 9000 端口服务没起来时本地顶位，客户端代码一行不用改，
切到 A 真实服务只改 RetrofitClient.init 的地址。

启动：python tools/mock_server.py            # 默认 8000 端口
      python tools/mock_server.py 8899      # 指定端口

鉴权：所有接口要求 Authorization: Bearer {token}，不对回 code 1001（与 A 文档一致）。
"""

import json
import sys
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

NOW = datetime(2026, 11, 24, 14, 0, 0)


def ts(delta_minutes: int = 0) -> str:
    return (NOW + timedelta(minutes=delta_minutes)).strftime("%Y-%m-%d %H:%M:%S")


def ok(data):
    return {"code": 0, "msg": "ok", "data": data}


def err(code, msg):
    return {"code": code, "msg": msg, "data": None}


# ---------------------------------------------------------------- 演示数据

TICKET_PRICE = 42.00

# getProductList：裸数组、origin_price、isHot 1/0（A 文档第 8 节）
PRODUCTS = [
    {"id": 1, "name": "双人套餐", "image": "http://127.0.0.1:8000/static/products/double.jpg",
     "price": 58.00, "origin_price": 68.00, "stock": 120, "salesCount": 66, "isHot": 1},
    {"id": 2, "name": "单人套餐", "image": "http://127.0.0.1:8000/static/products/single.jpg",
     "price": 32.00, "origin_price": 38.00, "stock": 80, "salesCount": 30, "isHot": 1},
    {"id": 3, "name": "大桶爆米花", "image": "http://127.0.0.1:8000/static/products/corn.jpg",
     "price": 25.00, "origin_price": 28.00, "stock": 60, "salesCount": 12, "isHot": 0},
    {"id": 4, "name": "可乐大杯", "image": "http://127.0.0.1:8000/static/products/cola.jpg",
     "price": 12.00, "origin_price": None, "stock": 2, "salesCount": 9, "isHot": 0},
    {"id": 5, "name": "香草冰淇淋", "image": "http://127.0.0.1:8000/static/products/icecream.jpg",
     "price": 15.00, "origin_price": None, "stock": 0, "salesCount": 2, "isHot": 0},
]

# 三张订单，覆盖 待支付/已支付→已完成/已取消；字段按 A 文档第 12 节列表项
ORDERS = {
    1001: {
        "orderId": 1001, "movieTitle": "寻梦环游记",
        "poster": "http://127.0.0.1:8000/static/posters/coco.jpg",
        "cinemaName": "星汇影城（春熙路店）", "hallName": "1号厅",
        "startTime": "2026-11-25 19:30:00", "seatCount": 2,
        "seats": ["5 排 6 座", "5 排 7 座"],
        "totalAmount": 142.00, "status": 1,
        "createTime": ts(-120), "expireTime": ts(-105),
        # 详情独有字段
        "cinemaAddress": "成都市锦江区春熙路1号", "langVersion": "国语 2D",
        "goods": [{"name": "双人套餐", "quantity": 1, "amount": 58.00}],
        "ticketAmount": 84.00, "goodsAmount": 58.00,
        "ticketCode": "1234 5678", "goodsCode": "4321",
    },
    1002: {  # 待支付：验证倒计时与取消
        "orderId": 1002, "movieTitle": "哪吒之魔童降世",
        "poster": "http://127.0.0.1:8000/static/posters/nzha.jpg",
        "cinemaName": "锦江国际影城", "hallName": "2号厅",
        "startTime": "2026-11-26 14:00:00", "seatCount": 1,
        "seats": ["8 排 9 座"],
        "totalAmount": 42.00, "status": 0,
        "createTime": ts(-3), "expireTime": ts(12),
        "cinemaAddress": "成都市锦江区红星路3号", "langVersion": "国语 2D",
        "goods": [], "ticketAmount": 42.00, "goodsAmount": 0.00,
        "ticketCode": None, "goodsCode": None,
    },
    1003: {  # 已完成：验证已完成 Tab status=2
        "orderId": 1003, "movieTitle": "心灵奇旅",
        "poster": "http://127.0.0.1:8000/static/posters/soul.jpg",
        "cinemaName": "天府艺术影城", "hallName": "3号厅",
        "startTime": "2026-11-20 19:30:00", "seatCount": 2,
        "seats": ["3 排 4 座", "3 排 5 座"],
        "totalAmount": 84.00, "status": 2,
        "createTime": ts(-60 * 72), "expireTime": ts(-60 * 72 + 15),
        "cinemaAddress": "成都市高新区天府大道9号", "langVersion": "原版 2D",
        "goods": [], "ticketAmount": 84.00, "goodsAmount": 0.00,
        "ticketCode": "9012 3456", "goodsCode": None,
    },
}

# 按 A 文档第 12 节裁剪列表项字段（详情独有字段不出现在列表里）
LIST_KEYS = ["orderId", "movieTitle", "poster", "cinemaName", "hallName", "startTime",
             "seatCount", "seats", "totalAmount", "status", "createTime", "expireTime"]

WATCH_STATS = {  # A 文档第 14 节字段
    "totalShows": 5, "totalMinutes": 525, "totalHours": 8.8,
    "totalTickets": 8, "totalAmount": 356.00,
    "lastWatchTime": "2026-11-20 19:30:00",
    "topCinema": {"name": "星汇影城（春熙路店）", "count": 3},
    "topMovie": {"title": "寻梦环游记", "count": 2},
    "typeDist": [
        {"type": "动画/家庭/冒险", "count": 3},
        {"type": "喜剧/动作/悬疑", "count": 2},
    ],
    "recentList": [
        {"title": "寻梦环游记", "poster": "http://127.0.0.1:8000/static/posters/coco.jpg",
         "type": "动画/家庭/冒险", "watchTime": "2026-11-20 19:30:00",
         "cinemaName": "星汇影城（春熙路店）"},
        {"title": "心灵奇旅", "poster": "http://127.0.0.1:8000/static/posters/soul.jpg",
         "type": "动画/家庭/奇幻", "watchTime": "2026-11-18 14:00:00",
         "cinemaName": "天府艺术影城"},
    ],
}

NEXT_ORDER_ID = [1004]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _send(self, payload: dict, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def _authed(self) -> bool:
        auth = self.headers.get("Authorization", "")
        return auth.startswith("Bearer ") and len(auth) > len("Bearer ")

    def _query(self) -> dict:
        return {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            return {}

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path))

    def do_OPTIONS(self):
        self._send(ok(None))

    # ------------------------------------------------------------ GET

    def do_GET(self):
        if not self._authed():
            return self._send(err(1001, "未登录或 token 失效"))
        path, q = urlparse(self.path).path, self._query()

        if path == "/api/getProductList":
            if "cinemaId" not in q:
                return self._send(err(400, "参数不合法"))
            return self._send(ok(PRODUCTS))  # 裸数组，热销置顶销量降序（数据本身已排好）

        if path == "/api/getOrderList":
            status = q.get("status")
            items = [
                {k: o[k] for k in LIST_KEYS}
                for o in ORDERS.values()
                if status in (None, "") or o["status"] == int(status)
            ]
            return self._send(ok({"list": items, "total": len(items)}))

        if path == "/api/getOrderDetail":
            order = ORDERS.get(int(q.get("orderId", -1)))
            if order is None:
                return self._send(err(2005, "订单不存在"))
            return self._send(ok(order))

        if path == "/api/getWatchStats":
            return self._send(ok(WATCH_STATS))

        return self._send(err(2005, "接口不存在: %s" % path))

    # ------------------------------------------------------------ POST

    def do_POST(self):
        if not self._authed():
            return self._send(err(1001, "未登录或 token 失效"))
        path, body = urlparse(self.path).path, self._body()

        if path == "/api/createOrder":
            seats = body.get("seats") or []
            goods = body.get("goods") or []
            if not (1 <= len(seats) <= 4):
                return self._send(err(400, "参数不合法：座位数需在 1 到 4 之间"))
            if seats[0].get("row") == 99:  # 约定的"座位被占"触发坐标，供测 2001
                return self._send(err(2001, "座位已被占用"))
            for g in goods:
                p = next((x for x in PRODUCTS if x["id"] == g.get("productId")), None)
                if p is None or g.get("quantity", 0) > 5 or g.get("quantity", 0) > p["stock"]:
                    return self._send(err(2002, "卖品库存不足"))
            goods_amount = sum(
                next(x["price"] for x in PRODUCTS if x["id"] == g["productId"]) * g["quantity"]
                for g in goods
            )
            total = round(TICKET_PRICE * len(seats) + goods_amount, 2)
            client_total = body.get("totalAmount")
            if client_total is not None and abs(float(client_total) - total) >= 0.01:
                return self._send(err(2003, "价格已变动"))
            oid = NEXT_ORDER_ID[0]
            NEXT_ORDER_ID[0] += 1
            ORDERS[oid] = {
                "orderId": oid, "movieTitle": "新订单影片", "poster": "", "cinemaName": "星汇影城（春熙路店）",
                "hallName": "1号厅", "startTime": "2026-11-27 19:30:00", "seatCount": len(seats),
                "seats": ["%s 排 %s 座" % (s["row"], s["col"]) for s in seats],
                "totalAmount": total, "status": 0, "createTime": ts(), "expireTime": ts(15),
                "cinemaAddress": "成都市锦江区春熙路1号", "langVersion": "国语 2D",
                "goods": [{"name": next(x["name"] for x in PRODUCTS if x["id"] == g["productId"]),
                           "quantity": g["quantity"],
                           "amount": round(next(x["price"] for x in PRODUCTS if x["id"] == g["productId"]) * g["quantity"], 2)}
                          for g in goods],
                "ticketAmount": TICKET_PRICE * len(seats), "goodsAmount": round(goods_amount, 2),
                "ticketCode": None, "goodsCode": None,
            }
            return self._send(ok({"orderId": oid, "totalAmount": total, "expireTime": ts(15)}))

        if path == "/api/payOrder":
            # A 定稿：请求体只有 orderId，没有 payType
            order = ORDERS.get(int(body.get("orderId", -1)))
            if order is None:
                return self._send(err(2005, "订单不存在"))
            if order["status"] in (2, 3):  # 已完成/已取消 → 已失效
                return self._send(err(2004, "订单已失效"))
            # 幂等：已支付直接返回原码
            if order["status"] != 1:
                order["status"] = 1
                order["ticketCode"] = "1234 5678"
                order["goodsCode"] = "4321" if order["goods"] else None
            return self._send(ok({
                "orderId": order["orderId"], "status": order["status"],
                "ticketCode": order["ticketCode"], "goodsCode": order["goodsCode"],
            }))

        if path == "/api/cancelOrder":
            order = ORDERS.get(int(body.get("orderId", -1)))
            if order is None:
                return self._send(err(2005, "订单不存在"))
            if order["status"] == 1:
                return self._send(err(400, "已支付订单不可取消"))
            if order["status"] in (2, 3):
                return self._send(err(2004, "订单已失效"))
            order["status"] = 3
            return self._send(ok({"orderId": order["orderId"], "status": 3}))

        return self._send(err(2005, "接口不存在: %s" % path))


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("交易链路 Mock 已启动（A 文档契约版）: http://127.0.0.1:%d/api/  （Ctrl+C 停止）" % port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
