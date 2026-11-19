---
description: 用 js.eval 在独立环境执行短段 JavaScript，取得最后一个运算式的值。
---

# 执行 JavaScript

用 `js.eval(source, options)` 在独立环境执行短段 JavaScript。Java 的 options 与回传值使用 JSON 字串；Python 用 `self.js.eval`，QuickJS 用 `await js.eval`。

## 选项

| 选项 | 说明 |
| --- | --- |
| `globals` | 传入 JSON 变数，在脚本内以同名全域存取。 |
| `timeout` | 正整数毫秒，预设 5000。 |

内建 `md5X`。回传最后一个运算式的 JSON 值，不接受 Promise。环境不提供网络、DOM 或模组汇入，完成、逾时或取消后即关闭。三种语言都需要 QuickJS 模组。

## 范例

```py
# Python：计算签名
sign = self.js.eval(
    "md5X(seed + path + salt)",
    {"globals": {"seed": "demo", "path": "/api/list", "salt": "demo-salt"}}
)
```

```java
// Java：options 与回传都是 JSON 字串
String sign = js.eval("md5X(seed + path + salt)",
        "{\"globals\":{\"seed\":\"demo\",\"path\":\"/api/list\",\"salt\":\"demo-salt\"}}");
```

:::tip
只拿来算签名、解密或解析短片段。需要完整 Node.js 套件时，改用 [Node.js 载入](/spider/node)。
:::
