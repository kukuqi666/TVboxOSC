---
description: Install Node.js, install dependencies, start the documentation site, build it and deploy to GitHub Pages.
---

# Quick Start

## Requirements

Node.js 22.18+ (22.x) or Node.js 24+ is required.

## Start the site

```sh
cd website/tvbox-docs
pnpm install
pnpm dev
```

The dev server prints a local URL, and the browser refreshes when you edit a `.md` file.

## Build and preview

```sh
pnpm build
pnpm preview
```

`pnpm build` writes the static site to `doc_build/`; `pnpm preview` serves that build locally. Deployment only needs the files in `doc_build/` — no Node.js server, worker or database.

:::tip
Override the deployed domain or path at build time with `SITE_URL`, for example `SITE_URL=https://kukuqi666.github.io/TV/ pnpm build`. Changing the path requires a rebuild.
:::

## Project layout

```text
tvbox-docs/
├── rspress.config.ts     # Site title, locales, logo and build options
├── theme/                # Optional custom theme and styles
├── docs/
│   ├── public/           # Static assets such as the logo
│   ├── zh/               # Simplified Chinese docs
│   │   ├── _nav.json     # Top navigation
│   │   ├── guide/        # Getting started
│   │   ├── spider/       # Spider integration
│   │   ├── config/       # Configuration reference
│   │   ├── local/        # Local API
│   │   ├── features/     # App features
│   │   └── commands/     # Playback commands
│   └── en/               # English docs, same structure as zh/
└── package.json
```

## Two config files per directory

- `_nav.json`: top navigation, placed in each locale root such as `docs/zh/_nav.json`.
- `_meta.json`: sidebar order and grouping, placed in each directory.

After adding a new `.md` page, add an entry to the `_meta.json` of its directory or it will not appear in the sidebar.

## Deploy to GitHub Pages

1. Open **Settings → Pages → Build and deployment → Source** and choose **GitHub Actions**.
2. Push changes under `website/tvbox-docs/` or the matching workflow to `fongmi`.
3. The workflow installs dependencies, builds the static site and publishes `doc_build/`.

## Next

- See [how a spider is loaded](/spider/load), or jump to the [three-language demo](/spider/demo).
- Look up fields in the [configuration reference](/config/examples).
- Control playback from scripts with the [local API](/local/connection).
