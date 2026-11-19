---
description: VodConfig 顶层栏位：站点、解析、直播、DNS、代理、规则与标头。
---

# Vod 顶层

`headers`、`proxy`、`rules` 与 `doh` 支援物件阵列、外部 JSON 阵列网址，或两者混用。

| 栏位 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `urls` | `Depot[]` | 否 | 配置仓库清单；登记各项配置后载入第一项，清单不可为空。 |
| `msg` | string | 否 | 配置回传的错误讯息；存在时停止载入，普通配置与仓库都适用。 |
| `spider` | string | 否 | 全域 Spider JAR 路径或 URL；Site 未指定 `jar` 时使用。 |
| `wallpaper` | string | 否 | 桌布图片、GIF 或影片位置。 |
| `logo` | string | 否 | App Logo 图片位置。 |
| `notice` | string | 否 | 启动后显示的公告文字。 |
| `sites` | `Site[]` | 是 | 点播站点清单。 |
| `parses` | `Parse[]` | 否 | 需要进一步处理 URL 时使用的解析规则。 |
| `lives` | `Live[]` | 否 | 内嵌直播配置清单；也可以另外载入独立 LiveConfig。 |
| `doh` | `Doh[]` | 否 | DNS over HTTPS 端点。 |
| `proxy` | `Proxy[]` | 否 | 依 host 套用的代理伺服器。 |
| `rules` | `Rule[]` | 否 | WebView 网络撷取与脚本规则。 |
| `headers` | `Header[]` | 否 | 依 host 注入或覆写 HTTP 请求标头。 |
| `hosts` | `string[]` | 否 | `原主机=目标主机或 IP` 的 DNS 覆盖。 |
| `flags` | `string[]` | 否 | 提供给 `playerContent` 的全域平台旗标。 |
| `ads` | `string[]` | 否 | 要拦截的主机名称清单。 |
| `danmaku` | string | 否 | 弹幕搜寻 API；可使用 `{name}` 与 `{episode}`。 |
| `assrt` | string | 否 | 字幕搜寻服务使用的存取字串。 |

## 配置仓库 Depot

`depot.json`

```json
{
  "urls": [
    { "name": "同目录配置", "url": "./vod.json" },
    { "name": "远端配置", "url": "https://example.com/vod.json" }
  ]
}
```

顶层出现 `urls` 时，App 登记清单后会载入第一项配置，清单不可为空；每一项包含 `name` 与 `url`。

## 可引用外部阵列的栏位

`VodConfig 顶层栏位片段`

```json
{
  "doh": "./doh.json",
  "headers": [
    "./headers.json",
    {
      "host": "media.example.com",
      "header": { "Referer": "https://example.com/" }
    }
  ],
  "proxy": "./proxy.json",
  "rules": [
    "./rules.json",
    {
      "name": "媒体请求范例",
      "hosts": ["player.example.com"],
      "regex": [".*m3u8.*"],
      "exclude": [".*preview.*"]
    }
  ]
}
```

整个栏位可以是一个位置，也可以在阵列中混用外部 JSON 位置与内嵌物件。外部档案本身必须回传 JSON 阵列。

:::tip
最小可用配置只需要 `sites`。其余栏位都可以省略，需要时再加。
:::
