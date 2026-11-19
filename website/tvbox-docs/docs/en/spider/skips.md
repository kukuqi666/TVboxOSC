---
description: Provide opening, middle and ending skip ranges through skips in playerContent.
---

# Skip Segments

Add `skips` at the top level of each episode's `playerContent` response:

```json
{
  "parse": 0,
  "url": "https://example.com/video.m3u8",
  "skips": [
    {"type": "opening", "start": 0, "end": 90000},
    {"type": "middle", "start": 600000, "end": 630000},
    {"type": "ending", "start": 1200000}
  ]
}
```

`opening` is the title sequence, `middle` a mid-roll and `ending` the credits. Times are milliseconds; `start` is where skipping begins and `end` where it jumps to. The example above jumps from 10:00 to 10:30.

## Rules

| Type | `end` | Notes |
| --- | --- | --- |
| `opening` | required | Must be later than `start`. |
| `middle` | required | Must be later than `start`; multiple ranges allowed. |
| `ending` | may be omitted when `start > 0` | Omitting it means "next episode"; without a next episode it jumps to the end. |

- Manually configured opening or ending skips take precedence; middle skips are unaffected.
- Malformed entries are ignored and do not affect the rest.

```js
// QuickJS: derive skip ranges from the duration
play(flag, id) {
  return {
    parse: 0,
    url: this.resolve(id),
    skips: [
      { type: "opening", start: 0, end: 90000 },
      { type: "ending", start: this.duration - 120000 }
    ]
  };
}
```

Related: [Result format](/spider/result), `SkipSegment` in [data objects](/spider/objects).
