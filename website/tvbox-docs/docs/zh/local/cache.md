---
description: /cache 的键值读写，以及 rule 命名空间隔离。
---

# /cache：键值快取

使用 GET 或表单 POST。实际储存键为 `"cache_" + (rule 为空 ? "" : rule + "_") + key`；`rule` 可用来隔离不同爬虫的命名空间。

| `do` | 参数 | 回应 |
| --- | --- | --- |
| `get` | `key`、`rule`（选填） | 回传储存的字串；不存在时为空字串。 |
| `set` | `key`、`value`、`rule`（选填） | 写入字串，回传 OK。 |
| `del` | `key`、`rule`（选填） | 移除键，回传 OK。 |

```sh
curl -G "http://127.0.0.1:9978/cache" --data-urlencode "do=set" --data-urlencode "rule=demo" --data-urlencode "key=greeting" --data-urlencode "value=Hello"

curl "http://127.0.0.1:9978/cache?do=get&rule=demo&key=greeting"

curl "http://127.0.0.1:9978/cache?do=del&rule=demo&key=greeting"
```

## 键的组成

| 组合 | 实际键 |
| --- | --- |
| `rule=demo`、`key=greeting` | `cache_demo_greeting` |
| `rule` 省略、`key=greeting` | `cache_greeting` |

:::tip
这是 App 端的通用键值储存，与爬虫的 [`net.cached`](/spider/cache)（按站点隔离的 TTL 快取）不是同一套储存。
:::

## 相关

- 爬虫快取与 Local：[快取与储存](/spider/cache)
- 档案传输：[档案与目录管理](/local/files)
