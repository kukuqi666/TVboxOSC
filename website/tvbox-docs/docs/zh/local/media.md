---
description: /media 回传目前播放状态，Content-Type 为 text/plain。
---

# /media：播放状态

```sh
curl "http://127.0.0.1:9978/media"
```

没有播放服务、播放器已释放，或读取失败时回传 `{}`。有资料时回传以下结构；Content-Type 是 `text/plain`，客户端需自行解析 JSON。

`/media 回应`

```json
{
  "state": 3,
  "speed": 1,
  "duration": 7200000,
  "position": 120000,
  "url": "https://example.com/video.m3u8",
  "title": "范例项目",
  "artist": "Demo",
  "artwork": "https://example.com/poster.jpg"
}
```

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `state` | integer | 3＝正在播放；6＝缓冲中；2＝就绪但未播放；其余状态回传 1。 |
| `speed` | number | 目前播放速率，1 表示正常速度。 |
| `duration` | integer | 播放器回报的总长，单位毫秒；未知长度可能是负值，不保证为 -1。 |
| `position` | integer | 目前播放位置，单位毫秒。 |
| `url` | string | 目前播放 URL；没有值时为空字串。 |
| `title` | string | 媒体标题；没有值时为空字串。 |
| `artist` | string | 媒体的 `artist` 中继资料；没有值时为空字串。 |
| `artwork` | string | 封面 URI；没有值时为空字串。 |

:::tip
需要更多控制能力（`canSeek`、`canSetSpeed`、`mediaKey`）时用 [/remote](/local/remote)。`/media` 只提供播放状态快照。
:::

## 相关

- 播放网址的完整形式：[日志资讯 API](/local/logs)
- 遥控指令：[/remote](/local/remote#remote-查询与控制)
