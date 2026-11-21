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
