---
description: LiveConfig 与 lives[] 来源物件栏位，以及内嵌与外部列表的取舍。
---

# Live 直播来源

独立 LiveConfig 的 JSON 顶层使用 `lives`，并支援 `spider`、`headers`、`proxy`、`rules`、`hosts`、`ads`、`urls` 与 `msg`；不读取独立的 `doh`。下表说明 `lives[]` 中的 Live 来源物件。

`url` 与 `groups` 通常择一；`groups` 为空且提供 `api` 时，会先呼叫 Spider 的 `liveContent(url)` 取得文字，再交由 LiveParser 判断格式。

:::warning
同名配置已保存的 `boot`、`pass` 与 `keep` 可能覆盖配置值；请同时检查 App 内的已保存设定。
:::

## 栏位

| 栏位 | 类型 | 必填 | 预设 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | — | 直播配置唯一名称。 |
| `url` | string | 否 | — | 外部 TXT、M3U 或 JSON 列表位置；与 `groups` 择一。 |
| `api` | string | 否 | — | `groups` 为空时，用此 Spider 的 `liveContent(url)` 取得外部列表文字。 |
| `ext` | string \| object \| array | 否 | — | 传给直播 Spider 的初始化资料；物件与阵列会先序列化为字串。 |
| `jar` | string | 否 | — | 此直播配置专用 JAR。 |
| `click` | string | 否 | — | 预设于 WebView 执行的 JavaScript 点击程式。 |
| `logo` | string | 否 | — | 预设 Logo 模板，可用 `{id}`、`{name}`、`{logo}`。 |
| `epg` | string | 否 | — | 节目表位置，逗号分隔；支援 XMLTV 与 API 模板。 |
| `ua` | string | 否 | — | 预设 User-Agent。 |
| `origin` | string | 否 | — | 预设 Origin 标头。 |
| `referer` | string | 否 | — | 预设 Referer 标头。 |
| `timeZone` | string | 否 | 系统时区 | 节目表时区，例如 `Asia/Taipei`。 |
| `keep` | string | 否 | — | 已保存频道定位资讯；通常由 App 自行维护。 |
| `timeout` | integer | 否 | `15` | 播放逾时秒数，最小 1。 |
| `header` | object | 否 | — | 直播请求附加 HTTP 标头。 |
| `catchup` | `Catchup` | 否 | — | 外部列表频道的预设节目回看设定；内嵌 `groups` 需在频道自行提供。 |
| `core` | `Core` | 否 | — | 特殊播放核心的进阶初始化设定。 |
| `groups` | `Group[]` | 否 | — | 内嵌频道分组。 |
| `boot` | boolean | 否 | `false` | 目前选中的直播配置为 true 时，载入完成后触发自动进入直播。 |
| `pass` | boolean | 否 | `false` | true 时不把 TXT／M3U 分组名称中的 `_后缀` 解析成密码；JSON 内明确设定的 `Group.pass` 不受影响。 |

## 载入外部列表

`live-config.json`

```json
{
  "lives": [
    {
      "name": "M3U 范例",
      "url": "./live.m3u",
      "epg": "https://example.com/epg.xml",
      "logo": "https://example.com/logo/{id}.png",
      "ua": "ExampleClient/1.0",
      "timeZone": "Asia/Taipei",
      "boot": false
    }
  ]
}
```

这是含 `lives` 阵列的完整 LiveConfig；`url` 指向可独立维护的 M3U、TXT 或 JSON 档案。

## 直接内嵌 Group / Channel

`live-config.json`

```json
{
  "lives": [
    {
      "name": "内嵌 JSON 范例",
      "groups": [
        {
          "name": "新闻",
          "channel": [
            {
              "name": "范例一台",
              "number": "1",
              "tvgId": "demo-1",
              "urls": [
                "https://example.com/live/main.m3u8$主线",
                "https://example.com/live/backup.m3u8$备线"
              ],
              "header": { "Referer": "https://example.com/" }
            }
          ]
        }
      ]
    }
  ]
}
```

`$主线`、`$备线` 是线路标签；同一频道可以提供多个位置。直接内嵌 `groups` 不经过外部列表的自动编号与预设值继承，请在 Channel 明确设定需要的栏位。

## 两种方式的差异

| 项目 | 外部 `url` | 内嵌 `groups` |
| --- | --- | --- |
| 维护方式 | 单独维护列表档案 | 与配置放同一份 |
| 预设值继承 | 继承 Live 的 `logo`、`ua`、`catchup` 等 | 需在 Channel 自行设定 |
| 自动编号 | 会补齐 | 不会 |
| 格式 | TXT、M3U、JSON | JSON |

格式细节见[直播列表的三种完整输入](/config/formats)，分组与频道栏位见 [Group / Channel](/config/channel)。
