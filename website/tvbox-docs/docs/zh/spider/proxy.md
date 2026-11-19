---
description: proxy／localProxy 的回传阵列、Java 的 Object[] 差异，以及 getProxyUrl 的用法。
---

# Proxy 回传

`proxy / localProxy`

```json
[
  200,
  "text/plain; charset=utf-8",
  "demo response",
  { "Cache-Control": "no-cache" }
]
```

上例是 Python／QuickJS 一般阵列模式：状态码、Content-Type、内容、选填标头；第 5 格 `1` 表示 Base64 内容。Python 方法名称是 `localProxy`，第三格也可直接回传 bytes。

## 各语言差异

| 环境 | 回传 | 第三格 | 备注 |
| --- | --- | --- | --- |
| Python | 阵列 | 文字、bytes 或 stream | 方法名为 `localProxy`。 |
| QuickJS | 阵列 | 文字、TypedArray 或 stream | `from=catvod` 另有 JSON response 模式。 |
| Java | `Object[]` | 必须是 `InputStream` | 第四格标头选填，不能直接放字串或 bytes；也可只回传一格 `NanoHTTPD.Response`。 |
| Node.js | 直接转接 HTTP 回应 | — | 见 [Node.js 载入](/spider/node)。 |

## 产生网址

使用 `getProxyUrl(params)` 产生网址，App 自动加入 `do`、`siteKey` 并编码参数。JAR 模式没有 `siteKey` 时，会找 `com.github.catvod.spider.Proxy.proxy(Map)` 静态入口。

```js
// QuickJS：产生代理网址放进播放结果
return {
  parse: 0,
  url: getProxyUrl({ type: "video", id: "demo-1" })
};
```

```java
// Java：静态入口
public static Object[] proxy(Map<String, String> params) throws Exception {
    try (InputStream in = fetch(params.get("id"))) {
        return new Object[]{200, "video/mp4", in};
    }
}
```

:::warning
成功交付后由 App 关闭 stream。若在回传前就发生例外，请自行关闭已开启的 stream，避免泄漏。
:::

相关：[串流读取与代理](/spider/streams)、[本地 API 的 /proxy](/local/internal)。
