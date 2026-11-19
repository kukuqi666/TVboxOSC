---
description: Live.core 的特殊播放核心初始化栏位，包含 auth、so、option 与 refs。
---

# 特殊播放核心

`Live.core` 只在对应播放核心初始化时使用；一般直播配置不需要填写。以下示范它在 Live 物件中的位置；`option` 的名称与值由该核心实作定义，不是通用配置参数。

## 栏位

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `auth` | string | 核心授权端点；`resp` 取得非空内容时，App 会改用本机 `/tvbus` 端点。 |
| `name` | string | 传给播放核心的名称；值也可以是回传文字的网址。 |
| `pass` | string | 传给播放核心的密码；值也可以是回传文字的网址。 |
| `broker` | string | 传给播放核心的 broker 位置。 |
| `domain` | string | 传给播放核心的 domain；值也可以是回传文字的网址。 |
| `resp` | string | 本机 `/tvbus` 端点要回传的内容，也可填回传文字的网址；取得非空内容时取代 `auth` 的对外位置。 |
| `sign` | string | 与 `pkg` 一起建立执行期 Hook；值也可以由网址取得。 |
| `pkg` | string | 与 `sign` 一起建立执行期 Hook；值也可以由网址取得。 |
| `so` | string | 核心原生函式库位置；App 会下载到本机后载入。 |
| `key` | string | 目前保留于 Core 配置中的识别值；现行 Core 流程没有公开读取方法。 |
| `option` | `Core.Option[]` | 初始化核心前逐笔套用的额外选项。 |
| `option[].key` | string | 选项名称。 |
| `option[].values` | `string[]` | 传给该选项的值清单。 |

## 范例

`lives[] 物件片段`

```json
{
  "name": "特殊核心示意",
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
特殊核心的授权与函式库由来源提供。未取得对应授权前，`auth` 与 `so` 会失败，频道无法播放。
:::

## 相关

- `/tvbus` 端点：[本地 API · 代理与内部端点](/local/internal)
- 频道 DRM：[共用物件](/config/common)

## 配置资源与 JAR 依赖

所有本地配置与依赖统一在 tvbox-config。spider 是默认 JAR，sites[].jar 覆盖该站点的默认值；JS/Python 通过 api/ext 引用脚本和规则。

[文件类别与双向依赖索引](https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/catalog.json) 包含 `references`、`used_by`、校验值、旧路径映射与已知上游缺失。外部 JAR 单独列出，静态索引不证明外部服务在线。
