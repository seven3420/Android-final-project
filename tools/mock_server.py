#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交易链路接口 Mock 服务（成员 C 联调用）。

用途：说明书 7.2 第一条要求"B 与 C 当天即按文档用假数据完成页面，不等后端实现"。
A 的 FastAPI 还没跑起来时，本地先用这份 mock 顶位，返回的 JSON 严格照
《电影票订购App课程设计说明书》4.2 表8 的接口名 / 方法 / 路径 / 入参 / 返回 data
与 4.3 关键接口示例逐字段构造，客户端可以提前把 Retrofit、Gson 与页面全跑通；
等 A 的后端就绪，只需把 RetrofitClient 的地址切过去，客户端代码一行不用改。

启动：python tools/mock_server.py            # 默认 0.0.0.0:8000
      python tools/mock_server.py 8899      # 指定端口

鉴权：所有接口要求请求头 Authorization: Bearer {token}。
      不带头或格式不对时返回 code 1001，与说明书 4.4 表9 一致，
      便于验证客户端 token 拦截器与"未登录跳登录页"的分支。

只支持 GET 与 POST，且带 CORS 头，方便直接用浏览器或 Postman 手工验证。
"""

import json
import sys
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

# 模拟"当前时间"，用于订单倒计时与过期时间字段
NOW = datetime(2026, 11, 24, 14, 0, 0)


def ts(delta_minutes: int = 0) -> str:
    """按说明书 5.2 的时间格式 yyyy-MM-dd HH:mm:ss 生成时间串。"""
    return (NOW + timedelta(minutes=delta_minutes)).strftime("%Y-%m-%d %H:%M:%S")


def ok(data):
    return {"code": 0, "msg": "ok", "data": data}


# ---------------------------------------------------------------- 业务数据

PRODUCTS = [
    {
        "id": 2001, "name": "双人套餐", "image": "http://127.0.0.1:8000/img/p2001.png",
        "price": 58.00, "originPrice": 78.00, "stock": 12, "salesCount": 320, "isHot": True,
    },
    {
        "id": 2002, "name": "大桶爆米花", "image": "http://127.0.0.1:8000/img/p2002.png",
        "price": 25.00, "originPrice": 25.00, "stock": 30, "salesCount": 512, "isHot": True,
    },
    {
        "id": 2003, "name": "中杯可乐", "image": "http://127.0.0.1:8000/img/p2003.png",
        "price": 12.00, "originPrice": 15.00, "stock": 2, "salesCount": 88, "isHot": False,
    },
    {
        # 库存 0，用于验证"售完置灰、加号禁用"（说明书 2.4 规则 4）
        "id": 2004, "name": "隐藏款周边", "image": "http://127.0.0.1:8000/img/p2004.png",
        "price": 99.00, "originPrice": 99.00, "stock": 0, "salesCount": 3, "isHot": False,
    },
]

ORDER_DETAIL = {
    # 说明书 4.3 订单详情示例原文，字段名与顺序保持一致
    "id": 20261119001,
    "status": 1,
    "movieTitle": "星际远征",
    "cinemaName": "星汇影城（春熙路店）",
    "hallName": "3 号厅",
    "startTime": "2026-11-20 19:30",
    "seats": ["5 排 8 座", "5 排 9 座"],
    "goods": [{"productId": 2001, "name": "双人套餐", "price": 58.00, "quantity": 2, "amount": 116.00}],
    "ticketAmount": 84.00,
    "goodsAmount": 116.00,
    "totalAmount": 200.00,
    "ticketCode": "8823 6619",
    "goodsCode": "6620",
    "createTime": ts(-40),
    "payTime": ts(-30),
    "expireTime": ts(5),
}

ORDER_LIST = [
    {
        "id": 20261119001, "status": 1, "movieTitle": "星际远征",
        "cinemaName": "星汇影城（春熙路店）", "hallName": "3 号厅",
        "startTime": "2026-11-20 19:30", "seatCount": 2,
        "ticketAmount": 84.00, "goodsAmount": 116.00, "totalAmount": 200.00,
        "ticketCode": "8823 6619", "goodsCode": "6620",
        "createTime": ts(-40), "expireTime": ts(5),
    },
    {
        # 待支付订单，expireTime 在未来，用于验证倒计时
        "id": 20261124002, "status": 0, "movieTitle": "深夜闭环",
        "cinemaName": "星汇影城（春熙路店）", "hallName": "5 号厅",
        "startTime": "2026-11-25 21:10", "seatCount": 1,
        "ticketAmount": 42.00, "goodsAmount": 0.00, "totalAmount": 42.00,
        "ticketCode": "", "goodsCode": "",
        "createTime": ts(-3), "expireTime": ts(12),
    },
    {
        "id": 20261118003, "status": 2, "movieTitle": "春日细雨",
        "cinemaName": "云影国际影城（太古里店）", "hallName": "2 号厅",
        "startTime": "2026-11-18 15:00", "seatCount": 3,
        "ticketAmount": 126.00, "goodsAmount": 0.00, "totalAmount": 126.00,
        "ticketCode": "1024 5566", "goodsCode": "",
        "createTime": ts(-60 * 24 * 6), "expireTime": ts(-60 * 24 * 6 + 15),
    },
]

WATCH_STATS = {
    "totalMovie": 12,
    "totalDuration": 1575,  # 26 小时 15 分钟
    "typeRank": [
        {"type": "科幻", "count": 5},
        {"type": "剧情", "count": 4},
        {"type": "动画", "count": 2},
        {"type": "喜剧", "count": 1},
    ],
    "monthCount": [
        {"month": "2026-06", "count": 1},
        {"month": "2026-07", "count": 2},
        {"month": "2026-08", "count": 3},
        {"month": "2026-09", "count": 2},
        {"month": "2026-10", "count": 2},
        {"month": "2026-11", "count": 2},
    ],
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    # ------------------------------------------------------------ 基础输出

    def _send(self, payload: dict, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        # 放开跨域，方便直接用浏览器地址栏或前端页面手工验证接口
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(body)

    def _authed(self) -> bool:
        auth = self.headers.get("Authorization", "")
        return auth.startswith("Bearer ") and len(auth) > len("Bearer ")

    def _deny(self) -> None:
        self._send({"code": 1001, "msg": "未登录或 token 失效", "data": None})

    def _query(self) -> dict:
        return {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:
            return {}

    def log_message(self, fmt, *args):  # 精简日志，只看路径
        print("%s %s" % (self.command, self.path))

    # ------------------------------------------------------------ 路由

    def do_OPTIONS(self):
        self._send({"code": 0, "msg": "ok", "data": None})

    def do_GET(self):
        if not self._authed():
            return self._deny()
        path = urlparse(self.path).path
        q = self._query()

        if path == "/api/getProductList":
            cinema_id = int(q.get("cinemaId", 1))
            print("    cinemaId=%s" % cinema_id)
            return self._send(ok({"list": PRODUCTS, "total": len(PRODUCTS)}))

        if path == "/api/getOrderList":
            status = q.get("status")
            items = ORDER_LIST if status in (None, "") else [
                o for o in ORDER_LIST if o["status"] == int(status)
            ]
            return self._send(ok({"list": items, "total": len(items)}))

        if path == "/api/getOrderDetail":
            if q.get("orderId") == "404":  # 便于验证 2005 订单不存在的分支
                return self._send({"code": 2005, "msg": "订单不存在", "data": None})
            return self._send(ok(ORDER_DETAIL))

        if path == "/api/getWatchStats":
            return self._send(ok(WATCH_STATS))

        return self._send({"code": 404, "msg": "接口不存在: %s" % path, "data": None})

    def do_POST(self):
        if not self._authed():
            return self._deny()
        path = urlparse(self.path).path
        body = self._body()

        if path == "/api/createOrder":
            print("    %s" % json.dumps(body, ensure_ascii=False))
            if not body.get("seats"):
                return self._send({"code": 2001, "msg": "座位已被占用", "data": None})
            goods = body.get("goods") or []
            goods_amount = sum(
                next((p["price"] for p in PRODUCTS if p["id"] == g.get("productId")), 0.0)
                * g.get("quantity", 0)
                for g in goods
            )
            ticket_amount = 42.00 * len(body.get("seats") or [])
            return self._send(ok({
                "orderId": 20261119001,
                "totalAmount": round(ticket_amount + goods_amount, 2),
                "expireTime": ts(15),
            }))

        if path == "/api/payOrder":
            if body.get("orderId") == 404:
                return self._send({"code": 2004, "msg": "订单已失效", "data": None})
            return self._send(ok({
                "status": 1,
                "ticketCode": "8823 6619",
                "goodsCode": "6620",
            }))

        if path == "/api/cancelOrder":
            return self._send(ok({"status": 3}))

        return self._send({"code": 404, "msg": "接口不存在: %s" % path, "data": None})


def main() -> None:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("交易链路 Mock 已启动: http://127.0.0.1:%d/api/  （Ctrl+C 停止）" % port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
