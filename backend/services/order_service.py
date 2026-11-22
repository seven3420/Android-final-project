"""
订单业务层：锁座、扣库存、金额重算、状态流转

状态机：
    0 待支付 ──支付──> 1 已支付 ──客户端展示──> 2 已完成
        └──用户取消 / 超过 15 分钟──> 3 已取消

三条硬约束（答辩重点）：
    1. 金额只认服务端重算；客户端传来的 totalAmount 仅用于发现改价（不一致报 2003）
    2. 扣库存用 UPDATE ... WHERE stock >= ?，影响行数为 0 即库存不足（2002）
    3. seat_lock 的唯一索引 (schedule_id,row_num,col_num) 兜底，
       并发抢同一座位时只有一个事务能插入成功，其余报 2001

释放逻辑统一走 _release()，取消订单与超时失效共用，
避免出现"座位放了、卖品库存没还"的不一致。
"""
import json
import random
import sqlite3
from datetime import datetime, timedelta

from database import transaction, cursor

# ---------- 业务约束 ----------
MAX_SEAT = 4          # 单次最多四座
MAX_GOODS_QTY = 5     # 单笔订单每种卖品最多五份
EXPIRE_MINUTES = 15   # 未支付订单有效期

# ---------- 错误码（说明书表 9）----------
ERR_SEAT_TAKEN = 2001
ERR_STOCK_SHORT = 2002
ERR_PRICE_CHANGED = 2003
ERR_ORDER_EXPIRED = 2004
ERR_ORDER_NOT_FOUND = 2005
ERR_PARAM = 400


class BizError(Exception):
    """业务异常，由 main.py 统一转成 {code, msg, data}"""

    def __init__(self, code: int, msg: str):
        super().__init__(msg)
        self.code = code
        self.msg = msg


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _seat_text(row: int, col: int) -> str:
    return f"{row} 排 {col} 座"


def _new_ticket_code() -> str:
    return f"{random.randint(0, 9999):04d} {random.randint(0, 9999):04d}"


def _new_goods_code() -> str:
    return f"{random.randint(0, 9999):04d}"


# ---------- 资源释放 ----------
def _release(conn, order_id: int, status: int):
    """统一释放函数：座位锁 + 卖品库存。
    取消订单与超时失效两个分支共用，避免"座位放了、货没还"。"""
    for g in conn.execute(
        "SELECT product_id, quantity FROM order_goods WHERE order_id=?", (order_id,)
    ):
        conn.execute(
            "UPDATE product SET stock = stock + ? WHERE id=?",
            (g["quantity"], g["product_id"]),
        )
    conn.execute("DELETE FROM seat_lock WHERE order_id=?", (order_id,))
    conn.execute("UPDATE order_info SET status=? WHERE id=?", (status, order_id))


def release_expired(conn):
    """惰性释放：把超过 15 分钟未支付的订单置为已取消，并归还资源"""
    rows = conn.execute(
        "SELECT id FROM order_info WHERE status=0 AND expire_time<=?", (now_str(),)
    ).fetchall()
    for r in rows:
        _release(conn, r["id"], 3)


def sweep_expired():
    """在独立事务中清理超时订单。
    必须先提交，否则后续业务报错回滚时会把释放一起撤销。"""
    with transaction() as conn:
        release_expired(conn)


