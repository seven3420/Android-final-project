"""冒烟测试：下单 → 支付 → 观影统计"""
import urllib.request, json
from datetime import datetime

BASE = "http://127.0.0.1:9000"
TODAY = datetime.now().strftime("%Y-%m-%d")

def post(path, data, token=None):
    body = json.dumps(data).encode()
    req = urllib.request.Request(BASE + path, data=body, headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    return json.loads(urllib.request.urlopen(req).read())

def get(path, token=None):
    req = urllib.request.Request(BASE + path)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    return json.loads(urllib.request.urlopen(req).read())

# 1. 登录
print("=== 1. 登录 ===")
r = post("/api/login", {"phone": "13800000001", "password": "123456"})
token = r["data"]["token"]
print(f"✅ token = {token[:20]}...")

# 2. 场次
print("\n=== 2. 场次 ===")
s = get(f"/api/getScheduleList?movieId=1&cinemaId=1&date={TODAY}")
sch = s["data"][0]
print(f"✅ scheduleId={sch['id']} price={sch['price']} hall={sch['hallName']}")

# 3. 下单（不传 totalAmount 绕开改价校验，直接看服务端重算后的值）
print("\n=== 3. 下单 ===")
o = post("/api/createOrder", {
    "scheduleId": sch["id"],
    "seats": [{"row": 3, "col": 5}, {"row": 3, "col": 6}],
    "goods": []
}, token)
print(f"  code={o['code']} msg={o['msg']}")
if o["code"] != 0:
    print(f"  ❌ {o}")
    exit(1)
oid = o["data"]["orderId"]
print(f"✅ orderId={oid} totalAmount={o['data']['totalAmount']}")

# 4. 支付
print("\n=== 4. 支付 ===")
p = post("/api/payOrder", {"orderId": oid}, token)
print(f"  code={p['code']} msg={p['msg']}")
print(f"✅ 取票码={p['data']['ticketCode']}")

# 5. 再来一单（不同电影，凑点数据）
print("\n=== 5. 再来一单（电影2）===")
s2 = get(f"/api/getScheduleList?movieId=2&cinemaId=1&date={TODAY}")
sch2 = s2["data"][0]
o2 = post("/api/createOrder", {
    "scheduleId": sch2["id"],
    "seats": [{"row": 4, "col": 4}],
    "goods": []
}, token)
print(f"  code={o2['code']} msg={o2['msg']}")
if o2["code"] == 0:
    oid2 = o2["data"]["orderId"]
    p2 = post("/api/payOrder", {"orderId": oid2}, token)
    print(f"✅ 第二单已支付 orderId={oid2}")

# 6. 观影统计
print("\n=== 6. 观影统计 ===")
stats = get("/api/getWatchStats", token)
print(json.dumps(stats, indent=2, ensure_ascii=False))
