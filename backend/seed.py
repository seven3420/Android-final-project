"""
演示数据脚本

运行：python seed.py
作用：清空所有表（并重置自增 id，保证每次重跑后演示 id 固定）后重新注入：
      1 个演示账号、影片、3 家影院 / 6 个影厅、未来 7 天场次、每家 5 种卖品

影片数据来源：
      读取 data/movies.json（由 fetch_tmdb.py 从 TMDB 同步生成，已随仓库提交）；
      文件缺失时执行 fetch_tmdb.py 重新生成。

演示账号：13800000001 / 123456
注意：重跑会清掉已有订单，演示前想恢复初始状态就执行一次。
"""
import json
import sqlite3
import sys
from datetime import datetime, timedelta
from pathlib import Path
from database import DB_PATH, init_db

DATA_FILE = Path(__file__).resolve().parent / "data" / "movies.json"


def load_movies():
    """从 data/movies.json 读取影片数据"""
    if not DATA_FILE.exists():
        print(f"❌ 未找到影片数据文件：{DATA_FILE}")
        print("   请先运行：python fetch_tmdb.py 生成（需要设置 TMDB_API_KEY）")
        sys.exit(1)

    raw = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    movies = [
        (m["title"], m["poster"], m["score"], m["duration"], m["type"],
         m["director"], m["actors"], m["description"], m["status"], m["release_date"])
        for m in raw
    ]
    print(f"✅ 已从 data/movies.json 读取 {len(movies)} 部影片")
    return movies


def seed():
    init_db()  # 先建表，防止第一次运行时表不存在
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # ===== 清空旧数据（防止重复运行冲突）=====
    for table in ["order_goods", "seat_lock", "order_info", "schedule",
                  "product", "hall", "cinema", "movie", "user"]:
        cur.execute(f"DELETE FROM {table}")
    # 重置自增序列，保证每次重跑后演示 id 固定（1,2,3...）
    cur.execute("DELETE FROM sqlite_sequence")

    # ===== 1. 用户（演示账号）=====
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    cur.execute(
        "INSERT INTO user (phone, password, nickname, create_time) VALUES (?, ?, ?, ?)",
        ("13800000001", "123456", "小明", now),
    )

    # ===== 2. 影片（来自 data/movies.json 或内置演示数据）=====
    movies = load_movies()
    cur.executemany(
        """INSERT INTO movie (title, poster, score, duration, type, director,
           actors, description, status, release_date)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", movies,
    )

    # ===== 3. 影院 =====
    cinemas = [
        ("星汇影城（春熙路店）", "成都市锦江区春熙路1号", 4.8, "028-80000001"),
        ("锦江国际影城",       "成都市锦江区红星路三段",   4.6, "028-80000002"),
        ("天府艺术影城",       "成都市天府新区天府大道",   4.5, "028-80000003"),
    ]
    cur.executemany(
        "INSERT INTO cinema (name, address, rating, phone) VALUES (?, ?, ?, ?)", cinemas,
    )

    # ===== 4. 影厅（每家 2 个厅，10 排 14 列）=====
    halls = []
    for cid in range(1, 4):          # 3 家影院
        for i in range(1, 3):        # 每家 2 个厅
            halls.append((cid, f"{i}号厅", 10, 14))
    cur.executemany(
        "INSERT INTO hall (cinema_id, name, row_count, col_count) VALUES (?, ?, ?, ?)", halls,
    )

    # ===== 5. 场次（当天起 7 天，每厅每天 4 场）=====
    schedules = []
    showtimes = ["10:30", "14:00", "19:30", "21:40"]
    price_map = {"now": 42.0, "soon": 38.0}

    # 3 家影院 × 2 个厅 = 6 个 hall
    # 按影厅轮转排片，保证每部影片每天都能排到场次
    cur.execute("SELECT id, status FROM movie ORDER BY id")
    rows = cur.fetchall()
    movie_ids_now = [r[0] for r in rows if r[1] == "now"]
    movie_ids_soon = [r[0] for r in rows if r[1] == "soon"]
    status_of = {r[0]: r[1] for r in rows}

    today = datetime.now()
    for day_offset in range(7):
        day = today + timedelta(days=day_offset)
        date_str = day.strftime("%Y-%m-%d")
        # 待映片从第 4 天起参与排片
        pool = (movie_ids_now + movie_ids_soon) if day_offset >= 3 else list(movie_ids_now)
        if not pool:
            continue
        for hid in range(1, 7):                      # 6 个影厅
            cinema_id = (hid - 1) // 2 + 1
            # 按「影厅号 + 天数×2」轮转，保证每家影院每天都能轮到不同影片
            start = (hid - 1) + day_offset * 2
            picks = [pool[(start + k) % len(pool)] for k in range(min(2, len(pool)))]
            # 每个影厅每天排 2 部影片 × 2 场
            for j, mid in enumerate(picks):
                for slot in range(2):               # 每部排 2 个时段
                    tm = showtimes[(j * 2 + slot + hid) % 4]
                    price = price_map[status_of[mid]]
                    schedules.append((mid, cinema_id, hid,
                                      f"{date_str} {tm}:00", price, "国语 2D"))
    cur.executemany(
        """INSERT INTO schedule (movie_id, cinema_id, hall_id, start_time, price, lang_version)
           VALUES (?, ?, ?, ?, ?, ?)""", schedules,
    )

    # ===== 6. 卖品（每家影院 5 种，独立库存）=====
    products = []
    templates = [
        ("单人套餐", "static/products/single.jpg", 32.0, 38.0, 150, 0),
        ("双人套餐", "static/products/double.jpg", 58.0, 68.0, 120, 1),
        ("大桶爆米花", "static/products/popcorn.jpg", 25.0, 28.0, 200, 1),
        ("可乐大杯", "static/products/cola.jpg", 12.0, None, 300, 0),
        ("香草冰淇淋", "static/products/icecream.jpg", 15.0, None, 100, 0),
    ]
    for cid in range(1, 4):
        for name, img, price, origin, stock, hot in templates:
            products.append((cid, name, img, price, origin, stock, 0, hot))
    cur.executemany(
        """INSERT INTO product (cinema_id, name, image, price, origin_price, stock, sales, is_hot)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", products,
    )

    conn.commit()
    conn.close()
    print(f"✅ 演示数据已注入：1 用户 / {len(movies)} 影片 / 3 影院 / 6 影厅 / {len(schedules)} 场次 / 卖品")
    print("   账号：13800000001  密码：123456")


if __name__ == "__main__":
    seed()
    seed()