# ---------- 9. 创建订单 ----------
def create_order(user_id: int, schedule_id: int, seats: list, goods: list,
                 client_amount: float | None = None) -> dict:
    if not seats:
        raise BizError(ERR_PARAM, "请至少选择一个座位")
    if len(seats) > MAX_SEAT:
        raise BizError(ERR_PARAM, f"单次最多选择 {MAX_SEAT} 个座位")

    positions = [(int(s.get("row", 0)), int(s.get("col", 0))) for s in seats]
    if len(set(positions)) != len(positions):
        raise BizError(ERR_PARAM, "座位重复")

    sweep_expired()

    with transaction() as conn:
        sch = conn.execute(
            """SELECT s.id, s.cinema_id, s.start_time, s.price,
                      h.row_count, h.col_count
               FROM schedule s JOIN hall h ON h.id = s.hall_id
               WHERE s.id=?""",
            (schedule_id,),
        ).fetchone()
        if not sch:
            raise BizError(ERR_ORDER_NOT_FOUND, "场次不存在")

        for r, c in positions:
            if not (1 <= r <= sch["row_count"] and 1 <= c <= sch["col_count"]):
                raise BizError(ERR_PARAM, "座位超出影厅范围")

        # 清理本场次的过期锁，防止唯一索引误伤
        conn.execute(
            "DELETE FROM seat_lock WHERE schedule_id=? AND expire_time<=?",
            (schedule_id, now_str()),
        )

        # 已售座位（已支付订单）
        sold = set()
        for row in conn.execute(
            "SELECT seats FROM order_info WHERE schedule_id=? AND status=1", (schedule_id,)
        ):
            for item in json.loads(row["seats"]):
                sold.add((item["row"], item["col"]))

        # 有效锁定座位
        locked = {
            (r["row_num"], r["col_num"])
            for r in conn.execute(
                "SELECT row_num, col_num FROM seat_lock WHERE schedule_id=? AND expire_time>?",
                (schedule_id, now_str()),
            )
        }

        for pos in positions:
            if pos in sold or pos in locked:
                raise BizError(ERR_SEAT_TAKEN, "座位已被占用")

        # 卖品校验 + 条件扣减（影响行数为 0 即库存不足）
        goods_amount = 0.0
        goods_items = []
        for g in goods or []:
            pid = int(g.get("productId", 0))
            qty = int(g.get("quantity", 0))
            if qty <= 0:
                raise BizError(ERR_PARAM, "卖品数量不合法")
            if qty > MAX_GOODS_QTY:
                raise BizError(ERR_PARAM, f"每种卖品最多 {MAX_GOODS_QTY} 份")

            p = conn.execute(
                "SELECT id, name, price FROM product WHERE id=? AND cinema_id=?",
                (pid, sch["cinema_id"]),
            ).fetchone()
            if not p:
                raise BizError(ERR_PARAM, "卖品不存在或不属于该影院")

            cur = conn.execute(
                "UPDATE product SET stock = stock - ? WHERE id=? AND stock >= ?",
                (qty, pid, qty),
            )
            if cur.rowcount == 0:
                raise BizError(ERR_STOCK_SHORT, f"{p['name']} 库存不足")

            amount = round(p["price"] * qty, 2)
            goods_amount += amount
            goods_items.append((pid, p["name"], p["price"], qty, amount))

        # 金额全部由服务端重算，客户端金额只用于发现变价
        ticket_amount = round(sch["price"] * len(positions), 2)
        total_amount = round(ticket_amount + goods_amount, 2)
        if client_amount is not None and abs(float(client_amount) - total_amount) > 0.001:
            raise BizError(ERR_PRICE_CHANGED, "价格已变动，请重新确认")

        create_time = now_str()
        expire_time = (datetime.now() + timedelta(minutes=EXPIRE_MINUTES)).strftime(
            "%Y-%m-%d %H:%M:%S"
        )

        cur = conn.execute(
            """INSERT INTO order_info
               (user_id, schedule_id, seats, seat_count, ticket_amount, goods_amount,
                total_amount, status, create_time, expire_time)
               VALUES (?,?,?,?,?,?,?,0,?,?)""",
            (
                user_id, schedule_id,
                json.dumps([{"row": r, "col": c} for r, c in positions]),
                len(positions), ticket_amount, goods_amount, total_amount,
                create_time, expire_time,
            ),
        )
        order_id = cur.lastrowid

        for pid, name, price, qty, amount in goods_items:
            conn.execute(
                """INSERT INTO order_goods (order_id, product_id, name, price, quantity, amount)
                   VALUES (?,?,?,?,?,?)""",
                (order_id, pid, name, price, qty, amount),
            )

        # 座位锁由唯一索引 (schedule_id,row_num,col_num) 兜底防并发
        try:
            for r, c in positions:
                conn.execute(
                    """INSERT INTO seat_lock (schedule_id, row_num, col_num, order_id, expire_time)
                       VALUES (?,?,?,?,?)""",
                    (schedule_id, r, c, order_id, expire_time),
                )
        except sqlite3.IntegrityError:
            raise BizError(ERR_SEAT_TAKEN, "座位已被占用")

        return {
            "orderId": order_id,
            "totalAmount": total_amount,
            "expireTime": expire_time,
        }


# ---------- 10. 模拟支付 ----------
def pay_order(user_id: int, order_id: int) -> dict:
    sweep_expired()

    with transaction() as conn:
        row = conn.execute(
            "SELECT * FROM order_info WHERE id=? AND user_id=?", (order_id, user_id)
        ).fetchone()
        if not row:
            raise BizError(ERR_ORDER_NOT_FOUND, "订单不存在")
        if row["status"] == 3:
            raise BizError(ERR_ORDER_EXPIRED, "订单已失效")
        if row["status"] != 0:
            # 已支付/已完成：幂等返回，避免重复点击报错
            return {
                "orderId": order_id,
                "status": row["status"],
                "ticketCode": row["ticket_code"],
                "goodsCode": row["goods_code"],
            }

        has_goods = conn.execute(
            "SELECT COUNT(*) AS c FROM order_goods WHERE order_id=?", (order_id,)
        ).fetchone()["c"] > 0
        ticket_code = _new_ticket_code()
        goods_code = _new_goods_code() if has_goods else None

        conn.execute(
            """UPDATE order_info
               SET status=1, pay_time=?, ticket_code=?, goods_code=?
               WHERE id=?""",
            (now_str(), ticket_code, goods_code, order_id),
        )

        # 支付成功后座位由订单状态体现，锁记录可以清理
        conn.execute("DELETE FROM seat_lock WHERE order_id=?", (order_id,))

        # 卖品销量累加
        for g in conn.execute(
            "SELECT product_id, quantity FROM order_goods WHERE order_id=?", (order_id,)
        ):
            conn.execute(
                "UPDATE product SET sales = sales + ? WHERE id=?",
                (g["quantity"], g["product_id"]),
            )

        return {
            "orderId": order_id,
            "status": 1,
            "ticketCode": ticket_code,
            "goodsCode": goods_code,
        }


