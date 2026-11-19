---
description: Exo 支援的播放指令与范例：跳转、倍速、音量、延迟、循环、章节与缩放。
---

# 播放指令

复制指令，存成播放按钮。Exo 与 mpv 都能使用。

## 新增指令按钮

1. 播放时，开启控制列的「指令」。
2. 按「新增」，填入名称、贴上指令。
3. 储存后，按一下即可执行。

mpv：进入「脚本」→「按钮」→「新增」→「新增指令按钮」。每个按钮填一条指令，不需加上 `mp.commandv(...)`。

## 播放与循环

**支援：Exo、mpv。** 播放、暂停、倍速与单片循环。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 播放／暂停 | `cycle pause` | 在播放与暂停之间切换。 |
| 暂停 | `set pause yes` | 暂停目前影片。 |
| 播放 | `set pause no` | 继续播放。 |
| 指定倍速 | `set speed 1.5` | 以 1.5 倍速播放。 |
| 调整倍速 | `add speed 0.25` | 目前倍速增加 0.25。 |
| 切换倍速 | `cycle-values speed 1 1.25 1.5 2` | 依序轮切，最后回到原速。 |
| 开启单片循环 | `set loop-file inf` | 重复播放目前影片。 |
| 关闭单片循环 | `set loop-file no` | 恢复一般播放顺序。 |
| 切换单片循环 | `cycle-values loop-file no inf` | 在一般播放与单片循环之间切换。 |

`pause`：`yes` 暂停、`no` 播放，支援 set、cycle-values、cycle。`cycle pause` 切换播放／暂停。

`speed`：0.1～5 倍，1 为原速，支援 set、add、cycle-values；add 超出范围时取上下限。

`loop-file`：`inf` 或 `yes` 开启、`no` 关闭，支援 set、cycle-values；单片无限循环，不支援指定次数或整份清单。

## 音量与同步

**支援：Exo、mpv。** 调整播放器音量与声音同步。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 指定音量 | `set volume 60` | 将播放器音量设为 60%。 |
| 降低音量 | `add volume -10` | 目前播放器音量减少 10。 |
| 切换音量 | `cycle-values volume 30 60 100` | 依序切换三个音量。 |
| 声音延后 0.2 秒 | `set audio-delay 0.2` | 设定声音相对画面的延迟。 |
| 声音提早 0.1 秒 | `add audio-delay -0.1` | 在目前延迟上减少 0.1 秒。 |

`volume`：0～100，0 静音，支援 set、add、cycle-values；播放器音量，系统音量不变，add 超出范围时取上下限。

`audio-delay`：秒，可用小数，0 清除，支援 set、add、cycle-values；声音正值延后、负值提早。

## 音轨切换

**支援：Exo、mpv。** 切换音轨、关闭音讯或恢复自动选轨。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 下一音轨 | `cycle aid` | 也可使用 `cycle aid up`；循环包含关闭音讯。 |
| 上一音轨 | `cycle aid down` | 反向循环，也包含关闭音讯。 |
| 指定音轨 | `set aid 2` | 需先确认目前影片有 ID 为 2 的可播放音轨。 |
| 关闭音讯 | `set aid no` | 停用音讯，影片仍可继续播放。 |
| 自动选轨 | `set aid auto` | 清除手动指定，依语言偏好与预设旗标重新选轨。 |
| 只在指定音轨间切换 | `cycle-values aid 2 4 3` | 依列出的 ID 顺序轮切，不加入关闭音讯。 |
| 反向切换指定音轨 | `cycle-values !reverse aid 2 4 3` | 按 ID 清单反向轮切。 |

`audio` 是 `aid` 的别名，可用于上述语法。范例 ID 需依目前影片调整。

音轨 ID 不是清单索引，可能随影片或播放器改变。Exo 可使用不含空白或分号的字串 ID。

Exo 的 `cycle-values` 音轨清单只接受不重复的 ID；任一 ID 不存在、无法播放或有歧义时不执行。`no`、`auto` 请使用 `set aid` 设定。

## 字幕调整

