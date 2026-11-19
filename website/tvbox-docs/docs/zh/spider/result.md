---
description: 探索类与播放类 Result 的完整栏位，以及 url 的多画质写法。
---

# Result 回传格式

## 探索类

`home · category`

```json
{
  "class": [
    { "type_id": "movie", "type_name": "电影" }
  ],
  "filters": {
    "movie": [
      {
        "key": "area",
        "name": "地区",
        "init": "",
        "value": [
          { "n": "全部", "v": "" },
          { "n": "台湾", "v": "tw" }
        ]
      }
    ]
  },
  "list": [
    {
      "vod_id": "demo-1",
      "vod_name": "范例项目",
      "vod_pic": "https://example.com/poster.jpg",
      "vod_remarks": "示意"
    }
  ],
  "pagecount": 1,
  "msg": "选填提示"
}
```

`homeContent` 使用 `class` / `filters`；列表、搜寻、详情使用 `list`。`filters` 第一层键必须等于分类的 `type_id` 值，本例是 `movie`。

## 播放类

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
      "name": "繁体中文",
      "lang": "zh-TW",
      "format": "text/vtt",
      "flag": 0
    }
  ],
  "danmaku": [
    { "url": "https://example.com/danmaku/demo-1.xml", "name": "示意弹幕" }
  ],
  "artwork": "https://example.com/poster.jpg",
  "desc": "示意播放资讯",
  "position": 120000
}
```

`url` 是核心栏位；`parse=0` 表示此栏位未要求解析，但 `jx=1` 或配置旗标比对仍可能触发解析。

## 完整 Result 栏位

| 栏位 | 类型 | 预设 | 说明 |
| --- | --- | --- | --- |
| `web` | `{url, mode?, cookie?: {url?, keys: string[]}[]}` | — | `action` 回应的开网页指令，只执行一次；详见[开网页](/spider/web)。 |
| `class` | `Class[]` | `[]` | 首页分类清单。 |
| `list` | `Vod[]` | `[]` | 首页、分类与搜寻卡片；详情取 `list[0]`。 |
| `filters` | `object` | `{}` | 以分类 ID 为键，值为 Filter 阵列或单一 Filter。 |
| `url` | `string \| string[] \| Url` | — | 播放 URL 或多画质清单，三种写法见下方范例。 |
| `skips` | `SkipSegment[]` | `[]` | 每集片头、片中、片尾的跳过区间，`start` / `end` 都用毫秒；格式错误的项目会忽略。 |
| `timeshift` | `object` | — | 直播回看范围与取源网址 `{url, start, window}`，格式见[直播回看](/spider/timeshift)。 |
| `header` | `object \| string` | `{}` | 播放请求标头；也接受 JSON 物件字串。整份为空时回退 `Site.header`，不逐键合并。 |
| `msg` | `string` | 空字串 | 显示提示文字；只有 `code` 为 0 时显示。 |
| `code` | `integer` | `0` | 讯息控制值，不是 HTTP 状态码；非 0 时隐藏 `msg`。 |
| `danmaku` | `Danmaku[] \| string` | `[]` | 弹幕清单、单一 URL 或 JSON 阵列字串，可使用档案或爬虫即时来源；受 App 弹幕载入设定控制。 |
| `subs` | `Sub[]` | `[]` | 外挂字幕清单。 |
| `playUrl` | `string` | 空字串 | 解析前缀；`json:` 后接 JSON 解析端点，`parse:` 后接具名解析器。 |
| `artwork` | `string` | 空字串 | 更新播放页显示的图片。 |
| `jxFrom` | `string` | 空字串 | 扩展 JSON 解析结果回报的解析器名称，用于成功提示；不是播放分组旗标。 |
| `flag` | `string` | 由 App 补入 | 播放分组与解析比对旗标；未填时 App 补入呼叫 `playerContent` 时的 `flag`。 |
| `desc` | `string` | 空字串 | 清理后更新播放页描述。 |
| `format` | `string` | — | 媒体 MIME type 提示，请填完整类型，例如 `application/x-mpegURL`。 |
| `click` | `string` | 空字串 | WebView 载入后执行的 JavaScript；`Site.click` 非空时优先。 |
| `key` | `string` | 由 App 补入 | App 内部回填的 Site key；爬虫不必提供，提供也会被覆写。 |
| `position` | `integer` | — | 播放起点，单位毫秒；120000 为 2 分钟。未填时不覆盖既有播放进度。 |
| `pagecount` | `integer` | `0` | 总页数；0 或未填表示未知，仍可继续载入更多。 |
| `parse` | `integer` | `0` | 1 要求解析；0 仍可能因 `jx=1` 或配置旗标比对进入解析。 |
| `jx` | `integer` | `0` | 1 要求进入解析，即使 `parse=0`。 |
| `drm` | `Drm` | — | 播放 DRM 设定，与 `Channel.drm` 使用相同物件，见[配置字典](/config/common)。 |

## url 的多画质写法

单一 URL 见上方播放范例。阵列必须以「名称、URL」成对排列，不是单纯的 URL 清单；也可改用 `values` 物件。顶层 `position` 是毫秒，`Url.position` 则是从 0 起算的画质索引，实际选取仍会由 App 调整。

**名称与 URL 成对阵列**

```json
{
  "parse": 0,
  "url": [
    "主画质", "https://example.com/video/main.m3u8",
    "低画质", "https://example.com/video/low.m3u8"
  ]
}
```

**Url 物件**

```json
{
  "parse": 0,
  "url": {
    "values": [
      { "n": "主画质", "v": "https://example.com/video/main.m3u8" },
      { "n": "低画质", "v": "https://example.com/video/low.m3u8" }
    ],
    "position": 0
  }
}
```

`drm` 的子栏位请参考[共用物件字典](/config/common)；详情的播放分组见[集数格式](/spider/episodes)。
