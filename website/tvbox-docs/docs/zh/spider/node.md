---
description: 以 index.js.md5 作为配置入口载入 Node.js bundle，并用 HTTP JSON 处理站点路由。
---

# Node.js 载入

一般 `./demo.js` 仍由 QuickJS 执行。Node.js 必须以 `index.js.md5` 作为配置入口，同目录提供 `index.js`、`index.config.js` 与 `index.config.js.md5`。

```text
node-demo/
├── index.js             # 入口
├── index.js.md5         # 配置入口：Site.api 指向这个档
├── index.config.js      # /config 回应的站点清单
└── index.config.js.md5
```

## 载入流程

1. App 启动 bundle 后，读取 `/config` 回应的 `video` 或 `data.video`。
2. 把 `video.sites` 映射为 `node:` 站点路由。
3. 爬虫以 HTTP JSON 处理接在各站点路由后的 `/init`、`/home`、`/category`、`/detail`、`/search` 与 `/play`。

```json
{
  "key": "node_demo",
  "name": "Node Demo",
  "type": 3,
  "api": "./node-demo/index.js.md5"
}
```

:::danger 不要只替换 api 前缀
Node.js 不是 QuickJS 的 `export default` 物件模式；单独把普通配置的 `api` 改成 `node:`，不会完成 Node bundle 初始化。
:::

## 与 QuickJS 的差异

| 项目 | QuickJS | Node.js |
| --- | --- | --- |
| 入口 | `./demo.js` | `./index.js.md5` |
| 方法呼叫 | 直接呼叫物件方法 | HTTP JSON 路由 |
| 套件 | 不能直接执行 Node.js 套件 | 可用 Node.js 生态 |
| 模组 | QuickJS 模组 | Node.js 模组 |

相关：[安装执行模组](/spider/modules)、[载入爬虫](/spider/load)。
