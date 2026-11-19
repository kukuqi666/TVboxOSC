---
description: The /action do commands: playback control, danmaku, refresh, push, casting and files.
---

# /action: Actions

Use GET or a form POST and select the operation with `do`.

## The do commands

| `do` | Parameters | Purpose |
| --- | --- | --- |
| `control` | `type` | Control the current player; commands in the table below. |
| `danmaku` | `text` | Send one live danmaku; requires a valid playback service and non-empty text. |
| `refresh` | `type`; plus `path` or `json` depending on type | Refresh a page, or push subtitle, danmaku and Vod updates. |
| `push` | `url` | Hand a non-empty URL to the App push flow. |
| `file` | `path` | Open a file on the device; this is not an upload. |
| `search` | `word` | Hand a non-empty keyword to the App search UI. |
| `setting` | `text`, `name` (optional) | Hand a configuration location or setting text to the settings flow; `text` cannot be empty. |
| `cast` | `config`, `device`, `history` | All three are JSON object strings handled by the App cast event. |
| `sync` | `type`, `mode`, `force` and others | Exchange history or favorites; see [device sync](/local/sync). |

## Playback control and live danmaku

| `type` | Purpose |
| --- | --- |
| `play` | Play |
| `pause` | Pause |
| `stop` | Stop |
| `prev` | Previous item |
| `next` | Next item |
| `repeat` | Toggle repeat mode |
| `replay` | Replay |

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=control" --data-urlencode "type=pause"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=danmaku" --data-urlencode "text=Sample danmaku"
```

## Refresh and push data

| `type` | Extra parameter | Purpose |
| --- | --- | --- |
| `live` | — | Live TV |
| `detail` | — | Details |
| `player` | — | Player |
| `category` | — | Categories |
| `subtitle` | `path` | Subtitle file location |
| `danmaku` | `path` | Danmaku file location |
| `vod` | `json` | JSON string of a Vod object |

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=refresh" --data-urlencode "type=category"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=refresh" --data-urlencode "type=subtitle" --data-urlencode "path=https://example.com/subtitle.srt"

curl "http://127.0.0.1:9978/action?do=refresh&type=vod" --data-urlencode 'json={"vod_id":"demo-1","vod_name":"Sample item"}'
```

## Play, search, settings and files

```sh
curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=push" --data-urlencode "url=https://example.com/video.m3u8"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=search" --data-urlencode "word=sample"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=setting" --data-urlencode "text=https://example.com/vod.json" --data-urlencode "name=Sample configuration"

curl -G "http://127.0.0.1:9978/action" --data-urlencode "do=file" --data-urlencode "path=/TV/subtitle.srt"
```

`do=file` dispatches by lowercase extension: `.apk` goes to the installer, `.srt`, `.ssa` and `.ass` go to the subtitle flow, and anything else goes to the settings flow. It uses device file locations and never uploads files from your computer.

## Casting existing history

`config` uses a Config record stored by the App, `device` comes from the target device's `/device`, and `history` reuses an existing History record. Replace the samples with real data, and never put a whole site configuration in `config`.

```sh
curl "http://127.0.0.1:9978/action?do=cast" --data-urlencode 'config={"type":0,"url":"https://example.com/vod.json","name":"Sample configuration"}' --data-urlencode 'device={"uuid":"demo-android-id","name":"Sample TV","ip":"http://192.0.2.10:9978","type":0}' --data-urlencode 'history={"key":"demo@@@demo-1@@@7","vodName":"Sample item","vodFlag":"Demo","vodRemarks":"Episode 1","episodeUrl":"demo://episode/1","position":120000}'
```

:::tip
`history.position` is in milliseconds, and `key` joins the site key, Vod ID and configuration ID. Copy these from an existing App record.
:::
