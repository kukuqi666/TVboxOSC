import { load, _ } from 'https://raw.githubusercontent.com/kukuqi666/TVboxOSC/main/website/tvbox-config/sources/qist/cat/js/lib/cat.js';
let key = '🐰兔小贝';
let HOST = 'https://www.tuxiaobei.com';
let siteKey = '';
let siteType = 0;
const IOS_UA = 'Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1';

async function request(reqUrl, agentSp) {
    let res = await req(reqUrl, {
        method: 'get',
        headers: {
            'User-Agent': agentSp || IOS_UA,
        },
    });
    return res.content
}

async function init(cfg) {
    siteKey = cfg.skey;
    siteType = cfg.stype
}

async function home(filter) {
    const classes = [{ type_id: '', type_name: '🐰全部' }, { type_id: 2, type_name: '🐰儿歌' }, { type_id: 3, type_name: '🐰故事' }, { type_id: 27, type_name: '🐰公益' }, { type_id: 9, type_name: '🐰十万个为什么' }, { type_id: 28, type_name: '🐰安全教育' }, { type_id: 29, type_name: '🐰动物奇缘' }, { type_id: 7, type_name: '🐰弟子规' }, { type_id: 5, type_name: '🐰古诗' }, { type_id: 6, type_name: '🐰三字经' }, { type_id: 8, type_name: '🐰千字文' }, { type_id: 11, type_name: '🐰数学' }, { type_id: 25, type_name: '🐰英语' }, { type_id: 24, type_name: '🐰折纸' }];
    const filterObj = {};
    return JSON.stringify({
        class: _.map(classes, (cls) => {
            cls.land = 1;
            cls.ratio = 1.78;
            return cls;
        }),
        filters: filterObj,
    })
}

async function homeVod() {
    const link = await request(HOST + '/list/mip-data?typeId=9&page=1&callback=');
    const html = link.match(/\((.*?)\);/)[1];
    const data = JSON.parse(html).data;
    let videos = _.map(data.items, (it) => {
        return {
            vod_id: it.video_id,
            vod_name: it.name,
            vod_pic: it.image,
            vod_remarks: it.root_category_name + ' | ' + it.duration_string || '',
        }
    });
    return JSON.stringify({
        list: videos,
    })
}

async function category(tid, pg, filter, extend) {
    if (pg <= 0 || typeof pg == 'undefined') pg = 1;
    const link = await request(HOST + '/list/mip-data?typeId=' + tid + '&page=' + pg + '&callback=');
    const html = link.match(/\((.*?)\);/)[1];
    const data = JSON.parse(html).data;
    let videos = _.map(data.items, (it) => {
        return {
            vod_id: it.video_id,
            vod_name: it.name,
            vod_pic: it.image,
            vod_remarks: it.root_category_name + ' | ' + it.duration_string || '',
        }
    });
    const pgCount = pg * 30 > data.totalCount ? parseInt(pg) : parseInt(pg) + 1;
    return JSON.stringify({
        page: parseInt(pg),
        pagecount: pgCount,
        limit: 30,
        total: data.totalCount,
        list: videos,
    })
}

async function detail(id) {
    const vod = {
        vod_id: id,
        vod_remarks: '',
    };
    const playlist = ['点击播放' + '$' + HOST + '/play/' + id];
    vod.vod_play_from = "道长在线";
    vod.vod_play_url = playlist.join('#');
    return JSON.stringify({
        list: [vod],
    });
}

async function play(flag, id, flags) {
    const html = await request(id);
    const $ = load(html);
    const pvideo = $("body mip-search-video[video-src*=http]");
    const purl = pvideo[0].attribs['video-src'];
    // console.debug('兔小贝 purl =====>' + purl); // js_debug.log
    return JSON.stringify({
        parse: 0,
        url: purl,
    });
}

async function search(wd, quick) {
    const link = HOST + "/search/" + wd;
    const html = await request(link);
    const $ = load(html);
    const list = $("div.list-con > div.items");
    let videos = _.map(list, (it) => {
        const a = $(it).find("a:first")[0];
        const img = $(it).find("mip-img:first")[0];
        const tt = $(it).find("p:first")[0];
        const remarks = $(it).find("p")[1];
        return {
            vod_id: a.attribs.href.replace(/.*?\/play\/(.*)/g, '$1'),
            vod_name: tt.children[0].data,
            vod_pic: img.attribs["src"],
            vod_remarks: remarks.children[0].data || "",
        };
    });
    return JSON.stringify({
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
    }
}