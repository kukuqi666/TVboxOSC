---
description: Top-level VodConfig fields: sites, parsing, live TV, DNS, proxies, rules and headers.
---

# Vod Top Level

`headers`, `proxy`, `rules` and `doh` accept object arrays, URLs of external JSON arrays, or a mix of both.

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `urls` | `Depot[]` | no | Configuration depot; every entry is registered and the first is loaded, the list cannot be empty. |
| `msg` | string | no | Error message from the configuration; presence stops loading, for plain configurations and depots alike. |
| `spider` | string | no | Global Spider JAR path or URL; used when a Site has no `jar`. |
| `wallpaper` | string | no | Wallpaper image, GIF or video location. |
| `logo` | string | no | App logo image location. |
| `notice` | string | no | Notice text shown after launch. |
| `sites` | `Site[]` | yes | VOD site list. |
| `parses` | `Parse[]` | no | Parsing rules used when a URL needs further handling. |
| `lives` | `Live[]` | no | Inline live configurations; a separate LiveConfig can also be loaded. |
| `doh` | `Doh[]` | no | DNS over HTTPS endpoints. |
| `proxy` | `Proxy[]` | no | Proxies applied by host. |
| `rules` | `Rule[]` | no | WebView network capture and script rules. |
| `headers` | `Header[]` | no | Inject or override HTTP request headers by host. |
| `hosts` | `string[]` | no | DNS overrides in `original=target host or IP` form. |
| `flags` | `string[]` | no | Global platform flags passed to `playerContent`. |
| `ads` | `string[]` | no | Host names to intercept. |
| `danmaku` | string | no | Danmaku search API; supports `{name}` and `{episode}`. |
| `assrt` | string | no | Access string for the subtitle search service. |

## Configuration depot

`depot.json`

```json
{
  "urls": [
    { "name": "Local configuration", "url": "./vod.json" },
    { "name": "Remote configuration", "url": "https://example.com/vod.json" }
  ]
}
```

When `urls` appears at the top level, the App registers the list and loads the first configuration; the list cannot be empty and each entry has `name` and `url`.

## Fields that accept external arrays

`VodConfig top-level fragment`

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
      "name": "Media rule sample",
      "hosts": ["player.example.com"],
      "regex": [".*m3u8.*"],
      "exclude": [".*preview.*"]
    }
  ]
}
```

A whole field can be a single location, or an array mixing external JSON locations with inline objects. The external file itself must return a JSON array.

:::tip
The smallest usable configuration only needs `sites`. Every other field can be omitted until you need it.
:::
