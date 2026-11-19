---
description: Complete JSON, M3U and TXT live list samples, plus the setting lines they support.
---

# Live List Formats

## JSON

`live.json`

```json
[
  {
    "name": "News",
    "channel": [
      {
        "name": "Sample Channel 1",
        "number": "1",
        "tvgId": "demo-1",
        "urls": [
          "https://example.com/live/main.m3u8$main",
          "https://example.com/live/backup.m3u8$backup"
        ]
      }
    ]
  }
]
```

The top level is a Group array, which is the best fit for full channel fields and multiple lines.

## M3U

`live.m3u`

```txt
#EXTM3U url-tvg="https://example.com/epg.xml"
#EXTINF:-1 tvg-id="demo-1" tvg-name="Sample Channel 1" tvg-chno="1" tvg-logo="https://example.com/logo/demo-1.png" group-title="News",Sample Channel 1
#EXTVLCOPT:http-user-agent=ExampleClient/1.0
#EXTVLCOPT:http-referrer=https://example.com/
https://example.com/live/demo.m3u8
```

Supports EPG, groups, channel ID, number, logo, User-Agent and Referer.

## TXT

`live.txt`

```txt
News,#genre#
ua=ExampleClient/1.0
referer=https://example.com/
format=hls
Sample Channel 1,https://example.com/live/main.m3u8$main#https://example.com/live/backup.m3u8$backup
```

`#genre#` starts a group; separate multiple locations of the same channel with `#`.

## TXT / M3U setting lines

| Setting | Notes |
| --- | --- |
| `ua=` | User-Agent. |
| `parse=` | Whether to enter parsing, 0 or 1. |
| `click=` | WebView click handler. |
| `header=` | Extra request headers. |
| `format=` | Media MIME type. |
| `origin=` | Origin header. |
| `referer=` | Referer header. |
| `forceKey=` | Force the given DRM license location. |

Setting lines go after a group header and apply to the channels that follow in that group.

## Which format

| Need | Recommended |
| --- | --- |
| Full fields, multiple lines, DRM | JSON |
| An existing generic playlist | M3U |
| Hand-maintained, one channel per line | TXT |

:::tip
When an M3U lacks the `.m3u8` suffix or uses a non-standard Content-Type, set `format=` explicitly to avoid player misdetection.
:::
