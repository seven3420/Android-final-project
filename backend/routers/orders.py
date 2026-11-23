"""
接口 9-13：订单模块（除下单外都需要登录）
POST /api/createOrder     下单：锁座 + 扣库存 + 服务端重算金额
POST /api/payOrder        模拟支付：生成取票码 / 取餐码
POST /api/cancelOrder     取消未支付订单：释放座位与库存
GET  /api/getOrderList    订单列表：按状态筛选 + 分页
GET  /api/getOrderDetail  订单详情：含座位、卖品、金额明细、取票码

本文件只做三件事：拿 user_id、接收参数、调 services/order_service.py。
所有事务、校验、状态流转都在业务层，方便单独测试与答辩讲解。
"""
from fastapi import APIRouter, Header, Query, HTTPException, Request
from pydantic import BaseModel, Field

from utils import ok, image_url
from routers.auth import require_user
from services import order_service as svc

router = APIRouter()


# ---------- 请求体 ----------
class SeatItem(BaseModel):
    row: int
    col: int


class GoodsItem(BaseModel):
    productId: int
    quantity: int


class CreateOrderBody(BaseModel):
    scheduleId: int
    seats: list[SeatItem]
    goods: list[GoodsItem] = Field(default_factory=list)
    totalAmount: float | None = None  # 可选：仅用于发现改价


class OrderIdBody(BaseModel):
    orderId: int


# ---------- 9. 创建订单 ----------
@router.post("/api/createOrder")
def create_order(body: CreateOrderBody, authorization: str = Header(default=None)):
    uid = require_user(authorization)
    data = svc.create_order(
        user_id=uid,
        schedule_id=body.scheduleId,
        seats=[s.model_dump() for s in body.seats],
        goods=[g.model_dump() for g in body.goods],
        client_amount=body.totalAmount,
    )
    return ok(data)


# ---------- 10. 模拟支付 ----------
@router.post("/api/payOrder")
def pay_order(body: OrderIdBody, authorization: str = Header(default=None)):
    uid = require_user(authorization)
    return ok(svc.pay_order(uid, body.orderId))


# ---------- 11. 取消订单 ----------
@router.post("/api/cancelOrder")
def cancel_order(body: OrderIdBody, authorization: str = Header(default=None)):
    uid = require_user(authorization)
    return ok(svc.cancel_order(uid, body.orderId))


# ---------- 12. 订单列表 ----------
@router.get("/api/getOrderList")
def get_order_list(
    request: Request,
    status: int | None = Query(None, description="0待支付 1已支付 2已完成 3已取消，不传则全部"),
    pageNum: int = Query(1, ge=1),
    pageSize: int = Query(10, ge=1),
    authorization: str = Header(default=None),
):
    uid = require_user(authorization)
    data = svc.get_order_list(uid, status, pageNum, pageSize)
    base = str(request.base_url)
    for item in data["list"]:
        item["poster"] = image_url(base, item["poster"])
    return ok(data)


# ---------- 13. 订单详情 ----------
@router.get("/api/getOrderDetail")
def get_order_detail(
    request: Request,
    orderId: int = Query(..., alias="orderId"),
    authorization: str = Header(default=None),
):
    uid = require_user(authorization)
    data = svc.get_order_detail(uid, orderId)
    data["poster"] = image_url(str(request.base_url), data["poster"])
    return ok(data)


# ---------- 14. 观影统计 ----------
@router.get("/api/getWatchStats")
def get_watch_stats(request: Request, authorization: str = Header(default=None)):
    uid = require_user(authorization)
    from database import cursor

    # 有效订单 = 已支付(1) 或 已完成(2)
    with cursor() as cur:
        # ① 核心聚合
        cur.execute("""
            SELECT
                COUNT(*)            AS totalShows,
                SUM(m.duration)     AS totalMinutes,
                SUM(o.seat_count)   AS totalTickets,
                SUM(o.total_amount) AS totalAmount,
                MAX(s.start_time)   AS lastWatchTime
            FROM order_info o
            JOIN schedule s ON o.schedule_id = s.id
            JOIN movie m    ON s.movie_id    = m.id
            WHERE o.user_id = ? AND o.status IN (1, 2)
        """, (uid,))
        row = cur.fetchone()
        total_shows = row["totalShows"] or 0

        # ② 最常去的影院
        cur.execute("""
            SELECT c.name AS cinemaName, COUNT(*) AS count
            FROM order_info o
            JOIN schedule s ON o.schedule_id = s.id
            JOIN cinema c   ON s.cinema_id   = c.id
            WHERE o.user_id = ? AND o.status IN (1, 2)
            GROUP BY c.id
            ORDER BY count DESC
            LIMIT 1
        """, (uid,))
        top_cinema = cur.fetchone()

        # ③ 最常看的电影
        cur.execute("""
            SELECT m.title AS movieTitle, COUNT(*) AS count
            FROM order_info o
            JOIN schedule s ON o.schedule_id = s.id
            JOIN movie m    ON s.movie_id    = m.id
            WHERE o.user_id = ? AND o.status IN (1, 2)
            GROUP BY m.id
            ORDER BY count DESC
            LIMIT 1
        """, (uid,))
        top_movie = cur.fetchone()

        # ④ 类型分布（按 movie.type 整体分组，如 "动画/冒险/喜剧"）
        cur.execute("""
            SELECT m.type AS type, COUNT(*) AS count
            FROM order_info o
            JOIN schedule s ON o.schedule_id = s.id
            JOIN movie m    ON s.movie_id    = m.id
            WHERE o.user_id = ? AND o.status IN (1, 2)
            GROUP BY m.type
            ORDER BY count DESC
        """, (uid,))
        type_dist = cur.fetchall()

        # ⑤ 最近 5 部看过的电影
        cur.execute("""
            SELECT m.title AS title, m.poster AS poster, m.type AS type,
                   s.start_time AS watchTime, c.name AS cinemaName
            FROM order_info o
            JOIN schedule s ON o.schedule_id = s.id
            JOIN movie m    ON s.movie_id    = m.id
            JOIN cinema c   ON s.cinema_id   = c.id
            WHERE o.user_id = ? AND o.status IN (1, 2)
            ORDER BY s.start_time DESC
            LIMIT 5
        """, (uid,))
        recent = cur.fetchall()

    data = {
        "totalShows":   total_shows,
        "totalMinutes": row["totalMinutes"] or 0,
        "totalHours":   round((row["totalMinutes"] or 0) / 60, 1),
        "totalTickets": row["totalTickets"] or 0,
        "totalAmount":  round(row["totalAmount"] or 0, 2),
        "lastWatchTime": row["lastWatchTime"] or "",
        "topCinema": {
            "name":  top_cinema["cinemaName"],
            "count": top_cinema["count"],
        } if top_cinema else None,
        "topMovie": {
            "title": top_movie["movieTitle"],
            "count": top_movie["count"],
        } if top_movie else None,
        "typeDist": [{"type": r["type"], "count": r["count"]} for r in type_dist],
        "recentList": [
            {
                "title":      r["title"],
                "poster":     image_url(str(request.base_url), r["poster"]),
                "type":       r["type"],
                "watchTime":  r["watchTime"],
                "cinemaName": r["cinemaName"],
            }
            for r in recent
        ],
    }

    return ok(data)
