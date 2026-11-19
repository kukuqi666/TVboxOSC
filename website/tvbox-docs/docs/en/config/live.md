---
description: LiveConfig and lives[] source fields, plus how to choose between inline and external lists.
---

# Live Sources

A standalone LiveConfig uses `lives` at the top level and supports `spider`, `headers`, `proxy`, `rules`, `hosts`, `ads`, `urls` and `msg`; it does not read a separate `doh`. The table below covers the Live source objects inside `lives[]`.

`url` and `groups` are usually mutually exclusive; when `groups` is empty but `api` is set, the App first calls the spider's `liveContent(url)` for text and lets LiveParser decide the format.

:::warning
Saved `boot`, `pass` and `keep` values for a configuration with the same name may override configuration values; check the saved settings in the App as well.
:::

## Fields

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `name` | string | yes | — | Unique live configuration name. |
| `url` | string | no | — | External TXT, M3U or JSON list location; mutually exclusive with `groups`. |
| `api` | string | no | — | When `groups` is empty, use this spider's `liveContent(url)` to obtain the external list text. |
| `ext` | string \| object \| array | no | — | Initialization data for the live spider; objects and arrays are serialized first. |
| `jar` | string | no | — | JAR dedicated to this live configuration. |
| `click` | string | no | — | Default JavaScript click handler run in the WebView. |
| `logo` | string | no | — | Default logo template; supports `{id}`, `{name}`, `{logo}`. |
| `epg` | string | no | — | EPG location, comma separated; supports XMLTV and API templates. |
| `ua` | string | no | — | Default User-Agent. |
| `origin` | string | no | — | Default Origin header. |
| `referer` | string | no | — | Default Referer header. |
| `timeZone` | string | no | system zone | EPG time zone, for example `Asia/Taipei`. |
| `keep` | string | no | — | Saved channel positioning; usually maintained by the App. |
| `timeout` | integer | no | `15` | Playback timeout in seconds, minimum 1. |
| `header` | object | no | — | Additional HTTP headers for live requests. |
| `catchup` | `Catchup` | no | — | Default catch-up settings for channels from external lists; inline `groups` must set it per channel. |
| `core` | `Core` | no | — | Advanced initialization for special playback cores. |
| `groups` | `Group[]` | no | — | Inline channel groups. |
| `boot` | boolean | no | `false` | When the selected live configuration is true, entering live TV automatically after loading. |
| `pass` | boolean | no | `false` | When true, do not parse the `_suffix` of TXT／M3U group names as a password; explicit `Group.pass` in JSON is unaffected. |

## Loading an external list

`live-config.json`

```json
{
  "lives": [
    {
      "name": "M3U sample",
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

This is a complete LiveConfig with a `lives` array; `url` points at an M3U, TXT or JSON file you can maintain separately.

## Inline Group / Channel

`live-config.json`

```json
{
  "lives": [
    {
      "name": "Inline JSON sample",
      "groups": [
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

`$main` and `$backup` are line labels; a channel may expose several locations. Inline `groups` skip the automatic numbering and default inheritance of external lists, so set the fields a channel needs explicitly.

## Comparing the two approaches

| Item | External `url` | Inline `groups` |
| --- | --- | --- |
| Maintenance | List file maintained separately | Same file as the configuration |
| Default inheritance | Inherits `logo`, `ua`, `catchup` from Live | Must be set on each Channel |
| Automatic numbering | Filled in | Not applied |
| Formats | TXT, M3U, JSON | JSON |

See [the three live list formats](/config/formats) for format details and [Group / Channel](/config/channel) for channel fields.
