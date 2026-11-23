"""
演示数据脚本

运行：python seed.py
作用：清空所有表（并重置自增 id，保证每次重跑后演示 id 固定）后重新注入：
      1 个演示账号、4 部影片、3 家影院 / 6 个影厅、未来 7 天场次、每家 5 种卖品

演示账号：13800000001 / 123456
注意：重跑会清掉已有订单，演示前想恢复初始状态就执行一次。
"""
import sqlite3
from datetime import datetime, timedelta
from database import DB_PATH, init_db


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

    # ===== 2. 影片（片名/主演/上映日期取自猫眼实时榜单，评分与时长为演示值）=====
    movies = [
        # ===== 热映 now（6 部） =====
        ("猪猪侠大电影·星际行动", "static/posters/ggbond.jpg", 9.0, 85, "动画/冒险/喜剧",
         "李腾栋", "陆双,徐经纬,刘青扬",
         "猪猪星被宇宙入侵者占领，超星五灵侠逃亡地球寻找音符圣杯，重新领悟团结合作。", "now", "2024-05-01"),
        ("唐探1900", "static/posters/detective1900.jpg", 9.2, 136, "喜剧/动作/悬疑",
         "陈思诚,戴墨", "王宝强,刘昊然,周润发",
         "1900 年旧金山唐人街，阿鬼与秦福因一场凶杀案结缘，开启笑闹探案之旅。", "now", "2025-01-29"),
        ("寻梦环游记", "static/posters/coco.jpg", 9.6, 105, "动画/家庭/冒险",
         "李·昂克里奇", "安东尼·冈萨雷斯,本杰明·布拉特,盖尔·加西亚·贝纳尔",
         "鞋匠家庭出身的米格在亡灵节误入亡灵世界，在追寻音乐梦的路上找回家族记忆。", "now", "2017-11-24"),
        ("哪吒之魔童降世", "static/posters/nezha.jpg", 9.6, 110, "动画/奇幻",
         "饺子", "吕艳婷,囧森瑟夫,瀚墨",
         "灵珠与魔丸被掉包，本应是英雄的哪吒成了混世魔王，“我命由我不由天”。", "now", "2019-07-26"),
        ("心灵奇旅", "static/posters/soul.jpg", 9.2, 101, "动画/喜剧/冒险",
         "彼特·道格特", "杰米·福克斯,蒂娜·菲,菲利西亚·拉斯海德",
         "中学音乐教师乔伊意外闯入“生之来处”，与厌世灵魂 22 共同寻找生命的火花。", "now", "2020-12-25"),
        ("姜子牙", "static/posters/jiangziya.jpg", 8.3, 110, "动画/动作/冒险",
         "程腾,李炜", "郑希,杨凝,图特哈蒙",
         "封神之后，姜子牙为救小九踏上对抗天道的旅程，拉开封神宇宙的序幕。", "now", "2020-10-01"),
        # ===== 待映 soon（1 部） =====
        ("沙丘3", "static/posters/dune.jpg", 9.6, 165, "科幻/冒险",
         "丹尼斯·维伦纽瓦", "提莫西·查拉梅,赞达亚·科尔曼,杰森·莫玛",
         "“沙丘”三部曲终章，保罗·厄崔迪与厄拉科斯的命运走向终局。", "soon", "2026-12-31"),
    ]
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
    # 热映片轮流排，待映片只排部分
    movie_ids_now = [1, 2, 3]
    movie_ids_soon = [4]

    today = datetime.now()
    for day_offset in range(7):
        day = today + timedelta(days=day_offset)
        date_str = day.strftime("%Y-%m-%d")
        for hid in range(1, 7):                      # 6 个影厅
            cinema_id = (hid - 1) // 2 + 1
            # 热映片每天排完，待映片从 D4 开始排
            if day_offset < 3:
                movies_for_hall = movie_ids_now
            else:
                movies_for_hall = movie_ids_now + movie_ids_soon
            # 每个影厅每天排 2 部影片 × 2 场
            for j, mid in enumerate(movies_for_hall[:2]):
                for slot in range(2):               # 每部排 2 个时段
                    tm = showtimes[(j * 2 + slot + hid) % 4]
                    price = price_map["now"] if mid in movie_ids_now else price_map["soon"]
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
    print("✅ 演示数据已注入：1 用户 / 4 影片 / 3 影院 / 6 影厅 / 场次 / 卖品")
    print("   账号：13800000001  密码：123456")


if __name__ == "__main__":
    seed()
