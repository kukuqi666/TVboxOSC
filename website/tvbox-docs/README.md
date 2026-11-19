# TVboxOSC 文档站

Rspress 双语文档与配置导航的唯一源码入口。

```sh
cd website/tvbox-docs
pnpm install --frozen-lockfile
pnpm dev
SITE_URL=https://kukuqi666.github.io/TVboxOSC/ pnpm build
pnpm preview
```

构建输出为 `doc_build/`。构建前自动运行仓库根 `scripts/audit_configs.py`，将唯一配置发布目录 `../tvbox-config/` 复制到生成的 `docs/public/configs/`，并从 `legacy-src/` 生成导航页。

文档源码在 `docs/zh/` 与 `docs/en/`；同名页面保持对应，导航由各目录 `_meta.json` 管理。主题在 `theme/`。不要提交 `node_modules/`、`doc_build/`、生成的配置副本或 `docs/public/legacy/index.html`。

配置和 JAR 的类别与引用见 `../tvbox-config/README.md`、`../tvbox-config/catalog.json`。更改配置后在仓库根运行 `python3 scripts/audit_configs.py --write`。GitHub Actions 使用本目录构建并保存 Pages 产物，已启用 Pages 时在 main 构建成功后部署。
