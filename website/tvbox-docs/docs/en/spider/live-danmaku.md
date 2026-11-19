---
description: Provide live danmaku with danmaku(id, emit), plus the emit danmaku and subscription-state formats.
---

# Live Danmaku

Playback responses in Java／Python／QuickJS keep using `danmaku: [{name, url}]`. For a live source, build the URL with `getDanmakuUrl(id)` (`self.getDanmakuUrl(id)` in Python); the App then calls the site's `danmaku(id, emit)`. HTTP(S) sources are still loaded as danmaku files.

```js
// QuickJS: use a live source in the playback result
play(flag, id) {
  return {
    parse: 0,
    url: "https://example.com/video.m3u8",
    danmaku: [{ name: "Live danmaku", url: getDanmakuUrl(id) }]
  };
}
```

## Danmaku item

`emit(item)`

```json
{
  "text": "Live danmaku",
  "color": 6737151,
  "size": 25,
  "type": 1,
  "colors": [6737151, 13408767]
}
```

| Field | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `text` | string | yes | — | Decoded text; not all whitespace, at most 4096 characters. |
| `color` | integer | no | `16777215` (white) | RGB color, 0–16777215. |
| `size` | number | no | `0` | Text size in sp, 0–96; 0 follows the user setting. |
| `type` | integer | no | `1` | 1 scrolling, 4 bottom, 5 top, 6 reverse. |
| `colors` | integer[] | no | — | 2–8 RGB colors forming a gradient; replaces `color` when set. |

## Subscription state

State is also sent with `emit` and must not include `text`. `state` is `ready` (subscription available), `retrying` (recovering) or `error` (fatal); `msg` is optional, at most 512 characters, with no cookies, tokens or signed URLs. `ready` must mean the platform subscription works — never infer it from a WebSocket `open` alone.

`emit(item)`

```json
{
  "state": "retrying",
  "msg": "Connection lost, waiting to recover"
}
```

The App sets `connecting` itself and coalesces identical `state`／`msg`; initialization errors become `error`. After `error` the subscription stops and later callbacks are ignored; a deliberate stop clears the state and needs no `closed` report. States go to the log and are never shown as danmaku or repeated toasts.

## Stopping

`emit` accepts a Java `JSONObject`, a Python dict or a QuickJS object; Java calls `emit.accept(item)` and returns an `AutoCloseable`, while Python／QuickJS return a stop function. When the channel changes or playback closes, the App calls the stop function and the spider must cancel its own connection and heartbeat.

```py
# Python: return a stop function
def danmaku(self, id, emit):
    ws = self.net.connect(self.danmaku_url(id), {}, lambda socket, event: emit(decode(event)))

    def stop():
        ws.cancel()

    return stop
```

The spider owns the protocol, decoding and style conversion; the App owns display limits, so surplus or expired messages may be dropped. Full samples are in the CatVodSpider three-language demo.
