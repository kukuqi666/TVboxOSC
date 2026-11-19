---
description: 用 danmaku(id, emit) 提供即时弹幕，以及 emit 的弹幕物件与订阅状态格式。
---

# 即时弹幕

Java／Python／QuickJS 的播放回应沿用 `danmaku: [{name, url}]`。即时来源的 url 用 `getDanmakuUrl(id)` 产生，Python 使用 `self.getDanmakuUrl(id)`；App 会呼叫该站点的 `danmaku(id, emit)`。HTTP(S) 来源仍按弹幕档案载入。

```js
// QuickJS：播放结果里改用即时来源
play(flag, id) {
  return {
    parse: 0,
    url: "https://example.com/video.m3u8",
    danmaku: [{ name: "即时弹幕", url: getDanmakuUrl(id) }]
  };
}
```

## 弹幕物件

`emit(item)`

```json
{
  "text": "即时弹幕",
  "color": 6737151,
  "size": 25,
  "type": 1,
  "colors": [6737151, 13408767]
}
```

| 栏位 | 类型 | 必填 | 预设 | 说明 |
| --- | --- | --- | --- | --- |
| `text` | string | 是 | — | 解码后的文字；不得全空白，最多 4096 字元。 |
| `color` | integer | 否 | `16777215`（白色） | RGB 颜色，范围 0～16777215。 |
| `size` | number | 否 | `0` | 字级，单位 sp，范围 0～96；0 跟随使用者设定。 |
| `type` | integer | 否 | `1` | 1 滚动、4 底部、5 顶部、6 反向。 |
| `colors` | integer[] | 否 | — | 2～8 个 RGB 颜色组成渐层；省略时使用 `color`。 |

## 订阅状态

状态也使用 `emit`，不可同时包含 `text`。`state` 为 `ready`（订阅可用）、`retrying`（恢复中）或 `error`（终止失败）；`msg` 选填，最多 512 字元，不放 Cookie、token 或签名网址。`ready` 须确认平台订阅可用，不能仅以 WebSocket open 判断。

`emit(item)`

```json
{
  "state": "retrying",
  "msg": "连线中断，等待恢复"
}
```

App 自行设定 `connecting`、合并相同 `state`／`msg`；初始化例外转成 `error`。`error` 后停止订阅并忽略后续回呼，主动停止则清除状态，不需回报 `closed`。状态记录在日志，不会当成弹幕或反覆跳出提示。

## 停止

`emit` 接收 Java `JSONObject`、Python dict 或 QuickJS 物件；Java 用 `emit.accept(item)` 并回传 `AutoCloseable`，Python／QuickJS 回传停止函式。切台或关闭时，App 呼叫停止；爬虫须取消自己的连线与心跳。

```py
# Python：回传停止函式
def danmaku(self, id, emit):
    ws = self.net.connect(self.danmaku_url(id), {}, lambda socket, event: emit(decode(event)))

    def stop():
        ws.cancel()

    return stop
```

爬虫负责协定、解码与样式转换，App 负责显示限制，超量或过期讯息可能丢弃。完整范例见 CatVodSpider 的三语言 Demo。
