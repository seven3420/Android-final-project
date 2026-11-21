"""
接口 8：卖品模块（只读，不需要登录）
GET /api/getProductList  按影院查卖品，热销置顶

字段说明：stock 是实时库存（下单扣减、取消/超时归还），
salesCount 是累计销量（支付成功时累加），客户端据此展示"已售 N 份"。
"""
from fastapi import APIRouter, Query
from database import cursor
from utils import ok

router = APIRouter()


# ---------- 8. 卖品列表 ----------
@router.get("/api/getProductList")
def get_product_list(cinemaId: int = Query(..., alias="cinemaId")):
    with cursor() as cur:
        cur.execute(
            """SELECT id, name, image, price, origin_price, stock, sales AS salesCount,
                      is_hot AS isHot
               FROM product WHERE cinema_id=?
               ORDER BY is_hot DESC, sales DESC, id ASC""",
            (cinemaId,),
        )
        rows = cur.fetchall()

    return ok([dict(r) for r in rows])
