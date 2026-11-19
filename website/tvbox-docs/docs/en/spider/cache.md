---
description: TTL cache keys with stale and persist options, plus per-site Local storage.
---

# Cache and Storage

## TTL cache

`net.cached(key, options, loader)` caches JSON per site and coalesces loads for the same key.

| Option | Default | Purpose |
| --- | --- | --- |
| `ttl` | required | Validity in milliseconds. |
| `stale` | `true` | Return the stale value first and refresh in the background. |
| `persist` | `false` | Persist public data when enabled. |

Playback URLs should use a short TTL and `stale: false`, without persistence.

`peek(key)` reads an existing value, including expired ones; `refresh(key, options, loader)` returns immediately and refreshes in the background according to the TTL; `clearCache(key)` clears one entry, and omitting `key` clears every TTL entry for the current site and cancels loads.

```js
// QuickJS: cache a category list for ten minutes
async function category(tid, pg) {
  const data = await net.cached(`${tid}:${pg}`, { ttl: 600000 }, async () => {
    return await net.json(`https://example.com/list?tid=${tid}&pg=${pg}`);
  });
  return data;
}
```

Python's `self.net.cachedText(key, options, loader)` returns raw JSON text while `cached` decodes to a Python value; both share TTL and clearing rules. Java's `cached` returns a JSON string.

## Local

Use `local` in Java, `self.local` in Python or `local` in QuickJS for small settings. Read and write with `get(key)`／`set(key, value)`／`delete(key)`; the App isolates it per site. Java uses JSON strings, Python／QuickJS use JSON values. Clearing the TTL cache never touches Local.

```py
# Python: remember the last mirror used
last = self.local.get("mirror") or "https://example.com"
self.local.set("mirror", "https://mirror.example.com")
```

## Which one

| Need | Use |
| --- | --- |
| HTTP results that may expire | TTL cache |
| Small settings that survive restarts | Local |
| Large or binary files | File system + [`net.download`](/spider/network) |
