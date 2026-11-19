---
description: 分块读取远端串流，并把内容原样转送给 App 播放器或本地代理。
---

# 串流读取与代理

Java 用 `net.requestStreamAsync(url, NetOptions)`，Future 回传继承 InputStream 的 `Net.StreamResult`。Python 用 `self.net.openStream(url, options)`，QuickJS 用 `await net.openStream(url, options)`。用完以 try-with-resources、with 或 finally 关闭。

## 原样转送

将下列片段放入对应的 proxy 方法。成功交付后由 App 关闭 stream，不交付时自行 close。QuickJS handle 只供当次 proxy 使用一次，不存入 Local／快取；Java 需引入 `Net` 与 `NetOptions`。

**Java** `Demo.java / proxy`

```java
NetOptions options = NetOptions.from("{\"timeout\":15000,\"callTimeout\":15000}");
Net.StreamResult response = net.requestStreamAsync("https://example.com/data.bin", options).get();
return new Object[]{response.code, response.contentType, response, response.headers};
```

**Python** `spider.py / localProxy`

```python
response = self.net.stream("https://example.com/data.bin", {
    "timeout": 15000, "callTimeout": 15000
})
return [response["code"], response["contentType"], response["stream"], response["headers"]]
```

**QuickJS** `spider.js / proxy`

```js
const response = await net.stream("https://example.com/data.bin", {
  timeout: 15000, callTimeout: 15000
});
return [response.code, response.contentType, response.stream, response.headers];
```

## 自订串流正文

Python 用 `self.net.stream(generator())`，yield bytes／bytearray／连续 memoryview；QuickJS 用 `await net.stream(asyncGenerator())`，yield ArrayBuffer／TypedArray。将回应的 `stream` 放入 proxy 第三格；Java 直接回传自订 InputStream。

```py
# Python：边读边转，适合改写或解密
def localProxy(self, params):
    path = params["path"]
    return [200, "application/octet-stream", self.net.stream(transform(path))]

def transform(path):
    with self.net.openStream(f"https://example.com/{path}") as reader:
        while True:
            chunk = reader.read(65536)
            if not chunk:
                break
            yield chunk
```

产生器在播放器读取时才执行，不接受 HTTP options。reader 在产生器内开启，以 with／finally 关闭；App 关闭 body 时取消其等待中的串流请求与读取，再关闭产生器。

直播可用 `callTimeout: 0` 关闭整次 HTTP 期限，保留 `timeout` 防止读取卡住；Demo 使用 15 秒期限。读取大小、EOF 与取消限制见[开发说明](https://github.com/FongMi/CatVodSpider/blob/main/docs/development.md)。

:::warning
Java 的一般 `Object[]` 回传中，第三格必须是 `InputStream` 或 `Net.StreamResult`，不能直接放字串或 bytes。完整回传格式见 [Proxy 回传](/spider/proxy)。
:::
