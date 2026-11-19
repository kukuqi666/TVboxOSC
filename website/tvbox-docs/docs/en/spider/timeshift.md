---
description: Add timeshift to a playback result or Channel to expose a draggable live timeline.
---

# Live Timeshift

When the source can serve content by time, add `timeshift` to the playback response or to a live `Channel`. The App then shows a draggable timeline with live at the far right.

`playerContent`

```json
{
  "parse": 0,
  "url": "https://example.com/live.flv",
  "timeshift": {
    "url": "https://example.com/replay?time={time}",
    "start": 1792700000000,
    "window": 7200000
  }
}
```

`start` is the Unix time in milliseconds when this broadcast began and `window` is the maximum catch-up length. The timeline accumulates from the start and never exceeds `window`; the usable range depends on the source.

## Request flow

1. The App replaces `{time}` with the target Unix milliseconds and issues a GET.
2. `time=0` requests the live source again.
3. The endpoint returns a directly playable `url`, optionally with `header` and `format`.

When using a local proxy, build the URL with `getProxyUrl`.

## Behaviour

- Consecutive drags or channel changes cancel the previous request; on failure the App shows a message and keeps the current playback.
- Live danmaku from spiders is paused while catch-up is active and resumes at the live edge.
- Choosing catch-up from the EPG uses [`catchup`](/config/common) instead, scoped to that program.

:::tip
`Channel.timeshift` uses the same format as `Result.timeshift`; putting it in the live configuration applies it to every playback of that channel.
:::

Related: [Group / Channel fields](/config/channel), [web remote seek](/local/remote).
