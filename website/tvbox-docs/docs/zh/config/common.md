---
description: DoH、Proxy、Rule、Header、Catchup、Drm 与 Style 等共用物件的栏位。
---

# 共用物件

## Doh

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `Doh.name` | string | DoH 显示名称。 |
| `Doh.url` | string | DoH 查询端点。 |
| `Doh.ips` | `string[]` | Bootstrap IP。 |

## Proxy

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `Proxy.name` | string | 代理规则名称。 |
| `Proxy.hosts` | `string[]` | 适用 host，支援规则比对。 |
| `Proxy.urls` | `string[]` | 含主机与埠号的代理位置，例如 `http://127.0.0.1:8080`、`socks5://127.0.0.1:1080`；依 `http` 或 `socks` 前缀选择 Java 代理类型。 |

## Rule

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `Rule.name` | string | 规则显示名称。 |
| `Rule.hosts` | `string[]` | 触发目标 host。 |
| `Rule.regex` | `string[]` | 有效媒体位置的撷取条件。 |
| `Rule.click` | `string[]` | WebView 中要点击的 CSS 选择器清单；与 JavaScript `script` 分开设定。 |
| `Rule.script` | `string[]` | WebView 中执行的脚本。 |
| `Rule.exclude` | `string[]` | 不应被撷取的排除条件。 |

## Header

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `Header.host` | string | 要处理的 host。 |
| `Header.header` | object | 要注入或覆写的 HTTP 请求标头。 |

## 节目回看 Catchup

| 栏位 | 类型 | 预设 | 说明 |
| --- | --- | --- | --- |
| `Catchup.type` | string | 空字串；行为为附加 | `default` 完全替换；其他值附加于原位置。 |
| `Catchup.days` | string | — | 保留栏位；目前不限制可回看的天数。 |
| `Catchup.regex` | string | — | 判断设定是否套用的字串或规则。 |
| `Catchup.source` | string | — | 节目回看 URL 模板，请提供非空值；可含开始与结束时间 token。 |
| `Catchup.replace` | string | — | 附加模式使用 `正規表示式,替换内容`；`default` 模式不套用。 |

```json
{
  "name": "节目回看示意",
  "url": "./live.m3u",
  "catchup": {
    "type": "append",
    "days": "7",
    "regex": "example.com/live/",
    "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}",
    "replace": "live/,archive/"
  }
}
```

`${(b)yyyyMMddHHmmss}` 与 `${(e)yyyyMMddHHmmss}` 会分别替换为节目开始与结束时间，格式化使用装置系统时区，不使用 `Live.timeZone`。

`catchup` 用于从节目表选取回看，时间轴以该节目为范围。来源若支援依时间取源，可在频道提供 [`timeshift`](/spider/timeshift)，使用可拖曳的直播时间轴。

## Drm

| 栏位 | 类型 | 预设 | 说明 |
| --- | --- | --- | --- |
| `Drm.type` | string | — | 媒体 DRM 类型，例如 `widevine`、`playready`、`clearkey`。 |
| `Drm.key` | string | — | DRM 请求位置或该类型接受的金钥资料。 |
| `Drm.header` | object \| string | — | DRM 请求标头，可用物件或 JSON 物件字串。 |
| `Drm.forceKey` | boolean | `false` | 强制使用指定的 DRM 请求位置，不使用媒体内的预设位置；对应 `forceDefaultLicenseUri`。 |

## Style

| 栏位 | 类型 | 预设 | 说明 |
| --- | --- | --- | --- |
| `Style.type` | string | `rect` | `rect`、`oval` 或 `list`。 |
| `Style.ratio` | float | 依 type | 宽／高；正值最大 4。未设或小于等于 0 时，`oval` 为 1，其余为 0.75。 |

## DoH、Proxy、Hosts 与 Ads

`VodConfig 顶层栏位片段`

```json
{
  "doh": [
    {
      "name": "Example DoH",
      "url": "https://dns.example.com/dns-query",
      "ips": ["203.0.113.53"]
    }
  ],
  "proxy": [
    {
      "name": "本机代理范例",
      "hosts": ["media.example.com"],
      "urls": ["http://127.0.0.1:8080", "socks5://127.0.0.1:1080"]
    }
  ],
  "hosts": ["media.example.com=203.0.113.10"],
  "ads": ["ads.example.com"]
}
```

代理位置必须包含 scheme、host 与 port；`hosts` 使用 `原主机=目标主机或 IP`。

:::tip
`headers`、`proxy`、`rules` 与 `doh` 都可用外部 JSON 阵列网址。见 [Vod 顶层](/config/vod)。
:::
