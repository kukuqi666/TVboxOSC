---
description: Vod、Class、Filter、Sub、Danmaku 与 SkipSegment 物件栏位。
---

# 数据物件

演员或分类超连结使用 Java 的 `Json.link(name, target)`、Python 的 `self.link(name, target)` 或 QuickJS 的 `link(name, target)`。`target` 包含目标分类及筛选，可用 `id`、`name`、`type_flag`、`filter`。

```js
// QuickJS：把演员变成可点击的筛选连结
list.map((vod) => ({ ...vod, vod_actor: link(vod.vod_actor, { id: "movie", filter: { area: "tw" } }) }));
```

## Vod

| 栏位 | 说明 |
| --- | --- |
| `vod_id` | 传给 `detailContent` 的唯一值。 |
| `vod_name` | 显示名称。 |
| `vod_pic` / `vod_remarks` | 缩图与角标。 |
| `type_name` | 分类文字。 |
| `vod_year` / `vod_area` | 年份与地区。 |
| `vod_director` / `vod_actor` | 人员资讯。 |
| `vod_content` | 详细描述。 |
| `vod_play_from` / `vod_play_url` | 播放分组与集数，分隔方式见[播放分组与集数](/spider/episodes)。 |
| `vod_tag` / `action` | `folder` 表示资料夹；非空 `action` 在点击时触发操作。type 3 传给爬虫 `action()`；type 4 填回传 JSON 的接口网址。 |
| `cate` | 提供此物件时也视为资料夹，可含 `land`、`circle`、`ratio`。 |
| `land` / `circle` / `ratio` / `style` | 卡片外观覆盖。 |

## Class

| 栏位 | 说明 |
| --- | --- |
| `type_id` / `id` | 传给 `categoryContent` 的分类 ID。 |
| `type_name` / `name` | 分类显示名称。 |
| `type_flag` | 1 代表资料夹分类。 |
| `land` / `circle` / `ratio` | 该分类预设卡片样式。 |
| `filters` | 也可在分类物件内提供 Filter 阵列。 |

## Filter

| 栏位 | 说明 |
| --- | --- |
| `key` / `name` | 传入键与显示名称。 |
| `init` | 预设选项值。 |
| `value` | 选项阵列，例如 `[{"n":"全部","v":""}]`；`n` 是显示名称，`v` 是传入值。 |

## Sub

| 栏位 | 说明 |
| --- | --- |
| `url` | 字幕档位置。 |
| `name` / `lang` | 显示名称与语言代码。 |
| `format` | 字幕 MIME type，可省略并由 App 推定。 |
| `flag` | 0 交由 App 依字幕数量与偏好语言选择；非零使用 Media3 选择旗标：1 预设、2 强制、4 自动选择，可位元组合。 |

## Danmaku

| 栏位 | 说明 |
| --- | --- |
| `url` | HTTP(S) 资料网址，或由 `getDanmakuUrl(id)` 产生的即时来源。 |
| `name` | 选填显示名称。 |

## SkipSegment

| 栏位 | 说明 |
| --- | --- |
| `type` | `opening` 为片头，`middle` 为片中，`ending` 为片尾。 |
| `start` | 开始跳过的时间（毫秒）。 |
| `end` | 要跳到的时间（毫秒）；须晚于 `start`，仅片尾且 `start > 0` 时可省略。 |

---

- 播放分组与集数的字串格式：[播放分组与集数](/spider/episodes)
- 即时弹幕的 `emit` 物件：[即时弹幕](/spider/live-danmaku)
- DRM 子栏位：[配置字典 · 共用物件](/config/common)
