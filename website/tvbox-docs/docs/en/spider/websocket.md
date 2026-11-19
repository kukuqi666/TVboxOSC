---
description: One-shot WebSocket exchanges and persistent connections with reconnection, ping and idle timeout.
---

# WebSocket

Use `net.ws(url, options)` for a single exchange; it closes after the first complete message. Use `net.connect(url, options, callback)` for continuous messages; the callback receives `(socket, event)` where event is `open`／`message`／`retry`／`error`／`close`.

```js
// QuickJS: a persistent subscription
net.connect("wss://example.com/live", { idle: 60000, reconnect: true }, (socket, event) => {
  if (event.type === "open") socket.send(JSON.stringify({ subscribe: "demo-1" }));
  if (event.type === "message") handle(JSON.parse(event.content));
  if (event.type === "close") socket.close();
});
```

## Content and closing

`content` is text; binary data arrives as Java `byte[]`, Python `bytes` or QuickJS `Uint8Array`. Send with the provided `socket.send()`, close with `socket.close()`, and use `cancel` to interrupt immediately. Connections are also cancelled when the site or session is released.

## Timeouts and keepalive

- `timeout` limits connection establishment only.
- `ping` is the protocol Ping interval in milliseconds.
- `idle` is the timeout for receiving no text／binary message; 0 (the default) disables it. Sending messages and Ping／Pong do not reset the deadline.
- Platform subscriptions and application-level heartbeats belong to the spider; only set `idle` when the source guarantees ongoing traffic.

## Reconnection

`reconnect: true` enables reconnection (off by default); the wait starts at 1 second and doubles up to 30 seconds. Resubscribe on every open; on `retry` stop heartbeats and drop leftover packets. No reconnect happens after a deliberate close, release, or auth／certificate／protocol／callback errors.

## Related

- Live danmaku: [Live danmaku](/spider/live-danmaku)
- Cancellable waits: `sleep` in [Cache and storage](/spider/cache)
