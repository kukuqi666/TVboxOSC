---
description: /proxy、/parse、/image 与 /tvbus 等内部端点的行为。
---

# 代理与 App 内部端点

| 端点 | 参数与行为 |
| --- | --- |
| `/proxy` | 把 Query／表单参数、请求标头、parseBody 产生的 `files` 依序合并后交给 `BaseLoader.proxy`；后加入的同名键会覆盖前者。 |
| `/parse` | `jxs` 为以分号分隔的解析前缀；`url` 为目标网址。`parse.html` 会以每个前缀 + `url` 建立 iframe，回传 `text/html`。 |
| `/image/{key}` | 读取 `ImgUtil` 已快取的图片资料，使用图片本身的 MIME；找不到 `key` 时回传 404。不是任意网址的图片下载 API。 |
| `/tvbus` | 回传目前直播配置 `Core` 的 RESP 字串，不是频道列表。 |
| `/` | 回传 App 内建的 `index.html` 控制页；其他未匹配路径会尝试读取内建资源，不存在则回传 404。 |

`/proxy` 的 POST body 不是一律直接摊平成参数：一般表单进入 `parms`，multipart 会提供暂存档资讯，原始 body 依 NanoHTTPD 的 `parseBody` 结果传递。爬虫回应决定状态、Content-Type、串流与标头；无效回应或例外会回传 500。

```sh
curl -G "http://127.0.0.1:9978/parse" --data-urlencode "jxs=https://example.com/parse?url=" --data-urlencode "url=https://example.com/watch/demo-1"
```

代理回传格式与各语言差异请见[Spider Proxy 回传](/spider/proxy)；RESP 栏位请见 [Core 配置](/config/core)。

## 页面

| 路径 | 用途 |
| --- | --- |
| `/index.html` | 网页控制台首页。 |
| `/remote.html` | 独立遥控器。 |
| `/spider.html` | 爬虫测试页，见[测试爬虫](/spider/testing)。 |
| `/logs.html` | 日志查看，见[日志下载与分享](/local/logs)。 |
| `/parse.html` | 以 `jxs` 与 `url` 参数建立解析 iframe。 |

:::warning
`/proxy`、`/parse` 会依请求内容执行爬虫逻辑。它们没有额外的存取控制，仅适合在可信任的区域网络使用。
:::
