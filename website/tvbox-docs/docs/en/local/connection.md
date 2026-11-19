---
description: Service address, port range, how parameters are passed and response formats.
---

# Connection

Launch the App and use the service address shown on screen. The port starts at `9978` and is chosen from `9978` to `9998`, taking the first free port.

:::warning Connecting from another device
`127.0.0.1` refers to the device itself. From a computer or another phone, use the App device's LAN IP and port. The URLs and IPs in the examples are illustrative only.
:::

```sh
curl "http://127.0.0.1:9978/device"
```

Parameters normally go in the query string or an `application/x-www-form-urlencoded` form, not a JSON body. Encode Chinese text, URLs and JSON strings with `--data-urlencode`.

`/media`, `/device` and directory listings return JSON with a `text/plain` content type. Handle other responses according to their actual Content-Type.

:::danger OK does not mean the action ran
`/action` returning OK only means the request arrived; unknown commands, missing parameters or the absence of a playback service can all leave the action undone. The endpoints have no generic token check, so use them only on trusted LANs.
:::

## Endpoint map

| Endpoint | Purpose | Chapter |
| --- | --- | --- |
| `/action` | Actions, sync, casting | [Actions](/local/action), [Device sync](/local/sync) |
| `/remote` | Web playback control | [Web player and remote](/local/remote) |
| `/remote/key` | Remote key presses | [Web player and remote](/local/remote) |
| `/remote/subtitle`, `/remote/danmaku` | Apply subtitles and danmaku | [Web player and remote](/local/remote) |
| `/remote/info` | Connection status | [Web player and remote](/local/remote) |
| `/log/info` | Log information | [Logs](/local/logs) |
| `/media` | Playback state | [Playback state](/local/media) |
| `/device` | Device info | [Device info](/local/device) |
| `/file/**`, `/upload`, `/rename` … | File management | [File management](/local/files) |
| `/upload/start`, `/download/start` … | Resumable transfers | [Resumable transfers](/local/transfers) |
| `/cache` | Key-value cache | [Key-value cache](/local/cache) |
| `/proxy`, `/parse`, `/image`, `/tvbus` | Proxy and internal | [Proxy and internal](/local/internal) |

## Test tools

`curl` or any HTTP client works. In PowerShell `curl` is an alias for `Invoke-WebRequest`; use `curl.exe` when you need the real behavior.
