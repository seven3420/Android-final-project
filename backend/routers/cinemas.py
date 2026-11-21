"""
接口 5-7：影院 / 场次 / 座位
GET /api/getCinemaList    影院列表（按影片查哪些影院在放）
GET /api/getScheduleList  场次列表（按影院+影片+日期）
GET /api/getSeatMap       座位图

关键实现：
    1. 余票 leftSeat = 总座位 - 已售座位(SUM seat_count) - 有效锁数量，
       不能按"订单条数"算，一个订单可能买多张票
    2. 座位状态不落库，每次请求实时推导：
           已支付订单占用的座位  -> 1 已售
           未过期 seat_lock      -> 2 已锁
           其余                  -> 0 可选
       这样取消、超时后座位天然恢复，不需要额外回写座位表
    3. 影厅行列数以 hall 表为准，客户端按 rows/cols 渲染网格
"""
import json
from datetime import datetime
from fastapi import APIRouter, Query, HTTPException
from database import cursor
from utils import ok

router = APIRouter()


# ---------- 5. 影院列表 ----------
@router.get("/api/getCinemaList")
def get_cinema_list(
    movieId: int = Query(..., alias="movieId"),
    pageNum: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1),
):
    with cursor() as cur:
        # 该影片在哪些影院排了场
        cur.execute(
            """SELECT DISTINCT c.id, c.name, c.address, c.rating
               FROM cinema c
               JOIN schedule s ON s.cinema_id = c.id
               WHERE s.movie_id = ?
               ORDER BY c.id ASC LIMIT ? OFFSET ?""",
            (movieId, pageSize, (pageNum - 1) * pageSize),
        )
        rows = cur.fetchall()

        cur.execute(
            """SELECT COUNT(DISTINCT c.id) AS cnt
               FROM cinema c
               JOIN schedule s ON s.cinema_id = c.id
               WHERE s.movie_id = ?""",
            (movieId,),
        )
        total = cur.fetchone()["cnt"]

    return ok({"list": [dict(r) for r in rows], "total": total})


# ---------- 6. 场次列表 ----------
@router.get("/api/getScheduleList")
def get_schedule_list(
    cinemaId: int = Query(..., alias="cinemaId"),
    movieId: int = Query(..., alias="movieId"),
    date: str = Query(...),
):
    with cursor() as cur:
        cur.execute(
            """SELECT s.id, s.start_time, s.price, s.lang_version,
                      h.name AS hall_name,
                      h.row_count * h.col_count AS total_seats
               FROM schedule s
               JOIN hall h ON h.id = s.hall_id
               WHERE s.cinema_id=? AND s.movie_id=? AND DATE(s.start_time)=DATE(?)
               ORDER BY s.start_time ASC""",
            (cinemaId, movieId, date),
        )
        schedules = cur.fetchall()

        result = []
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        for s in schedules:
            # 已售出座位数 + 有效锁定座位数
            cur.execute(
                """SELECT COALESCE(SUM(seat_count), 0) AS cnt FROM order_info
                   WHERE schedule_id=? AND status=1""",  # 已支付，按座位数统计
                (s["id"],),
            )
            sold = cur.fetchone()["cnt"]
            cur.execute(
                """SELECT COUNT(*) AS cnt FROM seat_lock
                   WHERE schedule_id=? AND expire_time > ?""",
                (s["id"], now_str),
            )
            locked = cur.fetchone()["cnt"]

            result.append({
                "id": s["id"],
                "startTime": s["start_time"],
                "hallName": s["hall_name"],
                "price": s["price"],
                "langVersion": s["lang_version"],
                "leftSeat": s["total_seats"] - sold - locked,
            })

    return ok(result)


# ---------- 7. 座位图（核心：实时推导状态）----------
@router.get("/api/getSeatMap")
def get_seat_map(scheduleId: int = Query(..., alias="scheduleId")):
    with cursor() as cur:
        # 场次 + 影厅信息
        cur.execute(
            """SELECT s.id, s.price, h.row_count, h.col_count
               FROM schedule s JOIN hall h ON h.id = s.hall_id
               WHERE s.id=?""", (scheduleId,),
        )
        sch = cur.fetchone()
        if not sch:
            raise HTTPException(2005, "场次不存在")

        rows, cols = sch["row_count"], sch["col_count"]

        # 已售座位（已支付订单）
        cur.execute(
            "SELECT seats FROM order_info WHERE schedule_id=? AND status=1",
            (scheduleId,),
        )
        sold = set()
        for r in cur.fetchall():
            for seat in json.loads(r["seats"]):
                sold.add((seat["row"], seat["col"]))

        # 已锁座位（未过期的 seat_lock）
        now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        cur.execute(
            "SELECT row_num, col_num FROM seat_lock WHERE schedule_id=? AND expire_time>?",
            (scheduleId, now_str),
        )
        locked = {(r["row_num"], r["col_num"]) for r in cur.fetchall()}

    # 生成所有座位：0 可选 / 1 已售 / 2 已锁
    seats = []
    for r in range(1, rows + 1):
        for c in range(1, cols + 1):
            pos = (r, c)
            if pos in sold:
                status = 1
            elif pos in locked:
                status = 2
            else:
                status = 0
            seats.append({"row": r, "col": c, "status": status})

    return ok({
        "rows": rows,
        "cols": cols,
        "price": sch["price"],
        "seats": seats,
    })
