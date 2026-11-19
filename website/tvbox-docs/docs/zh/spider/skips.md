---
description: 用 playerContent 的 skips 提供片头、片中与片尾跳过区间。
---

# 片段跳过

在每集的 `playerContent` 回应顶层加入 `skips`：

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

`opening` 为片头、`middle` 为片中、`ending` 为片尾。时间单位是毫秒；`start` 是开始跳过的时间，`end` 是要跳到的时间。上例片中会从 10:00 跳到 10:30。

## 规则

| 类型 | `end` | 说明 |
| --- | --- | --- |
| `opening` | 必填 | 须晚于 `start`。 |
| `middle` | 必填 | 须晚于 `start`；可提供多段。 |
| `ending` | 可在 `start > 0` 时省略 | 省略表示换下一集，没有下一集则跳到片尾。 |

- 使用者手动设定的片头或片尾会优先套用；片中跳过不受影响。
- 格式错误的项目会被忽略，不影响其余项目。

```js
// QuickJS：依片长推算跳过区间
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

相关：[Result 回传格式](/spider/result)、[数据物件](/spider/objects)的 `SkipSegment`。
