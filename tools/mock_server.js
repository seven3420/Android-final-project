#!/usr/bin/env node
/**
 * 交易链路 Mock 服务（成员 C 联调用，Node 版）。
 *
 * 与 tools/mock_server.py 契约完全一致，按成员 A 的《后端接口文档》对齐：
 *   - getProductList 返回裸数组，原价键名 origin_price，isHot 是 1/0
 *   - payOrder 只收 orderId，幂等返回原码
 *   - getOrderList 已完成 Tab 传 status=2
 *   - 已支付订单取消回 400
 *
 * 为什么有这份 Node 版：开发机没有 Python，但装了 Node，可直接用
 *   node tools/mock_server.js 9000
 * 启动在 9000 端口后，模拟器里的 App 用默认地址 10.0.2.2:9000 即可联通。
 *
 * 演示数据按当前真实时间生成，倒计时与开场提醒都可直接演示。
 */

const http = require('http');
const { URL } = require('url');

const PORT = Number(process.argv[2] || 8000);
const TICKET_PRICE = 42.0;

const pad = (n) => String(n).padStart(2, '0');
const fmt = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
  `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
const at = (offsetMinutes) => fmt(new Date(Date.now() + offsetMinutes * 60000));

const ok = (data) => ({ code: 0, msg: 'ok', data });
const err = (code, msg) => ({ code, msg, data: null });

const PRODUCTS = [
  { id: 1, name: '双人套餐', image: '', price: 58.0, origin_price: 68.0, stock: 120, salesCount: 66, isHot: 1 },
  { id: 2, name: '单人套餐', image: '', price: 32.0, origin_price: 38.0, stock: 80, salesCount: 30, isHot: 1 },
  { id: 3, name: '大桶爆米花', image: '', price: 25.0, origin_price: 28.0, stock: 60, salesCount: 12, isHot: 0 },
  { id: 4, name: '可乐大杯', image: '', price: 12.0, origin_price: null, stock: 2, salesCount: 9, isHot: 0 },
  { id: 5, name: '香草冰淇淋', image: '', price: 15.0, origin_price: null, stock: 0, salesCount: 2, isHot: 0 },
];

const LIST_KEYS = [
  'orderId', 'movieTitle', 'poster', 'cinemaName', 'hallName', 'startTime',
  'seatCount', 'seats', 'totalAmount', 'status', 'createTime', 'expireTime',
];

const ORDERS = {
  1001: {
    orderId: 1001, movieTitle: '寻梦环游记', poster: '',
    cinemaName: '星汇影城（春熙路店）', hallName: '1号厅',
    startTime: at(60 * 26), seatCount: 2,
    seats: ['5 排 6 座', '5 排 7 座'],
    totalAmount: 142.0, status: 1,
    createTime: at(-120), expireTime: at(-105),
    cinemaAddress: '成都市锦江区春熙路1号', langVersion: '国语 2D',
    goods: [{ name: '双人套餐', quantity: 1, amount: 58.0 }],
    ticketAmount: 84.0, goodsAmount: 58.0,
    ticketCode: '1234 5678', goodsCode: '4321',
  },
  1002: {
    orderId: 1002, movieTitle: '哪吒之魔童降世', poster: '',
    cinemaName: '锦江国际影城', hallName: '2号厅',
    startTime: at(60 * 30), seatCount: 1,
    seats: ['8 排 9 座'],
    totalAmount: 42.0, status: 0,
    createTime: at(-3), expireTime: at(12),
    cinemaAddress: '成都市锦江区红星路3号', langVersion: '国语 2D',
    goods: [], ticketAmount: 42.0, goodsAmount: 0.0,
    ticketCode: null, goodsCode: null,
  },
  1003: {
    orderId: 1003, movieTitle: '心灵奇旅', poster: '',
    cinemaName: '天府艺术影城', hallName: '3号厅',
    startTime: at(-60 * 72), seatCount: 2,
    seats: ['3 排 4 座', '3 排 5 座'],
    totalAmount: 84.0, status: 2,
    createTime: at(-60 * 74), expireTime: at(-60 * 74 + 15),
    cinemaAddress: '成都市高新区天府大道9号', langVersion: '原版 2D',
    goods: [], ticketAmount: 84.0, goodsAmount: 0.0,
    ticketCode: '9012 3456', goodsCode: null,
  },
  // 已支付但已过开场时间：用于演示取票码"已过期"的虚化状态
  1004: {
    orderId: 1004, movieTitle: '流浪地球2', poster: '',
    cinemaName: '太平洋影城（春熙路店）', hallName: '5号厅',
    startTime: at(-60 * 20), seatCount: 1,
    seats: ['7 排 8 座'],
    totalAmount: 46.0, status: 1,
    createTime: at(-60 * 24), expireTime: at(-60 * 24 + 15),
    cinemaAddress: '成都市锦江区青年路18号', langVersion: '国语 2D',
    goods: [], ticketAmount: 46.0, goodsAmount: 0.0,
    ticketCode: '2468 1357', goodsCode: null,
  },
};

const WATCH_STATS = {
  totalShows: 5, totalMinutes: 525, totalHours: 8.8,
  totalTickets: 8, totalAmount: 356.0,
  lastWatchTime: at(-60 * 24 * 3),
  topCinema: { name: '星汇影城（春熙路店）', count: 3 },
  topMovie: { title: '寻梦环游记', count: 2 },
  typeDist: [
    { type: '动画/家庭/冒险', count: 3 },
    { type: '喜剧/动作/悬疑', count: 2 },
  ],
  recentList: [
    { title: '寻梦环游记', poster: '', type: '动画/家庭/冒险', watchTime: at(-60 * 24 * 3), cinemaName: '星汇影城（春熙路店）' },
    { title: '心灵奇旅', poster: '', type: '动画/家庭/奇幻', watchTime: at(-60 * 24 * 5), cinemaName: '天府艺术影城' },
  ],
};

let nextOrderId = 1004;

function send(res, payload, status = 200) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.writeHead(status, {
    'Content-Type': 'application/json;charset=UTF-8',
    'Content-Length': body.length,
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => (raw += chunk));
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch (_) {
        resolve({});
      }
    });
  });
}

function authed(req) {
  const auth = req.headers['authorization'] || '';
  return auth.startsWith('Bearer ') && auth.length > 'Bearer '.length;
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, ok(null));
  if (!authed(req)) return send(res, err(1001, '未登录或 token 失效'));

  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname;
  const q = Object.fromEntries(url.searchParams.entries());
  console.log(req.method, req.url);

  if (req.method === 'GET') {
    if (path === '/api/getProductList') {
      if (!q.cinemaId) return send(res, err(400, '参数不合法'));
      return send(res, ok(PRODUCTS));
    }
    if (path === '/api/getOrderList') {
      const status = q.status;
      const items = Object.values(ORDERS)
        .filter((o) => !status || o.status === Number(status))
        .map((o) => Object.fromEntries(LIST_KEYS.map((k) => [k, o[k]])));
      return send(res, ok({ list: items, total: items.length }));
    }
    if (path === '/api/getOrderDetail') {
      const order = ORDERS[Number(q.orderId)];
      return order ? send(res, ok(order)) : send(res, err(2005, '订单不存在'));
    }
    if (path === '/api/getWatchStats') {
      return send(res, ok(WATCH_STATS));
    }
    return send(res, err(2005, `接口不存在: ${path}`));
  }

  if (req.method === 'POST') {
    const body = await readBody(req);

    if (path === '/api/createOrder') {
      const seats = body.seats || [];
      const goods = body.goods || [];
      if (seats.length < 1 || seats.length > 4) {
        return send(res, err(400, '参数不合法：座位数需在 1 到 4 之间'));
      }
      if (seats[0] && seats[0].row === 99) return send(res, err(2001, '座位已被占用'));
      for (const g of goods) {
        const p = PRODUCTS.find((x) => x.id === g.productId);
        if (!p || g.quantity > 5 || g.quantity > p.stock) {
          return send(res, err(2002, '卖品库存不足'));
        }
      }
      const goodsAmount = goods.reduce((sum, g) => {
        const p = PRODUCTS.find((x) => x.id === g.productId);
        return sum + p.price * g.quantity;
      }, 0);
      const total = Math.round((TICKET_PRICE * seats.length + goodsAmount) * 100) / 100;
      if (body.totalAmount != null && Math.abs(Number(body.totalAmount) - total) >= 0.01) {
        return send(res, err(2003, '价格已变动'));
      }
      const oid = nextOrderId++;
      ORDERS[oid] = {
        orderId: oid, movieTitle: '星际远征', poster: '',
        cinemaName: '星汇影城（春熙路店）', hallName: '1号厅',
        startTime: at(180), seatCount: seats.length,
        seats: seats.map((s) => `${s.row} 排 ${s.col} 座`),
        totalAmount: total, status: 0,
        createTime: at(0), expireTime: at(15),
        cinemaAddress: '成都市锦江区春熙路1号', langVersion: '国语 2D',
        goods: goods.map((g) => {
          const p = PRODUCTS.find((x) => x.id === g.productId);
          return {
            name: p.name,
            quantity: g.quantity,
            amount: Math.round(p.price * g.quantity * 100) / 100,
          };
        }),
        ticketAmount: TICKET_PRICE * seats.length,
        goodsAmount: Math.round(goodsAmount * 100) / 100,
        ticketCode: null, goodsCode: null,
      };
      return send(res, ok({ orderId: oid, totalAmount: total, expireTime: at(15) }));
    }

    if (path === '/api/payOrder') {
      const order = ORDERS[Number(body.orderId)];
      if (!order) return send(res, err(2005, '订单不存在'));
      if (order.status === 2 || order.status === 3) return send(res, err(2004, '订单已失效'));
      if (order.status !== 1) {
        order.status = 1;
        order.ticketCode = String(order.orderId).padStart(4, '0') + ' 5678';
        order.goodsCode = order.goods.length ? '4321' : null;
      }
      return send(res, ok({
        orderId: order.orderId,
        status: order.status,
        ticketCode: order.ticketCode,
        goodsCode: order.goodsCode,
      }));
    }

    if (path === '/api/cancelOrder') {
      const order = ORDERS[Number(body.orderId)];
      if (!order) return send(res, err(2005, '订单不存在'));
      if (order.status === 1) return send(res, err(400, '已支付订单不可取消'));
      if (order.status === 2 || order.status === 3) return send(res, err(2004, '订单已失效'));
      order.status = 3;
      return send(res, ok({ orderId: order.orderId, status: 3 }));
    }

    return send(res, err(2005, `接口不存在: ${path}`));
  }

  return send(res, err(2005, `接口不存在: ${path}`));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`交易链路 Mock（Node 版）已启动: http://127.0.0.1:${PORT}/api/`);
  console.log('模拟器请用 http://10.0.2.2:' + PORT + '/，Ctrl+C 停止');
});
