---
description: /device returns device info used for discovery, casting and sync.
---

# /device: Device Info

`/device response`

```json
{
  "uuid": "demo-android-id",
  "name": "Sample TVboxOSC device",
  "ip": "http://192.0.2.10:9978",
  "type": 0,
  "serial": "",
  "eth": "",
  "wlan": "",
  "time": 1788825600000
}
```

The content is a JSON string with a `text/plain` content type. Never put device identifiers or MAC addresses in public samples; the table below only explains each field.

| Field | Type | Notes |
| --- | --- | --- |
| `uuid` | string | Android ID used by this App device. |
| `name` | string | Device display name. |
| `ip` | string | Full service address including `http://`, the LAN IP and the actual port. |
| `type` | integer | 0=TV build, 1=phone build. The Device object also uses 2 for DLNA devices, but the local `/device` never returns that value. |
| `serial` | string | Device serial as obtainable by the system; availability depends on the device and permissions. |
| `eth` | string | MAC address of `eth0`; not guaranteed on every device. |
| `wlan` | string | MAC address of `wlan0`; not guaranteed on every device. |
| `time` | integer | Unix time in milliseconds when the App instance was created, not the time of this HTTP response. |
| `id` | integer | Database identifier; `/device` builds an unsaved Device, so this field is normally omitted. |

## Where it is used

- **Discovery**: phones list TVs on the LAN that accept casting.
- **Casting**: the `device` parameter of `/action?do=cast`.
- **Sync**: the `device` parameter of `/action?do=sync`.

See [actions](/local/action) and [device sync](/local/sync).

:::warning
`uuid`, `serial` and MAC addresses are device identifiers. Do not paste them into public issues or screenshots.
:::
