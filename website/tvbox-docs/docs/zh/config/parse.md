---
description: 解析器类型、url 前缀与 ext.flag／ext.header 的比对比行为。
---

# Parse

## 类型

| `type` | 说明 |
| --- | --- |
| `0` | WebView |
| `1` | JSON API |
| `2` | JAR JSON |
| `3` | JAR 混合 |
| `4` | 并行尝试 |

## 栏位

| 栏位 | 类型 | 必填 | 预设 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | — | 解析器显示名称，也是具名选取依据。 |
| `type` | integer | 否 | `0` | 0=WebView、1=JSON API、2=JAR JSON、3=JAR 混合、4=并行尝试。 |
| `url` | string | 否 | — | 解析 API 端点或前缀。 |
| `ext.flag` | `string[]` | 否 | — | type 4 并行解析时，优先选取同类型且匹配旗标的解析器；没有匹配时回退该类型全部解析器。 |
| `ext.header` | object | 否 | — | 附加于解析请求；JSON 解析结果未提供标头时，也作为播放请求的预设标头。 |

## WebView 与 JSON API 范例

`parses 栏位值`

```json
[
  {
    "name": "WebView 解析范例",
    "type": 0,
    "url": "https://example.com/web?url="
  },
  {
    "name": "JSON API 解析范例",
    "type": 1,
    "url": "https://example.com/api?url=",
    "ext": {
      "flag": ["demo"],
      "header": { "Referer": "https://example.com/" }
    }
  }
]
```

`parses` 的值是阵列；`ext.flag` 是 type 4 并行解析的优先比对条件，不是全域白名单。`ext.header` 也可能作为解析结果的预设播放标头，详见上表。

## 何时需要解析

影視TV 先尝试直接播放爬虫回传的 `url`。需要额外处理时才进入解析：

- `Result.parse = 1` 或 `Result.jx = 1`。
- `Channel.parse = 1`（直播）。
- 配置旗标 `flags` 与 `ext.flag` 比对命中。

详见 [Result 回传格式](/spider/result)的 `parse`、`jx`、`jxFrom` 栏位。
