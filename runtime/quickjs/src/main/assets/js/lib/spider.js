import * as entry from %s;
import host from 'catvod:host';
import {Spider} from 'catvod';
import {stringify} from 'assets://js/lib/net.js';
import {dialogActions} from 'assets://js/lib/dialog.js';

// 同时接受新版 Spider 类，以及旧版默认对象和命名导出；来源状态仍归属当前实例。
const api = typeof entry.default === 'function' && entry.default.prototype instanceof Spider
    ? new entry.default() : legacySpider(entry.default ?? entry);
host.attach(createSpider(api), dialogActions);

function legacySpider(source) {
    const aliases = {
        init: 'init', homeContent: 'home', homeVideoContent: 'homeVod', categoryContent: 'category',
        detailContent: 'detail', searchContent: 'search', playerContent: 'play', liveContent: 'live',
        manualVideoCheck: 'sniffer', isVideoFormat: 'isVideo', proxy: 'proxy', action: 'action', destroy: 'destroy'
    };
    if (!source || typeof source !== 'object' || !Object.entries(aliases).some(([name, alias]) =>
        typeof source[name] === 'function' || typeof source[alias] === 'function')) {
        throw new TypeError('Spider requires a Spider class or an object exporting source methods');
    }
    const adapter = new Spider();
    for (const [name, alias] of Object.entries(aliases)) {
        const modern = typeof source[name] === 'function';
        const method = modern ? source[name] : source[alias];
        if (typeof method !== 'function') continue;
        Object.defineProperty(adapter, name, {
            value: async function(...args) {
                // 旧 detail 接受单个 ID，新接口仍保留完整 ID 列表。
                if (name === 'detailContent' && !modern) args[0] = args[0][0] ?? '';
                let value = await method.apply(source, args);
                // 旧接口常返回 JSON 字符串，先解析再交给统一的结果校验，避免双重编码。
                if (typeof value === 'string' && name !== 'liveContent' && name !== 'init' && name !== 'destroy'
                    && name !== 'manualVideoCheck' && name !== 'isVideoFormat') value = JSON.parse(value);
                return value;
            }, writable: true, configurable: true
        });
    }
    return adapter;
}

function createSpider(api) {
    const wrapped = Object.create(api);
    for (const name of ['homeContent', 'homeVideoContent', 'categoryContent', 'detailContent', 'searchContent', 'playerContent', 'liveContent', 'action']) bindSpiderMethod(wrapped, api, name, true);
    for (const name of ['init', 'proxy', 'manualVideoCheck', 'isVideoFormat']) bindSpiderMethod(wrapped, api, name, false);
    bindDanmaku(wrapped, api);
    return wrapped;
}

function output(value, method) {
    if (method === 'liveContent' && typeof value === 'string') return value;
    if (!value || typeof value !== 'object') throw new TypeError('Spider data must be an object or array');
    const json = stringify(value);
    if (json[0] !== '{' && json[0] !== '[') throw new TypeError('Spider data must be an object or array');
    return json;
}

function bindSpiderMethod(wrapped, api, name, serialize) {
    Object.defineProperty(wrapped, name, {
        value: async function(...args) {
            const value = await api[name](...args);
            return serialize ? output(value, name) : value;
        },
        writable: true, configurable: true, enumerable: true
    });
}

function bindDanmaku(wrapped, api) {
    const danmakus = new Map();
    wrapped.startDanmaku = async function(id, token) {
        const stop = await api.danmaku(id, item => {
            if (!item || typeof item !== 'object' || Array.isArray(item)) throw new TypeError('Danmaku must be an object');
            host.emitDanmaku(token, item);
        });
        if (typeof stop !== 'function') throw new TypeError('danmaku must return a stop function');
        danmakus.set(token, stop);
    };
    wrapped.stopDanmaku = async function(token) {
        const stop = danmakus.get(token);
        danmakus.delete(token);
        if (stop) await stop();
    };
    async function destroy() {
        const failures = [];
        for (const token of danmakus.keys()) {
            try {
                await wrapped.stopDanmaku(token);
            } catch (error) {
                failures.push(error);
            }
        }
        try {
            await api.destroy();
        } catch (error) {
            failures.push(error);
        }
        if (failures.length) throw failures[0];
    }

    Object.defineProperty(wrapped, 'destroy', {value: destroy, writable: true, configurable: true, enumerable: true});
}
