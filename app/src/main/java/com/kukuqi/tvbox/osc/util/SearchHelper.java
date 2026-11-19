package com.kukuqi.tvbox.osc.util;

import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.bean.SourceBean;
import com.kukuqi.tvbox.osc.ui.activity.FastSearchActivity;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class SearchHelper {

    public static HashMap<String, String> getSourcesForSearch() {
        HashMap<String, String> mCheckSources;
        try {
            String api = Hawk.get(HawkConfig.API_URL, "");
            if(api.isEmpty())return null;
            HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH, new HashMap<>());
            mCheckSources = mCheckSourcesForApi.get(api);
        } catch (Exception e) {
            return null;
        }
        if (mCheckSources == null || mCheckSources.isEmpty()) mCheckSources = getSources();
        dropHiddenSources(mCheckSources);
        return mCheckSources;
    }

    public static void putCheckedSources(HashMap<String, String> mCheckSources,boolean isAll) {
        String api = Hawk.get(HawkConfig.API_URL, "");
        if (api.isEmpty()) {
            return;
        }
        HashMap<String, HashMap<String, String>> mCheckSourcesForApi = Hawk.get(HawkConfig.SOURCES_FOR_SEARCH,null);

        if(isAll){
            if (mCheckSourcesForApi == null) return;
            if (mCheckSourcesForApi.containsKey(api)) mCheckSourcesForApi.remove(api);
        }else {
            if (mCheckSourcesForApi == null) mCheckSourcesForApi = new HashMap<>();
            mCheckSourcesForApi.put(api, mCheckSources);
        }
        FastSearchActivity.Companion.setCheckedSourcesForSearch(mCheckSources);
        Hawk.put(HawkConfig.SOURCES_FOR_SEARCH, mCheckSourcesForApi);
    }

    public static HashMap<String, String> getSources(){
        HashMap<String, String> mCheckSources = new HashMap<>();
        // 订阅用 hide 标记的站点只供推送、代理与深链使用，不参与搜索候选
        for (SourceBean bean : ApiConfig.get().getVisibleSourceBeanList()) {
            if (!bean.isSearchable()) {
                continue;
            }
            mCheckSources.put(bean.getKey(), "1");
        }
        return mCheckSources;
    }

    /** 丢弃已保存选择里指向隐藏站点的条目，避免旧配置把隐藏站点重新带进搜索。 */
    public static void dropHiddenSources(HashMap<String, String> sources) {
        dropHiddenSources(sources, ApiConfig.get().getSourceBeanList());
    }

    /**
     * 纯逻辑版本，便于单测。
     *
     * <p>找不到配置的 key 保留原状，交给后续流程报告来源失效；只剔除明确标记为隐藏的站点。</p>
     */
    public static void dropHiddenSources(HashMap<String, String> sources, List<SourceBean> known) {
        if (sources == null || sources.isEmpty()) return;
        HashMap<String, Boolean> hidden = new HashMap<>();
        if (known != null) for (SourceBean bean : known) if (bean != null && bean.getKey() != null) hidden.put(bean.getKey(), bean.isHide());
        sources.keySet().removeIf(key -> Boolean.TRUE.equals(hidden.get(key)));
    }

    public static List<String> splitWords(String text) {
        List<String> result = new ArrayList<>();
        result.add(text);
        String[] parts = text.split("\\W+");
        if (parts.length > 1) {
            result.addAll(Arrays.asList(parts));
        }
        return result;
    }

}
