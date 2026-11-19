---
description: App 提供的共用网络接口：HTTP、Cookie、Session、批次与下载。
---

# 共用网络

App 在初始化前提供网络接口：Java 使用 `net`、Python 使用 `self.net`、QuickJS 使用 `net`。时间单位为毫秒。完整参数与范例见 [CatVodSpider 开发说明](https://github.com/FongMi/CatVodSpider/blob/main/docs/development.md)。

## 方法一览

| 方法 | 用途 |
| --- | --- |
| `req` / `http` | 取得 code、headers、content 与 error；HTTP 非 2xx 需自行判断。 |
| `json` | 取得 JSON；连线失败、HTTP 非 2xx 或无效 JSON 会抛出例外。 |
| `get` / `post` | Java 取得文字正文；HTTP 非 2xx 仍保留正文。 |
| `getUserAgent` | Python／QuickJS 取得 App 的 User-Agent，按需放入 headers。 |
| `getCookie` / `setCookie` | 读写 WebView Cookie；Session 使用隔离 Cookie。 |
| `session` | 建立隔离 Cookie 的请求流程，用完关闭。 |
| `batch` | 批次 HTTP 请求，预设最多 4 笔并行，按输入顺序回传。 |
| `map` | 三语言逐项执行回呼，预设最多 4 项并行，保留输入顺序。 |
| `download` | 直接下载至档案，由共用下载器管理暂存、续传与分段。 |
| `openStream` | Python／QuickJS 分块读取，用完关闭 reader。 |
| `requestStreamAsync` | Java 传入 NetOptions，Future 回传 Net.StreamResult。 |
| `stream` | Python／QuickJS 原样转送网址，或由产生器提供 proxy 正文。 |
| `ws` | 一次 WebSocket 讯息交换，取得首个完整讯息后关闭。 |
| `connect` | 持续 WebSocket 连线，可设定断线恢复与接收逾时。 |
| `cached` / `peek` / `refresh` | TTL 快取、读取已有值与背景更新。 |
| `cachedText` | Python 取得快取的 JSON 原文。 |
| `clearCache` | 清除一笔；省略 key 清除目前站点全部 TTL 快取并取消载入。 |
| `sleep` | 可取消的等待，单位为毫秒。 |

## HTTP

只取 JSON 用 `net.json(url, options)`；连线失败、HTTP 非 2xx 或无效 JSON 会抛出例外。需要状态码、错误正文或 bytes 时用 `net.req`，QuickJS 用 `await net.http`。Java 的 options 除特别标示外使用 JSON 字串，`json`／`req` 也回传 JSON 字串。

QuickJS 的 `req` 同步执行；`http`／`json` 以 await 取得结果。Python／QuickJS 使用物件传 options，二进制回应用 `buffer: 3`，分别取得 bytes／ArrayBuffer。`net.getUserAgent()` 取得 App 的 UA，需要时自行放入 headers。

```py
# Python：取得 JSON，失败会抛例外
data = self.net.json("https://example.com/api/list", {"timeout": 15000})
```

```js
// QuickJS：需要状态码与错误正文
const res = await net.http("https://example.com/api/list", { timeout: 15000 });
if (res.code !== 200) throw new Error(res.error || String(res.code));
const data = JSON.parse(res.content);
```

## Cookie 与 Session

`cookie: true` 使用并更新 WebView Cookie；以 `getCookie(url)` 读取、`setCookie(url, cookie)` 写入单笔 Set-Cookie。`net.session()` 自动管理隔离 Cookie，不共用站点的 Cookie 或 TTL 快取，也不支援 persist。

Session 用完须关闭：Java 用 try-with-resources、Python 用 with、QuickJS 用 finally。关闭会取消其请求、串流与连线，之后不可继续使用。

```py
with self.net.session() as session:
    session.json("https://example.com/login", {"method": "POST"})
    data = session.json("https://example.com/api/list")
```

## 批次与下载

`net.batch(requests, limit)` 每笔传入 `{url, options}`，预设最多 4 笔并行，按输入顺序回传。每项还需快取、签名或解析时用 `net.map(items, mapper, limit)`，回呼收 item／index，各项自行检查 HTTP 状态。抛错后停止派送，已开始的工作继续完成；Java／Python 在背景执行绪回呼，QuickJS 可用 async。

Java 下载用 `net.download(url, optionsJson, file)`，Python／QuickJS 用 `net.download(url, path, options)`。`threads` 为 0 自动、1 单连线、2–4 并行上限；`size` 指定完整 bytes，`maxBytes` 限制写入大小。续传与分段依来源的 Range 与验证资讯决定，`cookie:true`／Session 不续传；完成后仍须确认档案内容。

```js
// QuickJS：批次取得三页列表
const pages = await net.batch(
  [1, 2, 3].map((pg) => ({ url: `https://example.com/list?pg=${pg}` })),
  3
);
```

## 相关章节

- 大文件下载与代理：[串流读取与代理](/spider/streams)
- WebSocket：[WebSocket](/spider/websocket)
- 快取与储存：[快取与储存](/spider/cache)
