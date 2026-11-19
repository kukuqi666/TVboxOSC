---
description: Every Result field for explore and playback responses, plus the multi-quality url forms.
---

# Result Format

## Explore responses

`home · category`

```json
{
  "class": [
    { "type_id": "movie", "type_name": "Movies" }
  ],
  "filters": {
    "movie": [
      {
        "key": "area",
        "name": "Region",
        "init": "",
        "value": [
          { "n": "All", "v": "" },
          { "n": "Taiwan", "v": "tw" }
        ]
      }
    ]
  },
  "list": [
    {
      "vod_id": "demo-1",
      "vod_name": "Sample item",
      "vod_pic": "https://example.com/poster.jpg",
      "vod_remarks": "Sample"
    }
  ],
  "pagecount": 1,
  "msg": "Optional message"
}
```

`homeContent` uses `class` / `filters`; lists, search and details use `list`. The first-level key of `filters` must equal the category `type_id`, `movie` in this example.

## Playback responses

`playerContent`

```json
{
  "url": "https://example.com/video/demo-1.m3u8",
  "parse": 0,
  "header": {
    "User-Agent": "ExampleClient/1.0",
    "Referer": "https://example.com/"
  },
  "format": "application/x-mpegURL",
  "subs": [
    {
      "url": "https://example.com/subs/demo-1.vtt",
      "name": "Traditional Chinese",
      "lang": "zh-TW",
      "format": "text/vtt",
      "flag": 0
    }
  ],
  "danmaku": [
    { "url": "https://example.com/danmaku/demo-1.xml", "name": "Sample danmaku" }
  ],
  "artwork": "https://example.com/poster.jpg",
  "desc": "Sample playback info",
  "position": 120000
}
```

`url` is the core field; `parse=0` means this field did not request parsing, though `jx=1` or a configuration flag match can still trigger it.

## Complete Result fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `web` | `{url, mode?, cookie?: {url?, keys: string[]}[]}` | — | Open-page instruction for `action`, runs once; see [Open a web page](/spider/web). |
| `class` | `Class[]` | `[]` | Home page category list. |
| `list` | `Vod[]` | `[]` | Cards for home, category and search; details use `list[0]`. |
| `filters` | `object` | `{}` | Keyed by category ID, each value a Filter array or a single Filter. |
| `url` | `string \| string[] \| Url` | — | Playback URL or a multi-quality list; the three forms are below. |
| `skips` | `SkipSegment[]` | `[]` | Opening, middle and ending skip ranges in milliseconds; malformed entries are ignored. |
| `timeshift` | `object` | — | Live catch-up window and source URL `{url, start, window}`; see [Live timeshift](/spider/timeshift). |
| `header` | `object \| string` | `{}` | Playback request headers; JSON object strings also accepted. An empty object falls back to `Site.header` without per-key merging. |
| `msg` | `string` | empty | Message text; shown only when `code` is 0. |
| `code` | `integer` | `0` | Message control value, not an HTTP status; a non-zero value hides `msg`. |
| `danmaku` | `Danmaku[] \| string` | `[]` | Danmaku list, single URL or JSON array string; file or live spider sources, subject to the App danmaku setting. |
| `subs` | `Sub[]` | `[]` | External subtitle list. |
| `playUrl` | `string` | empty | Parse prefix; `json:` for a JSON endpoint, `parse:` for a named parser. |
| `artwork` | `string` | empty | Updates the artwork shown on the playback page. |
| `jxFrom` | `string` | empty | Parser name reported by extended JSON parsing, used in the success message; not a play group flag. |
| `flag` | `string` | filled by App | Play group and parse matching flag; filled with the `playerContent` flag when omitted. |
| `desc` | `string` | empty | Cleaned description shown on the playback page. |
| `format` | `string` | — | Media MIME hint, use the full type such as `application/x-mpegURL`. |
| `click` | `string` | empty | JavaScript run after the WebView loads; `Site.click` wins when non-empty. |
| `key` | `string` | filled by App | Site key injected by the App; spiders need not provide it and it is overwritten. |
| `position` | `integer` | — | Start position in milliseconds; 120000 is 2 minutes. Existing progress is kept when omitted. |
| `pagecount` | `integer` | `0` | Total pages; 0 or absent means unknown and more pages may still load. |
| `parse` | `integer` | `0` | 1 requests parsing; with 0, `jx=1` or a flag match may still trigger it. |
| `jx` | `integer` | `0` | 1 requests parsing even when `parse=0`. |
| `drm` | `Drm` | — | Playback DRM settings; same object as `Channel.drm`, see the [configuration reference](/config/common). |

## Multi-quality url forms

A single URL is shown in the playback sample above. An array must alternate "name, URL" pairs — it is not a plain list of URLs. The `values` object is the alternative. Top-level `position` is milliseconds, while `Url.position` is a zero-based quality index; the App may still adjust the actual selection.

**Name and URL pairs**

```json
{
  "parse": 0,
  "url": [
    "Main", "https://example.com/video/main.m3u8",
    "Low", "https://example.com/video/low.m3u8"
  ]
}
```

**Url object**

```json
{
  "parse": 0,
  "url": {
    "values": [
      { "n": "Main", "v": "https://example.com/video/main.m3u8" },
      { "n": "Low", "v": "https://example.com/video/low.m3u8" }
    ],
    "position": 0
  }
}
```

For `drm` sub-fields see the [shared object reference](/config/common); for detail play groups see [Groups and episodes](/spider/episodes).
