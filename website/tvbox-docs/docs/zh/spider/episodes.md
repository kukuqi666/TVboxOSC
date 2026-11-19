---
description: vod_play_from 与 vod_play_url 的分隔符号，以及 playerContent 收到的 id。
---

# 播放分组与集数

三个分隔符号决定一切：

| 符号 | 作用 |
| --- | --- |
| `$$$` | 分隔播放分组 |
| `#` | 分隔同组集数 |
| `$` | 分隔集数名称与 id |

`list[]` 中的 Vod 栏位片段：

```json
{
  "vod_play_from": "主线$$$备用线路",
  "vod_play_url": "第 01 集$demo://ep/1#第 02 集$demo://ep/2$$$第 01 集$backup://ep/1"
}
```

上例展开后是两条线路：

| 分组 | 集数 |
| --- | --- |
| 主线 | 第 01 集 → `demo://ep/1`；第 02 集 → `demo://ep/2` |
| 备用线路 | 第 01 集 → `backup://ep/1` |

使用者点击某集后，`$` 右侧的 value 会成为 `playerContent(flag, id, …)` 的 `id`；`flag` 则是所属分组的名称。

## 建议做法

- 集数名称只放显示用文字，不要在 `$` 左右两侧放分隔符号。
- 播放 ID 尽量短且稳定，真正网址留到 `playerContent` 再取得。
- 分组数量要与 `vod_play_from` 的段数一致，缺一段会对应到错的分组。

```js
// QuickJS：组出两条线路
detail(id) {
  const from = ["主线", "备用线路"].join("$$$");
  const url = [
    ["第 01 集$demo://ep/1", "第 02 集$demo://ep/2"].join("#"),
    ["第 01 集$backup://ep/1"].join("#")
  ].join("$$$");
  return { list: [{ vod_id: id, vod_play_from: from, vod_play_url: url }] };
}
```

:::tip
播放结果的其他栏位（`header`、`subs`、`skips`）属于 `playerContent` 的回传，不是 Vod 栏位。见 [Result 回传格式](/spider/result)。
:::
