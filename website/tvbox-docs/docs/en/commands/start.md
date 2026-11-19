---
description: Playback commands supported by Exo, with examples for seek, speed, volume, delay, loop, chapters and scaling.
---

# Playback Commands

Copy a command and save it as a playback button. Works with both Exo and mpv.

## Adding a command button

1. While playing, open **Commands** in the control bar.
2. Tap **Add**, enter a name and paste the command.
3. Save it, then tap once to run it.

On mpv: **Scripts** → **Buttons** → **Add** → **Add command button**. One command per button; do not wrap it in `mp.commandv(...)`.

## Playback and loop

**Supported by: Exo and mpv.** Play, pause, change speed and loop the current file.

| Action | Command | Notes |
| --- | --- | --- |
| Play / pause | `cycle pause` | Toggles between play and pause. |
| Pause | `set pause yes` | Pauses the current video. |
| Play | `set pause no` | Resumes playback. |
| Set speed | `set speed 1.5` | Plays at 1.5×. |
| Adjust speed | `add speed 0.25` | Adds 0.25 to the current speed. |
| Cycle speed | `cycle-values speed 1 1.25 1.5 2` | Cycles and returns to normal speed. |
| Enable single-file loop | `set loop-file inf` | Repeats the current video. |
| Disable single-file loop | `set loop-file no` | Restores normal playback order. |
| Toggle single-file loop | `cycle-values loop-file no inf` | Toggles between normal playback and looping. |

`pause`: `yes` paused, `no` playing; supports set, cycle-values and cycle. `cycle pause` toggles play/pause.

`speed`: 0.1–5×, 1 is normal; supports set, add and cycle-values. `add` clamps to the valid range.

`loop-file`: `inf` or `yes` on, `no` off; supports set and cycle-values. Loops a single file indefinitely; no repeat count or playlist loop.

## Volume and sync

**Supported by: Exo and mpv.** Adjust player volume and audio synchronization.

| Action | Command | Notes |
| --- | --- | --- |
| Set volume | `set volume 60` | Sets player volume to 60%. |
| Lower volume | `add volume -10` | Reduces current player volume by 10. |
| Cycle volume | `cycle-values volume 30 60 100` | Cycles through three volume levels. |
| Delay audio by 0.2 s | `set audio-delay 0.2` | Sets audio delay relative to the video. |
| Advance audio by 0.1 s | `add audio-delay -0.1` | Reduces the current delay by 0.1 seconds. |

`volume`: 0–100, 0 is muted; supports set, add and cycle-values. Affects player volume only, not system volume; `add` clamps.

`audio-delay`: seconds, decimals allowed, 0 clears; supports set, add and cycle-values. Positive delays audio, negative advances it.

## Audio track selection

**Supported by: Exo and mpv.** Switch audio tracks, disable audio, or restore automatic track selection.

| Action | Command | Notes |
| --- | --- | --- |
| Next audio track | `cycle aid` | `cycle aid up` also works; the cycle includes disabling audio. |
| Previous audio track | `cycle aid down` | Cycles backwards, also including disabled audio. |
| Select an audio track | `set aid 2` | The current media must have a playable track with ID 2. |
| Disable audio | `set aid no` | Disables audio; the video keeps playing. |
| Automatic track selection | `set aid auto` | Clears the manual choice and selects again by language preference and default flags. |
| Cycle only selected tracks | `cycle-values aid 2 4 3` | Cycles through the listed IDs without including disabled audio. |
| Cycle selected tracks backwards | `cycle-values !reverse aid 2 4 3` | Cycles backwards through the ID list. |

`audio` is an alias of `aid` and works with the syntax above. Adjust the example IDs for the current media.

An audio track ID is not a list index and may change between media or players. Exo accepts string IDs without spaces or semicolons.

Exo accepts only unique IDs in a `cycle-values` audio track list; the command is skipped if any ID is missing, unplayable or ambiguous. Use `set aid` for `no` and `auto`.

## Subtitle adjustment

**Supported by: Exo and mpv.** Adjust primary subtitle timing; secondary subtitles are untouched.

