---
description: Key-value reads and writes on /cache, with rule namespaces.
---

# /cache: Key-Value Cache

Use GET or a form POST. The stored key is `"cache_" + (rule empty ? "" : rule + "_") + key`; `rule` isolates namespaces for different spiders.

| `do` | Parameters | Response |
| --- | --- | --- |
| `get` | `key`, `rule` (optional) | The stored string; empty when missing. |
| `set` | `key`, `value`, `rule` (optional) | Writes the string and returns OK. |
| `del` | `key`, `rule` (optional) | Removes the key and returns OK. |

```sh
curl -G "http://127.0.0.1:9978/cache" --data-urlencode "do=set" --data-urlencode "rule=demo" --data-urlencode "key=greeting" --data-urlencode "value=Hello"

curl "http://127.0.0.1:9978/cache?do=get&rule=demo&key=greeting"

curl "http://127.0.0.1:9978/cache?do=del&rule=demo&key=greeting"
```

## Key composition

| Combination | Stored key |
| --- | --- |
| `rule=demo`, `key=greeting` | `cache_demo_greeting` |
| `rule` omitted, `key=greeting` | `cache_greeting` |

:::tip
This is the App-level generic key-value store. It is not the same storage as the spider's [`net.cached`](/spider/cache), which is a per-site TTL cache.
:::

## Related

- Spider cache and Local: [cache and storage](/spider/cache)
- File transfers: [file management](/local/files)
