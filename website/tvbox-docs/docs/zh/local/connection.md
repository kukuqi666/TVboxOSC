---
description: 服务位址、埠号范围、参数传递方式与回应格式。
---

# 连线与回应

启动 App，使用画面显示的服务位址。埠号从 `9978` 到 `9998`，取第一个可用的埠。

:::warning 从其他装置连线
`127.0.0.1` 指目前装置。从电脑或另一台手机操作时，请换成 App 装置的区域网络 IP 与埠号。范例网址与 IP 仅供示意。
:::

```sh
curl "http://127.0.0.1:9978/device"
```

一般参数放在 Query String 或 `application/x-www-form-urlencoded` 表单，不使用整包 JSON body。中文、URL 与 JSON 字串以 `--data-urlencode` 编码。

`/media`、`/device` 与目录列表以 `text/plain` 回传 JSON。其他回应请依实际 Content-Type 处理。

:::danger OK 不等于动作已完成
`/action` 回传 OK 只表示已收到请求；未知指令、缺少参数或没有播放服务时，可能不执行。端点没有通用 token 验证，仅限可信任的区域网络使用。
:::

## 端点总览

| 端点 | 用途 | 章节 |
| --- | --- | --- |
| `/action` | 动作指令、同步、投放 | [动作指令](/local/action)、[装置同步](/local/sync) |
| `/remote` | 网页播放控制 | [网页播放与遥控器](/local/remote) |
| `/remote/key` | 遥控器按键 | [网页播放与遥控器](/local/remote) |
| `/remote/subtitle`、`/remote/danmaku` | 套用字幕与弹幕 | [网页播放与遥控器](/local/remote) |
| `/remote/info` | 连线状态 | [网页播放与遥控器](/local/remote) |
| `/log/info` | 日志资讯 | [日志下载与分享](/local/logs) |
| `/media` | 播放状态 | [播放状态](/local/media) |
| `/device` | 装置资讯 | [装置资讯](/local/device) |
| `/file/**`、`/upload`、`/rename` … | 档案管理 | [档案与目录管理](/local/files) |
| `/upload/start`、`/download/start` … | 续传与装置下载 | [续传与装置下载](/local/transfers) |
| `/cache` | 键值快取 | [键值快取](/local/cache) |
| `/proxy`、`/parse`、`/image`、`/tvbus` | 代理与内部端点 | [代理与内部端点](/local/internal) |

## 测试工具

用 `curl` 或任何 HTTP 客户端都可以。PowerShell 的 `curl` 是 `Invoke-WebRequest` 的别名，需要完整行为时请用 `curl.exe`。
