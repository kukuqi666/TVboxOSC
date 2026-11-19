---
description: The web player, standalone remote, subtitle and danmaku application, plus /remote responses and error codes.
---

# Web Player and Remote

Open the App's service URL in a browser. The **Play** tab shows artwork, title and progress, with controls for playback, episode or channel switching, single-item repeat, seeking and speed. The **Search** tab opens by default, and search and URL push keep their own input and browser history.

Drag the progress slider to preview a time and release to seek; arrow keys step five seconds. Live TV hides progress, repeat and speed controls, and the slider is disabled for media that cannot seek. The page offers 0.5×, 1.0×, 2.0× and 3.0× buttons.

## Tab URLs

Semantic tab parameters are used; old numeric tabs no longer map to their former pages and unknown values fall back to search. The files tab accepts an extra `path`, for example `/?tab=files&path=/TV`.

| URL | Purpose |
| --- | --- |
| `/?tab=search` | Search; the default when `tab` is omitted. |
| `/?tab=push` | URL push. |
| `/?tab=play` | Playback control. |
| `/?tab=subtitles` | Subtitle upload and application. |
| `/?tab=danmaku` | Live danmaku and danmaku sources. |
| `/?tab=settings` | Configuration, device actions, logs and spider test entry. |
| `/?tab=files` | Device files and transfers. |

## /remote: query and control

GET returns state; POST sends a command in the form field `type`. Success returns JSON (`application/json`) with no caching; with no local playback it returns `{"active":false}`. The response never includes the playback URL — the log information API provides the full URL. The page polls about once per second only while the play tab is in the foreground and refreshes immediately when you return to it.

```sh
curl "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=repeat_on" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=repeat_off" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=seek" --data-urlencode "position=120000" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=speed" --data-urlencode "speed=1.5" "http://127.0.0.1:9978/remote"
```

### Commands

| `type` | Purpose |
| --- | --- |
| `play` | Play; restarts from the beginning when finished. |
| `pause` | Pause the current video. |
| `stop` | Stop playback. |
| `prev` | Previous episode／channel; requires item navigation support. |
| `next` | Next episode／channel; requires item navigation support. |
| `repeat_on` | Enable single-item repeat for the current video; repeating the request stays on. |
| `repeat_off` | Disable single-item repeat; repeating the request stays off. |
| `seek` | Jump to `position` in milliseconds; requires seek support and does not change play/pause state. |
| `speed` | Set 0.1–5.0× with `speed`; requires speed support, unavailable for live TV. |

### Response fields

| Field | Type | Notes |
| --- | --- | --- |
| `active` | boolean | Whether local playback can be controlled; other fields are omitted when false. |
| `title` | string | Display title of the current video. |
| `mediaKey` | string | Identifier of the current source; pass it as `key` when applying subtitles or danmaku, and refetch it after switching videos. |
| `artwork` | string | Local artwork endpoint for the current video; empty when there is none, and the page shows a placeholder if loading fails. Only `/remote` returns this field. |
| `position` | integer | Current position in milliseconds, minimum 0. |
| `duration` | integer | Total duration in milliseconds; 0 when unknown. |
| `state` | integer | 1 ended; 2 ready but not playing; 3 playing; 6 buffering. |
| `playWhenReady` | boolean | Whether the player is set to play; false once the video has ended. |
| `repeatOne` | boolean | Whether single-item repeat is on, replaying the same video when it ends. |
| `live` | boolean | Whether this is live TV; the page hides the repeat toggle and the API rejects repeat commands. |
| `canNavigate` | boolean | Whether previous／next are supported. |
| `canSeek` | boolean | Whether web seeking is supported; also true for live TV with `timeshift`, false when the duration is unknown or seeking is unsupported. |
| `canSetSpeed` | boolean | Whether speed is supported; false for live TV or unsupported players. |
| `speed` | number | Current playback speed, for example 1 or 1.5. |
| `engine` | string | Current engine, `ExoPlayer` or `mpv`. |
| `video` | string | Video size text reported by the player. |

## Artwork

`artwork` points at `/remote/artwork?key=…` on the same service and returns PNG on success; the browser sends no source URL, Referer or Cookie. The `key` must match the current artwork; after the artwork changes or playback stops, old keys return 404. A missing or malformed key is 400, a load timeout is 503, and a load or encoding failure is 500.

## Other behavior

- The existing `/action?do=control&type=repeat` still toggles repeat.
- Previous, next and stop may be dispatched to the playback UI first; confirm the result with a follow-up GET.
- `seek` takes a non-negative integer `position` in milliseconds and clamps to the end when it exceeds the duration; missing, malformed or 64-bit-overflowing values return 400, and 409 when `canSeek` is false. Sources with [live timeshift](/spider/timeshift) can seek too, relative to the start of the current catch-up window.
- `speed` must be a finite value between 0.1 and 5.0, otherwise 400; 409 when `canSetSpeed` is false.

## Error codes

| Status | Situation |
| --- | --- |
| 400 | Missing or unknown command; invalid `seek` / `speed` parameters. |
| 403 | Cross-origin browser control request. |
| 405 | Unsupported HTTP method. |
| 409 | No controllable playback, live repeat, or unsupported previous／next. |
| 503 | Main thread busy or the request was interrupted. |

## /remote/key: standalone remote

Open `/remote.html` from the top-right corner of the App page to use the remote. Returning keeps the previous tab and file directory.

```sh
curl --data-urlencode "key=ok" "http://127.0.0.1:9978/remote/key"
```

| POST form field | Notes |
| --- | --- |
| `key` | `up`／`down`／`left`／`right` (directions), `ok` (confirm), `back`, `menu`, or `0`–`9`. |
| `action` | `press` for a tap (default). A long press sends `down`, then `repeat`, then `up`; use `cancel` to cancel. |
| `id` | Required for non-`press` actions, 1–64 alphanumerics or `-`. The same long press reuses the same `key` and `id`. |

A tap or the start of a long press needs a foreground page or a focused dialog, otherwise 409. Success returns `{"accepted":true,"holding":false}`; `holding: true` means the key is still down. A long press releases automatically after 5 seconds without another `repeat`; still send `up` or `cancel` to finish normally.

## Applying subtitles and danmaku

First GET `/remote` to obtain the current `mediaKey`, then POST the form fields `key` and `path`. Subtitles must first be uploaded to shared device storage and may be SRT／SSA／ASS; danmaku may reuse existing files in the same scope or an HTTP(S) URL.

```sh
# Replace MEDIA_KEY with the mediaKey you just fetched
curl --data-urlencode "key=MEDIA_KEY" --data-urlencode "path=/TV/subtitle.srt" "http://127.0.0.1:9978/remote/subtitle"
curl --data-urlencode "key=MEDIA_KEY" --data-urlencode "path=https://example.com/danmaku.xml" "http://127.0.0.1:9978/remote/danmaku"
```

With no controllable local playback, live TV, or after switching to another source, the response is 409; a malformed `key`, invalid file or out-of-scope path returns 400. These POSTs, playback control and the key API all check the browser Origin, and cross-origin requests return 403.

## /remote/info: connection status and device info

Tap the connection status on the home page to see the App version, variant, buildType, device name, Android version, SDK and ABI. The data comes from GET `/remote/info`, returned as JSON with no caching; it is separate from `/device`, which serves discovery and sync. The page keeps the last successful read while offline.
