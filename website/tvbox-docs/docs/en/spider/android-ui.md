---
description: Get the Application Context and Activity through Runtime and show toasts.
---

# Android UI

The shared SDK's `com.github.catvod.Runtime` exposes `Runtime.context()` for the Application Context, `Runtime.activity()` for the current Activity and `Runtime.toast()` for text toasts. Java must import the class explicitly; Python obtains it with `jclass`. The host provides these interfaces, and spider JARs depend on the SDK with `compileOnly`.

```java
import com.github.catvod.Runtime;

Runtime.toast("Switched to the backup line");
```

```py
# Python: obtain Runtime with jclass
Runtime = jclass("com.github.catvod.Runtime")
Runtime.toast("Switched to the backup line")
```

## Activity and getActivity

Java／Python spiders extending Spider can also call `getActivity()`／`self.getActivity()`. The Activity may be `null`／`None`; only fetch it when showing a custom dialog on the main thread, and never keep a page reference. `Runtime.toast()` is dispatched to the main thread by the App.

QuickJS can return a `msg` field from `action` to show a toast.

## Initialization entry

The JAR's own `com.github.catvod.spider.Init.init(Context)` is an optional initialization entry, separate from `Runtime`; each spider is still initialized by the App through `Spider.init(Context, ext)`.

:::warning
Never hold long-lived references to an Activity or View. They become stale after navigation or rotation and can leak memory.
:::
