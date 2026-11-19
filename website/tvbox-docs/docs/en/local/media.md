---
description: /media returns the current playback state with a text/plain content type.
---

# /media: Playback State

```sh
curl "http://127.0.0.1:9978/media"
```

With no playback service, a released player or a failed read, the response is `{}`. When data exists, it returns the structure below; the Content-Type is `text/plain`, so clients must parse the JSON themselves.

`/media response`

```json
{
  "state": 3,
  "speed": 1,
  "duration": 7200000,
  "position": 120000,
  "url": "https://example.com/video.m3u8",
  "title": "Sample item",
  "artist": "Demo",
  "artwork": "https://example.com/poster.jpg"
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `state` | integer | 3 playing; 6 buffering; 2 ready but not playing; any other state reports 1. |
| `speed` | number | Current speed; 1 is normal. |
| `duration` | integer | Duration reported by the player in milliseconds; unknown durations may be negative and are not guaranteed to be -1. |
| `position` | integer | Current position in milliseconds. |
| `url` | string | Current playback URL; empty when there is none. |
| `title` | string | Media title; empty when there is none. |
| `artist` | string | The media `artist` metadata; empty when there is none. |
| `artwork` | string | Artwork URI; empty when there is none. |

:::tip
For richer control (`canSeek`, `canSetSpeed`, `mediaKey`) use [/remote](/local/remote). `/media` only returns a snapshot of playback state.
:::

## Related

- The full form of the playback URL: [log information API](/local/logs)
- Remote commands: [/remote](/local/remote#remote-query-and-control)
