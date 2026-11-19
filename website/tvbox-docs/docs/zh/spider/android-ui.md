---
description: 用 Runtime 取得 Application Context 与 Activity，并用 toast 显示提示。
---

# Android UI

共用 SDK 的 `com.github.catvod.Runtime` 以 `Runtime.context()` 取得 Application Context、`Runtime.activity()` 取得目前 Activity，`Runtime.toast()` 显示文字提示。Java 需明确汇入这个类别；Python 使用 `jclass` 取得。这些接口由宿主提供，爬虫 JAR 使用 `compileOnly` 依赖 SDK。

```java
import com.github.catvod.Runtime;

Runtime.toast("已使用备用线路");
```

```py
# Python：以 jclass 取得 Runtime
Runtime = jclass("com.github.catvod.Runtime")
Runtime.toast("已使用备用线路")
```

## Activity 与 getActivity

继承 Spider 的 Java／Python 爬虫也可使用 `getActivity()`／`self.getActivity()`。Activity 可能为 `null`／`None`；自订 Dialog 在主执行绪显示时才取得 Activity，不保存页面引用。`Runtime.toast()` 由 App 切到主执行绪显示。

QuickJS 的文字提示可从 action 回传 `msg` 栏位。

## 初始化入口

JAR 自己的 `com.github.catvod.spider.Init.init(Context)` 是可选的初始化入口，与 `Runtime` 分开；每个爬虫仍由 App 呼叫 `Spider.init(Context, ext)`。

:::warning
不要保存 Activity 或 View 的长期引用。换页、旋转萤幕后旧引用会失效，并可能造成内存泄漏。
:::
