/*
* @File     : ciliduo.js
* @Author   : jade
* @Date     : 2024/3/1 13:26
* @Email    : jadehh@1ive.com
* @Software : Samples
* @Desc     : 磁力多
*/

import {_, load} from 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cat.js';
import {VodDetail, VodShort} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/vod.js"
import * as Utils from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/utils.js";
import {Spider} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/js/spider.js";


class CiliDuoSpider extends Spider {
    constructor() {
        super();
        this.siteUrl = "https://of.cilido.top"
        this.apiUrl = ""
        this.vodShortObj = {}
    }

    getName() {
        return "🔞┃磁力多BT┃🔞"
    }

    getAppName() {
        return "磁力多BT"
    }
    getJSName() {
        return "ciliduo"
    }

    getType() {
        return 3
    }
    getProxy(src) {
        return Utils.base64Decode(src)
    }

    async home(filter) {
        try {
            await this.jadeLog.info("正在解析首页类别", true)
            let $ = await this.getHtml()
            let proxy_src = Utils.getStrByRegex(/var proxy = atob\('(.*?)'\)/, $.html())
            this.apiUrl = this.getProxy(proxy_src)
            let params = `/?host=${Utils.getHost(this.siteUrl).split("://").slice(-1)[0]}&v=1`
            let homeContent = await this.fetch(this.apiUrl, params, this.getHeader())
            return await this.parseVodShortListFromDoc(load(homeContent))
        } catch (e) {
            await this.jadeLog.error(`首页解析失败,失败原因为:${e}`)
        }

    }

    async parseVodShortListFromDoc($) {
        let rootElemet = $("[class=\"htab\"]")
        let navElements = rootElemet.find("a")
        let vodElements = $("[class=\"hotwords\"]").find("ul")
        for (let i = 0; i < navElements.length; i++) {
            let navElement = navElements[i]
            if (i !== navElements.length - 1) {
                let type_name = $(navElement).text()
                if (type_name === "热门") {
                    type_name = "最近更新"
                }
                this.classes.push(this.getTypeDic(type_name, type_name))
                let vodElement = vodElements[i]
                let vod_list = []
                for (const vodShorElement of $(vodElement).find("a")) {
                    let vodShort = new VodShort()
                    vodShort.vod_id = vodShorElement.attribs.href
                    vodShort.vod_name = $(vodShorElement).html()
                    vodShort.vod_pic = Utils.RESOURCEURL + "/resources/cili.jpg"
                    vod_list.push(vodShort)
                }
                this.vodShortObj[type_name] = vod_list
            }
        }
        return this.result.home(this.classes, [], this.filterObj)
    }

    async parseVodShortListFromDocBySearch($) {
        let vod_list = []
        let vodElements = $("[class=\"ssbox\"]")
        for (const vodElement of vodElements.slice(0, -1)) {
            let vodShort = new VodShort()
            vodShort.vod_id = $(vodElement).find("a")[0].attribs.href
            vodShort.vod_name = $($(vodElement).find("a")[0]).text()
            vodShort.vod_remarks = $($(vodElement).find("span")[0]).text()
            vodShort.vod_pic = Utils.RESOURCEURL + "/resources/cili.jpg"
            vod_list.push(vodShort)
        }
        return vod_list
    }

    async parseVodDetailFromDoc($) {
        let html = $.html()
        let vodDetail = new VodDetail()
        vodDetail.vod_name = $($("[class=\"bt_title\"]")).text()
        vodDetail.vod_pic = Utils.RESOURCEURL + "/resources/cili.jpg"
        vodDetail.vod_remarks = Utils.getStrByRegex(/<br>收录时间：<span>(.*?)<\/span>/, $.html())
        vodDetail.vod_content = "下载速度:" + Utils.getStrByRegex(/下载速度：<span>(.*?)<\/span>/, $.html())
        vodDetail.vod_play_from = ["磁力连接"].join("$$$")
        let vodItems = []
        let contentElement = $("[class=\"content\"]").find("span")[0]
        let episodeUrl = contentElement.attribs.href;
        let episodeName = contentElement.attribs.title;
        vodItems.push(episodeName + "$" + episodeUrl);
        vodDetail.vod_play_url = [vodItems.join("#")].join("$$$")
        return vodDetail
    }

    async setHomeVod() {
        this.homeVodList = this.vodShortObj["最近更新"]
    }

    async setCategory(tid, pg, filter, extend) {
        this.vodList = this.vodShortObj[tid]
    }

    async setDetail(id) {
        if (id.indexOf("search") > -1) {
            let content = await this.fetch(this.apiUrl + id, null, this.getHeader())
            let vod_list = await this.parseVodShortListFromDocBySearch(load(content))
            id = vod_list[0].vod_id
        }
        await this.jadeLog.debug(id)

        let detailUrl = this.apiUrl + id
        let detailContent = await this.fetch(detailUrl, null, this.getHeader())
        this.vodDetail = await this.parseVodDetailFromDoc(load(detailContent))
    }

    async setSearch(wd, quick) {
        let searchUrl = this.apiUrl + `search?word=${wd}`
        let content = await this.fetch(searchUrl, null, this.getHeader())
        this.vodList = await this.parseVodShortListFromDocBySearch(load(content))
    }

}

let spider = new CiliDuoSpider()

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

async function proxy(segments, headers) {
    return await spider.proxy(segments, headers)
}


export function __jsEvalReturn() {
    return {
        init: init,
        home: home,
        homeVod: homeVod,
        category: category,
        detail: detail,
        play: play,
        proxy: proxy,
        search: search,
    };
}
export {spider}