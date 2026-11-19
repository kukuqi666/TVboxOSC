---
description: 点播与直播配置的完整范例，以及路径与引用规则。
---

# 完整范例

选择点播或直播范例，换成自己的名称与网址。标示为「物件片段」的范例，需放入对应栏位。

:::warning 范例路径
主配置的 `./` 与 `../` 以配置档位置为基准；外部阵列与直播列表内请用完整网址。`example.com` 与测试 IP 仅供示意。
:::

## 点播配置：Java、Python、QuickJS

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
      "name": "JSON 解析范例",
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

Java 使用 `csp_` 类别名称；Python 与 QuickJS 档案使用 `./` 相对路径。

## 直播配置：外部 M3U

`live-config.json`

```json
{
  "lives": [
    {
      "name": "M3U 范例",
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

`url` 也可改成 `./live.txt` 或 `./live.json`；内容格式会由 LiveParser 判断。

## 选择下一步

| 你要做的事 | 章节 |
| --- | --- |
| 了解顶层栏位与仓库 | [Vod 顶层](/config/vod) |
| 设定单个站点 | [Site](/config/site) |
| 处理需要解析的网址 | [Parse](/config/parse) |
| 加入直播 | [Live](/config/live) |
| 看直播列表格式 | [直播格式](/config/formats) |
| 共用物件与 DRM | [共用物件](/config/common) |
