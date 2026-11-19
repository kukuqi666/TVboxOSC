---
description: 在 action 回传 web，开一般网页或独立内容视窗，并可在登入后自动关闭。
---

# 开网页

卡片的 `action` 触发后，在回应顶层提供 `web`。

## 一般网页

`action`

```json
{
  "web": {
    "url": "https://example.com/"
  }
}
```

## 独立内容视窗

`action`

```json
{
  "web": {
    "url": "https://example.com/game",
    "mode": "view"
  }
}
```

`url` 必填，只接受 HTTP 或 HTTPS。`mode` 省略或填 `browser` 为一般网页；`view` 为独立内容视窗，不启动外部 App，也不会自动全萤幕。两者返回都先退出全萤幕，再依网页历史回上一页；没有历史才关闭。

## 登入后自动关闭

`action`

```json
{
  "web": {
    "url": "https://example.com/login",
    "cookie": [
      {"keys": ["sid", "token"]},
      {"url": "https://login.example.com/", "keys": ["auth"]}
    ]
  }
}
```

`cookie` 为选填阵列，每组 `url` 省略时使用开启网址。同组 `keys` 必须全部非空，不同组任一组成即完成；至少一个指定 Cookie 比开启前改变，且目前页面与该组的 scheme、host、port 相同，才自动关闭。此条件不代表站点已验证帐号授权。

| 栏位 | 必填 | 说明 |
| --- | --- | --- |
| `web.url` | 是 | 要开启的网址，只接受 HTTP／HTTPS。 |
| `web.mode` | 否 | `browser`（预设）或 `view`。 |
| `web.cookie[].url` | 否 | 该组 Cookie 所在的网址；省略时用 `web.url`。 |
| `web.cookie[].keys` | 是 | 必须全部非空的 Cookie 名称。 |

:::warning
自动关闭只是「Cookie 已写入」的判断，不等于登入成功。需要确认授权时，请由爬虫在自己的请求中验证。
:::

相关：[Vod 的 action 栏位](/spider/objects)、[方法与时機](/spider/lifecycle)。
