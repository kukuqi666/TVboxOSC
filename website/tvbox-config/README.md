# TVBox 统一配置发布目录

主入口：`tvbox.json`；R18：`R18.json`。两者保持原公开路径。

| 目录 | 内容与依赖 |
| --- | --- |
| 根目录 JSON | 本站配置入口，spider 默认 JAR 可被 sites[].jar 覆盖 |
| `jar/` | 本地 Spider JAR；按内容去重，同名不同版本保留校验值后缀 |
| `js/` | 本站共享 JS 与 drpy 运行依赖 |
| `live/` | 本站直播列表，包含从伪 PNG 归类的文本 |
| `18/`、`legacy/` | 现有子仓与历史配置入口 |
| `sources/qist/` | qist/tvbox 配置、规则与脚本 |
| `sources/zyjk/` | Dong-learn9/TVBox-zyjk 配置、规则与脚本 |
| `sources/tvbox18/` | qirenzhidao/tvbox18 配置、规则与脚本 |
| `sources/cliya/` | cliya/TVBox 来源包 |
| `sources/xiongjian83/` | xiongjian83/TvBox 来源包 |
| `docs/` | 保留的上游说明与归属声明 |
| `catalog.json` | 文件类别、MD5/SHA-256、正反向依赖、旧路径映射及已知缺失 |

## 引用规则

- `spider` 提供配置默认 JAR，单个站点的 `jar` 字段优先覆盖；JS/Python 来源通过 `api` 和 `ext` 指向脚本或规则。
- 本仓已有依赖统一引用同一份 raw 文件，移动或去重后必须同时更新消费者；JAR 内字节不变，MD5 按实际文件校验。
- 来源包里的 JS/Python 子目录保留运行所需的结构；不能只凭文件名相同就合并，不同内容的同名 JAR 不能覆盖。
- catalog 的 `files[].references` 是正向依赖，`files[].used_by` 是反向消费者；`external_jars` 单独记录仍由外部提供的 JAR，未验证其在线可用性。
- `aliases` 将旧的 website/tvbox、18plus 和 remote-jars 文件位置映射到唯一现存文件，不发布软链接别名。

## 本地 JAR 与直接消费者

以下索引由实际文本引用生成。完整消费者及多级引用链见 catalog；它不能分析 JAR 内加密代码的动态资源请求。

| JAR | 直接消费者数 | 示例配置/脚本 |
| --- | ---: | --- |
| `jar/0.jar` | 1 | `sources/zyjk/py/pluto.json` |
| `jar/0180b8e24333d78a249dce6a-pc0330.jar` | 1 | `sources/xiongjian83/18/19.json` |
| `jar/18.jar` | 2 | `sources/zyjk/19.json`、`sources/zyjk/tvbox1.json` |
| `jar/6b727c38e839f6f31a1229c1-HeChengChaXiu.jar` | 1 | `sources/xiongjian83/X.json` |
| `jar/957f4f3ee9526dcc8a0d1d6d-custom_spider.jar` | 1 | `sources/xiongjian83/18/19.json` |
| `jar/XBPQ.jar` | 1 | `sources/zyjk/tvbox2.json` |
| `jar/XBPQ_upgraded.jar` | 1 | `sources/qist/0825.json` |
| `jar/XYQ.jar` | 1 | `sources/qist/XYQ.json` |
| `jar/XYQH.jar` | 2 | `sources/xiongjian83/X.json`、`sources/zyjk/tvbox2.json` |
| `jar/Yoursmile.jar` | 9 | `sources/tvbox18/XYQTVBox/Yoursmile/XC.json`、`sources/zyjk/19.json`、`sources/zyjk/js.json` 等 |
| `jar/a0ed72665ec77e2ee8d2ed0b-xc0126.jar` | 1 | `sources/xiongjian83/18/19.json` |
| `jar/custom_spider-646c5449e0.jar` | 1 | `sources/qist/0827.json` |
| `jar/custom_spider-7c994f68b7.jar` | 1 | `sources/tvbox18/XYQTVBox/drpy_js/drjs_config.json` |
| `jar/custom_spider.jar` | 1 | `sources/tvbox18/XYQTVBox/XYQTVBox.json` |
| `jar/douban.jar` | 1 | `jhkx-full.json` |
| `jar/fan-170b3430a9.jar` | 1 | `sources/zyjk/tvbox2.json` |
| `jar/fan-bfd1ef131e.jar` | 3 | `sources/qist/0821.json`、`sources/qist/0826.json`、`sources/qist/fty.json` |
| `jar/fan-cae8c128c4.jar` | 1 | `sources/xiongjian83/X.json` |
| `jar/fan.jar` | 1 | `sources/tvbox18/XYQTVBox/fty/fty.json` |
| `jar/fix1110.jar` | 1 | `sources/tvbox18/XYQTVBox/qiaoji/qjtvbox.json` |
| `jar/fm.jar` | 2 | `sources/zyjk/js.json`、`sources/zyjk/tvbox1.json` |
| `jar/fm_push.jar` | 1 | `sources/tvbox18/adult.json` |
| `jar/int.jar` | 1 | `sources/xiongjian83/X.json` |
| `jar/ok.jar` | 1 | `sources/tvbox18/ok/ok.json` |
| `jar/pbdb.jar` | 1 | `tvbox.json` |
| `jar/pg-16c7e5e159.jar` | 2 | `sources/tvbox18/pg/jsm.json`、`sources/tvbox18/tv.json` |
| `jar/pg-77875c266d.jar` | 1 | `sources/tvbox18/app.json` |
| `jar/pg23a94bb_upgraded.jar` | 1 | `sources/qist/99188.json` |
| `jar/pg_upgraded.jar` | 3 | `sources/qist/0825.json`、`sources/qist/9918.json`、`sources/qist/js.json` |
| `jar/spider-b4886fad7b.jar` | 3 | `sources/qist/dianshi.json`、`sources/qist/jsm.json`、`sources/qist/xiaosa/api.json` |
| `jar/spider.jar` | 2 | `jhkx-full.json`、`tvbox.json` |
| `jar/xbxy.jar` | 1 | `sources/xiongjian83/X.json` |

## 已知上游缺失

34 条相对依赖在导入的上游素材中本就缺失，涉及 13 份配置；本站 raw 引用没有断链，MD5 校验无误。默认主入口与 R18 没有这些本地缺失。完整逐项记录在 catalog 的 `upstream_missing`，这些配置不能作为完整验收通过的来源。

## 维护

在仓库根运行 `python3 scripts/audit_configs.py --write` 更新索引，再运行不带参数的同一命令复核。CI 会对完整目录重新计算校验值和依赖，阻止新增缺失或重复文件。不要重新建立 tvbox 第二发布根、remote-jars 副本或嵌套 Git 仓库。
