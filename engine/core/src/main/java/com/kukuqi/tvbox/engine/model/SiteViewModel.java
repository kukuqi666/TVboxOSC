package com.kukuqi.tvbox.engine.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.kukuqi.tvbox.engine.Constant;
import com.kukuqi.tvbox.engine.api.SiteApi;
import com.kukuqi.tvbox.engine.api.config.VodConfig;
import com.kukuqi.tvbox.engine.bean.Result;
import com.kukuqi.tvbox.engine.bean.Site;
import com.kukuqi.tvbox.engine.exception.ExtractException;
import com.github.catvod.utils.Trans;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class SiteViewModel extends ViewModel {

    /** Hard ceiling so a bad subscription value cannot pin a task forever. */
    private static final long MAX_SITE_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final ViewModelSearchRunner searches;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        tasks = new ViewModelTaskRunner<>(TaskType.class);
        searches = new ViewModelSearchRunner();
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        Site site = VodConfig.get().getHome();
        execute(TaskType.RESULT, timeout(site), result, () -> SiteApi.homeContent(site));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, timeout(VodConfig.get().getSite(key)), result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, timeout(VodConfig.get().getSite(key)), action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, timeout(VodConfig.get().getSite(key)), result, () -> SiteApi.detailContent(key, id));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, timeout(site), result, SearchTask.create(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        searchContent(sites, keyword, quick, SearchTask.FIRST_PAGE);
    }

    /**
     * Batched search for a specific page.
     *
     * <p>Sources that paginate search results need the page number threaded through; a caller that
     * keeps asking for page one silently caps results at the first screen.</p>
     */
    public void searchContent(List<Site> sites, String keyword, boolean quick, String page) {
        String target = page == null || page.trim().isEmpty() ? SearchTask.FIRST_PAGE : page;
        searches.start(sites, site -> SearchTask.create(site, keyword, quick, target), search::postValue);
    }

    /**
     * Per site deadline.
     *
     * <p>A subscription may raise {@code "timeout"} for sources that query slow upstreams, and a
     * built-in default would otherwise cut them off. The value never falls below the global
     * search budget so an aggressively small value cannot break normal browsing.</p>
     */
    private static long timeout(Site site) {
        if (site == null || site.isEmpty()) return Constant.TIMEOUT_VOD;
        return Math.max(Constant.TIMEOUT_VOD, Math.min(site.getTimeout(), MAX_SITE_TIMEOUT));
    }

    private void execute(TaskType type, long timeoutMs, MutableLiveData<Result> liveData, Callable<Result> callable) {
        tasks.execute(type, timeoutMs, callable, liveData::postValue, error -> {
            if (error instanceof ExtractException) liveData.postValue(Result.error(error.getMessage()));
            else liveData.postValue(Result.empty());
            error.printStackTrace();
        });
    }

    public void stopSearch() {
        searches.stop();
    }

    @Override
    protected void onCleared() {
        stopSearch();
        tasks.cancelAll();
    }

    private record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

        private static final String FIRST_PAGE = "1";

        SearchTask {
            keyword = site == null ? Trans.t2s(keyword) : site.toSearchKey(keyword);
        }

        private static SearchTask create(Site site, String keyword, boolean quick) {
            return create(site, keyword, quick, FIRST_PAGE);
        }

        private static SearchTask create(Site site, String keyword, boolean quick, String page) {
            return new SearchTask(site, keyword, quick, page);
        }

        @Override
        public Result call() throws Exception {
            if (quick && !site.isQuickSearch()) return Result.empty();
            return SiteApi.searchContent(site, keyword, quick, page);
        }
    }

    private enum TaskType {RESULT, ACTION}
}
