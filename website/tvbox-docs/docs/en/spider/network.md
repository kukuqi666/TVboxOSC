---
description: The shared networking interface provided by the App: HTTP, cookies, sessions, batching and downloads.
---

# Shared Networking

The App provides networking before initialization: `net` in Java, `self.net` in Python and `net` in QuickJS. Time units are milliseconds. See the [CatVodSpider development notes](https://github.com/FongMi/CatVodSpider/blob/main/docs/development.md) for full parameters and samples.

## Method overview

| Method | Purpose |
| --- | --- |
| `req` / `http` | Returns code, headers, content and error; you check non-2xx responses yourself. |
| `json` | Returns JSON; connection failure, non-2xx or invalid JSON throws. |
| `get` / `post` | Java: returns the text body; the body is kept for non-2xx responses. |
| `getUserAgent` | Python／QuickJS: the App's User-Agent, add it to headers when needed. |
| `getCookie` / `setCookie` | Read and write WebView cookies; sessions use isolated cookies. |
| `session` | A request flow with isolated cookies; close it when done. |
| `batch` | Batched HTTP requests, 4 concurrent by default, results in input order. |
| `map` | Runs a callback per item in all three languages, 4 concurrent by default, keeps input order. |
| `download` | Downloads straight to a file; staging, resume and segmentation handled by the shared downloader. |
| `openStream` | Python／QuickJS chunked reading; close the reader when done. |
| `requestStreamAsync` | Java: pass NetOptions, the Future yields a Net.StreamResult. |
| `stream` | Python／QuickJS: pass a URL through unchanged, or supply a generator for the proxy body. |
| `ws` | One WebSocket exchange; closes after the first complete message. |
| `connect` | Persistent WebSocket with reconnection and idle timeout. |
| `cached` / `peek` / `refresh` | TTL cache, read existing values, background refresh. |
| `cachedText` | Python: raw cached JSON text. |
| `clearCache` | Clears one entry; omitting `key` clears all TTL cache for the site and cancels loads. |
| `sleep` | A cancellable wait, in milliseconds. |

## HTTP

Use `net.json(url, options)` when you only need JSON; connection failure, non-2xx or invalid JSON throws. Use `net.req` when you need the status code, error body or bytes, and `await net.http` in QuickJS. Java options are JSON strings unless noted, and `json`／`req` also return JSON strings.

QuickJS `req` runs synchronously; `http`／`json` are awaited. Python／QuickJS pass options as objects and use `buffer: 3` for binary responses, yielding bytes／ArrayBuffer. `net.getUserAgent()` returns the App UA; add it to headers yourself when needed.

```py
# Python: fetch JSON, throws on failure
data = self.net.json("https://example.com/api/list", {"timeout": 15000})
```

```js
// QuickJS: status code and error body
const res = await net.http("https://example.com/api/list", { timeout: 15000 });
if (res.code !== 200) throw new Error(res.error || String(res.code));
const data = JSON.parse(res.content);
```

## Cookies and sessions

`cookie: true` uses and updates WebView cookies; read with `getCookie(url)` and write a single Set-Cookie with `setCookie(url, cookie)`. `net.session()` manages isolated cookies automatically; it does not share the site's cookies or TTL cache and does not support `persist`.

Always close a session: try-with-resources in Java, `with` in Python, `finally` in QuickJS. Closing cancels its requests, streams and connections, and it cannot be reused.

```py
with self.net.session() as session:
    session.json("https://example.com/login", {"method": "POST"})
    data = session.json("https://example.com/api/list")
```

## Batching and downloads

`net.batch(requests, limit)` takes `{url, options}` per entry, 4 concurrent by default, and returns results in input order. When each item also needs caching, signing or parsing, use `net.map(items, mapper, limit)`; the callback receives item／index and must check the HTTP status itself. Dispatch stops on a thrown error while started work finishes; Java／Python call back on background threads, QuickJS may use `async`.

Java downloads with `net.download(url, optionsJson, file)`; Python／QuickJS use `net.download(url, path, options)`. `threads` is 0 for automatic, 1 for a single connection, 2–4 for a concurrent cap; `size` sets the exact byte count and `maxBytes` caps the written size. Resume and segmentation depend on Range and validators from the source; `cookie:true` / sessions never resume. Verify the resulting file afterwards.

```js
// QuickJS: fetch three pages in one batch
const pages = await net.batch(
  [1, 2, 3].map((pg) => ({ url: `https://example.com/list?pg=${pg}` })),
  3
);
```

## Related

- Large files and proxying: [Streams and proxy](/spider/streams)
- WebSockets: [WebSocket](/spider/websocket)
- Caching and storage: [Cache and storage](/spider/cache)
