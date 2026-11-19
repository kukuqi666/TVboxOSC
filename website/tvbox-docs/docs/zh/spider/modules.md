---
description: 在 App 的模组页安装 Python、QuickJS、Node.js、mpv、Thunder 与 BD-J 模组。
---

# 安装执行模组

在 App 的模组页安装 Python、QuickJS 或 Node.js 的独立 APK。Thunder、mpv 与 BD-J 也在此管理，按需要安装。

## 安装与更新

选择模组、下载并完成系统安装。版本需与 App 的模组接口及装置 ABI 相容。

## 测试与重启

安装后执行模组测试，确认能初始化。若提示重新启动，重开 App 后再载入爬虫。

:::warning
QuickJS 使用 QuickJS-NG 引擎，不能直接执行 Node.js 套件；Node.js 请依[独立载入方式](/spider/node)设定。使用新接口时，请一并更新 App 与对应模组。
:::

## 各语言需要的模组

| 语言 | 需要安装 | 说明 |
| --- | --- | --- |
| Java JAR | 不需要 | 由 App 载入 JAR，但 App 不编译 Java。 |
| Python | Python 模组 | 输出继承 `base.spider.Spider` 的类别。 |
| QuickJS | QuickJS 模组 | ES module，预设汇出物件。 |
| Node.js | Node.js 模组 | 以 `index.js.md5` 作为配置入口，见 [Node.js 载入](/spider/node)。 |
| 播放 | mpv（选用） | 使用 mpv 引擎或 Lua 外挂时需要。 |
| 其他 | Thunder、BD-J | 按需要安装。 |
