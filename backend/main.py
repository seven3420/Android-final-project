"""
FastAPI 入口 —— 整个后端的启动文件

分层结构：
    routers/     接口层：解析参数、校验登录、拼装统一响应
    services/    业务层：订单锁座、扣库存、状态流转等事务逻辑
    database.py  数据层：SQLite 连接管理 + 9 张表结构

本文件职责：
    1. lifespan：服务启动时自动建表（首次运行不用手动建库）
    2. 注册 5 个路由模块，共 14 个接口
    3. 放开 CORS：手机与电脑各自 IP 不同，不放开会被浏览器拦截
    4. 全局异常处理：把 HTTPException / BizError 统一转成 {code, msg, data}

启动：uvicorn main:app --host 0.0.0.0 --port 9000 --reload
文档：http://localhost:9000/docs（手机访问换成电脑局域网 IP）
"""
from contextlib import asynccontextmanager
import os

from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles

from database import init_db
from utils import ok, fail
from services.order_service import BizError
from routers import auth, movies, cinemas, products, orders


# ---------- 生命周期：启动时建表 ----------
@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()  # 首次启动自动建表
    yield


# ---------- 应用实例 ----------
app = FastAPI(title="电影票订购 App 后端", lifespan=lifespan)

# CORS：放开所有来源（手机和电脑不同 IP 也要能访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------- 静态资源：海报与卖品图 ----------
# 图片放 backend/static/ 下，通过 /static/xxx.jpg 访问，
# 数据库里只存相对路径，接口返回时再拼成完整 URL（见 utils.image_url）
STATIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static")
os.makedirs(STATIC_DIR, exist_ok=True)
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


# ---------- 路由注册 ----------
app.include_router(auth.router)
app.include_router(movies.router)
app.include_router(cinemas.router)
app.include_router(products.router)
app.include_router(orders.router)


# ---------- 全局异常处理 ----------
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(status_code=200, content=fail(exc.status_code, exc.detail))


@app.exception_handler(BizError)
async def biz_exception_handler(request: Request, exc: BizError):
    """业务异常统一转成 {code, msg, data}，HTTP 状态始终 200"""
    return JSONResponse(status_code=200, content=fail(exc.code, exc.msg))


@app.get("/")
async def root():
    return ok({"service": "电影票订购后端", "version": "1.0", "docs": "/docs"})
