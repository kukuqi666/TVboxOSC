---
description: Fields for the shared DoH, Proxy, Rule, Header, Catchup, Drm and Style objects.
---

# Shared Objects

## Doh

| Field | Type | Notes |
| --- | --- | --- |
| `Doh.name` | string | DoH display name. |
| `Doh.url` | string | DoH query endpoint. |
| `Doh.ips` | `string[]` | Bootstrap IPs. |

## Proxy

| Field | Type | Notes |
| --- | --- | --- |
| `Proxy.name` | string | Proxy rule name. |
| `Proxy.hosts` | `string[]` | Hosts the rule applies to, rule matching supported. |
| `Proxy.urls` | `string[]` | Proxy locations including host and port, such as `http://127.0.0.1:8080` or `socks5://127.0.0.1:1080`; the `http` or `socks` prefix selects the Java proxy type. |

## Rule

| Field | Type | Notes |
| --- | --- | --- |
| `Rule.name` | string | Rule display name. |
| `Rule.hosts` | `string[]` | Trigger hosts. |
| `Rule.regex` | `string[]` | Conditions that match valid media locations. |
| `Rule.click` | `string[]` | CSS selectors to click in the WebView; configured separately from the JavaScript `script`. |
| `Rule.script` | `string[]` | Scripts run in the WebView. |
| `Rule.exclude` | `string[]` | Conditions that must not be captured. |

## Header

| Field | Type | Notes |
| --- | --- | --- |
| `Header.host` | string | Host to handle. |
| `Header.header` | object | HTTP request headers to inject or override. |

## Catch-up (Catchup)

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `Catchup.type` | string | empty; behaves as append | `default` replaces the location entirely; other values append to the original location. |
| `Catchup.days` | string | — | Reserved; no catch-up window is currently enforced. |
| `Catchup.regex` | string | — | String or rule deciding whether the settings apply. |
| `Catchup.source` | string | — | Catch-up URL template; must be non-empty and may contain start and end time tokens. |
| `Catchup.replace` | string | — | In append mode `regex,replacement`; not applied in `default` mode. |

```json
{
  "name": "Catch-up sample",
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

`${(b)yyyyMMddHHmmss}` and `${(e)yyyyMMddHHmmss}` are replaced by the program's start and end time, formatted in the device time zone rather than `Live.timeZone`.

`catchup` applies when choosing catch-up from the EPG, scoped to that program. If the source can serve content by time, provide [`timeshift`](/spider/timeshift) on the channel for a draggable live timeline.

## Drm

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `Drm.type` | string | — | Media DRM type such as `widevine`, `playready`, `clearkey`. |
| `Drm.key` | string | — | DRM license location or key data accepted by that type. |
| `Drm.header` | object \| string | — | DRM request headers, as an object or a JSON object string. |
| `Drm.forceKey` | boolean | `false` | Force the given license location instead of the one inside the media; maps to `forceDefaultLicenseUri`. |

## Style

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `Style.type` | string | `rect` | `rect`, `oval` or `list`. |
| `Style.ratio` | float | depends on type | Width／height; positive values up to 4. Unset or ≤ 0 gives 1 for `oval` and 0.75 otherwise. |

## DoH, proxy, hosts and ads

`VodConfig top-level fragment`

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
      "name": "Local proxy sample",
      "hosts": ["media.example.com"],
      "urls": ["http://127.0.0.1:8080", "socks5://127.0.0.1:1080"]
    }
  ],
  "hosts": ["media.example.com=203.0.113.10"],
  "ads": ["ads.example.com"]
}
```

Proxy locations must include scheme, host and port; `hosts` uses `original host=target host or IP`.

:::tip
`headers`, `proxy`, `rules` and `doh` all accept external JSON array URLs. See [Vod top level](/config/vod).
:::
