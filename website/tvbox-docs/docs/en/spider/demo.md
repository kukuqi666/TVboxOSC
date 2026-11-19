---
description: Minimal VOD samples in Java, Python and QuickJS covering category, list, detail and play.
---

# Three-Language Demo

The sample flow is category → list → detail → play. Replace `example.com` with your own source and add search and error handling.

:::tip Response format
Java builds JSON strings with Android's built-in `JSONObject`／`JSONArray`; Python returns dicts／lists and QuickJS returns objects／arrays, which the App serializes. Live TV can return a Group array or raw TXT／M3U text.
:::

## Java

`com/github/catvod/spider/Demo.java`

```java
// Minimal VOD sample; example.com is illustrative.
package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class Demo extends Spider {

    // Returns the category list.
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject item = new JSONObject().put("type_id", "movie").put("type_name", "Movies");
        return new JSONObject().put("class", new JSONArray().put(item)).toString();
    }

    // The sample has one page; later pages return an empty list.
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
            HashMap<String, String> extend) throws Exception {
        int page = Math.max(1, Integer.parseInt(pg));
        JSONArray items = new JSONArray();
        if (page == 1) items.put(card("demo-1", "Sample item"));
        return new JSONObject().put("list", items).put("page", page)
                .put("pagecount", 1).put("limit", 20).put("total", 1).toString();
    }

    // Episodes use stable IDs; the real URL is resolved on play.
    @Override
    public String detailContent(List<String> ids) throws Exception {
        JSONObject item = card(ids.get(0), "Sample item").put("vod_play_from", "Demo")
                .put("vod_play_url", "Episode 1$demo://episode/1");
        return new JSONObject().put("list", new JSONArray().put(item)).toString();
    }

    // Turns the playback ID into a real URL.
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String url = id.replace("demo://", "https://example.com/");
        return new JSONObject().put("parse", 0).put("url", url).toString();
    }

    private JSONObject card(String id, String name) throws Exception {
        return new JSONObject().put("vod_id", id).put("vod_name", name)
                .put("vod_pic", "https://example.com/poster.jpg");
    }
}
```

## Python

`demo.py`

```python
# Minimal VOD sample; example.com is illustrative.
from base.spider import Spider as BaseSpider


class Spider(BaseSpider):
    def init(self, extend=""):
        pass

    # Returns the category list.
    def homeContent(self, filter):
        return {
            "class": [{"type_id": "movie", "type_name": "Movies"}]
        }

    # The shared helper handles paging over the full list.
    def categoryContent(self, tid, pg, filter, extend):
        items = [self.card("demo-1", "Sample item")]
        return self.page(items, pg, 20)

    # Episodes use stable IDs; the real URL is resolved on play.
    def detailContent(self, ids):
        item = self.card(ids[0], "Sample item")
        item.update({
            "vod_play_from": "Demo",
            "vod_play_url": "Episode 1$demo://episode/1"
        })
        return {"list": [item]}

    # Turns the playback ID into a real URL.
    def playerContent(self, flag, id, vipFlags):
        url = id.replace("demo://", "https://example.com/")
        return {"parse": 0, "url": url}

    def card(self, id, name):
        return {
            "vod_id": id,
            "vod_name": name,
            "vod_pic": "https://example.com/poster.jpg"
        }
```

## QuickJS

`demo.js`

```js
// Minimal VOD sample; example.com is illustrative.
const card = (id, name) => ({
  vod_id: id,
  vod_name: name,
  vod_pic: "https://example.com/poster.jpg"
});

export default {
  init(ext) {},

  // Returns the category list.
  home(filter) {
    return {
      class: [{ type_id: "movie", type_name: "Movies" }]
    };
  },

  // The shared helper handles paging over the full list.
  category(tid, pg, filter, extend) {
    const items = [card("demo-1", "Sample item")];
    return page(items, pg, 20);
  },

  // Episodes use stable IDs; the real URL is resolved on play.
  detail(id) {
    const item = {
      ...card(id, "Sample item"),
      vod_play_from: "Demo",
      vod_play_url: "Episode 1$demo://episode/1"
    };
    return { list: [item] };
  },

  // Turns the playback ID into a real URL.
  play(flag, id, vipFlags) {
    const url = id.replace("demo://", "https://example.com/");
    return { parse: 0, url };
  }
};
```

## Full samples

Full samples live in CatVodSpider: [Java](https://github.com/FongMi/CatVodSpider/blob/main/app/src/main/java/com/github/catvod/spider/Demo.java), [Python](https://github.com/FongMi/CatVodSpider/blob/main/examples/python/spider.py) and [QuickJS](https://github.com/FongMi/CatVodSpider/blob/main/examples/javascript/spider.js), covering filters, search paging, live TV, caching and web operations.

## Next

- Check when each method runs in [methods and timing](/spider/lifecycle).
- Look up response fields in the [Result format](/spider/result).
- Use [shared networking](/spider/network) for HTTP requests.
