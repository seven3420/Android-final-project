"""
接口 3-4：影片模块（只读，不需要登录）
GET /api/getMovieList    热映/待映列表（status=now/soon，分页）
GET /api/getMovieDetail  影片详情

返回结构：列表接口统一为 {list:[...], total:n}，字段全部驼峰命名。
"""
from fastapi import APIRouter, Query, HTTPException, Request
from database import cursor
from utils import ok, fail, image_url

router = APIRouter()


# ---------- 3. 影片列表 ----------
@router.get("/api/getMovieList")
def get_movie_list(
    request: Request,
    status: str = Query("now", description="now=热映 soon=待映"),
    pageNum: int = Query(1, ge=1),
    pageSize: int = Query(10, ge=1),
):
    with cursor() as cur:
        cur.execute("SELECT COUNT(*) AS cnt FROM movie WHERE status=?", (status,))
        total = cur.fetchone()["cnt"]

        cur.execute(
            "SELECT id, title, poster, score, duration, type, release_date "
            "FROM movie WHERE status=? ORDER BY id ASC LIMIT ? OFFSET ?",
            (status, pageSize, (pageNum - 1) * pageSize),
        )
        rows = cur.fetchall()

    base = str(request.base_url)
    items = []
    for r in rows:
        item = dict(r)
        item["poster"] = image_url(base, item["poster"])
        items.append(item)

    return ok({
        "list": items,
        "total": total,
    })


# ---------- 4. 影片详情 ----------
@router.get("/api/getMovieDetail")
def get_movie_detail(
    request: Request,
    movieId: int = Query(..., alias="movieId"),
):
    with cursor() as cur:
        cur.execute(
            "SELECT id, title, poster, score, duration, type, director, actors, description "
            "FROM movie WHERE id=?",
            (movieId,),
        )
        row = cur.fetchone()
    if not row:
        raise HTTPException(2005, "影片不存在")

    data = dict(row)
    data["poster"] = image_url(str(request.base_url), data["poster"])
    return ok(data)
