---
description: Use the App spider test page to load source, pick a method, pass arguments and inspect responses.
---

# Testing a Spider

Open the App's web UI and go to **Settings → Spider test**, or open `/spider.html` directly. Python and QuickJS require the [matching module](/spider/modules) first.

## Workflow

1. Load a Java JAR and pick a class, or load a `.py`／`.js` file; `ext` is optional.
2. Pick a method, fill in the arguments as a JSON array, run it and inspect the result and network log.
3. After editing, save, reload and retest; you can format the source or restore a version.
4. Once `play` returns a valid result, send it to the App and confirm playback.

## Limits

- The App does not compile Java; after editing a `.java` file, rebuild and reload the JAR.
- The default test timeout is 30 seconds; it can be set from 1 to 300.
- Reloading, cancelling or leaving cleans up the test job.
- If the source was changed by another page, reread it before saving.

:::warning
**Confirm the response, then confirm playback**

A passing module test and a correct URL do not prove playback works. Always confirm picture, sound, subtitles and danmaku in the App.
:::

## Filling in arguments

Method arguments go into a JSON array in declaration order, for example:

```json
["movie", "1", false, {}]
```

which maps to `categoryContent(tid, pg, filter, extend)`. Java `HashMap` and Python `dict` are both passed as JSON objects.
