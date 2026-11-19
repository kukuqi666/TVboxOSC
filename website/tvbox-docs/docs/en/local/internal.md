---
description: Behavior of the internal /proxy, /parse, /image and /tvbus endpoints.
---

# Proxy and Internal Endpoints

| Endpoint | Parameters and behavior |
| --- | --- |
| `/proxy` | Merges query／form parameters, request headers and `files` from `parseBody` in that order, then hands the result to `BaseLoader.proxy`; later values win on duplicate keys. |
| `/parse` | `jxs` is a semicolon-separated list of parse prefixes and `url` is the target URL. `parse.html` builds an iframe per prefix + `url` and returns `text/html`. |
| `/image/{key}` | Serves image data already cached by `ImgUtil` using the image's own MIME type; 404 when the key is unknown. This is not a generic image download API. |
| `/tvbus` | Returns the RESP string of the current live configuration's `Core`, not a channel list. |
| `/` | Serves the App's built-in `index.html` control page; other unmatched paths try built-in resources and return 404 when missing. |

`/proxy` does not flatten every POST body into parameters: plain forms arrive in `parms`, multipart bodies expose temporary file info, and the raw body follows NanoHTTPD's `parseBody` result. The spider's response decides status, Content-Type, stream and headers; an invalid response or exception returns 500.

```sh
curl -G "http://127.0.0.1:9978/parse" --data-urlencode "jxs=https://example.com/parse?url=" --data-urlencode "url=https://example.com/watch/demo-1"
```

For proxy response formats and per-language differences see [spider proxy response](/spider/proxy); for RESP fields see the [Core configuration](/config/core).

## Pages

| Path | Purpose |
| --- | --- |
| `/index.html` | Web console home. |
| `/remote.html` | Standalone remote. |
| `/spider.html` | Spider test page, see [testing a spider](/spider/testing). |
| `/logs.html` | Log viewer, see [logs](/local/logs). |
| `/parse.html` | Builds parse iframes from the `jxs` and `url` parameters. |

:::warning
`/proxy` and `/parse` execute spider logic based on request content. They have no additional access control and should only be used on trusted LANs.
:::
