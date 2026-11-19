---
description: /device 回传装置资讯，用于装置探索、投放与同步。
---

# /device：装置资讯

`/device 回应`

```json
{
  "uuid": "demo-android-id",
  "name": "TVboxOSC 示意装置",
  "ip": "http://192.0.2.10:9978",
  "type": 0,
  "serial": "",
  "eth": "",
  "wlan": "",
  "time": 1788825600000
}
```

内容为 JSON 字串，Content-Type 是 `text/plain`。不要把装置识别码或 MAC 位址填入公开范例；下方只列栏位用途。

| 栏位 | 类型 | 说明 |
| --- | --- | --- |
| `uuid` | string | 此 App 装置使用的 Android ID。 |
| `name` | string | 装置显示名称。 |
| `ip` | string | 完整服务位址，包含 `http://`、区域网络 IP 与实际埠号。 |
| `type` | integer | 0＝电视版；1＝手机版。Device 物件另以 2 表示 DLNA 装置，但本机 `/device` 不使用此值。 |
| `serial` | string | 系统可取得的装置序号；可用性受装置与权限限制。 |
| `eth` | string | `eth0` 的 MAC 位址；不保证每个装置都能取得。 |
| `wlan` | string | `wlan0` 的 MAC 位址；不保证每个装置都能取得。 |
| `time` | integer | App 实例建立时的 Unix 时间，单位毫秒；不是这次 HTTP 回应的时间。 |
| `id` | integer | 资料库识别码；`/device` 建立的是未储存的 Device，因此通常不输出此栏位。 |

## 用途

- **装置探索**：手机列出区域网络内可投放的电视。
- **投放**：`/action?do=cast` 的 `device` 参数。
- **同步**：`/action?do=sync` 的 `device` 参数。

见[动作指令](/local/action)与[装置同步](/local/sync)。

:::warning
`uuid`、`serial` 与 MAC 属于装置识别资讯，请勿贴进公开 issue 或截图。
:::
