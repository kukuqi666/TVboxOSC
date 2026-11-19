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
| Python 来源 | 同步隔离导入、失败释放、网络归属及 Cookie/UA | 第三方 Python 扩展与具体站点逻辑 |
| Node bundle | 校验和、独立进程、HTTP 路由、重启、二进制代理 | Node 弹幕推送尚未接入当前 UI |
| Node 宿主消息 | 网页、提示、Cookie/UA、播放信息已连接 | 完整播放场景及第三方交互需设备验证 |
| 测试台 | 每个会话独享加载器，清理不再销毁正式来源 | 全局加载器尚未完整迁移参考实现的 FutureTask/retire 并发生命周期 |
| 配置 | 支持普通配置、urls/storeHouse 仓库、zyplayer data 结构 | 加密 JAR 的动态爬虫及外网站点需逐项运行验证 |
| 播放与 UI | 保留现有 UI 和播放器适配 | 模块管理没有提供独立 MPV APK，不能按参考文档假定它已可安装 |

JAR 中的加密资源不能仅靠静态 DEX 判断可用性。配置审计将它们列为警告，详细条目见配置发布目录的 `catalog.json`，不会显示为完整播放验收通过。
