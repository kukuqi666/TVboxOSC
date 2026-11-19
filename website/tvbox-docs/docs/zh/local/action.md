---
description: /action 的 do 指令：播放控制、弹幕、刷新、推送、投放与档案。
---

# /action：动作指令

使用 GET 或表单 POST，以 `do` 选择操作。

## do 一览

| `do` | 参数 | 用途 |
| --- | --- | --- |
| `control` | `type` | 控制目前播放器；可用指令见下表。 |
| `danmaku` | `text` | 送出一条即时弹幕；需要有效的播放服务与非空文字。 |
| `refresh` | `type`；依类型加 `path` 或 `json` | 重新整理页面，或推送字幕、弹幕及 Vod 更新。 |
| `push` | `url` | 把非空 URL 交给 App 推送播放流程。 |
| `file` | `path` | 开启装置上的档案；不是上传档案。 |
| `search` | `word` | 把非空关键字交给 App 搜寻介面。 |
| `setting` | `text`、`name`（选填） | 把配置位置或设定文字交给设定流程；`text` 不可为空。 |
| `cast` | `config`、`device`、`history` | 三者均为 JSON 物件字串，交给 App 投放事件处理。 |
| `sync` | `type`、`mode`、`force` 等 | 交换观看记录或收藏；见[装置同步](/local/sync)。 |

## 播放控制与即时弹幕

| `type` | 用途 |
| --- | --- |
| `play` | 播放 |
| `pause` | 暂停 |
| `stop` | 停止 |
| `prev` | 上一项 |
| `next` | 下一项 |
| `repeat` | 切换循环模式 |
| `replay` | 重新播放 |

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=control" --data-urlencode "type=pause"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=danmaku" --data-urlencode "text=范例弹幕"
```

## 重新整理与推送资料

| `type` | 额外参数 | 用途 |
| --- | --- | --- |
| `live` | — | 直播 |
| `detail` | — | 详情 |
| `player` | — | 播放器 |
| `category` | — | 分类 |
| `subtitle` | `path` | 字幕档位置 |
| `danmaku` | `path` | 弹幕档位置 |
| `vod` | `json` | Vod 物件的 JSON 字串 |

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=refresh" --data-urlencode "type=category"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=refresh" --data-urlencode "type=subtitle" --data-urlencode "path=https://example.com/subtitle.srt"

curl "http://127.0.0.1:9978/action?do=refresh&type=vod" --data-urlencode 'json={"vod_id":"demo-1","vod_name":"范例项目"}'
```

## 播放、搜寻、配置与档案

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=push" --data-urlencode "url=https://example.com/video.m3u8"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=search" --data-urlencode "word=范例"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=setting" --data-urlencode "text=https://example.com/vod.json" --data-urlencode "name=示意配置"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=file" --data-urlencode "path=/TV/subtitle.srt"
```

`do=file` 依小写副档名分派：`.apk` 交给安装流程；`.srt`、`.ssa`、`.ass` 交给字幕流程；其他交给设定流程。它使用装置档案位置，不会把电脑上的档案上传。

## 投放既有观看纪录

`config` 使用 App 储存的 Config 记录，`device` 取自目标装置的 `/device`，`history` 沿用既有 History 记录。请将范例替换为实际资料，`config` 不可改填整份站点配置。

```sh
curl "http://127.0.0.1:9978/action?do=cast" --data-urlencode 'config={"type":0,"url":"https://example.com/vod.json","name":"示意配置"}' --data-urlencode 'device={"uuid":"demo-android-id","name":"示意电视","ip":"http://192.0.2.10:9978","type":0}' --data-urlencode 'history={"key":"demo@@@demo-1@@@7","vodName":"范例项目","vodFlag":"Demo","vodRemarks":"第 1 集","episodeUrl":"demo://episode/1","position":120000}'
```

:::tip
`history.position` 单位是毫秒；`key` 由站点 key、Vod ID 与配置 ID 组成，建议直接从 App 的既有记录复制。
:::
