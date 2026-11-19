---
description: Parser types, url prefixes and how ext.flag／ext.header matching behaves.
---

# Parse

## Types

| `type` | Meaning |
| --- | --- |
| `0` | WebView |
| `1` | JSON API |
| `2` | JAR JSON |
| `3` | JAR hybrid |
| `4` | Parallel attempt |

## Fields

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `name` | string | yes | — | Parser display name, also the key for selecting it by name. |
| `type` | integer | no | `0` | 0=WebView, 1=JSON API, 2=JAR JSON, 3=JAR hybrid, 4=parallel attempt. |
| `url` | string | no | — | Parsing API endpoint or prefix. |
| `ext.flag` | `string[]` | no | — | For type 4 parallel parsing, prefer parsers of the same type whose flags match; with no match, fall back to all parsers of that type. |
| `ext.header` | object | no | — | Added to parsing requests; also the default playback headers when the JSON result provides none. |

## WebView and JSON API sample

`parses array`

```json
[
  {
    "name": "WebView parser sample",
    "type": 0,
    "url": "https://example.com/web?url="
  },
  {
    "name": "JSON API parser sample",
    "type": 1,
    "url": "https://example.com/api?url=",
    "ext": {
      "flag": ["demo"],
      "header": { "Referer": "https://example.com/" }
    }
  }
]
```

The value of `parses` is an array; `ext.flag` is a priority matcher for type 4 parallel parsing, not a global allow list. `ext.header` can also become the default playback headers for parsing results, as described above.

## When parsing happens

TVboxOSC first tries to play the `url` returned by the spider. Parsing is entered only when needed:

- `Result.parse = 1` or `Result.jx = 1`.
- `Channel.parse = 1` (live TV).
- A configuration `flags` value matches `ext.flag`.

See `parse`, `jx` and `jxFrom` in the [Result format](/spider/result).
