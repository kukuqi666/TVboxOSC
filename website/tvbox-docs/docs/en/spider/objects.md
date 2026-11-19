---
description: Fields for the Vod, Class, Filter, Sub, Danmaku and SkipSegment objects.
---

# Data Objects

Use `Json.link(name, target)` in Java, `self.link(name, target)` in Python or `link(name, target)` in QuickJS for actor or category hyperlinks. `target` holds the destination category and filters, using `id`, `name`, `type_flag` and `filter`.

```js
// QuickJS: turn actors into clickable filter links
list.map((vod) => ({ ...vod, vod_actor: link(vod.vod_actor, { id: "movie", filter: { area: "tw" } }) }));
```

## Vod

| Field | Notes |
| --- | --- |
| `vod_id` | Unique value passed to `detailContent`. |
| `vod_name` | Display name. |
| `vod_pic` / `vod_remarks` | Thumbnail and corner badge. |
| `type_name` | Category text. |
| `vod_year` / `vod_area` | Year and region. |
| `vod_director` / `vod_actor` | People. |
| `vod_content` | Description. |
| `vod_play_from` / `vod_play_url` | Play groups and episodes; separators in [Groups and episodes](/spider/episodes). |
| `vod_tag` / `action` | `folder` marks a folder; a non-empty `action` fires on tap. For type 3 it is passed to the spider's `action()`; for type 4 it holds the JSON endpoint URL. |
| `cate` | Also marked as a folder when present; may contain `land`, `circle`, `ratio`. |
| `land` / `circle` / `ratio` / `style` | Card appearance overrides. |

## Class

| Field | Notes |
| --- | --- |
| `type_id` / `id` | Category ID passed to `categoryContent`. |
| `type_name` / `name` | Category display name. |
| `type_flag` | 1 marks a folder category. |
| `land` / `circle` / `ratio` | Default card style for that category. |
| `filters` | A Filter array may also be provided inside the category object. |

## Filter

| Field | Notes |
| --- | --- |
| `key` / `name` | Value key and display name. |
| `init` | Default option value. |
| `value` | Option array such as `[{"n":"All","v":""}]`; `n` is the label and `v` the value passed in. |

## Sub

| Field | Notes |
| --- | --- |
| `url` | Subtitle file location. |
| `name` / `lang` | Display name and language code. |
| `format` | Subtitle MIME type; may be omitted and inferred by the App. |
| `flag` | 0 lets the App choose by subtitle count and preferred language; non-zero uses Media3 selection flags: 1 default, 2 forced, 4 auto-select, combinable as bits. |

## Danmaku

| Field | Notes |
| --- | --- |
| `url` | HTTP(S) data URL, or a live source produced by `getDanmakuUrl(id)`. |
| `name` | Optional display name. |

## SkipSegment

| Field | Notes |
| --- | --- |
| `type` | `opening`, `middle` or `ending`. |
| `start` | Time in milliseconds to start skipping. |
| `end` | Time in milliseconds to jump to; must be later than `start` and may be omitted only for `ending` when `start > 0`. |

---

- Separator format for groups and episodes: [Groups and episodes](/spider/episodes)
- The `emit` object for live danmaku: [Live danmaku](/spider/live-danmaku)
- DRM sub-fields: [Configuration reference · Shared objects](/config/common)
