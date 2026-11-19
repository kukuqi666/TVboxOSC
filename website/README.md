# Website 目录

本目录集中维护配置、文档站源码和公共图片。

| 目录 | 职责 |
| --- | --- |
| `tvbox-config/` | 唯一配置发布根，含主入口、共享依赖和来源素材 |
| `tvbox-docs/` | Rspress 文档站源码，工作流从此处构建 |
| `images/` | README 及站点共用的原始图片，只维护这一份 |

原 `tvbox/` 已合并进 `tvbox-config/sources/qist/`。JAR 集中到 `tvbox-config/jar/`，相同内容只存一份，配置引用已经同步。来源的 JS/Python 包结构保留，用于运行时导入；目录分类与文件依赖见 `tvbox-config/README.md` 和 `tvbox-config/catalog.json`。

主入口仍是 `tvbox-config/tvbox.json`，R18 仍是 `tvbox-config/R18.json`。原 `tvbox/`、`18plus/` 和 `remote-jars/` 下的链接使用 catalog 的 aliases 查找新位置，不再维护重复发布根或软链接。

## 校验与构建

在仓库根执行：

```sh
python3 scripts/audit_configs.py
cd website/tvbox-docs
pnpm install --frozen-lockfile
SITE_URL=https://kukuqi666.github.io/TVboxOSC/ pnpm build
```

修改文件或引用后先执行 `python3 scripts/audit_configs.py --write`。检查会阻止新增断引用、MD5 不匹配、重复文件和过期索引。上游原有缺失已逐项登记，不代表相应来源完整可用。

构建会先检查依赖、生成旧导航页并复制统一配置及公共图片，再输出到 `tvbox-docs/doc_build/`。GitHub Actions 保存 Pages 产物，并在已启用 Pages 的仓库中于 main 构建成功后部署。配置在文档站 `/configs/` 下同步提供；配置中的 raw 地址仍以 main 为准。

旧 `website/scripts/` 和审计报告已收拢为根目录 `scripts/audit_configs.py` 与公开 `catalog.json`，不再维护多份相互矛盾的清单。

公共图片在站点 /images/ 下提供；README 使用 website/images/ 相对路径。aa.png 是壁纸示例，bb.png 是文件加速示例，logo.png 保留原始标志。tvbox-docs/docs/public/images/ 是构建副本，已忽略，不手工维护。
