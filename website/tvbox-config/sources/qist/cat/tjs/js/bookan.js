/*
* @File     : bookan.js
* @Author   : jade
* @Date     : 2024/1/31 13:44
* @Email    : jadehh@1ive.com
* @Software : Samples
* @Desc     :
*/
import {_} from 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/lib/cat.js';
import {Spider} from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/tjs/js/spider.js";

function formatPlayUrl(name) {
    return name
        .trim()
        .replace(/<|>|《|》/g, '')
        .replace(/\$|#/g, ' ')
        .trim();
}

class BooKanSpider extends Spider {
    constructor() {
        super();
        this.siteUrl = "https://api.bookan.com.cn";
    }

    getName() {
        return "🎵┃看书┃🎵"
    }

    getAppName() {
        return "看书"
    }

    getJSName() {
        return "bookan"
    }

    getType() {
        return 10
    }

    async parseVodShortListFromJson(obj) {
        let books = [];
        for (const book of obj.list) {
            books.push({
                book_id: book.id, book_name: book.name, book_pic: book.cover, book_remarks: book.extra.author,
            });
        }
        return books
    }

    async setClasses() {
        {
            this.classes = [{type_id: '1305', type_name: '少年读物'}, {
                type_id: '1304', type_name: '儿童文学'
            }, {type_id: '1320', type_name: '国学经典'}, {type_id: '1306', type_name: '文艺少年'}, {
                type_id: '1309', type_name: '育儿心经'
            }, {type_id: '1310', type_name: '心理哲学'}, {type_id: '1307', type_name: '青春励志'}, {
                type_id: '1312', type_name: '历史小说'
            }, {type_id: '1303', type_name: '故事会'}, {type_id: '1317', type_name: '音乐戏剧'}, {
                type_id: '1319', type_name: '相声评书'
            },]
        }
    }

    async setCategory(tid, pg, filter, extend) {
        let content = await this.fetch(`${this.siteUrl}/voice/book/list?instance_id=25304&page=${pg}&category_id=${tid}&num=24`, null, this.getHeader());
        let data = JSON.parse(content).data;
        this.vodList = await this.parseVodShortListFromJson(data)
    }


    async parseVodDetailfromJson(obj) {
        let book = {
            audio: 1,
            type_name: '',
            book_year: '',
            book_area: '',
            book_remarks: '',
            book_actor: '',
            book_director: '',
            book_content: '',
        };
        let us = _.map(obj.list, function (b) {
            return formatPlayUrl(b.title) + '$' + b.file;
        }).join('#');
        book.volumes = '书卷';
        book.urls = us;
        return book
    }

    async setDetail(id) {
        let content = await this.fetch(`${this.siteUrl}/voice/album/units?album_id=${id}&page=1&num=200&order=1`, null, this.getHeader());
        let data = JSON.parse(content).data;
        this.vodDetail = await this.parseVodDetailfromJson(data)
        this.vodDetail.book_id = id
    }

    async play(flag, id, flags) {
        return JSON.stringify({
            parse: 0, url: id,
        });
    }

    async setSearch(wd, quick) {
        let content = await this.fetch(`https://es.bookan.com.cn/api/v3/voice/book?instanceId=25304&keyword=${wd}&pageNum=1&limitNum=20`,null,this.getHeader());
        let data = JSON.parse(content).data;
        this.vodList = await this.parseVodShortListFromJson(data)
    }

}
let spider = new BooKanSpider()

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
        init: init,
        home: home,
        homeVod: homeVod,
        category: category,
        detail: detail,
        play: play,
        search: search,
    };
}
export {spider}