| Action | Command | Notes |
| --- | --- | --- |
| Delay subtitles by 0.2 s | `set sub-delay 0.2` | Sets primary subtitle delay. |
| Advance subtitles by 0.1 s | `add sub-delay -0.1` | Reduces the current delay by 0.1 seconds. |
| Clear subtitle delay | `set sub-delay 0` | Restores original subtitle timing. |
| Cycle subtitle delay | `cycle-values sub-delay 0 0.1 0.2` | Cycles through three delay values. |

`sub-delay`: seconds, decimals allowed, 0 clears; supports set, add and cycle-values. Positive delays primary subtitles, negative advances them; secondary subtitles are unchanged.

## Seek and chapters

**Supported by: Exo and mpv.** Jump to a time, percentage or chapter.

| Action | Command | Notes |
| --- | --- | --- |
| Forward 90 seconds | `seek +90` | Relative seconds by default. |
| Back 10 seconds | `seek -10 relative` | Moves back from the current position. |
| Jump to 120 seconds | `seek 120 absolute` | Seconds from the start. |
| Jump to 10 seconds before the end | `seek -10 absolute` | A negative value counts back from the end. |
| Jump to the midpoint | `seek 50 absolute-percent` | A percentage of the full duration. |
| Forward 10% of the duration | `seek 10 relative-percent` | Moves forward by 10% of the full duration. |
| Jump to the third chapter | `set chapter 2` | Chapter indexes start at 0. |
| Next chapter | `add chapter 1` | Jumps to the next chapter. |
| Previous chapter | `add chapter -1` | Returns to the chapter start if the current chapter has played for more than 5 seconds. |
| Cycle the first two chapters | `cycle-values chapter 0 1` | The video must contain both chapters. |

The source must support seeking; percentages and end-relative seeks need a known duration. Out-of-range values clamp to the start or the end.

`chapter`: zero based; `add` accepts only 1 or -1; supports set, add and cycle-values. Requires chapters; `set chapter 2` jumps to the third chapter.

## Video adjustment

**Supported by: Exo and mpv.** Adjust video width, height and aspect ratio.

| Action | Command | Notes |
| --- | --- | --- |
| Widen video | `set video-scale-x 1.15` | Width increases by 15%. |
| Make video taller | `set video-scale-y 1.15` | Height increases by 15%. |
| Cycle video height | `cycle-values video-scale-y 1 1.15 1.3` | Cycles height factors. |
| Set video aspect | `set video-aspect-override 16:9` | Overrides the source aspect with 16:9. |
| Cycle video aspect | `cycle-values video-aspect-override no 16:9 4:3 2.35:1` | Cycles source and fixed aspects. |
| Restore source aspect | `set video-aspect-override no` | Clears the override while keeping extra scaling and panning. |

A scale of `1` removes the extra scaling on that axis while keeping the aspect mode. Zooming in may crop the edges; subtitles, danmaku and the control UI keep their size.

`video-scale-x`: 0 < factor ≤ 10000, 1 resets; supports set and cycle-values. Width factor; 1.15 is 15% wider.

`video-scale-y`: 0 < factor ≤ 10000, 1 resets; supports set and cycle-values. Height factor; 1.15 is 15% taller.

`video-aspect-override`: accepts `no`, a positive number or w:h such as 16:9, 4:3, 2.35:1; supports set and cycle-values. Overrides the aspect; `no` restores the source aspect while keeping extra scaling and panning. Fill mode still stretches to the viewport. 0, -1 and out-of-range floats are rejected.

## cycle-values

`cycle-values` supports the shared properties listed in each section. It cycles from the current value and wraps after the last entry. If the current value is not in the list, it starts at the first entry.

Add `!reverse` to cycle backwards; if the current value is not in the list, it starts at the last entry.

```txt
cycle-values !reverse speed 1 1.25 1.5 2
```

Exo checks the whole list first and skips the command if any value is invalid. Exo's `cycle` supports `pause`, `aid` and `audio`; audio tracks accept `up` or `down` to set the direction. Keep property names lowercase.

## Limits

:::warning
When nothing is playing or the media does not support the operation, the App shows "the current playback cannot run this command".
:::

Exo supports only the syntax on this page: no semicolon chaining, extra prefixes or seek flags.

Lua scripts and `mpv.conf` apply to mpv only; you can import your own scripts.

This page describes Exo. mpv follows its native rules, so volume and loop behavior may differ.