# ---------- 11. 取消订单 ----------
def cancel_order(user_id: int, order_id: int) -> dict:
    sweep_expired()

    with transaction() as conn:
        row = conn.execute(
            "SELECT status FROM order_info WHERE id=? AND user_id=?", (order_id, user_id)
        ).fetchone()
        if not row:
            raise BizError(ERR_ORDER_NOT_FOUND, "订单不存在")
        if row["status"] == 3:
            raise BizError(ERR_ORDER_EXPIRED, "订单已失效")
        if row["status"] != 0:
            raise BizError(ERR_PARAM, "已支付订单不可取消")

        _release(conn, order_id, 3)
        return {"orderId": order_id, "status": 3}


# ---------- 12. 订单列表 ----------
def get_order_list(user_id: int, status: int | None = None,
                   page_num: int = 1, page_size: int = 10) -> dict:
    sweep_expired()

    with cursor() as conn:
        where = "WHERE o.user_id=?"
        args: list = [user_id]
        if status is not None:
            where += " AND o.status=?"
            args.append(status)

        total = conn.execute(
            f"SELECT COUNT(*) AS c FROM order_info o {where}", args
        ).fetchone()["c"]

        rows = conn.execute(
            f"""SELECT o.id, o.seats, o.seat_count, o.total_amount, o.status,
                       o.create_time, o.expire_time,
                       m.title AS movie_title, m.poster,
                       c.name AS cinema_name, h.name AS hall_name, s.start_time
                FROM order_info o
                JOIN schedule s ON s.id = o.schedule_id
                JOIN movie m ON m.id = s.movie_id
                JOIN cinema c ON c.id = s.cinema_id
                JOIN hall h ON h.id = s.hall_id
                {where}
                ORDER BY o.id DESC LIMIT ? OFFSET ?""",
            args + [page_size, (page_num - 1) * page_size],
        ).fetchall()

    order_list = []
    for r in rows:
        seats = json.loads(r["seats"])
        order_list.append({
            "orderId": r["id"],
            "movieTitle": r["movie_title"],
            "poster": r["poster"],
            "cinemaName": r["cinema_name"],
            "hallName": r["hall_name"],
            "startTime": r["start_time"],
            "seatCount": r["seat_count"],
            "seats": [_seat_text(s["row"], s["col"]) for s in seats],
            "totalAmount": r["total_amount"],
            "status": r["status"],
            "createTime": r["create_time"],
            "expireTime": r["expire_time"],
        })

    return {"list": order_list, "total": total}


# ---------- 13. 订单详情 ----------
def get_order_detail(user_id: int, order_id: int) -> dict:
    sweep_expired()

    with cursor() as conn:
        row = conn.execute(
            """SELECT o.id, o.seats, o.seat_count, o.status,
                      o.ticket_amount, o.goods_amount, o.total_amount,
                      o.ticket_code, o.goods_code, o.create_time, o.expire_time,
                      m.title AS movie_title, m.poster, m.duration,
                      c.name AS cinema_name, c.address AS cinema_address,
                      h.name AS hall_name, s.start_time, s.lang_version
               FROM order_info o
               JOIN schedule s ON s.id = o.schedule_id
               JOIN movie m ON m.id = s.movie_id
               JOIN cinema c ON c.id = s.cinema_id
               JOIN hall h ON h.id = s.hall_id
               WHERE o.id=? AND o.user_id=?""",
            (order_id, user_id),
        ).fetchone()
        if not row:
            raise BizError(ERR_ORDER_NOT_FOUND, "订单不存在")

        goods = conn.execute(
            "SELECT name, quantity, amount FROM order_goods WHERE order_id=?",
            (order_id,),
        ).fetchall()

    seats = json.loads(row["seats"])
    return {
        "orderId": row["id"],
        "status": row["status"],
        "movieTitle": row["movie_title"],
        "poster": row["poster"],
        "cinemaName": row["cinema_name"],
        "cinemaAddress": row["cinema_address"],
        "hallName": row["hall_name"],
        "startTime": row["start_time"],
        "langVersion": row["lang_version"],
        "seats": [_seat_text(s["row"], s["col"]) for s in seats],
        "seatCount": row["seat_count"],
        "goods": [
            {"name": g["name"], "quantity": g["quantity"], "amount": g["amount"]}
            for g in goods
        ],
        "ticketAmount": row["ticket_amount"],
        "goodsAmount": row["goods_amount"],
        "totalAmount": row["total_amount"],
        "ticketCode": row["ticket_code"],
        "goodsCode": row["goods_code"],
        "createTime": row["create_time"],
        "expireTime": row["expire_time"],
    }
