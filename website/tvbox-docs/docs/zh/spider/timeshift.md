---
description: 在播放结果或 Channel 加入 timeshift，提供可拖曳的直播时间轴。
---

# 直播回看

来源支援依时间取源时，在播放回应或直播列表的 `Channel` 加入 `timeshift`。App 会显示可拖曳的时间轴，最右侧为直播。

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

`start` 是本场直播开始的 Unix 毫秒，`window` 是最多可回看的毫秒数。时间轴从本场开始累加，最长不超过 `window`；可用范围由来源决定。

## 取源流程

1. App 将网址的 `{time}` 换成目标 Unix 毫秒后 GET。
2. `time=0` 表示重新取得直播来源。
3. 端点回传直接播放的 `url`，并可附上 `header` 与 `format`。

使用本地代理时，以 `getProxyUrl` 产生网址。

## 行为

- 连续拖曳或切台会取消旧请求；失败时提示并保留目前播放。
- 回看期间暂停爬虫即时弹幕，回到直播后恢复。
- 从节目表选取回看则使用 [`catchup`](/config/common#节目回看-catchup)，时间轴以该节目为范围。

:::tip
`Channel.timeshift` 与 `Result.timeshift` 格式相同，写在直播配置里时可直接套用到该频道的每次播放。
:::

相关：[Group / Channel 栏位](/config/channel)、[网页遥控跳转](/local/remote)。
