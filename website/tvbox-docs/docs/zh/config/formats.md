---
description: JSON、M3U 与 TXT 三种直播列表格式的完整范例与设定行。
---

# 直播列表的三种完整输入

## JSON

`live.json`

```json
[
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
        ]
      }
    ]
  }
]
```

最外层是 Group 阵列，适合完整保存频道栏位与多线路。

## M3U

`live.m3u`

```txt
#EXTM3U url-tvg="https://example.com/epg.xml"
#EXTINF:-1 tvg-id="demo-1" tvg-name="范例一台" tvg-chno="1" tvg-logo="https://example.com/logo/demo-1.png" group-title="新闻",范例一台
#EXTVLCOPT:http-user-agent=ExampleClient/1.0
#EXTVLCOPT:http-referrer=https://example.com/
https://example.com/live/demo.m3u8
```

支援节目表、分组、频道 ID、号码、Logo、User-Agent 与 Referer。

## TXT

`live.txt`

```txt
新闻,#genre#
ua=ExampleClient/1.0
referer=https://example.com/
format=hls
范例一台,https://example.com/live/main.m3u8$主线#https://example.com/live/backup.m3u8$备线
```

`#genre#` 建立分组；同一频道的多个位置使用 `#` 分隔。

## TXT / M3U 设定行

| 设定 | 说明 |
| --- | --- |
| `ua=` | User-Agent。 |
| `parse=` | 是否进入解析，0 或 1。 |
| `click=` | WebView 点击程式。 |
| `header=` | 额外请求标头。 |
| `format=` | 媒体 MIME type。 |
| `origin=` | Origin 标头。 |
| `referer=` | Referer 标头。 |
| `forceKey=` | 强制使用指定的 DRM 请求位置。 |

设定行写在分组标题行之后，会套用到该分组后续的频道。

## 怎么选

| 需求 | 建议格式 |
| --- | --- |
| 需要完整栏位、多线路、DRM | JSON |
| 已有一份通用播放列表 | M3U |
| 手工维护、一行一台 | TXT |

:::tip
M3U 缺 `.m3u8` 后缀或 Content-Type 不规范时，用 `format=` 明确指定，可降低播放器误判。
:::
