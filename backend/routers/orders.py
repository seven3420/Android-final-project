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
from fastapi import APIRouter, Header, Query, HTTPException
from pydantic import BaseModel, Field

from utils import ok
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
    status: int | None = Query(None, description="0待支付 1已支付 2已完成 3已取消，不传则全部"),
    pageNum: int = Query(1, ge=1),
    pageSize: int = Query(10, ge=1),
    authorization: str = Header(default=None),
):
    uid = require_user(authorization)
    return ok(svc.get_order_list(uid, status, pageNum, pageSize))


# ---------- 13. 订单详情 ----------
@router.get("/api/getOrderDetail")
def get_order_detail(
    orderId: int = Query(..., alias="orderId"),
    authorization: str = Header(default=None),
):
    uid = require_user(authorization)
    return ok(svc.get_order_detail(uid, orderId))


# ---------- 14. 观影统计（D5）----------
@router.get("/api/getWatchStats")
def get_watch_stats(authorization: str = Header(default=None)):
    require_user(authorization)
    raise HTTPException(501, "接口开发中（D5 实现）")
