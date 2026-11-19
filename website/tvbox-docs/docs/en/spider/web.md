---
description: Return web from an action to open a page or a standalone content view, optionally closing after login.
---

# Open a Web Page

After a card's `action` fires, provide `web` at the top level of the response.

## Regular page

`action`

```json
{
  "web": {
    "url": "https://example.com/"
  }
}
```

## Standalone content view

`action`

```json
{
  "web": {
    "url": "https://example.com/game",
    "mode": "view"
  }
}
```

`url` is required and accepts only HTTP or HTTPS. `mode` omitted or `browser` opens a regular page; `view` opens a standalone content view that launches no external App and never auto-fullscreens. Both exit fullscreen first, then navigate back through page history, closing only when there is no history.

## Close automatically after login

`action`

```json
{
  "web": {
    "url": "https://example.com/login",
    "cookie": [
      {"keys": ["sid", "token"]},
      {"url": "https://login.example.com/", "keys": ["auth"]}
    ]
  }
}
```

`cookie` is an optional array; when a group omits `url` it uses the opened URL. Every `key` in a group must be non-empty, and any single group completing is enough. At least one named cookie must differ from before the page opened, and the current page must match that group's scheme, host and port — only then does the page close automatically. This condition does not mean the site verified the account.

| Field | Required | Notes |
| --- | --- | --- |
| `web.url` | yes | URL to open; HTTP／HTTPS only. |
| `web.mode` | no | `browser` (default) or `view`. |
| `web.cookie[].url` | no | Where that group's cookies live; defaults to `web.url`. |
| `web.cookie[].keys` | yes | Cookie names that must all be non-empty. |

:::warning
Auto-closing only proves cookies were written, not that login succeeded. When you need real authorization, verify it in the spider's own requests.
:::

Related: the Vod `action` field in [data objects](/spider/objects), [methods and timing](/spider/lifecycle).
