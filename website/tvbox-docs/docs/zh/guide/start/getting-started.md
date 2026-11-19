---
description: 取得 Node.js、安装依赖、启动文件站，以及构建与部署到 GitHub Pages。
---

# 快速开始

## 环境需求

需要 Node.js 22.18+（22.x）或 Node.js 24+。

## 启动文件站

```sh
cd website/tvbox-docs
pnpm install
pnpm dev
```

开发服务器会显示本机网址，修改 `.md` 后浏览器会自动刷新。

## 构建与预览

```sh
pnpm build
pnpm preview
```

`pnpm build` 会输出静态网站到 `doc_build/`；`pnpm preview` 依构建结果启动本机预览。正式部署只需要 `doc_build/` 内的文件，不需要 Node.js、Worker 或资料库。

:::tip
指定的域名或路径可用 `SITE_URL` 在构建时覆写，例如 `SITE_URL=https://kukuqi666.github.io/TV/ pnpm build`。更换路径需重新构建。
:::

## 目录结构

```text
tvbox-docs/
├── rspress.config.ts     # 站点标题、语言、Logo 与构建选项
├── theme/                # 可选的自定义主题与样式
├── docs/
│   ├── public/           # Logo 等静态资源
│   ├── zh/               # 简体中文文档
│   │   ├── _nav.json     # 顶部导航
│   │   ├── guide/        # 开始使用
│   │   ├── spider/       # 爬虫介接
│   │   ├── config/       # 配置字典
│   │   ├── local/        # 本地 API
│   │   ├── features/     # App 功能
│   │   └── commands/     # 播放指令
│   └── en/               # 英文文档，结构同 zh/
└── package.json
```

## 每个目录的两个配置档

- `_nav.json`：顶部导航，放在语言根目录，例如 `docs/zh/_nav.json`。
- `_meta.json`：侧边栏顺序与分组，放在每个目录内。

新增一页 `.md` 后，记得在所属目录的 `_meta.json` 加入条目，否则侧边栏不会显示。

## 部署到 GitHub Pages

1. 在 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**。
2. 把 `website/tvbox-docs/` 或对应的 workflow 更新推送到 `fongmi`。
3. 流程会安装依赖、构建静态网站，并发布 `doc_build/`。

## 下一步

- 了解[爬虫怎么载入](/spider/load)，或直接看[三语言 Demo](/spider/demo)。
- 需要配置栏位时查[配置字典](/config/examples)。
- 用脚本控制播放，看[本地 API](/local/connection)。
