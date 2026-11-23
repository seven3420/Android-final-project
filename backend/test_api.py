"""
接口冒烟测试：覆盖 1-13 号接口，含负向用例（锁座冲突、订单不存在等）

用法：
    1. 先启动服务：Ctrl+Shift+B 或 uvicorn main:app --port 9000
    2. 再运行：python test_api.py
    3. 需要测其他地址时设环境变量，如 API_BASE=http://192.168.1.5:9000

说明：
    - 每行输出 `code=实际值 (期望 xxx)`，✅ 通过 / ❌ 不通过
    - 会写入真实订单；想恢复干净演示数据，跑完执行 python seed.py
"""
import urllib.request, json, datetime, os

BASE = os.environ.get("API_BASE", "http://localhost:9000")


def test(name, path, method="GET", data=None, token=None, expect=0):
    try:
        url = BASE + path
        headers = {}
        if data:
            headers["Content-Type"] = "application/json"
            data = json.dumps(data).encode()
        if token:
            headers["Authorization"] = f"Bearer {token}"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        result = json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        result = json.loads(e.read())
    except Exception as e:
        print(f"❌ {name}: 请求失败 {e}")
        return None

    code = result.get("code")
    mark = "✅" if code == expect else "❌"
    print(f"{mark} {name}: code={code} (期望 {expect})  msg={result.get('msg', '')}")
    return result


print("=" * 46)
print("FastAPI 接口冒烟测试")
print("=" * 46)

test("根接口", "/")
login = test("1.登录", "/api/login", "POST", {"phone": "13800000001", "password": "123456"})
token = (login.get("data") or {}).get("token") if login else None

test("2.用户信息", "/api/getUserInfo", token=token)
test("3.影片列表(热映)", "/api/getMovieList?status=now&pageNum=1&pageSize=10")
test("4.影片详情", "/api/getMovieDetail?movieId=1")
test("5.影院列表", "/api/getCinemaList?movieId=1")

today = datetime.date.today().strftime("%Y-%m-%d")
schedules = test("6.场次列表", f"/api/getScheduleList?cinemaId=1&movieId=1&date={today}")
test("7.座位图(scheduleId=1)", "/api/getSeatMap?scheduleId=1")
test("8.卖品列表", "/api/getProductList?cinemaId=1")

print("-" * 46)
print("订单模块 9-13")
print("-" * 46)

sch_list = (schedules or {}).get("data") or []
if not sch_list:
    print("⚠️ 今天没有可用场次，跳过订单测试")
else:
    sid = sch_list[0]["id"]
    seat_map = test("7b.座位图(取空闲座位)", f"/api/getSeatMap?scheduleId={sid}")
    free = [s for s in (seat_map.get("data") or {}).get("seats", []) if s["status"] == 0]

    if len(free) < 4:
        print("⚠️ 空闲座位不足，跳过订单测试")
    else:
        seats1 = [{"row": free[0]["row"], "col": free[0]["col"]}]
        seats2 = [{"row": free[1]["row"], "col": free[1]["col"]}]

        # 9. 下单（含卖品）
        created = test(
            "9.创建订单(1 座 + 1 份卖品)", "/api/createOrder", "POST",
            {"scheduleId": sid, "seats": seats1,
             "goods": [{"productId": 1, "quantity": 1}]},
            token=token,
        )
        order_id = ((created or {}).get("data") or {}).get("orderId")

        # 9b. 同一座位重复下单应被拦截
        test(
            "9b.重复锁座拦截", "/api/createOrder", "POST",
            {"scheduleId": sid, "seats": seats1}, token=token, expect=2001,
        )

        # 9c. 座位图应出现“已锁”状态
        sm = test("9c.座位图(锁定态)", f"/api/getSeatMap?scheduleId={sid}")
        locked = [s for s in (sm.get("data") or {}).get("seats", []) if s["status"] == 2]
        print(("✅" if locked else "❌") + f" 锁定座位数: {len(locked)}")

        # 13. 订单详情
        test(f"13.订单详情(orderId={order_id})",
             f"/api/getOrderDetail?orderId={order_id}", token=token)

        # 10. 支付
        paid = test("10.模拟支付", "/api/payOrder", "POST",
                    {"orderId": order_id}, token=token)
        codes = (paid or {}).get("data") or {}
        print(("✅" if codes.get("ticketCode") else "❌")
              + f" 取票码: {codes.get('ticketCode')}  取餐码: {codes.get('goodsCode')}")

        # 12. 订单列表
        test("12.订单列表(已支付)", "/api/getOrderList?status=1", token=token)

        # 9d. 再下一单并取消，验证座位与库存释放
        created2 = test(
            "9d.创建待支付订单", "/api/createOrder", "POST",
            {"scheduleId": sid, "seats": seats2,
             "goods": [{"productId": 1, "quantity": 2}]},
            token=token,
        )
        order2 = ((created2 or {}).get("data") or {}).get("orderId")
        test("11.取消订单", "/api/cancelOrder", "POST", {"orderId": order2}, token=token)

        sm2 = test("11b.座位图(取消后释放)", f"/api/getSeatMap?scheduleId={sid}")
        target = next((s for s in (sm2.get("data") or {}).get("seats", [])
                       if s["row"] == seats2[0]["row"] and s["col"] == seats2[0]["col"]), None)
        print(("✅" if target and target["status"] == 0 else "❌")
              + f" 取消后座位状态: {target['status'] if target else '未找到'} (0=可选)")

        # 11c. 越权访问他人订单应报 2005
        test("13b.订单不存在", "/api/getOrderDetail?orderId=999999",
             token=token, expect=2005)

print("=" * 46)
print("测试完毕")