**支援：Exo、mpv。** 调整主字幕的时间，副字幕不变。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 字幕延后 0.2 秒 | `set sub-delay 0.2` | 设定主字幕延迟。 |
| 字幕提早 0.1 秒 | `add sub-delay -0.1` | 在目前延迟上减少 0.1 秒。 |
| 清除字幕延迟 | `set sub-delay 0` | 恢复原始字幕时间。 |
| 切换字幕延迟 | `cycle-values sub-delay 0 0.1 0.2` | 依序轮切三个延迟值。 |

`sub-delay`：秒，可用小数，0 清除，支援 set、add、cycle-values；主字幕正值延后、负值提早，副字幕不变。

## 跳转与章节

**支援：Exo、mpv。** 跳到指定时间、影片比例或章节。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 快转 90 秒 | `seek +90` | 预设使用相对秒数。 |
| 倒退 10 秒 | `seek -10 relative` | 从目前位置倒退。 |
| 跳到第 120 秒 | `seek 120 absolute` | 使用从片头起算的秒数。 |
| 跳到结束前 10 秒 | `seek -10 absolute` | 负数由片尾倒数。 |
| 跳到影片一半 | `seek 50 absolute-percent` | 使用整部片长的百分比。 |
| 快转片长的 10% | `seek 10 relative-percent` | 从目前位置向后移动片长的 10%。 |
| 跳到第三章 | `set chapter 2` | 章节索引从 0 起算。 |
| 下一章 | `add chapter 1` | 跳到下一章。 |
| 上一章 | `add chapter -1` | 本章已播放超过 5 秒时，先回到本章开头。 |
| 轮切前两章 | `cycle-values chapter 0 1` | 影片需包含这两个章节。 |

来源需支援跳转；百分比与从片尾倒数都需要已知片长。超出时间范围时停在起点或终点。

`chapter`：从 0 起算，add 仅接受 1、-1，支援 set、add、cycle-values；需有章节，`set chapter 2` 跳第三章。

## 画面调整

**支援：Exo、mpv。** 调整影片宽高与比例。

| 功能 | 指令 | 说明 |
| --- | --- | --- |
| 加宽影片 | `set video-scale-x 1.15` | 宽度增加 15%。 |
| 增高影片 | `set video-scale-y 1.15` | 高度增加 15%。 |
| 切换影片高度 | `cycle-values video-scale-y 1 1.15 1.3` | 依序轮切高度倍率。 |
| 指定影片比例 | `set video-aspect-override 16:9` | 将来源比例覆写为 16:9。 |
| 切换影片比例 | `cycle-values video-aspect-override no 16:9 4:3 2.35:1` | 在来源比例与指定比例间轮切。 |
| 恢复来源比例 | `set video-aspect-override no` | 清除比例覆写，保留额外缩放与平移。 |

缩放倍率设为 `1`，取消该方向的额外缩放，保留画面比例模式。放大可能裁切边缘；字幕、弹幕与控制介面大小不变。

`video-scale-x`：0 < 倍率 ≤ 10000，1 取消缩放，支援 set、cycle-values；宽度倍率，1.15 加宽 15%。

`video-scale-y`：0 < 倍率 ≤ 10000，1 取消缩放，支援 set、cycle-values；高度倍率，1.15 增高 15%。

`video-aspect-override`：支援 `no`、正数小数或宽:高（例如 16:9、4:3、2.35:1），支援 set、cycle-values；覆写影片比例，`no` 恢复来源比例，保留额外缩放与平移。填充模式仍拉伸至播放区域。不接受 0、-1 或超出 float 范围的比例。
## 轮切数值

`cycle-values` 支援各区块列出的共用属性，依目前值轮切，末项后回到首项。目前值不在清单时，从首项开始。

加上 `!reverse` 反向切换；目前值不在清单时，从末项开始。

```txt
cycle-values !reverse speed 1 1.25 1.5 2
```

Exo 会先检查整份清单，任一值无效就不执行。Exo 的 `cycle` 支援 `pause`、`aid` 与 `audio`，音轨可加上 `up` 或 `down` 指定方向。属性名称请使用小写。

## 使用限制

:::warning
未播放或影片不支援该操作时，会提示「目前播放无法执行此指令」。
:::

Exo 不支援分号串接、额外前缀或 seek 旗标。

Lua 外挂与 `mpv.conf` 仅限 mpv，可自行汇入外挂。

本页范围以 Exo 为准；mpv 依原生规则，音量与循环行为可能不同。
