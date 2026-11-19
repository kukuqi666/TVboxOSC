---
description: Read remote streams in chunks and hand the content to the App player or local proxy unchanged.
---

# Streams and Proxy

Java uses `net.requestStreamAsync(url, NetOptions)`; the Future yields a `Net.StreamResult` extending InputStream. Python uses `self.net.openStream(url, options)` and QuickJS uses `await net.openStream(url, options)`. Close with try-with-resources, `with` or `finally`.

## Pass-through

Put the following snippet in the matching proxy method. Once delivered the App closes the stream; if you do not deliver it, close it yourself. A QuickJS handle is valid for one proxy call only — never store it in Local or the cache. Java needs the `Net` and `NetOptions` imports.

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

## Custom stream bodies

Python uses `self.net.stream(generator())`, yielding bytes／bytearray／contiguous memoryview; QuickJS uses `await net.stream(asyncGenerator())`, yielding ArrayBuffer／TypedArray. Put the response `stream` in the third proxy slot; in Java return a custom InputStream.

```py
# Python: read and transform on the fly
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

Generators run only when the player reads, and accept no HTTP options. Open readers inside the generator and close them with `with`／`finally`; when the App closes the body it cancels pending stream requests and reads, then closes the generator.

For live TV, `callTimeout: 0` removes the overall HTTP deadline while `timeout` still prevents a stuck read; the demo uses 15 seconds. Read size, EOF and cancellation limits are in the [development notes](https://github.com/FongMi/CatVodSpider/blob/main/docs/development.md).

:::warning
In a Java `Object[]` response the third slot must be an `InputStream` or `Net.StreamResult`; it cannot be a string or bytes. See [Proxy response](/spider/proxy) for the full format.
:::
