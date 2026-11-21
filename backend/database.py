"""
数据层：SQLite 连接管理 + 9 张表结构

表关系（都为逻辑外键，已开启 PRAGMA foreign_keys）：
    cinema 1--n hall 1--n schedule
    movie  1--n schedule
    user   1--n order_info 1--n order_goods
    schedule 1--n seat_lock   ← 唯一索引兜底防并发重复锁座

命名约定：数据库字段用下划线（row_count），接口层用驼峰（rowCount），
转换在 SQL 的 AS 别名里完成，客户端拿到的永远是驼峰。

两个连接管理器，按"需不需要事务"来选：
    cursor()      查询和单条写，结束自动 commit，异常自动 rollback
    transaction() 多步写，BEGIN IMMEDIATE 立即拿写锁，
                  用于"锁座 + 扣库存 + 写订单"必须全成功或全失败的场景
"""
import sqlite3
import os
from contextlib import contextmanager

DB_PATH = os.path.join(os.path.dirname(__file__), "movies.db")


# ---------- 连接管理 ----------
def get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


@contextmanager
def cursor():
    conn = get_conn()
    try:
        cur = conn.cursor()
        yield cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


@contextmanager
def transaction():
    """写事务：BEGIN IMMEDIATE 立即取写锁，保证锁座与扣库存的原子性"""
    conn = sqlite3.connect(DB_PATH, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("BEGIN IMMEDIATE")
    try:
        yield conn
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise
    finally:
        conn.close()


# ---------- 建表 ----------
CREATE_TABLES_SQL = """
-- 1. 用户表
CREATE TABLE IF NOT EXISTS user (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    phone        TEXT    NOT NULL UNIQUE,
    password     TEXT    NOT NULL,
    nickname     TEXT,
    avatar       TEXT,
    create_time  TEXT    NOT NULL
);

-- 2. 影片表
CREATE TABLE IF NOT EXISTS movie (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT    NOT NULL,
    poster        TEXT    NOT NULL,
    score         REAL,
    duration      INTEGER NOT NULL,
    type          TEXT    NOT NULL,
    director      TEXT,
    actors        TEXT,
    description   TEXT,
    status        TEXT    NOT NULL,  -- now / soon
    release_date  TEXT
);

-- 3. 影院表
CREATE TABLE IF NOT EXISTS cinema (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT    NOT NULL,
    address  TEXT    NOT NULL,
    rating   REAL,
    phone    TEXT
);

-- 4. 影厅表
CREATE TABLE IF NOT EXISTS hall (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    cinema_id  INTEGER NOT NULL,
    name       TEXT    NOT NULL,
    row_count  INTEGER NOT NULL,
    col_count  INTEGER NOT NULL,
    FOREIGN KEY (cinema_id) REFERENCES cinema(id)
);

-- 5. 场次表
CREATE TABLE IF NOT EXISTS schedule (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    movie_id    INTEGER NOT NULL,
    cinema_id   INTEGER NOT NULL,
    hall_id     INTEGER NOT NULL,
    start_time  TEXT    NOT NULL,
    price       REAL    NOT NULL,
    lang_version TEXT,
    FOREIGN KEY (movie_id)  REFERENCES movie(id),
    FOREIGN KEY (cinema_id) REFERENCES cinema(id),
    FOREIGN KEY (hall_id)   REFERENCES hall(id)
);

-- 6. 卖品表
CREATE TABLE IF NOT EXISTS product (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    cinema_id    INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    image        TEXT,
    price        REAL    NOT NULL,
    origin_price REAL,
    stock        INTEGER NOT NULL DEFAULT 100,
    sales        INTEGER NOT NULL DEFAULT 0,
    is_hot       INTEGER DEFAULT 0,
    FOREIGN KEY (cinema_id) REFERENCES cinema(id)
);

-- 7. 订单表
CREATE TABLE IF NOT EXISTS order_info (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INTEGER NOT NULL,
    schedule_id    INTEGER NOT NULL,
    seats          TEXT    NOT NULL,       -- JSON 文本
    seat_count     INTEGER NOT NULL,
    ticket_amount  REAL    NOT NULL,
    goods_amount   REAL    NOT NULL DEFAULT 0,
    total_amount   REAL    NOT NULL,
    status         INTEGER NOT NULL,       -- 0待支付 1已支付 2已完成 3已取消
    ticket_code    TEXT,
    goods_code     TEXT,
    create_time    TEXT    NOT NULL,
    pay_time       TEXT,
    expire_time    TEXT    NOT NULL,
    FOREIGN KEY (user_id)     REFERENCES user(id),
    FOREIGN KEY (schedule_id) REFERENCES schedule(id)
);

-- 8. 卖品明细表
CREATE TABLE IF NOT EXISTS order_goods (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id   INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    name       TEXT    NOT NULL,
    price      REAL    NOT NULL,
    quantity   INTEGER NOT NULL,
    amount     REAL    NOT NULL,
    FOREIGN KEY (order_id)   REFERENCES order_info(id),
    FOREIGN KEY (product_id) REFERENCES product(id)
);

-- 9. 座位锁表（唯一索引防并发重复售座）
CREATE TABLE IF NOT EXISTS seat_lock (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id INTEGER NOT NULL,
    row_num     INTEGER NOT NULL,
    col_num     INTEGER NOT NULL,
    order_id    INTEGER NOT NULL,
    expire_time TEXT    NOT NULL,
    UNIQUE (schedule_id, row_num, col_num),
    FOREIGN KEY (order_id) REFERENCES order_info(id)
);
"""


def init_db():
    """启动时调用：建表（已存在则跳过）"""
    with cursor() as cur:
        cur.executescript(CREATE_TABLES_SQL)
    print(f"[DB] 数据库就绪: {DB_PATH}")
