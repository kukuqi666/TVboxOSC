---
description: Run short JavaScript snippets in an isolated environment with js.eval and read the last expression.
---

# Run JavaScript

`js.eval(source, options)` runs a short JavaScript snippet in an isolated environment. Java passes options and reads results as JSON strings; Python uses `self.js.eval` and QuickJS uses `await js.eval`.

## Options

| Option | Purpose |
| --- | --- |
| `globals` | JSON variables exposed to the script under the same names. |
| `timeout` | Positive integer in milliseconds, 5000 by default. |

`md5X` is built in. The result is the JSON value of the last expression; promises are not accepted. The environment provides no networking, DOM or module imports, and is disposed after completion, timeout or cancellation. All three languages need the QuickJS module.

## Samples

```py
# Python: compute a signature
sign = self.js.eval(
    "md5X(seed + path + salt)",
    {"globals": {"seed": "demo", "path": "/api/list", "salt": "demo-salt"}}
)
```

```java
// Java: options and result are both JSON strings
String sign = js.eval("md5X(seed + path + salt)",
        "{\"globals\":{\"seed\":\"demo\",\"path\":\"/api/list\",\"salt\":\"demo-salt\"}}");
```

:::tip
Use this for signatures, decryption or short parsing snippets. When you need full Node.js packages, use [Node.js loading](/spider/node).
:::
