"""
接口 1-2：账号模块
POST /api/login          登录，返回 token
GET  /api/getUserInfo    获取当前用户信息

实现要点：
    - 课程作业不引入 JWT：登录成功生成随机 token 存内存表，
      重启服务后 token 失效，客户端需重新登录
    - 需要登录的接口统一调 require_user()，从 Authorization 头解析 user_id，
      orders.py 里 9-13 号接口都依赖它
"""
import secrets
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel
from database import cursor
from utils import ok, fail

router = APIRouter()

# 简易内存 token 表（课程作业够用）
TOKEN_STORE: dict[str, int] = {}  # token -> user_id


class LoginBody(BaseModel):
    phone: str
    password: str


def require_user(token: str | None) -> int:
    """从 Authorization 头解析 user_id，没登录抛 1001"""
    if not token:
        raise HTTPException(1001, "未登录或 token 失效")
    token = token.replace("Bearer ", "")
    uid = TOKEN_STORE.get(token)
    if not uid:
        raise HTTPException(1001, "未登录或 token 失效")
    return uid


# ---------- 1. 登录 ----------
@router.post("/api/login")
def login(body: LoginBody):
    with cursor() as cur:
        cur.execute(
            "SELECT id, nickname, avatar FROM user WHERE phone=? AND password=?",
            (body.phone, body.password),
        )
        row = cur.fetchone()
    if not row:
        return fail(1001, "账号或密码错误")

    token = secrets.token_hex(16)
    TOKEN_STORE[token] = row["id"]
    return ok({
        "token": token,
        "userInfo": {
            "id": row["id"],
            "nickname": row["nickname"],
            "avatar": row["avatar"],
        },
    })


# ---------- 2. 获取用户信息 ----------
@router.get("/api/getUserInfo")
def get_user_info(authorization: str = Header(default=None)):
    uid = require_user(authorization)
    with cursor() as cur:
        cur.execute(
            "SELECT id, nickname, avatar FROM user WHERE id=?", (uid,),
        )
        row = cur.fetchone()
    if not row:
        raise HTTPException(2005, "用户不存在")
    return ok({"id": row["id"], "nickname": row["nickname"], "avatar": row["avatar"]})
