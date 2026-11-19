---
description: 网页播放页、独立遥控器、字幕与弹幕套用，以及 /remote 的回应与错误码。
---

# 网页播放与遥控器

在浏览器开启 App 的服务网址。「播放」页显示封面、标题与进度，可控制播放、切换集数或频道、单集循环、跳转与倍速。预设开启「搜寻」页，搜寻与网址推送各自保留输入内容及浏览器记录。

拖曳进度滑杆预览时间，放手后跳转；方向键每次调整五秒。直播隐藏进度、循环与倍速控制，不能跳转的影片停用滑杆。网页提供 0.5×、1.0×、2.0×、3.0× 四个倍速按钮。

## 分页网址

使用语意分页参数；旧数字分页不再对应原页面，未知值会回到搜寻页。档案页可另带 `path`，例如 `/?tab=files&path=/TV`。

| 网址 | 用途 |
| --- | --- |
| `/?tab=search` | 搜寻；未指定 `tab` 时的预设页。 |
| `/?tab=push` | 网址推送。 |
| `/?tab=play` | 播放控制。 |
| `/?tab=subtitles` | 字幕上传与套用。 |
| `/?tab=danmaku` | 即时弹幕与弹幕来源。 |
| `/?tab=settings` | 配置、装置操作、日志与爬虫测试入口。 |
| `/?tab=files` | 装置档案与传输。 |

## /remote：查询与控制

GET 查询状态，POST 以表单栏位 `type` 送出指令。成功回传 JSON（`application/json`），禁止快取；没有本机播放时回传 `{"active":false}`。回应不包含播放网址；完整网址由日志资讯 API 提供。网页仅在播放页位于前景时约每秒查询，返回播放页立即更新。

```sh
curl "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=repeat_on" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=repeat_off" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=seek" --data-urlencode "position=120000" "http://127.0.0.1:9978/remote"
curl --data-urlencode "type=speed" --data-urlencode "speed=1.5" "http://127.0.0.1:9978/remote"
```

### 指令

| `type` | 用途 |
| --- | --- |
| `play` | 播放；已播完时从头播放。 |
| `pause` | 暂停目前影片。 |
| `stop` | 停止播放。 |
| `prev` | 上一集／上一台；须支援切换项目。 |
| `next` | 下一集／下一台；须支援切换项目。 |
| `repeat_on` | 开启目前影片的单集循环；重复请求仍为开启。 |
| `repeat_off` | 关闭单集循环；重复请求仍为关闭。 |
| `seek` | 跳到 `position` 指定的毫秒位置；须支援跳转，不改变播放／暂停状态。 |
| `speed` | 以 `speed` 设定 0.1–5.0 倍速；须支援倍速，直播不可用。 |

### 回应栏位

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `active` | boolean | 是否有可遥控的本机播放；false 时不回传其余栏位。 |
| `title` | string | 目前影片的显示标题。 |
| `mediaKey` | string | 目前播放来源的识别键；套用字幕或弹幕时以 `key` 传回，换片后须重新取得。 |
| `artwork` | string | 目前影片的本机封面端点；没有封面时为空字串，载入失败时网页显示占位图。仅 `/remote` 回传此栏位。 |
| `position` | integer | 目前播放位置，单位毫秒，最小为 0。 |
| `duration` | integer | 总长，单位毫秒；未知长度为 0。 |
| `state` | integer | 1＝播放结束；2＝就绪但未播放；3＝正在播放；6＝缓冲中。 |
| `playWhenReady` | boolean | 播放器是否设定为播放；影片已播完时为 false。 |
| `repeatOne` | boolean | 目前是否开启单集循环，播完会重新播放同一部影片。 |
| `live` | boolean | 是否为直播；网页隐藏循环开关，API 拒绝循环指令。 |
| `canNavigate` | boolean | 是否支援上一项／下一项。 |
| `canSeek` | boolean | 是否支援网页跳转；支援 `timeshift` 的直播也可使用，未知长度或不可跳转时为 false。 |
| `canSetSpeed` | boolean | 是否支援倍速；直播或播放器不支援时为 false。 |
| `speed` | number | 目前播放倍速，例如 1 或 1.5。 |
| `engine` | string | 目前播放引擎，`ExoPlayer` 或 `mpv`。 |
| `video` | string | 播放器回报的视讯尺寸文字。 |

