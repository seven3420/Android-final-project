"""
统一响应体：所有接口都返回 {code, msg, data}

    code = 0          成功
    code = 400        参数不合法
    code = 1001       未登录 / token 失效
    code = 2001~2005  业务错误：座位被占、库存不足、价格变动、订单失效、订单不存在

HTTP 状态码始终是 200，客户端只判断 body 里的 code，
避免 Android 端还要同时处理网络层异常和业务异常。
"""


def ok(data=None, msg="ok"):
    """成功响应：code 固定 0"""
    return {"code": 0, "msg": msg, "data": data}


def fail(code: int, msg: str):
    """失败响应：data 固定 None，错误码见模块说明"""
    return {"code": code, "msg": msg, "data": None}


def image_url(base: str, path: str | None) -> str | None:
    """把库里存的相对路径拼成完整 URL

    数据库只存 static/posters/xxx.jpg，接口按本次请求的地址拼接：
    电脑访问返回 http://localhost:9000/...，手机访问自动变成 http://192.168.x.x:9000/...，
    客户端（Glide）拿到就能直接加载，不需要自己拼 baseUrl。
    """
    if not path:
        return None
    if path.startswith(("http://", "https://")):
        return path  # 兼容以后换成外链或 CDN 的情况
    return base.rstrip("/") + "/" + path.lstrip("/")
