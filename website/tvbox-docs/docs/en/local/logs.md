---
description: View and export logs with /logs.html, plus /log/info fields and the privacy scope.
---

# Logs

Open `/logs.html` to view logs; capture runs for up to 60 minutes. **Stop capture** halts collection on the source device; **Pause display** only freezes the view.

**Download log** and **Share log** export the same file, containing version, device, playback summary, diagnostics and the raw log. If the browser cannot share files it falls back to a download, which you can share from your downloads; if reading information fails, press **Reload information** to keep the log.

The device list in the phone build offers **Actions** and **Logs** buttons; long-press a device name to rename it. Aliases live only on that phone, keyed by a stable device identifier so IP changes do not affect them; clearing the name restores the original.

## Log information API

```sh
curl "http://127.0.0.1:9978/log/info"
```

| Field | Purpose |
| --- | --- |
| `version` / `versionCode` / `variant` / `buildType` | App version and build information. |
| `device` / `android` / `sdk` / `abis` | Device and Android information. |
| `playback` | Playback state without `artwork`. |
| `playback.url` | The resolved full URL including the query; empty when there is no source. |
| `collection` | Log collection state including READ_LOGS permission, current UID／PID and other diagnostics. |
| `playbackLog` | Diagnostic sample of the current playback; still reported by actual state when no player exists. |
| `lastPlaybackError` | The most recently saved playback error, `null` when there is none. |

GET returns JSON; when the source is retained after stopping or failing, the URL is retained too. It never includes the artwork URL, UUID, serial number or MAC.

:::warning
Logs can contain full playback URLs and query parameters. Review them before sharing to avoid leaking signatures or authorization data.
:::

## Related

- The full playback URL is only available here; `/remote` does not include it: [web player and remote](/local/remote)
- Device info: [/device](/local/device)
