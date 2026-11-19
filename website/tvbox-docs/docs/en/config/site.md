---
description: Site types, api prefixes, ext passing and display-related fields.
---

# Site

## Four types

| `type` | Meaning |
| --- | --- |
| `0` | XML over HTTP |
| `1` | JSON over HTTP |
| `3` | JAR / PY / JS |
| `4` | HTTP extension |

## Fields

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `key` | string | yes | — | Unique site identifier, never duplicated. |
| `name` | string | yes | — | Display name. |
| `type` | integer | no | `0` | 0=XML HTTP, 1=JSON HTTP, 3=Spider; 4=extended HTTP JSON, where category filters travel as Base64 `ext` and the site `ext` as `extend`. |
| `api` | string | yes | — | HTTP endpoint, `csp_` class, or a `./`／`../` relative `.js`／`.py` path; absolute URLs also work. |
| `ext` | string \| object \| array | no | — | For type 3 passed to `Spider.init`; HTTP sites receive it as `extend`. Objects and arrays are serialized first; the root configuration may use a URL or relative path. |
| `jar` | string | no | — | JAR location overriding the global `spider`. |
| `click` | string | no | — | JavaScript click handler run in the WebView. |
| `playUrl` | string | no | — | Parse URL prefix for type 0/1; supports `json:` endpoints or `parse:` parser names. For type 3/4 it comes from the playback `Result.playUrl`. |
| `hide` | integer | no | `0` | 1 hides the site from the site list. |
| `indexs` | integer | no | `0` | 1 marks the site as an index site. |
| `timeout` | integer | no | `15` | Playback timeout in seconds, minimum 1. |
| `searchable` | integer | no | `1` | 0=permanently disabled, 1=enabled; may also be saved as 2 at runtime. |
| `changeable` | integer | no | `1` | 0=locked, 1=switchable; may also be saved as 2 at runtime. |
| `lang` | string | no | — | Search keyword conversion language, supports Traditional/Simplified Chinese tags; other non-empty values keep the original text, an empty value uses the existing conversion. |
| `danmaku` | integer | no | `1` | 0 disables automatic search through the global danmaku API for this site; the spider's own danmaku is unaffected. |
| `quickSearch` | integer | no | `1` | 0 skips quick search, 1 enables it. |
| `categories` | `string[]` | no | — | When names match, keep only matching categories in list order; when nothing matches, keep all. |
| `header` | object | no | — | HTTP API request headers; also the default when the playback `Result.header` is empty. Type 3 requests made by the spider do not inherit it. |
| `style` | `Style` | no | — | Default card style for the site. |

## Type 3 site sample

`sites[] object fragment`

```json
{
  "key": "js_demo",
  "name": "QuickJS Demo",
  "type": 3,
  "api": "./demo.js",
  "ext": { "region": "tw" },
  "searchable": 1,
  "changeable": 1,
  "categories": ["Movies", "Series"],
  "header": { "User-Agent": "ExampleClient/1.0" },
  "style": { "type": "rect", "ratio": 1.33 }
}
```

To switch to Python just change `api` to `./demo.py`; for a Java class use `csp_Demo`. Relative paths also work for `ext` and `jar`.

Python, QuickJS and Node.js need the [modules](/spider/modules) installed first; confirm responses on the [spider test page](/spider/testing).

:::tip
`header` only affects HTTP requests issued by the App. Requests a type 3 spider makes itself must set headers in code or through `net` options.
:::
