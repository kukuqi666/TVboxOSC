/*
* @File     : dj0898_book_open.js.js
* @Author   : jade
* @Date     : 2023/12/22 17:00
* @Email    : jadehh@1ive.com
* @Software : Samples
* @Desc     :
*/
import {_} from 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cat.js';
import {Spider} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/js/spider.js";
import {BookShort} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/book.js";

class DJ0898Spider extends Spider {
    constructor() {
        super();
        this.siteUrl = "http://m.dj0898.com";
    }

    getName() {
        return "🎵┃世纪DJ音乐网┃🎵"
    }

    getAppName() {
        return "世纪DJ音乐网"
    }

    getJSName() {
        return "dj0898_book_open"
    }

    getType() {
        return 10
    }

    async parseVodShortListFromDoc($) {
        let books = []
        const list = $("ul.djddv_djList > li");
        for (const it of list) {
            let bookShort = new BookShort();
            const a = $(it).find("a")[1];
            bookShort.book_id = a.attribs.href
            bookShort.book_pic = $(it).find("img:first")[0].attribs.src;
            const tt = $(it).find("strong:first")[0];
            bookShort.book_name = tt.children[0].data
            bookShort.book_remarks = "🎵" + $(it).find("font")[5].children[0].data || ""
            books.push(bookShort)
        }
        return books
    }

    async parseVodShortListFromDocByCategory($) {
        const list = $("ul.djddv_djList > li");
        let videos = _.map(list, (it) => {
            const a = $(it).find("a")[1];
            const img = $(it).find("img:first")[0];
            const tt = $(it).find("strong:first")[0];
            const remarks = $(it).find("font")[5];
            return {
                book_id: a.attribs.href,
                book_name: tt.children[0].data,
                book_pic: img.attribs["src"],
                book_remarks: "🎵" + remarks.children[0].data || "",
            };
        });
        const hasMore = $("ul.page_link > li > a:contains(\u00a0)").length > 0;
        this.page = hasMore ? parseInt(this.page) + 1 : parseInt(this.page);
        return videos
    }

    async parseVodShortListFromDocBySearch($) {
        const list = $("ul.djddv_djList > li");
        return _.map(list, (it) => {
            const a = $(it).find("a")[1];
            const img = $(it).find("img:first")[0];
            const tt = $(it).find("strong:first")[0];
            const remarks = $(it).find("font:first")[0];
            return {
                book_id: a.attribs.href,
                book_name: tt.children[0].data,
                book_pic: img.attribs["src"],
                book_remarks: "🎵" + remarks.children[0].data || "",
            };
        })
    }

    async parseVodDetailFromDoc(id) {
        const vod = {
            book_id: id,
            audio: 1,
            type_name: '',
            book_year: '',
            book_area: '',
            book_remarks: '',
            book_actor: '',
            book_director: '',
            book_content: '',
        };
        const playlist = ["点击播放" + "$" + vod.book_id];
        vod.volumes = "世纪DJ音乐网";
        vod.urls = playlist.join("#");
        return vod
    }

    async setClasses() {
        this.classes = [{type_id: 1, type_name: "🎧串烧舞曲"}, {type_id: 2, type_name: "🎧外文舞曲"}, {
            type_id: 3,
            type_name: "🎧早场暖场"
        }, {type_id: 4, type_name: "🎧中文舞曲"}, {type_id: 5, type_name: "🎧其他舞曲"}, {
            type_id: 6,
            type_name: "🎧国外电音"
        }, {type_id: 8, type_name: "🎧慢歌连版"}, {type_id: 9, type_name: "🎧酒吧潮歌"}, {
            type_id: 10,
            type_name: "🎧中文串烧"
        }, {type_id: 11, type_name: "🎧外文串烧"}, {type_id: 12, type_name: "🎧中外串烧"}, {
            type_id: 13,
            type_name: "🎧车载串烧"
        }, {type_id: 14, type_name: "🎧越鼓串烧"}, {type_id: 40, type_name: "🎧3D/环绕"}, {
            type_id: 45,
            type_name: "🎧口水旋律"
        }, {type_id: 46, type_name: "🎧精品收藏"}, {type_id: 47, type_name: "🎧开场舞曲"}, {
            type_id: 48,
            type_name: "🎧印度舞曲"
        }, {type_id: 49, type_name: "🎧编排套曲"}, {type_id: 20, type_name: "🎧DuTch"}, {
            type_id: 21,
            type_name: "🎧Mash up"
        }, {type_id: 22, type_name: "🎧ClubHouse"}, {type_id: 23, type_name: "🎧ElectroHouse"}, {
            type_id: 24,
            type_name: "🎧越南鼓Dj"
        }, {type_id: 30, type_name: "🎧Funky"}, {type_id: 31, type_name: "🎧Reggae"}, {
            type_id: 32,
            type_name: "🎧Rnb"
        }, {type_id: 33, type_name: "🎧Hip Hop"}, {type_id: 34, type_name: "🎧Dubstep"}, {
            type_id: 8017,
            type_name: "🎧Hardstyle"
        }, {type_id: 8018, type_name: "🎧Hands Up"}];

    }

    async setFilterObj() {
    }

    async setHomeVod() {
        let $ = await this.getHtml(this.siteUrl + "/dance/lists/id/10/1")
        this.homeVodList = await this.parseVodShortListFromDoc($)
    }

    async setCategory(tid, pg, filter, extend) {
        const link = this.siteUrl + "/dance/lists/id/" + tid + "/" + pg;
        let $ = await this.getHtml(link)
        this.vodList = await this.parseVodShortListFromDocByCategory($)
    }

    async setDetail(id) {
        this.vodDetail = await this.parseVodDetailFromDoc(id);
    }

    async setPlay(flag, id, flags) {
        let $ = await this.getHtml(id)
        const audio = $("body audio[src*=http]");
        this.playUrl = audio[0].attribs.src
    }

    async setSearch(wd, quick) {
        let $ = await this.getHtml(this.siteUrl + "/index.php/dance/so/key?key=" + wd + "&cid=0&p=1")
        this.vodList = await this.parseVodShortListFromDocBySearch($)
    }
}

let spider = new DJ0898Spider()

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