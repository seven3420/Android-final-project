"""
影片数据同步脚本（TMDB 官方 API）

运行：python fetch_tmdb.py
前提：先设置 API Key（二选一，任选一种方式）
      PowerShell:  $env:TMDB_API_KEY="你的 v3 key"
      PowerShell:  $env:TMDB_READ_TOKEN="你的 v4 读访问令牌"

作用：
    1. 从 TMDB 拉取院线热映 / 待映影片（中文数据：片名、简介、类型、评分、时长、导演、主演）
    2. 海报下载到 static/posters/，断网环境也能正常显示
    3. 结果写入 data/movies.json，seed.py 读取后注入数据库

说明：
    - 本脚本只在开发阶段联网运行一次，后端运行时不依赖 TMDB
    - 自动过滤恐怖片（genre 27），符合演示内容要求
    - 数据来源：TMDB。此产品使用 TMDB API，但未获得 TMDB 认可或认证。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# api.themoviedb.org 在部分网络被拦截，api.tmdb.org 为可访问的同接口备用域名；
# 如需切换可设置环境变量 TMDB_API_HOST（仅域名部分，如 api.themoviedb.org）
API_BASE = f"https://{os.getenv('TMDB_API_HOST', 'api.tmdb.org')}/3"
IMG_BASE = "https://image.tmdb.org/t/p/w500"

BASE_DIR = Path(__file__).resolve().parent
POSTER_DIR = BASE_DIR / "static" / "posters"
DATA_FILE = BASE_DIR / "data" / "movies.json"

# 不参与演示的题材：27=恐怖 / 53=惊悚（演示影片不得包含恐怖、惊悚类内容）
EXCLUDE_GENRE_IDS = {27, 53}

# 热映取 6 部、待映取 1 部
NOW_COUNT = 6
SOON_COUNT = 1


def get_credentials():
    """读取 API 凭证：优先 v4 读访问令牌，其次 v3 key"""
    token = os.getenv("TMDB_READ_TOKEN", "").strip()
    key = os.getenv("TMDB_API_KEY", "").strip()
    if not token and not key:
        print("❌ 未找到 TMDB 凭证。请先设置环境变量：")
        print('   $env:TMDB_API_KEY="你的 v3 key"')
        print("   或")
        print('   $env:TMDB_READ_TOKEN="你的 v4 读访问令牌"')
        print("   注册地址：https://www.themoviedb.org/settings/api")
        sys.exit(1)
    return token, key


def api_get(path, token="", key="", params=None):
    """调用 TMDB 接口，返回解析后的 JSON"""
    query = {"language": "zh-CN"}
    if key:
        query["api_key"] = key
    if params:
        query.update(params)

    url = f"{API_BASE}{path}?{urllib.parse.urlencode(query)}"
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/json")

    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = 2 * (attempt + 1)
                print(f"   请求过频，{wait}s 后重试…")
                time.sleep(wait)
                continue
            if e.code == 401:
                print("❌ 凭证无效（401），请检查 API Key / 读访问令牌")
                sys.exit(1)
            print(f"❌ 请求失败 {path}: HTTP {e.code}")
            return None
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"   网络异常（{e}），{2}s 后重试…")
            time.sleep(2)
    return None


def download_poster(poster_path, movie_id):
    """下载海报到本地，返回相对路径；失败返回 None"""
    if not poster_path:
        return None

    target = POSTER_DIR / f"tmdb_{movie_id}.jpg"
    if target.exists() and target.stat().st_size > 0:
        print(f"   海报已存在，跳过：{target.name}")
        return f"static/posters/{target.name}"

    url = IMG_BASE + poster_path
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            target.write_bytes(resp.read())
        print(f"   海报已下载：{target.name}（{target.stat().st_size // 1024} KB）")
        return f"static/posters/{target.name}"
    except Exception as e:
        print(f"   ⚠️ 海报下载失败（{e}），该影片将跳过")
        return None


def pick_movies(list_path, count, status, token, key):
    """从 TMDB 榜单中筛选并补全影片详情"""
    listing = api_get(list_path, token, key, {"region": "CN", "page": 1})
    if not listing or not listing.get("results"):
        print(f"⚠️ {list_path} 未返回数据")
        return []

    picked = []
    for item in listing["results"]:
        if len(picked) >= count:
            break

        genre_ids = set(item.get("genre_ids") or [])
        if genre_ids & EXCLUDE_GENRE_IDS:
            print(f"   跳过恐怖片：{item.get('title')}")
            continue
        if not item.get("poster_path"):
            print(f"   跳过无海报影片：{item.get('title')}")
            continue

        time.sleep(0.3)
        detail = api_get(
            f"/movie/{item['id']}", token, key,
            {"append_to_response": "credits"},
        )
        time.sleep(0.3)
        if not detail:
            continue

        # 时长、类型缺失的影片不适合排片，跳过
        runtime = detail.get("runtime") or 0
        genres = [g["name"] for g in (detail.get("genres") or [])]
        if not runtime or not genres:
            print(f"   跳过信息不全影片：{item.get('title')}")
            continue

        poster_rel = download_poster(item["poster_path"], item["id"])
        if not poster_rel:
            continue

        crew = (detail.get("credits") or {}).get("crew") or []
        directors = [c["name"] for c in crew if c.get("job") == "Director"]
        cast = (detail.get("credits") or {}).get("cast") or []
        actors = [c["name"] for c in cast[:3]]

        title = detail.get("title") or item.get("title") or ""
        overview = (detail.get("overview") or item.get("overview") or "").strip()
        release = detail.get("release_date") or item.get("release_date") or ""

        picked.append({
            "title": title,
            "poster": poster_rel,
            "score": round(float(detail.get("vote_average") or 0), 1),
            "duration": runtime,
            "type": "/".join(genres),
            "director": ",".join(directors) or "未知",
            "actors": ",".join(actors) or "未知",
            "description": overview[:120] or f"{title}，{release}上映。",
            "status": status,
            "release_date": release,
        })
        print(f"   ✓ {title}（{runtime}分钟 / {detail.get('vote_average')}分）")

    return picked


def fetch_by_ids(ids, status, token, key):
    """按指定 TMDB id 拉取影片详情（不查榜单，用于自定义片单）"""
    picked = []
    for mid in ids:
        time.sleep(0.3)
        detail = api_get(f"/movie/{mid}", token, key, {"append_to_response": "credits"})
        time.sleep(0.3)
        if not detail:
            print(f"   ⚠️ id={mid} 拉取失败，跳过")
            continue

        genre_ids = {g["id"] for g in (detail.get("genres") or [])}
        if genre_ids & EXCLUDE_GENRE_IDS:
            print(f"   跳过恐怖/惊悚片：{detail.get('title')}（id={mid}）")
            continue

        runtime = detail.get("runtime") or 0
        genres = [g["name"] for g in (detail.get("genres") or [])]
        if not runtime or not genres:
            print(f"   跳过信息不全影片：{detail.get('title')}（id={mid}）")
            continue

        poster_rel = download_poster(detail.get("poster_path"), detail["id"])
        if not poster_rel:
            continue

        crew = (detail.get("credits") or {}).get("crew") or []
        directors = [c["name"] for c in crew if c.get("job") == "Director"]
        cast = (detail.get("credits") or {}).get("cast") or []
        actors = [c["name"] for c in cast[:3]]

        title = detail.get("title") or ""
        overview = (detail.get("overview") or "").strip()
        release = detail.get("release_date") or ""

        picked.append({
            "title": title,
            "poster": poster_rel,
            "score": round(float(detail.get("vote_average") or 0), 1),
            "duration": runtime,
            "type": "/".join(genres),
            "director": ",".join(directors) or "未知",
            "actors": ",".join(actors) or "未知",
            "description": overview[:120] or f"{title}，{release}上映。",
            "status": status,
            "release_date": release,
        })
        print(f"   ✓ {title}（{runtime}分钟 / {detail.get('vote_average')}分）")

    return picked


def main():
    POSTER_DIR.mkdir(parents=True, exist_ok=True)
    DATA_FILE.parent.mkdir(parents=True, exist_ok=True)

    token, key = get_credentials()

    now_ids = [int(x) for x in os.getenv("TMDB_MOVIE_IDS_NOW", "").split(",") if x.strip()]
    soon_ids = [int(x) for x in os.getenv("TMDB_MOVIE_IDS_SOON", "").split(",") if x.strip()]

    if now_ids or soon_ids:
        print("=== 按指定 id 拉取（TMDB_MOVIE_IDS_NOW / TMDB_MOVIE_IDS_SOON）===")
        now_movies = fetch_by_ids(now_ids, "now", token, key) if now_ids else []
        print()
        soon_movies = fetch_by_ids(soon_ids, "soon", token, key) if soon_ids else []
    else:
        print("=== 拉取热映影片 ===")
        now_movies = pick_movies("/movie/now_playing", NOW_COUNT, "now", token, key)

        print("\n=== 拉取待映影片 ===")
        soon_movies = pick_movies("/movie/upcoming", SOON_COUNT, "soon", token, key)

    movies = now_movies + soon_movies
    if not movies:
        print("\n❌ 没有拉到任何可用影片，请检查网络或凭证")
        sys.exit(1)

    DATA_FILE.write_text(
        json.dumps(movies, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\n✅ 已写入 {DATA_FILE}")
    print(f"   共 {len(movies)} 部（热映 {len(now_movies)} / 待映 {len(soon_movies)}）")
    print("   下一步：python seed.py 注入数据库")


if __name__ == "__main__":
    main()
