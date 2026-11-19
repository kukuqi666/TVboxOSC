---
description: The vod_play_from and vod_play_url separators, and the id received by playerContent.
---

# Groups and Episodes

Three separators decide everything:

| Symbol | Meaning |
| --- | --- |
| `$$$` | Separates play groups |
| `#` | Separates episodes within a group |
| `$` | Separates the episode name from its id |

Vod fields inside `list[]`:

```json
{
  "vod_play_from": "Main$$$Backup",
  "vod_play_url": "Episode 01$demo://ep/1#Episode 02$demo://ep/2$$$Episode 01$backup://ep/1"
}
```

This expands to two lines:

| Group | Episodes |
| --- | --- |
| Main | Episode 01 → `demo://ep/1`; Episode 02 → `demo://ep/2` |
| Backup | Episode 01 → `backup://ep/1` |

When a user taps an episode, the value right of `$` becomes the `id` passed to `playerContent(flag, id, …)`; `flag` is the group name.

## Recommendations

- Keep episode names as display text only; never put separators around `$`.
- Keep playback IDs short and stable, and resolve the real URL inside `playerContent`.
- The number of groups must match the segments in `vod_play_from`; a missing segment shifts every later group.

```js
// QuickJS: build two lines
detail(id) {
  const from = ["Main", "Backup"].join("$$$");
  const url = [
    ["Episode 01$demo://ep/1", "Episode 02$demo://ep/2"].join("#"),
    ["Episode 01$backup://ep/1"].join("#")
  ].join("$$$");
  return { list: [{ vod_id: id, vod_play_from: from, vod_play_url: url }] };
}
```

:::tip
Other playback fields (`header`, `subs`, `skips`) belong to the `playerContent` response, not to Vod. See [Result format](/spider/result).
:::
