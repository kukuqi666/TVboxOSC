---
description: Live.core initialization fields for special playback cores, including auth, so and option.
---

# Special Playback Cores

`Live.core` is used only when the matching playback core initializes; ordinary live configurations do not need it. The sample below shows its position inside a Live object; `option` names and values are defined by that core's implementation and are not generic configuration parameters.

## Fields

| Field | Type | Notes |
| --- | --- | --- |
| `auth` | string | Core authorization endpoint; when `resp` yields non-empty content the App switches to the local `/tvbus` endpoint. |
| `name` | string | Name passed to the core; may be a URL returning text. |
| `pass` | string | Password passed to the core; may be a URL returning text. |
| `broker` | string | Broker location passed to the core. |
| `domain` | string | Domain passed to the core; may be a URL returning text. |
| `resp` | string | Content the local `/tvbus` endpoint returns, or a URL returning text; non-empty content replaces the external `auth` location. |
| `sign` | string | Paired with `pkg` to create a runtime hook; may also be fetched from a URL. |
| `pkg` | string | Paired with `sign` to create a runtime hook; may also be fetched from a URL. |
| `so` | string | Location of the core's native library; the App downloads it locally before loading. |
| `key` | string | Identifier retained in the Core configuration; the current Core flow exposes no reader for it. |
| `option` | `Core.Option[]` | Extra options applied one by one before the core initializes. |
| `option[].key` | string | Option name. |
| `option[].values` | `string[]` | Values passed to that option. |

## Sample

`lives[] object fragment`

```json
{
  "name": "Special core sample",
  "url": "./live.m3u",
  "core": {
    "auth": "https://example.com/core/auth",
    "name": "demo",
    "pass": "demo-pass",
    "broker": "https://example.com/core/broker",
    "domain": "example.com",
    "so": "https://example.com/core/libcore.so",
    "option": []
  }
}
```

:::warning
The core's authorization and native library come from the source. Without the matching authorization, `auth` and `so` fail and the channel will not play.
:::

## Related

- The `/tvbus` endpoint: [local API · proxy and internal](/local/internal)
- Channel DRM: [shared objects](/config/common)

## Configuration resources and JAR dependencies

Local configurations and dependencies live in tvbox-config. The spider field supplies the default JAR; sites[].jar overrides it for one site. JS/Python sources reference scripts and rules through api/ext.

[File types and dependency index](https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/catalog.json) lists references, used_by, checksums, old path aliases and known upstream omissions. External JARs are listed separately; static validation does not establish their availability.
