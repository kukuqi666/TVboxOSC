/*
* @File     : 4kysxz.js.js
* @Author   : jade
* @Date     : 2024/1/24 16:47
* @Email    : jadehh@1ive.com
* @Software : Samples
* @Desc     : 4k高清网 (已失效)
*/
import {_} from 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cat.js';
import {VodDetail, VodShort} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/vod.js"
import * as Utils from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/utils.js";
import {Spider} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/js/spider.js";

class YSXZSpider extends Spider {
    constructor() {
        super();
        this.siteUrl = "https://4kysxz.top"
    }

    getAppName() {
        return `4K高清网`
    }

    getName() {
        return `🚄┃4K高清网┃🚄`
    }

    getJSName() {
        return "4kysxz"
    }

    getType() {
        return 3
    }

    async init(cfg) {
        await super.init(cfg);
    }

    async parseVodShortListFromDoc($) {
        const cards = $('div.row.posts-wrapper >div > article > div.entry-media > div > a')
        return _.map(cards, (n) => {
            let id = n.attribs['href'];
            let name = $($(n).find('img')[0]).attr('alt').replaceAll('<strong>', '').replaceAll('</strong>', '').split(' ')[0];
            let pic = $($(n).find('img')[0]).attr('data-src');
            return {
                vod_id: id, vod_name: name, vod_pic: pic, vod_remarks: '',
            };
        });
    }

    async parseVodDetailFromDoc($) {
        let vodDetail = new VodDetail();
        let vodElement = $("[class=\"entry-content u-text-format u-clearfix\"]")
        let text = "";
        for (const vodEle of vodElement) {
            text = text + $(vodEle).text().replaceAll("：", ":") + "\n"
        }
        vodDetail.vod_name = $($("[class=\"article-title\"]")).text()
        vodDetail.vod_pic = $($("[class=\"entry-content u-text-format u-clearfix\"]")).find("img")[0].attribs["src"]
        vodDetail.vod_area = Utils.getStrByRegex(/上映地区(.*?)\n/, text).replaceAll(":", "")
        vodDetail.vod_director = Utils.getStrByRegex(/导演(.*?)\n/, text).replaceAll(":", "")
        vodDetail.vod_actor = Utils.getStrByRegex(/主演(.*?)\n/, text).replaceAll(":", "")
        vodDetail.vod_content = Utils.getStrByRegex(/剧情简介(.*?)\n/, text).replaceAll(":", "").replaceAll("·", "")
        let actors = _.map($('div.entry-content.u-text-format.u-clearfix > div:nth-child(10) > div > span > span'), (n) => {
            return $(n).text().split(' ')[0];
        });
        vodDetail.vod_actor = actors.join('/');
        let directors = _.map($('div.entry-content.u-text-format.u-clearfix > div:nth-child(6) > div > span'), (n) => {
            return $(n).text().split(' ')[0];
        });
        vodDetail.vod_director = directors.join('/');
        vodDetail.vod_name = $('div.site-content > section > div > header > h1').text().trim();
        let playUrlStr = '';
        let playFromStr = '';
        //高清直播
        const cards = $('div.entry-content.u-text-format.u-clearfix > custag > ul > li > a');
        if (cards.length > 0) {
            let playUrls = _.map(cards, (n) => {
                let playUrl = n.attribs['href'];
                if (playUrl.indexOf('url=') > 0) {
                    playUrl = playUrl.split('url=')[1].split('&name')[0];
                }
                return $(n).text() + '$' + playUrl;
            });
            playUrlStr = playUrls.join('#');
            playFromStr = '高清直播';
        }
        if (!this.catOpenStatus) {
            //磁力链接
            const tbs = $('loginshow > table');
            let playFrom = '';
            let nameUrls = [];
            for (let i = 0; i < tbs.length; i++) {
                if (i % 2 == 0) {
                    playFrom = $(tbs[i]).find('tbody > tr >td').text().replaceAll('WEB', '磁力');
                } else if (i % 2 == 1) {
                    const tds = $(tbs[i]).find('tbody > tr >td');
                    let nameUrl = '';
                    for (let j = 0; j < tds.length; j++) {
                        if (j % 2 == 0) {
                            nameUrl = $(tds[j]).text().split('.')[0].split(' ')[0];
                        } else if (j % 2 == 1) {
                            nameUrl = nameUrl + '$' + $(tds[j]).text().split('【')[0];
                            nameUrls.push(nameUrl);
                        }
                    }
                    if (playFromStr.length > 0) {
                        playFromStr += '$$$';
                        playUrlStr += '$$$';
                    }
                    playFromStr += playFrom;
                    playUrlStr += nameUrls.join('#');
                }
            }
        }
        vodDetail.vod_play_from = playFromStr
        vodDetail.vod_play_url = playUrlStr
        return vodDetail
    }


    async setClasses() {
        this.classes = []
        this.classes.push(this.getTypeDic("首页", "/#"))
    }

    async getFilter(typeElements) {
        let value_list = []
        value_list.push({
            "n": "全部", "v": "全部",
        })
        for (const typeElement of typeElements) {
            value_list.push({
                "n": typeElement.attribs["title"],
                "v": typeElement.attribs["href"].split("/").slice(-1)[0].split(".")[0],
            })
        }
        return [{"key": "1", "name": "类型", "value": value_list}]
    }

    async setFilterObj() {
        let $ = await this.getHtml()
        let navElements = $("[class=\"navbar-item menu-item-has-children\"]")
        let extent_list = []
        for (const navElement of navElements) {
            let type_name = $($(navElement).find("a")[0]).text()
            if (type_name.indexOf("影视") > -1) {
                let extend_dic = {"key": "1", "name": type_name, "value": []}
                let type_elements = $($(navElement).find("ul")).find("a")
                for (const type_element of type_elements) {
                    extend_dic["value"].push({"n": $(type_element).text(), "v": type_element.attribs["href"]})
                }
                extent_list.push(extend_dic)
            }
        }
        this.filterObj["/#"] = extent_list
    }

    async setCategory(tid, pg, filter, extend) {
        let url;
        if (extend["1"] === undefined) {
            url = this.siteUrl + tid
        } else {
            if (extend["1"].indexOf("category") > -1) {
                url = this.siteUrl + extend["1"].split(".")[0] + "_" + pg + ".html"
            } else {
                url = this.siteUrl + extend["1"]
            }
        }
        let $ = await this.getHtml(url)
        this.vodList = await this.parseVodShortListFromDoc($)
    }

    async setHomeVod() {
        let $ = await this.getHtml()
        this.homeVodList = await this.parseVodShortListFromDoc($)
    }

    async setDetail(id) {
        const $ = await this.getHtml(id);
        this.vodDetail = await this.parseVodDetailFromDoc($)
    }

    async setSearch(wd, quick) {
        let $ = await this.getHtml(this.siteUrl + '/search.php?q=' + wd)
        this.vodList = await this.parseVodShortListFromDoc($)
    }
}

let spider = new YSXZSpider()

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
        search: search,
        proxy: proxy
    };
}

export {spider}