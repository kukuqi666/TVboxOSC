---
description: 站点类型、api 前缀、ext 传递与显示相关栏位。
---

# Site 点播站点

## 四种类型

| `type` | 说明 |
| --- | --- |
| `0` | XML HTTP |
| `1` | JSON HTTP |
| `3` | JAR / PY / JS |
| `4` | HTTP 扩充 |

## 栏位

| 栏位 | 类型 | 必填 | 预设 | 说明 |
| --- | --- | --- | --- | --- |
| `key` | string | 是 | — | 站点唯一识别码，不可重复。 |
| `name` | string | 是 | — | 显示名称。 |
| `type` | integer | 否 | `0` | 0=XML HTTP、1=JSON HTTP、3=Spider；4=扩充 HTTP JSON，分类筛选以 Base64 `ext` 传送，站点 `ext` 另以 `extend` 传送。 |
| `api` | string | 是 | — | HTTP 端点、`csp_` 类别，或以 `./`、`../` 表示的 `.js`／`.py` 相对路径；也可使用完整网址。 |
| `ext` | string \| object \| array | 否 | — | type 3 传给 `Spider.init`；HTTP 站点以 `extend` 传送。物件与阵列先序列化为字串；主配置可填 URL 或相对路径。 |
| `jar` | string | 否 | — | 覆盖全域 `spider` 的 JAR 位置。 |
| `click` | string | 否 | — | 在 WebView 执行的 JavaScript 点击程式。 |
| `playUrl` | string | 否 | — | type 0/1 的解析 URL 前缀；支援 `json:` 端点或 `parse:` 解析器名称。type 3/4 由播放 `Result.playUrl` 提供。 |
| `hide` | integer | 否 | `0` | 1 时从站点清单隐藏。 |
| `indexs` | integer | 否 | `0` | 1 时作为索引站点。 |
| `timeout` | integer | 否 | `15` | 播放逾时秒数，最小 1。 |
| `searchable` | integer | 否 | `1` | 0=永久停用、1=启用；执行期间也可能保存为 2。 |
| `changeable` | integer | 否 | `1` | 0=禁止切换、1=可切换；执行期间也可能保存为 2。 |
| `lang` | string | 否 | — | 搜寻关键字转换语系，支援繁简中文标签；非空的其他值保留原文，空值使用既有转换。 |
| `danmaku` | integer | 否 | `1` | 0 停用此站点透过全域弹幕 API 自动搜寻，不影响 Spider 自带弹幕。 |
| `quickSearch` | integer | 否 | `1` | 0 跳过快速搜寻，1 启用。 |
| `categories` | `string[]` | 否 | — | 有匹配名称时只保留匹配分类并依清单排序；完全无匹配时保留原分类。 |
| `header` | object | 否 | — | HTTP API 请求标头；播放 `Result.header` 全空时也作为预设。type 3 不会自动套用到 Spider 自行发出的请求。 |
| `style` | `Style` | 否 | — | 站点预设卡片样式。 |

## Type 3 站点范例

`sites[] 物件片段`

```json
{
  "key": "js_demo",
  "name": "QuickJS Demo",
  "type": 3,
  "api": "./demo.js",
  "ext": { "region": "tw" },
  "searchable": 1,
  "changeable": 1,
  "categories": ["电影", "戏剧"],
  "header": { "User-Agent": "ExampleClient/1.0" },
  "style": { "type": "rect", "ratio": 1.33 }
}
```

改用 Python 时只需把 `api` 换成 `./demo.py`；Java 类别则填 `csp_Demo`。相对路径也适用于 `ext` 与 `jar`。

Python、QuickJS 与 Node.js 需先完成[模组安装](/spider/modules)，再用[爬虫测试页](/spider/testing)检查回传结果。

:::tip
`header` 只影响 App 发出的 HTTP 请求。type 3 的爬虫自己发出的请求要在代码里设定，或使用 `net` 的 options。
:::
