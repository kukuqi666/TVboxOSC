---
description: Load a Node.js bundle through index.js.md5 and handle site routes over HTTP JSON.
---

# Node.js Loading

A plain `./demo.js` still runs on QuickJS. Node.js must use `index.js.md5` as the configuration entry, with `index.js`, `index.config.js` and `index.config.js.md5` in the same directory.

```text
node-demo/
├── index.js             # Entry
├── index.js.md5         # Config entry: Site.api points here
├── index.config.js      # Site list returned by /config
└── index.config.js.md5
```

## Loading flow

1. The App starts the bundle and reads `video` or `data.video` from the `/config` response.
2. It maps `video.sites` to `node:` site routes.
3. The spider handles `/init`, `/home`, `/category`, `/detail`, `/search` and `/play` appended to each site route over HTTP JSON.

```json
{
  "key": "node_demo",
  "name": "Node Demo",
  "type": 3,
  "api": "./node-demo/index.js.md5"
}
```

:::danger Don't just swap the api prefix
Node.js is not QuickJS's `export default` object model. Changing the `api` of a normal configuration to `node:` does not initialize a Node bundle.
:::

## Differences from QuickJS

| Item | QuickJS | Node.js |
| --- | --- | --- |
| Entry | `./demo.js` | `./index.js.md5` |
| Method calls | Direct object methods | HTTP JSON routes |
| Packages | Cannot run Node.js packages directly | Full Node.js ecosystem |
| Module | QuickJS module | Node.js module |

Related: [runtime modules](/spider/modules), [loading a spider](/spider/load).
