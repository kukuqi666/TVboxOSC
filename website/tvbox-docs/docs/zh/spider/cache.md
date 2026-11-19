---
description: TTL 快取的 key、stale 与 persist 用法，以及按站点隔离的 Local 储存。
---

# 快取与储存

## TTL 快取

`net.cached(key, options, loader)` 按站点快取 JSON，同 key 合并载入。

| 选项 | 预设 | 说明 |
| --- | --- | --- |
| `ttl` | 必填 | 有效毫秒数。 |
| `stale` | `true` | 过期先回旧值并背景更新。 |
| `persist` | `false` | 启用时持久化公开资料。 |

播放网址使用短 TTL、`stale: false`，不持久化。

`peek(key)` 只读已有值，包括过期值；`refresh(key, options, loader)` 立即返回，依 TTL 背景更新；`clearCache(key)` 清一笔，省略 key 清除目前站点全部 TTL 快取并取消载入。

```js
// QuickJS：分类列表快取 10 分钟
async function category(tid, pg) {
  const data = await net.cached(`${tid}:${pg}`, { ttl: 600000 }, async () => {
    return await net.json(`https://example.com/list?tid=${tid}&pg=${pg}`);
  });
  return data;
}
```

Python 的 `self.net.cachedText(key, options, loader)` 取得 JSON 原文；`cached` 则解码为 Python 值，两者共用 TTL 与清除规则。Java 的 `cached` 回传 JSON 字串。

## Local

小型设定用 Java 的 `local`、Python 的 `self.local` 或 QuickJS 的 `local`。以 `get(key)`／`set(key, value)`／`delete(key)` 读写，App 按站点隔离；Java 使用 JSON 字串，Python／QuickJS 使用 JSON 值。清 TTL 快取不会删除 Local。

```py
# Python：记录上次使用的镜像站
last = self.local.get("mirror") or "https://example.com"
self.local.set("mirror", "https://mirror.example.com")
```

## 选择哪一种

| 需求 | 用 |
| --- | --- |
| HTTP 结果可过期重取 | TTL 快取 |
| 小型设定、跨次执行保留 | Local |
| 大量或二进制档案 | 档案系统 + [`net.download`](/spider/network) |
