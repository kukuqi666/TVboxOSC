---
description: Java、Python 与 QuickJS 的最小点播范例，涵盖分类、列表、详情与播放。
---

# 三语言 Demo

范例流程为「分类 → 列表 → 详情 → 播放」。请将 `example.com` 换成自己的来源，并补上搜寻与错误处理。

:::tip 回传格式
Java 用 Android 内建的 `JSONObject`／`JSONArray` 组装 JSON 字串；Python 回传 dict／list，QuickJS 回传物件／阵列，由 App 序列化。直播可回传 Group 阵列或 TXT／M3U 原始文字。
:::

## Java

`com/github/catvod/spider/Demo.java`

```java
// 最小点播范例；example.com 为示意网址。
package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class Demo extends Spider {

    // 回传分类清单。
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject item = new JSONObject().put("type_id", "movie").put("type_name", "电影");
        return new JSONObject().put("class", new JSONArray().put(item)).toString();
    }

    // 范例只有一页；第二页起回传空清单。
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
            HashMap<String, String> extend) throws Exception {
        int page = Math.max(1, Integer.parseInt(pg));
        JSONArray items = new JSONArray();
        if (page == 1) items.put(card("demo-1", "范例项目"));
        return new JSONObject().put("list", items).put("page", page)
                .put("pagecount", 1).put("limit", 20).put("total", 1).toString();
    }

    // 集数先使用稳定 ID，播放时再取得网址。
    @Override
    public String detailContent(List<String> ids) throws Exception {
        JSONObject item = card(ids.get(0), "范例项目").put("vod_play_from", "Demo")
                .put("vod_play_url", "第 1 集$demo://episode/1");
        return new JSONObject().put("list", new JSONArray().put(item)).toString();
    }

    // 将播放 ID 换成实际网址。
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
# 最小点播范例；example.com 为示意网址。
from base.spider import Spider as BaseSpider


class Spider(BaseSpider):
    def init(self, extend=""):
        pass

    # 回传分类清单。
    def homeContent(self, filter):
        return {
            "class": [{"type_id": "movie", "type_name": "电影"}]
        }

    # 完整清单交给共用方法分页。
    def categoryContent(self, tid, pg, filter, extend):
        items = [self.card("demo-1", "范例项目")]
        return self.page(items, pg, 20)

    # 集数先使用稳定 ID，播放时再取得网址。
    def detailContent(self, ids):
        item = self.card(ids[0], "范例项目")
        item.update({
            "vod_play_from": "Demo",
            "vod_play_url": "第 1 集$demo://episode/1"
        })
        return {"list": [item]}

    # 将播放 ID 换成实际网址。
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
// 最小点播范例；example.com 为示意网址。
const card = (id, name) => ({
  vod_id: id,
  vod_name: name,
  vod_pic: "https://example.com/poster.jpg"
});

export default {
  init(ext) {},

  // 回传分类清单。
  home(filter) {
    return {
      class: [{ type_id: "movie", type_name: "电影" }]
    };
  },

  // 完整清单交给共用方法分页。
  category(tid, pg, filter, extend) {
    const items = [card("demo-1", "范例项目")];
    return page(items, pg, 20);
  },

  // 集数先使用稳定 ID，播放时再取得网址。
  detail(id) {
    const item = {
      ...card(id, "范例项目"),
      vod_play_from: "Demo",
      vod_play_url: "第 1 集$demo://episode/1"
    };
    return { list: [item] };
  },

  // 将播放 ID 换成实际网址。
  play(flag, id, vipFlags) {
    const url = id.replace("demo://", "https://example.com/");
    return { parse: 0, url };
  }
};
```

## 完整范例

完整范例见 CatVodSpider 的 [Java](https://github.com/FongMi/CatVodSpider/blob/main/app/src/main/java/com/github/catvod/spider/Demo.java)、[Python](https://github.com/FongMi/CatVodSpider/blob/main/examples/python/spider.py) 与 [QuickJS](https://github.com/FongMi/CatVodSpider/blob/main/examples/javascript/spider.js)，涵盖筛选、搜寻分页、直播、快取与网页操作。

## 下一步

- 对照[方法与呼叫时机](/spider/lifecycle)确认每个方法何时执行。
- 回传栏位查 [Result 回传格式](/spider/result)。
- 需要 HTTP 请求时用[共用网络](/spider/network)。
