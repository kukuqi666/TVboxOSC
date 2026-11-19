---
description: 安装与宿主签名、模块接口及设备 ABI 匹配的独立运行模块。
---

# 安装执行模块

在“我的 → 模块管理”安装 Python、QuickJS、Node.js、Thunder 或 BD-J。当前界面和订阅数据库沿用 TVboxOSC，运行模块与主包分别安装。

## 安装与更新

模块必须与主包签名一致，并声明匹配的 `tvbox.runtime.contract`。当前开发代码使用以下接口；已经发布的 3.1.1 附件保持原状，不能把新接口的 APK 覆盖到旧版发布目录。

| 模块 | 开发版本 | 接口 | 执行入口 |
| --- | --- | --- | --- |
| QuickJS | 1.1.0 / code 5 | `tvbox-quickjs-v2` | 普通 JS / ES module |
| Python | 1.1.0 / code 4 | `tvbox-python-v2` | 继承 `base.spider.Spider` |
| Node.js | 1.1.0 / code 4 | `tvbox-nodejs-v2` | `index.js.md5` bundle |
| Thunder | 沿用现有版本 | `tvbox-thunder-v1` | 下载与磁力解析 |
| BD-J | 沿用现有版本 | `tvbox-bdj-v1` | Java 运行环境 |

主包及模块的自有包名使用 `com.kukuqi`。共享 Spider 协议仍保留 `com.github.catvod`，以兼容现有 JAR。QuickJS 的 Java 包和 JNI 从固定源码配套重建，不能只改 Java 包名后继续使用旧原生库。

安装后执行模块测试。如果已加载模块被更新，需要重启应用，避免旧 Java 类与新原生库混用。启动时会清理废弃的 `code_cache/runtimes/{quickjs,python,nodejs}`、`no_backup/runtimes/{quickjs,python,nodejs}` 与旧 `files/nodejs/server.cjs`，订阅和用户脚本不在清理范围内。

## 对照范围与剩余差距

2026-11-19 核对的参考代码为 FongMi/TV `5bf18d6`、CatVodSpider `ab5b005`。这里按能力记录，不能用文件数量推算兼容百分比。

| 能力 | 本仓状态 | 仍需验证或完善 |
| --- | --- | --- |
| 模块发现、签名、ABI、版本与接口验证 | 宿主与 SDK 共用一套 Provider 和加载器 | 每次正式发布仍需签名产物验证 |
| QuickJS Java/JNI 与 SDK 求值 | 统一为 kukuqi 包，支持独立来源会话 | 更多第三方脚本的实际业务兼容 |
| Python 来源 | 隔离导入、网络归属、Cookie/UA；固定 cryptography、cffi、NumPy、pywasm 等扩展与配套 CPython 原生库 | 具体站点逻辑和额外第三方扩展仍需单独验证 |
| Node bundle | 校验和、独立进程、HTTP 路由、重启、二进制代理；danmuPush/clearDanmu 接入现有弹幕视图 | 不改变用户弹幕开关，切换播放器时丢弃旧目标更新 |
| Node 宿主消息 | 网页、提示、Cookie/UA、播放信息已连接 | 完整播放场景及第三方交互需设备验证 |
| 来源生命周期与测试台 | JAR/JS/Python/Node 共用 FutureTask 并发去重；取消加载、初始化失败释放、关闭与销毁串行；旧代后台销毁 | 外部 JAR 自身的 close/destroy 行为仍由其实现负责 |
| 配置 | 普通配置、urls/storeHouse 仓库、zyplayer data 结构；本地 JS 文件与相对模块导入；旧 QuickJS 引用列为审计错误 | 35 条加密 JAR 动态类警告及外网站点仍需逐项运行验证 |
| 播放与 UI | 保留现有 UI、Media3/MPV 适配和软硬解设置；清除不再实例化的 IJK 源码、JNI 调用及演示资源 | 尚未分发独立 MPV APK，BD-J 仍不等于完整交互式蓝光菜单 |

JAR 中的加密资源不能仅靠静态 DEX 判断可用性。配置审计将它们列为警告，详细条目见配置发布目录的 `catalog.json`，不会显示为完整播放验收通过。

## 配置 JAR 与依赖清理

两份配置包 `957f4f3ee9526dcc8a0d1d6d-custom_spider.jar`、`spider-b4886fad7b.jar` 原来仍使用旧 QuickJS 类型，无法与 kukuqi 包的模块链接。通过 smali-dexlib2 3.0.9 逐类型迁移并重新生成 DEX，迁移前验证了调用到的 wrapper 类型、方法及构造器。原有 275、3229 个 DEX 类及站点资源保持完整；派生包去掉已失效的原始签名元数据，输入、输出、wrapper 摘要及迁移方法记录在配置 `catalog.json` 的 `jar_migrations`。

JAR 的父加载器经过共享 SDK 的模块路由，允许解析独立 QuickJS 模块提供的类型。显式声明的 JAR MD5 在下载或读取本地文件后也会校验；初始化失败不再被缓存成成功的空来源。静态审计同时扫描 DEX 类型和反射字符串，阻止旧包名重新混入。

废弃 IJK 的实现、演示布局、DNS 原生调用及混淆规则已移除。保留当前 DKPlayer 视图、CorePlayer、Media3 和 MPV。旧下载工具统一为 `com.github.catvod.net.Download`；未使用的生物验证依赖和只提供颜色常量的旧库已移除，原界面颜色值保留。

## Python 构建输入

Python 使用 Chaquopy 17.2.0 和 CPython 3.10。`engine/chaquo/runtime.lock.json` 固定参考原生包的来源提交、发布标签、清单摘要和 9 个扩展 wheel。`requirements.txt` 固定其余 Python 包版本。构建需要 Python 3.10、JDK 21 或以上、Android SDK/NDK，以及 `patchelf`。

```bash
python3 scripts/prepare_python_runtime.py --sync
TVBOX_BUILD_PYTHON=python3.10 ./gradlew :runtime-python:assembleDebug
```

同步和构建都会验证输入摘要。模块消费最终转换的 AAR，统一 CPython、Chaquopy 桥接、stdlib 扩展和加密库，替换 requirements 内的 C++ 库为宿主播放器使用的同一内容，并重新生成资产摘要。校验 ARM64 ELF 和 16 KB LOAD 对齐，保留库内许可证与固定输入来源。当前 Python 模块发布 ABI 仍为 `arm64-v8a`，不能把它安装到仅支持 32 位的设备。

配置整理后执行 `python3 scripts/audit_configs.py --write`。若需要重新迁移指定配置 JAR，可执行 `python3 scripts/migrate_config_jars.py website/tvbox-config/jar/文件名.jar`；工具在验证 SDK 后重建 DEX，并更新消费配置的 MD5，不能使用原始二进制字符串替换。

## 本轮验证

2026-11-19：107 项 JVM 单测、39 项脚本测试、60 项普通主包设备回归、38 项混淆主包关键回归通过。普通与 R8 主包构建、Python/QuickJS Release 模块构建、文档构建均通过。设备用例包含真实 JAR 的 JS HTML 解析桥接、Python AES-GCM/NumPy/WASM 实算、Node bundle/代理，以及现有界面的弹幕推送与清空。混淆回归使用普通测试 APK，避免测试依赖自身的裁剪或类名变化混入宿主验证。

配置审计覆盖 2301 个文件、32 个 JAR、2375 条本地依赖，重复、缺失、MD5 和结构错误为 0；35 条加密 JAR 动态类警告仍保留。外部订阅返回 200 只代表本次可读取，不等同于所有站点均可播放。
