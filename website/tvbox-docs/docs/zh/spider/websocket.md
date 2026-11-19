---
description: 一次性 WebSocket 交换与持续连线，含断线恢复、ping 与 idle 逾时。
---

# WebSocket

一次交换用 `net.ws(url, options)`，取得首个完整讯息后关闭。持续收讯用 `net.connect(url, options, callback)`，回呼接收 `(socket, event)`，事件为 `open`／`message`／`retry`／`error`／`close`。

```js
// QuickJS：订阅持续连线
net.connect("wss://example.com/live", { idle: 60000, reconnect: true }, (socket, event) => {
  if (event.type === "open") socket.send(JSON.stringify({ subscribe: "demo-1" }));
  if (event.type === "message") handle(JSON.parse(event.content));
  if (event.type === "close") socket.close();
});
```

## 内容与关闭

`content` 为文字，二进制则为 Java `byte[]`、Python `bytes` 或 QuickJS `Uint8Array`。使用回呼传入的 `socket.send()` 发送、`socket.close()` 关闭，需立即中断时用 `cancel`。站点或 Session 释放时也会取消连线。

## 逾时与心跳

- `timeout` 只限制建立连线。
- `ping` 是协定 Ping 的毫秒间隔。
- `idle` 是未收到文字／二进制讯息的逾时毫秒数，预设 0 关闭；送出讯息与 Ping／Pong 不会重设期限。
- 平台订阅与应用层心跳由爬虫处理，只在来源保证持续回讯时设定 `idle`。

## 断线恢复

`reconnect: true` 启用断线恢复，预设关闭，等待从 1 秒倍增至 30 秒。每次 open 重新订阅，`retry` 时停止心跳并清除残包。主动关闭、释放或认证／凭证／协定／回呼错误后不再重连。

## 相关章节

- 即时弹幕：[即时弹幕](/spider/live-danmaku)
- 取消等待：[快取与储存](/spider/cache) 的 `sleep`
