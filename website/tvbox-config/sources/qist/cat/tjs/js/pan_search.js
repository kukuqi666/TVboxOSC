/*
* @File     : pan_search.js
* @Author   : jade
* @Date     : 2023/12/25 17:18
* @Email    : jadehh@1ive.com
* @Software : Samples
* @Desc     : 阿里盘搜（仅支持搜搜）
*/
import {_, load} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cat.js";
import {Spider} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/js/spider.js";
import {VodDetail, VodShort} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/vod.js";
import { detailContent,initCloud,playContent,getHeaders } from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cloud.js";

class PanSearchSpider extends Spider {
    constructor() {
        super();
        this.siteUrl = "https://www.pansearch.me/"
    }

    getName() {
        return "🗂️┃阿里盘搜┃🗂️"
    }

    getAppName() {
        return "阿里盘搜"
    }

    getJSName() {
        return "pan_search"
    }

    getType() {
        return 3
    }

    getSearchHeader() {
        let headers = this.getHeader();
        headers["x-nextjs-data"] = "1";
        return headers;
    }


    async init(cfg) {
        await super.init(cfg);
        await initCloud(this.cfgObj);
    }

    async parseVodDetailfromJson(obj) {
        let item = JSON.parse(obj)
        let vodDetail = new VodDetail();
        let splitList = item["content"].split("\n");
        vodDetail.vod_name = splitList[0].replaceAll(/<\\?[^>]+>/g, "").replace("名称：", "");
        let date = new Date(item["time"])
        vodDetail.vod_remarks = date.toLocaleDateString().replace(/\//g, "-") + " " + date.toTimeString().substr(0, 8)
        vodDetail.vod_pic = item["image"]
        let share_url = ""
        for (const content of splitList) {
            if (content.indexOf("描述") > -1) {
                vodDetail.vod_content = content.replace("描述：", "").replaceAll(/<\\?[^>]+>/g, "")
            }
            if (content.indexOf("标签：") > -1) {
                vodDetail.type_name = content.replace("🏷 标签：", "")
            }
            if (content.indexOf("链接：") > -1) {
                share_url = content.replaceAll(/<\\?[^>]+>/g, "").replace("链接：", "");
            }
        }
        let playVod = await detailContent([share_url])
        vodDetail.vod_play_from = _.keys(playVod).join('$$$');
        vodDetail.vod_play_url = _.values(playVod).join('$$$');
        return vodDetail
    }

    async parseVodShortListFromDocBySearch($, wd) {
        let vod_list = []
        let buildId = JSON.parse($("script[id=__NEXT_DATA__]")[0].children[0].data)["buildId"]
        let url = this.siteUrl + "_next/data/" + buildId + "/search.json?keyword=" + encodeURIComponent(wd) + "&pan=aliyundrive";
        let aliContent = await this.fetch(url, null, this.getSearchHeader())
        if (!_.isEmpty(aliContent)) {
            let items = JSON.parse(aliContent)["pageProps"]["data"]["data"]
            for (const item of items) {
                let vodShort = new VodShort()
                vodShort.vod_id = JSON.stringify(item)
                let splitList = item["content"].split("\n");
                vodShort.vod_name = splitList[0].replaceAll(/<\\?[^>]+>/g, "").replace("名称：", "");
                let date = new Date(item["time"])
                vodShort.vod_remarks = date.toLocaleDateString().replace(/\//g, "-") + " " + date.toTimeString().substr(0, 8)
                vodShort.vod_pic = item["image"]
                vod_list.push(vodShort)
            }
            return vod_list
        } else {
            await this.jadeLog.error("搜索页面解析失败", true)
        }
    }

    async setDetail(id) {
        this.vodDetail = await this.parseVodDetailfromJson(id)
    }

    async setSearch(wd, quick) {
        let html = await this.fetch(this.siteUrl, null, this.getHeader())
        if (!_.isEmpty(html)) {
            let $ = load(html)
            this.vodList = await this.parseVodShortListFromDocBySearch($, wd)
        }
    }
    async setPlay(flag, id, flags) {
        this.playUrl = await playContent(flag, id, flags);
        this.result.setHeader(getHeaders(flag))
    }
}

let spider = new PanSearchSpider()

async function init(cfg) {
    await spider.init(cfg)
}

async function home(filter) {
    return await spider.home(filter)
}

async function homeVod() {
    return await spider.homeVod()
}

async function category(tid, pg, filter, extend) {
    return await spider.category(tid, pg, filter, extend)
}

async function detail(id) {
    return await spider.detail(id)
}

async function play(flag, id, flags) {
    return await spider.play(flag, id, flags)
}

async function search(wd, quick) {
    return await spider.search(wd, quick)
}

export function __jsEvalReturn() {
    return {
        init: init, home: home, homeVod: homeVod, category: category, detail: detail, play: play, search: search,
    };
}
export {spider}