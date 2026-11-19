---
description: Browse, upload, download, rename, move and delete device files over HTTP.
---

# File Management

Relative paths resolve against the Android shared external storage root `Path.root()`, not the App's private data directory; actual access is still limited by system storage permissions.

## Endpoints

| Endpoint | Method | Parameters and behavior |
| --- | --- | --- |
| `/file/{path}` | GET | Browse a directory or download a file; `/file` is the shared storage root. |
| `/upload` | POST `multipart/form-data` | `path` is required and the directory must exist; files go in a multipart field. |
| `/newFolder` | GET / POST | `path` is an existing parent directory, `name` the new folder name. |
| `/rename` | POST form | `path` is an existing file or directory, `name` the new name; the target must not exist. |
| `/move` | POST form | `path` is the source, `target` an existing destination directory; the name is kept, existing names are not overwritten, and a directory cannot move into itself. |
| `/delFolder` | GET / POST | `path` required; recursively deletes the item, and the storage root cannot be deleted. |
| `/delFile` | GET / POST | Shares the delete logic with `/delFolder` and is not limited to plain files. |

```sh
curl "http://127.0.0.1:9978/file/TV"

curl -G "http://127.0.0.1:9978/newFolder" --data-urlencode "path=TV" --data-urlencode "name=demo-empty"
```

`Sample response for /file/TV`

```json
{
  "parent": "",
  "files": [
    {
      "name": "demo.mp4",
      "path": "/TV/demo.mp4",
      "time": "2026/09/08 12:00:00",
      "dir": 0
    },
    {
      "name": "subtitles",
      "path": "/TV/subtitles",
      "time": "2026/09/08 12:00:00",
      "dir": 1
    }
  ]
}
```

| Field | Purpose |
| --- | --- |
| `parent` | `.` at the root; an empty string when the parent is the root; otherwise the parent's relative path. |
| `files[].name` | File or directory name. |
| `files[].path` | Path from the root, usually with a leading `/`, such as `/TV/demo.mp4`. |
| `files[].time` | Last modified time formatted as `yyyy/MM/dd HH:mm:ss` in the device time zone. |
| `files[].dir` | 1 for a directory, 0 for a regular file. |

## Downloads, Range and caching

A normal download returns 200; a single valid Range returns 206, supporting start-end, open-ended and suffix byte ranges. Multiple or invalid ranges return 416; `If-None-Match` returns 304 when the ETag matches, and `If-Range` returns the full file when it does not match.

```sh
curl "http://127.0.0.1:9978/file/TV/demo.mp4" -H "Range: bytes=0-1023" --output demo-part.bin
```

## Rename and move

```sh
curl --data-urlencode "path=/TV/subtitle.srt" --data-urlencode "name=demo.srt" "http://127.0.0.1:9978/rename"
curl --data-urlencode "path=/TV/demo.srt" --data-urlencode "target=/TV/subtitles" "http://127.0.0.1:9978/move"
```

Both files and directories are supported; a new name is just a name and cannot contain path separators. Existing targets are not overwritten, and a directory cannot move inside itself. The web UI offers batch move and delete, acting item by item and reporting each result.

## Upload and delete

```sh
# Upload into an existing TV directory
curl "http://127.0.0.1:9978/upload?path=TV" -F "file=@./subtitle.srt"
```

The multipart field name is arbitrary; the example uses `file`. The filename comes from the upload field and cannot be empty, `.`, `..` or contain path separators. ZIPs are extracted into the target directory and other files are copied atomically; a missing file, missing directory or failed operation returns 500 with a text message.

:::danger Delete endpoints remove data immediately
`/delFile` and `/delFolder` currently share one recursive delete routine; never assume a directory survives because of the endpoint name. The example only targets the sample directory created earlier — confirm the contents before running it.
:::

```sh
curl -G "http://127.0.0.1:9978/delFolder" --data-urlencode "path=TV/demo-empty"
```

Upload, create, rename, move and delete are all restricted to the shared storage root, and the root itself cannot be deleted or moved. LAN requests to `/file` are restricted the same way; loopback requests may resolve other accessible paths through `Path.local`.

## Related

- Resume and large files: [resumable transfers](/local/transfers)
- Applying subtitles: [web player and remote](/local/remote)
