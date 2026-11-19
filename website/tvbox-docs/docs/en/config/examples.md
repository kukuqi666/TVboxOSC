---
description: Complete VOD and live TV configuration samples, plus path and reference rules.
---

# Full Examples

Pick a VOD or live TV sample and replace names and URLs with your own. Samples marked "object fragment" belong inside the field named in their heading.

:::warning Sample paths
`./` and `../` in the root configuration resolve from the configuration file location; use absolute URLs inside external arrays and live lists. `example.com` and the test IPs are illustrative only.
:::

## VOD configuration: Java, Python, QuickJS

`vod.json`

```json
{
  "spider": "./spider.jar",
  "sites": [
    {
      "key": "java_demo",
      "name": "Java Demo",
      "type": 3,
      "api": "csp_Demo"
    },
    {
      "key": "py_demo",
      "name": "Python Demo",
      "type": 3,
      "api": "./demo.py"
    },
    {
      "key": "js_demo",
      "name": "QuickJS Demo",
      "type": 3,
      "api": "./demo.js",
      "ext": { "region": "tw" }
    }
  ],
  "parses": [
    {
      "name": "JSON parser sample",
      "type": 1,
      "url": "https://example.com/parse?url=",
      "ext": {
        "flag": ["demo"],
        "header": { "User-Agent": "ExampleClient/1.0" }
      }
    }
  ]
}
```

Java uses a `csp_` class name; Python and QuickJS files use a `./` relative path.

## Live configuration: external M3U

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

`url` can also point to `./live.txt` or `./live.json`; LiveParser decides the format.

## Where to go next

| I want to | Read |
| --- | --- |
| Understand top-level fields and depots | [Vod top level](/config/vod) |
| Configure a single site | [Site](/config/site) |
| Handle URLs that need parsing | [Parse](/config/parse) |
| Add live TV | [Live](/config/live) |
| See live list formats | [Live formats](/config/formats) |
| Shared objects and DRM | [Shared objects](/config/common) |
