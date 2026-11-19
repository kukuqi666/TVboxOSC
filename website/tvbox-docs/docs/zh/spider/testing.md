---
description: 用 App 的爬虫测试页载入源码、选择方法、填入参数并查看回传。
---

# 测试爬虫

连线 App 的网页，从「设定 → 爬虫测试」开启 `/spider.html`。Python 与 QuickJS 需先[安装对应模组](/spider/modules)。

## 操作流程

1. 载入 Java JAR 并选择类别，或载入 `.py`／`.js`；`ext` 为选填。
2. 选择方法，以 JSON 阵列填入参数，执行并查看结果与网络纪录。
3. 修改后储存、重新载入并重测；可格式化来源或还原版本。
4. `play` 回传有效结果后，送到 App 确认播放。

## 限制

- App 不编译 Java，修改 `.java` 后需重新产生并载入 JAR。
- 测试逾时预设 30 秒，可设为 1–300 秒。
- 重新载入、取消或离开会清理测试工作。
- 来源若已被其他页面修改，先重新读取再储存。

:::warning
**先确认回传，再确认播放**

模组测试与正确的 URL 不代表播放成功，仍需在 App 确认画面、声音、字幕与弹幕。
:::

## 常见参数填法

方法参数依定义顺序填入 JSON 阵列，例如：

```json
["movie", "1", false, {}]
```

对应 `categoryContent(tid, pg, filter, extend)`。Java 的 `HashMap` 与 Python 的 `dict` 都直接以 JSON 物件传入。