## 封面

`artwork` 指向同一服务的 `/remote/artwork?key=…`，成功回传 PNG；浏览器不需传送来源网址、Referer 或 Cookie。`key` 须对应目前封面；更换封面或停止播放后，旧 key 回传 404。缺少或格式不正确的 key 为 400；载入逾时为 503，载入或编码失败为 500。

## 其他行为

- 既有 `/action?do=control&type=repeat` 仍为切换循环。
- 上一项、下一项及停止可能先派送至播放介面，应以后续 GET 确认结果。
- `seek` 的 `position` 是非负整数毫秒，超过总长时跳到结尾；缺少、格式错误或超过 64 位元整数范围时回传 400。`canSeek` 为 false 时回传 409。支援[直播回看](/spider/timeshift)的来源也可跳转，位置以目前回看范围的起点计算。
- `speed` 必须是 0.1–5.0 范围内的有限数值，否则回传 400；`canSetSpeed` 为 false 时回传 409。

## 错误码

| 状态 | 情况 |
| --- | --- |
| 400 | 缺少或未知指令；`seek` / `speed` 参数无效。 |
| 403 | 跨来源的浏览器控制请求。 |
| 405 | 不支援的 HTTP 方法。 |
| 409 | 无可控制的播放、直播循环，或不支援上一项／下一项。 |
| 503 | 主执行绪忙碌或请求中断。 |

## /remote/key：独立遥控器

从 App 网页右上角开启 `/remote.html`，即可操作遥控器。返回时会保留原分页与档案目录。

```sh
curl --data-urlencode "key=ok" "http://127.0.0.1:9978/remote/key"
```

| POST 表单参数 | 说明 |
| --- | --- |
| `key` | `up`／`down`／`left`／`right`（方向）、`ok`（确认）、`back`（返回）、`menu`（选单）或 `0`–`9`。 |
| `action` | `press`＝单按（预设）。长按依序送 `down`（按住）、`repeat`（持续）、`up`（放开）；取消用 `cancel`。 |
| `id` | 非 `press` 必填，限 1–64 个英数字或 `-`。同次长按沿用相同的 `key` 与 `id`。 |

单按或开始长按需有前景页面或取得焦点的对话框，否则回传 409。成功回传 `{"accepted":true,"holding":false}`；`holding` 为 true 表示仍按住。长按 5 秒未续送 `repeat` 会自动放开；正常结束仍请送 `up` 或 `cancel`。

## 字幕上传与弹幕套用

先以 GET `/remote` 取得目前 `mediaKey`，再以 POST 表单的 `key` 与 `path` 套用来源。字幕须先上传到装置共用储存区，支援 SRT／SSA／ASS；弹幕可使用同范围内的既有档案，或 HTTP(S) 网址。

```sh
# 将 MEDIA_KEY 替换成刚查到的 mediaKey
curl --data-urlencode "key=MEDIA_KEY" --data-urlencode "path=/TV/subtitle.srt" "http://127.0.0.1:9978/remote/subtitle"
curl --data-urlencode "key=MEDIA_KEY" --data-urlencode "path=https://example.com/danmaku.xml" "http://127.0.0.1:9978/remote/danmaku"
```

没有可控制的本机播放、直播或已换成另一个播放来源时回传 409；`key` 格式不正确、档案无效或超出允许路径时回传 400。这些 POST 与播放控制、按键 API 均检查浏览器 Origin，跨来源请求回传 403。

## /remote/info：连线状态与装置资讯

点主页连线状态可查看 App 版本、variant、buildType、装置名称、Android 版本、SDK 与 ABI。资料由 GET `/remote/info` 提供，回传 JSON 并禁止快取；与提供装置探索及同步资料的 `/device` 分开。网页离线时保留最后一次成功读取的资讯。
