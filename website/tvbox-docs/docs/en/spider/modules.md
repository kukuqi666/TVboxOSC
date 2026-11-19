---
description: Install the Python, QuickJS, Node.js, mpv, Thunder and BD-J modules from the App module page.
---

# Runtime Modules

Install the standalone APKs for Python, QuickJS or Node.js from the App module page. Thunder, mpv and BD-J are managed there too — install what you need.

## Install and update

Pick a module, download it and finish the system install. Versions must match the App's module interface and the device ABI.

## Test and restart

After installing, run the module test to confirm it initializes. If prompted to restart, reopen the App before loading a spider.

:::warning
QuickJS uses the QuickJS-NG engine and cannot run Node.js packages directly; follow the [Node.js loading](/spider/node) instructions instead. When you use a new interface, update both the App and the matching module.
:::

## Modules per language

| Language | Module needed | Notes |
| --- | --- | --- |
| Java JAR | None | The App loads the JAR, but does not compile Java. |
| Python | Python module | Export a class extending `base.spider.Spider`. |
| QuickJS | QuickJS module | ES module with a default export. |
| Node.js | Node.js module | Uses `index.js.md5` as the entry, see [Node.js loading](/spider/node). |
| Playback | mpv (optional) | Needed for the mpv engine or Lua scripts. |
| Other | Thunder, BD-J | Install as needed. |
