import { load, _ } from "https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/js/lib/cat.js";

let key = "世纪DJ音乐网_book";
let HOST = "http://m.dj0898.com";
let siteKey = "";
let siteType = 0;

const MOBILE_UA = "Mozilla/5.0 (Linux; Android 11; M2007J3SC Build/RKQ1.200826.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/77.0.3865.120 MQQBrowser/6.2 TBS/045714 Mobile Safari/537.36";

async function request(reqUrl, agentSp) {
    let res = await req(reqUrl, {
        method: "get",
        headers: {
            "User-Agent": agentSp || MOBILE_UA,
            Referer: HOST,
        },
    });
    return res.content;
}

async function init(cfg) {
    siteKey = cfg.skey;
    siteType = cfg.stype;
}

async function home(filter) {
    const classes = [{ type_id: 1, type_name: "🎧串烧舞曲" }, { type_id: 2, type_name: "🎧外文舞曲" }, { type_id: 3, type_name: "🎧早场暖场" }, { type_id: 4, type_name: "🎧中文舞曲" }, { type_id: 5, type_name: "🎧其他舞曲" }, { type_id: 6, type_name: "🎧国外电音" }, { type_id: 8, type_name: "🎧慢歌连版" }, { type_id: 9, type_name: "🎧酒吧潮歌" }, { type_id: 10, type_name: "🎧中文串烧" }, { type_id: 11, type_name: "🎧外文串烧" }, { type_id: 12, type_name: "🎧中外串烧" }, { type_id: 13, type_name: "🎧车载串烧" }, { type_id: 14, type_name: "🎧越鼓串烧" }, { type_id: 40, type_name: "🎧3D/环绕" }, { type_id: 45, type_name: "🎧口水旋律" }, { type_id: 46, type_name: "🎧精品收藏" }, { type_id: 47, type_name: "🎧开场舞曲" }, { type_id: 48, type_name: "🎧印度舞曲" }, { type_id: 49, type_name: "🎧编排套曲" }, { type_id: 20, type_name: "🎧DuTch" }, { type_id: 21, type_name: "🎧Mash up" }, { type_id: 22, type_name: "🎧ClubHouse" }, { type_id: 23, type_name: "🎧ElectroHouse" }, { type_id: 24, type_name: "🎧越南鼓Dj" }, { type_id: 30, type_name: "🎧Funky" }, { type_id: 31, type_name: "🎧Reggae" }, { type_id: 32, type_name: "🎧Rnb" }, { type_id: 33, type_name: "🎧Hip Hop" }, { type_id: 34, type_name: "🎧Dubstep" }, { type_id: 8017, type_name: "🎧Hardstyle" }, { type_id: 8018, type_name: "🎧Hands Up" }];
    const filterObj = {};
    return JSON.stringify({
        class: _.map(classes, (cls) => {
            cls.land = 1;
            cls.ratio = 1.78;
            return cls;
        }),
        filters: filterObj,
    });
}

async function homeVod() {
    const link = HOST + "/dance/lists/id/10/1";
    const html = await request(link);
    const $ = load(html);
    const list = $("ul.djddv_djList > li");
    let videos = _.map(list, (it) => {
        const a = $(it).find("a")[1];
        const img = $(it).find("img:first")[0];
        const tt = $(it).find("strong:first")[0];
        const remarks = $(it).find("font")[5];
        return {
            vod_id: a.attribs.href,
            vod_name: tt.children[0].data,
            vod_pic: img.attribs["src"],
            vod_remarks: "🎵" + remarks.children[0].data || "",
        };
    });
    return JSON.stringify({
        list: videos,
    });
}

async function category(tid, pg, filter, extend) {
    if (pg <= 0 || typeof pg == "undefined") pg = 1;
    const link = HOST + "/dance/lists/id/" + tid + "/" + pg;
    const html = await request(link);
    const $ = load(html);
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
    const pgCount = hasMore ? parseInt(pg) + 1 : parseInt(pg);
    return JSON.stringify({
        page: parseInt(pg),
        pagecount: pgCount,
        limit: 60,
        total: 60 * pgCount,
        list: videos,
    });
}

async function detail(id) {
    const vod = {
		audio: 1,
        book_id: id,
        type_name: '',
        book_year: '',
        book_area: '',
        book_remarks: '',
        book_actor: '',
        book_director: '',
        book_content: '',
    };
    const playlist = ["点击播放" + "$" + vod.book_id];
    vod.volumes = "道长在线";
    vod.urls = playlist.join("#");
    return JSON.stringify({
        list: [vod],
    });
}

async function play(flag, id, flags) {
    const html = await request(id);
    const $ = load(html);
    const audio = $("body audio[src*=http]");
    const audioUrl = audio[0].attribs.src;
    // console.debug('世纪DJ音乐网 audioUrl =====>' + audioUrl); // js_debug.log
    return JSON.stringify({
        parse: 0,
        url: audioUrl,
    });
}

async function search(wd, quick, pg) {
    if (pg <= 0 || typeof pg == "undefined") pg = 1;
    const link = HOST + "/index.php/dance/so/key?key=" + wd + "&cid=0&p=" + pg;
    const html = await request(link);
    const $ = load(html);
    const list = $("ul.djddv_djList > li");
    let videos = _.map(list, (it) => {
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
    });
    const hasMore = $("ul.page_link > li > a:contains(\u00a0)").length > 0;
    const pgCount = hasMore ? parseInt(pg) + 1 : parseInt(pg);
    return JSON.stringify({
        page: parseInt(pg),
        pagecount: pgCount,
        limit: 60,
        total: 60 * pgCount,
        list: videos,
        land: 1,
        ratio: 1.78,
    });
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