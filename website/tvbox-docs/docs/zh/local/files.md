---
description: 用 HTTP 端点浏览、上传、下载、重新命名、搬移与删除装置档案。
---

# 档案与目录管理

一般相对路径以 Android 共用外部储存根目录 `Path.root()` 为基准，不是 App 的私有 data 目录；实际存取仍受系统储存权限限制。

## 端点

| 端点 | 使用方式 | 参数与行为 |
| --- | --- | --- |
| `/file/{path}` | GET | 浏览目录或下载档案；`/file` 为共用储存根目录。 |
| `/upload` | POST `multipart/form-data` | `path` 必填且目录须已存在；档案使用 multipart 栏位上载。 |
| `/newFolder` | GET / POST | `path` 为既有父目录，`name` 为新资料夹名称。 |
| `/rename` | POST 表单 | `path` 为既有档案或目录，`name` 为新名称；目标不可已存在。 |
| `/move` | POST 表单 | `path` 为来源，`target` 为既有目标目录；保留名称，不覆盖同名项目，也不可搬进自身目录。 |
| `/delFolder` | GET / POST | `path` 必填；递回删除指定项目，不可删除储存根目录。 |
| `/delFile` | GET / POST | 与 `/delFolder` 共用删除逻辑，并非只允许删除一般档案。 |

```sh
curl "http://127.0.0.1:9978/file/TV"

curl -G "http://127.0.0.1:9978/newFolder" --data-urlencode "path=TV" --data-urlencode "name=demo-empty"
```

`浏览 /file/TV 的示意回应`

```json
{
  "parent": "",
  "files": [
    {
      "name": "demo.mp4",
      "path": "/TV/demo.mp4",
      "time": "2026/09/08 12:00:00",
      "dir": 0
    },
    {
      "name": "subtitles",
      "path": "/TV/subtitles",
      "time": "2026/09/08 12:00:00",
      "dir": 1
    }
  ]
}
```

| 栏位 | 用途 |
| --- | --- |
| `parent` | 位于根目录时为 `.`；上一层是根目录时为空字串；其他回传上一层相对路径。 |
| `files[].name` | 档案或目录名称。 |
| `files[].path` | 根目录内的路径通常带前导 `/`，例如 `/TV/demo.mp4`。 |
| `files[].time` | 最后修改时间，以装置时区格式化为 `yyyy/MM/dd HH:mm:ss`。 |
| `files[].dir` | 1＝目录；0＝一般档案。 |

## 下载、Range 与快取

一般下载回传 200；单一有效 Range 回传 206，支援起迄、开放结尾及尾端位元组范围。多段或无效范围回传 416；`If-None-Match` 命中 ETag 时回传 304，`If-Range` 不符时改回完整档案。

```sh
curl "http://127.0.0.1:9978/file/TV/demo.mp4" -H "Range: bytes=0-1023" --output demo-part.bin
```

## 重新命名与搬移

```sh
curl --data-urlencode "path=/TV/subtitle.srt" --data-urlencode "name=demo.srt" "http://127.0.0.1:9978/rename"
curl --data-urlencode "path=/TV/demo.srt" --data-urlencode "target=/TV/subtitles" "http://127.0.0.1:9978/move"
```

档案与目录均可操作；新名称只是一个名称，不能包含路径分隔符。目标已存在时不覆盖，搬移也不能把目录放进自己里面。网页提供批次搬移与删除，逐项操作并回报结果。

## 一般上传与删除

```sh
# 上传至已存在的 TV 目录
curl "http://127.0.0.1:9978/upload?path=TV" -F "file=@./subtitle.srt"
```

multipart 栏位名称可自订，上例使用 `file`；档名由上传栏位提供，不能是空字串、`.`、`..` 或包含路径分隔符。ZIP 会解压到指定目录，其他档案使用原子复制；缺少档案、目录不存在或操作失败会回传 500 与文字讯息。

:::danger 删除端点会直接删除资料
`/delFile` 与 `/delFolder` 目前使用同一段递回删除程式；不要依端点名称假设目录不会被删除。下例只针对先前建立的示意目录，执行前务必确认内容。
:::

```sh
curl -G "http://127.0.0.1:9978/delFolder" --data-urlencode "path=TV/demo-empty"
```

上传、新建、重新命名、搬移与删除都限制在共用储存根目录内，禁止删除或搬移根目录本身。`/file` 的区域网络请求也限于此范围；本机 loopback 请求可由 `Path.local` 解析其他可存取路径。

## 相关

- 续传与大档案：[可续传上传与装置下载](/local/transfers)
- 套用字幕：[网页播放与遥控器](/local/remote)
