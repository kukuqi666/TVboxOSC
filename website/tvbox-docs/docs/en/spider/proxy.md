---
description: The proxy／localProxy response array, how Java's Object[] differs, and getProxyUrl.
---

# Proxy Response

`proxy / localProxy`

```json
[
  200,
  "text/plain; charset=utf-8",
  "demo response",
  { "Cache-Control": "no-cache" }
]
```

This is Python／QuickJS array mode: status, Content-Type, content and optional headers; a fifth slot of `1` marks Base64 content. The Python method is named `localProxy`, and the third slot may also return bytes.

## Per-language differences

| Runtime | Return | Third slot | Notes |
| --- | --- | --- | --- |
| Python | array | text, bytes or stream | Method name is `localProxy`. |
| QuickJS | array | text, TypedArray or stream | `from=catvod` also offers a JSON response mode. |
| Java | `Object[]` | must be an `InputStream` | Fourth slot headers are optional; strings and bytes are rejected. A single `NanoHTTPD.Response` may also be returned. |
| Node.js | forwards the HTTP response | — | See [Node.js loading](/spider/node). |

## Building the URL

Use `getProxyUrl(params)` to build the URL; the App appends `do`, `siteKey` and encodes parameters. In JAR mode without a `siteKey`, the App looks for the static entry `com.github.catvod.spider.Proxy.proxy(Map)`.

```js
// QuickJS: proxy URL inside the playback result
return {
  parse: 0,
  url: getProxyUrl({ type: "video", id: "demo-1" })
};
```

```java
// Java: static entry
public static Object[] proxy(Map<String, String> params) throws Exception {
    try (InputStream in = fetch(params.get("id"))) {
        return new Object[]{200, "video/mp4", in};
    }
}
```

:::warning
The App closes a stream after it is delivered. If an exception occurs before you return, close any stream you already opened.
:::

Related: [Streams and proxy](/spider/streams), [local API /proxy](/local/internal).
