package com.kukuqi.tvbox.osc.ui.fragment;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.blankj.utilcode.util.ColorUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.RegexUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.blankj.utilcode.util.SpanUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.catvod.crawler.Spider;
import com.kukuqi.tvbox.osc.R;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.base.BaseLazyFragment;
import com.kukuqi.tvbox.osc.bean.ParseBean;
import com.kukuqi.tvbox.osc.bean.SourceBean;
import com.kukuqi.tvbox.osc.bean.Subtitle;
import com.kukuqi.tvbox.osc.bean.VodInfo;
import com.kukuqi.tvbox.osc.cache.CacheManager;
import com.kukuqi.tvbox.osc.event.RefreshEvent;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.player.controller.VodController;
import com.kukuqi.tvbox.osc.server.RemoteServer;
import com.kukuqi.tvbox.osc.ui.activity.DetailActivity;
import com.kukuqi.tvbox.osc.ui.adapter.ParseAdapter;
import com.kukuqi.tvbox.osc.ui.dialog.PlayingControlDialog;
import com.kukuqi.tvbox.osc.ui.dialog.PlayingControlRightDialog;
import com.kukuqi.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.kukuqi.tvbox.osc.ui.dialog.SelectDialog;
import com.kukuqi.tvbox.osc.ui.dialog.SubtitleDialog;
import com.kukuqi.tvbox.osc.util.AdBlocker;
import com.kukuqi.tvbox.osc.util.DefaultConfig;
import com.kukuqi.tvbox.osc.util.HawkConfig;
import com.kukuqi.tvbox.osc.util.LOG;
import com.kukuqi.tvbox.osc.util.MD5;
import com.kukuqi.tvbox.osc.util.PlayerHelper;
import com.kukuqi.tvbox.osc.util.VideoParseRuler;

import com.kukuqi.tvbox.osc.util.thunder.Thunder;
import com.kukuqi.tvbox.osc.viewmodel.SourceViewModel;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.enums.PopupPosition;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.HttpHeaders;
import com.lzy.okgo.model.Response;
import com.obsez.android.lib.filechooser.ChooserDialog;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import me.jessyan.autosize.AutoSize;
import xyz.doikki.videoplayer.player.ProgressManager;

public class PlayFragment extends BaseLazyFragment {
    private MyVideoView mVideoView;
    private TextView mPlayLoadTip;
    private ImageView mPlayLoadErr;
    private View mPlayLoading;
    private VodController mController;
    private SourceViewModel sourceViewModel;
    private Handler mHandler;
    private final com.kukuqi.tvbox.osc.core.EpisodePreloader<com.kukuqi.tvbox.engine.bean.Result> nextEpisode =
            new com.kukuqi.tvbox.osc.core.EpisodePreloader<>(command -> new Handler(android.os.Looper.getMainLooper()).post(command));

    private final long videoDuration = -1;
    /**
     * 记录当前播放url
     */
    private String mCurrentUrl;
    private boolean mFullWindows;
    /**
     * 非全屏下的设置弹窗
     */
    private BasePopupView mPlayingControlDialog;
    /**
     * 全屏下的设置弹窗
     */
    private BasePopupView mPlayingControlRightDialog;
    /**
     * 视频播放出错时,自动切换另一个播放器,这个开关避免多次切换
     */
    boolean retriedSwitchPlayer = false;
    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_SUBTITLE_SIZE_CHANGE) {
            mController.mSubtitleView.setTextSize((int) event.obj);
        } else if (event.type == RefreshEvent.TYPE_BATTERY_CHANGE && mController.mMyBatteryView!=null){
            mController.mMyBatteryView.updateBattery((int) event.obj);
        }
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    public long getSavedProgress(String url) {
        if (qualityResumePosition >= 0) return qualityResumePosition;
        int st = 0;
        try {
            st = mVodPlayerCfg.getInt("st");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        long skip = st * 1000L;
        if (corePlayResult != null && corePlayResult.hasPosition()) return Math.max(skip, corePlayResult.getPosition());
        Object theCache=CacheManager.getCache(MD5.string2MD5(url));
        if (theCache == null) {
            return skip;
        }
        long rec = 0;
        if (theCache instanceof Long) {
            rec = (Long) theCache;
        } else if (theCache instanceof String) {
            try {
                rec = Long.parseLong((String) theCache);
            } catch (NumberFormatException e) {
                System.out.println("String value is not a valid long.");
            }
        } else {
            System.out.println("Value cannot be converted to long.");
        }
        return Math.max(rec, skip);
    }

    private void initView() {
        EventBus.getDefault().register(this);
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case 100:
                        stopParse();
                        errorWithRetry("嗅探错误", false);
                        break;
                }
                return false;
            }
        });
        mVideoView = findViewById(R.id.mVideoView);
        mPlayLoadTip = findViewById(R.id.play_load_tip);
        mPlayLoading = findViewById(R.id.play_loading);
        mPlayLoadErr = findViewById(R.id.play_load_error);
        mController = new VodController(requireContext());
        mController.showParse(false);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setGestureEnabled(true);
        ProgressManager progressManager = new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {
                if (Hawk.get(HawkConfig.PRIVATE_BROWSING, false)) return;
                CacheManager.save(MD5.string2MD5(url), progress);
            }

            @Override
            public long getSavedProgress(String url) {
                return PlayFragment.this.getSavedProgress(url);
            }
        };
        mVideoView.setProgressManager(progressManager);
        mController.setListener(new VodController.VodControlListener() {
            final DetailActivity activity = (DetailActivity) mActivity;
            @Override
            public void chooseSeries() {
                //activity中已处理
                activity.showAllSeriesDialog();
            }

            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = progressKey;
                PlayFragment.this.playNext(rmProgress);
                if (rmProgress && preProgressKey != null)
                    CacheManager.delete(MD5.string2MD5(preProgressKey), 0);
            }
            @Override public void progress(long duration, long position) { if (!applySourceSkips(duration, position)) preloadNext(duration, position); }

            @Override
            public void playPre() {
                PlayFragment.this.playPrevious();
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                mVodInfo.playerCfg = mVodPlayerCfg.toString();
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfg));
            }

            @Override
            public void replay(boolean replay) {
                autoRetryCount = 0;
                play(replay);
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false);
            }

            @Override
            public void selectSubtitle() {
                try {
                    selectMySubtitle();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void selectAudioTrack() {
                selectMyAudioTrack();
            }

            @Override
            public void prepared() {
                initSubtitleView();
                if (qualityResumePosition >= 0) {
                    boolean playing = qualityResumePlaying;
                    qualityResumePosition = -1;
                    if (mVideoView.getMediaPlayer() instanceof com.kukuqi.tvbox.osc.player.CorePlayer) {
                        com.kukuqi.tvbox.osc.player.CorePlayer core = (com.kukuqi.tvbox.osc.player.CorePlayer) mVideoView.getMediaPlayer();
                        if (!playing) mVideoView.post(() -> { if (mVideoView != null && mVideoView.getMediaPlayer() == core) mVideoView.pause(); });
                    }
                }
            }

            @Override
            public void toggleFullScreen() {
                activity.toggleFullPreview();
            }

            @Override
            public void exit() {
                activity.onBackPressed();
            }

            @Override
            public void onHideBottom() {
                if (mFullWindows){
                    ImmersionBar.with(activity)
                            .hideBar(BarHide.FLAG_HIDE_BAR)
                            .init();
                }
            }

            @Override
            public void showSetting() {
                if (mFullWindows){
                    mPlayingControlRightDialog = new XPopup.Builder(activity)
                            .isViewMode(true)//改为view模式无法自动响应返回键操作,onBackPress时手动dismiss
                            .hasNavigationBar(false)
                            .popupHeight(ScreenUtils.getScreenHeight())
                            .popupPosition(PopupPosition.Right)
                            .asCustom(new PlayingControlRightDialog(activity,mController,mVideoView));
                    mPlayingControlRightDialog.show();
                }else {
                    mPlayingControlDialog = new XPopup.Builder(activity)
                            .isViewMode(true)
                            .hasNavigationBar(false)
                            .asCustom(new PlayingControlDialog(activity,mController,mVideoView));
                    mPlayingControlDialog.show();
                }
            }

            @Override
            public void pip() {
                activity.enterPip();
            }

            @Override
            public void showParseRoot(boolean show, ParseAdapter adapter) {
                DetailActivity activity = (DetailActivity)mActivity;
                activity.showParseRoot(show,adapter);
            }
        });
        mVideoView.setVideoController(mController);
    }

    public boolean hideAllDialogSuccess(){
        if (mPlayingControlRightDialog!=null && mPlayingControlRightDialog.isShow()){
            mPlayingControlRightDialog.dismiss();
            return true;
        }
        if (mPlayingControlDialog!=null && mPlayingControlDialog.isShow()){
            mPlayingControlDialog.dismiss();
            return true;
        }
        return false;
    }

    /**
     * activity返回/点击播放器切换全屏操作等
     */
    public void changedLandscape(boolean fullWindows) {
        mFullWindows = fullWindows;
        if (fullWindows){
            int[] size = mVideoView.getVideoSize();
            int width = size[0];
            int height = size[1];
            if (width>height){//根据视频尺寸判断是否横屏,小视频则只在activity改了预览尺寸(全屏预览)
                //横屏(传感器)
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            ImmersionBar.with(mActivity)
                    .hideBar(BarHide.FLAG_HIDE_BAR)
                    .navigationBarColor(R.color.black)//即使隐藏部分时候还是会显示
                    .fitsSystemWindows(false)
                    .init();
        }else {//非全屏统一设置竖屏,activity处理为小的预览尺寸
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            ImmersionBar.with(mActivity)
                    .hideBar(BarHide.FLAG_SHOW_BAR)
                    .navigationBarColor(R.color.white)
                    .fitsSystemWindows(true)
                    .init();
        }

        mController.changedLandscape(fullWindows);
    }

    //设置字幕
    void setSubtitle(String path) {
        if (path != null && path.length() > 0) {
            // 设置字幕
            mController.mSubtitleView.setVisibility(View.GONE);
            mController.mSubtitleView.setSubtitlePath(path);
            mController.mSubtitleView.setVisibility(View.VISIBLE);
        }
    }

    void selectMySubtitle() throws Exception {
        SubtitleDialog subtitleDialog = new SubtitleDialog(getActivity());
        subtitleDialog.setSubtitleViewListener(new SubtitleDialog.SubtitleViewListener() {
            @Override
            public void setTextSize(int size) {
                mController.mSubtitleView.setTextSize(size);
            }

            @Override
            public void setSubtitleDelay(int milliseconds) {
                mController.mSubtitleView.setSubtitleDelay(milliseconds);
            }

            @Override
            public void selectInternalSubtitle() {
                selectMyInternalSubtitle();
            }

            @Override
            public void setTextStyle(int style) {
                setSubtitleViewTextStyle(style);
            }

            @Override
            public void subtitleOpen(boolean b) {
                mController.openSubtitle(b);
            }
        });
        subtitleDialog.setSearchSubtitleListener(new SubtitleDialog.SearchSubtitleListener() {
            @Override
            public void openSearchSubtitleDialog() {
                SearchSubtitleDialog searchSubtitleDialog = new SearchSubtitleDialog(getActivity());
                searchSubtitleDialog.setSubtitleLoader(new SearchSubtitleDialog.SubtitleLoader() {
                    @Override
                    public void loadSubtitle(Subtitle subtitle) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String zimuUrl = subtitle.getUrl();
                                LOG.i("Remote Subtitle Url: " + zimuUrl);
                                setSubtitle(zimuUrl);//设置字幕
                                searchSubtitleDialog.dismiss();
                            }
                        });
                    }
                });
                if (mVodInfo.playFlag.contains("Ali") || mVodInfo.playFlag.contains("parse")) {
                    searchSubtitleDialog.setSearchWord(mVodInfo.playNote);
                } else {
                    searchSubtitleDialog.setSearchWord(mVodInfo.name);
                }
                searchSubtitleDialog.show();
            }
        });
        subtitleDialog.setLocalFileChooserListener(new SubtitleDialog.LocalFileChooserListener() {
            @Override
            public void openLocalFileChooserDialog() {
                new ChooserDialog(getActivity(),R.style.FileChooser)
                        .withFilter(false, false, "srt", "ass", "scc", "stl", "ttml")
                        .withStartFile("/storage/emulated/0/Download")
                        .withChosenListener(new ChooserDialog.Result() {
                            @Override
                            public void onChoosePath(String path, File pathFile) {
                                LOG.i("Local Subtitle Path: " + path);
                                setSubtitle(path);//设置字幕
                            }
                        })
                        .build()
                        .show();
            }
        });
        subtitleDialog.show();
    }

    @SuppressLint("UseCompatLoadingForColorStateLists")
    void setSubtitleViewTextStyle(int style) {
        if (style == 0) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFFFFF));
        } else if (style == 1) {
            mController.mSubtitleView.setTextColor(getContext().getResources().getColorStateList(R.color.color_FFB6C1));
        }
    }

    void selectMyAudioTrack() {
        com.kukuqi.tvbox.osc.player.PlaybackTracks.select(requireActivity(), mVideoView, mController.mSubtitleView, true);
    }

    void selectMyInternalSubtitle() {
        com.kukuqi.tvbox.osc.player.PlaybackTracks.select(requireActivity(), mVideoView, mController.mSubtitleView, false);
    }

    void setTip(String msg, boolean loading, boolean err) {
        if (!isAdded()) return;
        //影魔
        requireActivity().runOnUiThread(() -> {
            mPlayLoadTip.setText(msg);
            mPlayLoadTip.setVisibility(View.VISIBLE);
            mPlayLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            mPlayLoadErr.setVisibility(err ? View.VISIBLE : View.GONE);

            if ("视频播放出错".equals(msg)){
                if (!retriedSwitchPlayer){
                    ToastUtils.showShort("播放出错,正在尝试切换播放器");
                    retriedSwitchPlayer = true;
                    mController.mPlayerBtn.performClick();
                }else {
                    SpanUtils.with(mPlayLoadTip)
                            .append("视频播放出错，")
                            .append("切换播放器")
                            .setClickSpan(ColorUtils.getColor(R.color.orange), false, view -> {
                                mController.mPlayerBtn.performClick();
                            }).create();
                }
            }
        });
    }

    void hideTip() {
        mPlayLoadTip.setVisibility(View.GONE);
        mPlayLoading.setVisibility(View.GONE);
        mPlayLoadErr.setVisibility(View.GONE);
    }

    void errorWithRetry(String err, boolean finish) {
        if (!autoRetry() && isAdded()) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (finish) {
                        Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                    } else {
                        setTip(err, false, true);
                    }
                }
            });
        }
    }

    private String removeMinorityUrl(String tsUrlPre, String m3u8content) {
        if (!m3u8content.startsWith("#EXTM3U")) return null;
        String linesplit = "\n";
        if (m3u8content.contains("\r\n"))
            linesplit = "\r\n";
        String[] lines = m3u8content.split(linesplit);

        HashMap<String, Integer> preUrlMap = new HashMap<>();
        for (String line : lines) {
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            int ilast = line.lastIndexOf('.');
            if (ilast <= 4) {
                continue;
            }
            String preUrl = line.substring(0, ilast - 4);
            Integer cnt = preUrlMap.get(preUrl);
            if (cnt != null) {
                preUrlMap.put(preUrl, cnt + 1);
            } else {
                preUrlMap.put(preUrl, 1);
            }
        }
        if (preUrlMap.size() <= 1) return null;
        if (preUrlMap.size() > 5) return null;//too many different url, can not identify ads url
        int maxTimes = 0;
        String maxTimesPreUrl = "";
        for (Map.Entry<String, Integer> entry : preUrlMap.entrySet()) {
            if (entry.getValue() > maxTimes) {
                maxTimesPreUrl = entry.getKey();
                maxTimes = entry.getValue();
            }
        }
        if (maxTimes == 0) return null;

        boolean dealedExtXKey = false;
        for (int i = 0; i < lines.length; ++i) {
            if (!dealedExtXKey && lines[i].startsWith("#EXT-X-KEY")) {
                int keyStart = lines[i].indexOf("URI=\"");
                int keyEnd = keyStart < 0 ? -1 : lines[i].indexOf('"', keyStart + 5);
                String keyUrl = keyEnd < 0 ? null : lines[i].substring(keyStart + 5, keyEnd);
                if (keyUrl != null && !keyUrl.isEmpty() && !keyUrl.startsWith("http://") && !keyUrl.startsWith("https://")) {
                    String newKeyUrl;
                    if (keyUrl.charAt(0) == '/') {
                        int ifirst = tsUrlPre.indexOf('/', 9);//skip https://, http://
                        newKeyUrl = tsUrlPre.substring(0, ifirst) + keyUrl;
                    } else
                        newKeyUrl = tsUrlPre + keyUrl;
                    lines[i] = lines[i].replace("URI=\"" + keyUrl + "\"", "URI=\"" + newKeyUrl + "\"");
                }
                dealedExtXKey = true;
            }
            if (lines[i].length() == 0 || lines[i].charAt(0) == '#') {
                continue;
            }
            if (lines[i].startsWith(maxTimesPreUrl)) {
                if (!lines[i].startsWith("http://") && !lines[i].startsWith("https://")) {
                    if (lines[i].charAt(0) == '/') {
                        int ifirst = tsUrlPre.indexOf('/', 9);//skip https://, http://
                        lines[i] = tsUrlPre.substring(0, ifirst) + lines[i];
                    } else
                        lines[i] = tsUrlPre + lines[i];
                }
            } else {
                if (i > 0 && lines[i - 1].length() > 0 && lines[i - 1].charAt(0) == '#') {
                    lines[i - 1] = "";
                }
                lines[i] = "";
            }
        }
        return TextUtils.join(linesplit, lines);
    }

    void playUrl(String url, HashMap<String, String> headers) {
        mCurrentUrl = url;
        if (!Hawk.get(HawkConfig.VIDEO_PURIFY, true)) {
            startPlayUrl(url, headers);
            return;
        }
        if (!url.contains("://127.0.0.1/") && !url.contains(".m3u8")) {
            startPlayUrl(url, headers);
            return;
        }
        OkGo.getInstance().cancelTag("m3u8-1");
        OkGo.getInstance().cancelTag("m3u8-2");
        //remove ads in m3u8
        HttpHeaders hheaders = new HttpHeaders();
        if(headers != null){
            for (Map.Entry<String, String> s : headers.entrySet()) {
                hheaders.put(s.getKey(), s.getValue());
            }
        }

        OkGo.<String>get(url)
                .tag("m3u8-1")
                .headers(hheaders)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                        String content = response.body();
                        if (!content.startsWith("#EXTM3U")) {
                            startPlayUrl(url, headers);
                            return;
                        }

                        String[] lines = null;
                        if (content.contains("\r\n"))
                            lines = content.split("\r\n", 10);
                        else
                            lines = content.split("\n", 10);
                        String forwardurl = "";
                        boolean dealedFirst = false;
                        for (String line : lines) {
                            if (!"".equals(line) && line.charAt(0) != '#') {
                                if (dealedFirst) {
                                    //跳转行后还有内容，说明不需要跳转
                                    forwardurl = "";
                                    break;
                                }
                                if (line.endsWith(".m3u8") || line.contains(".m3u8?")) {
                                    if (line.startsWith("http://") || line.startsWith("https://")) {
                                        forwardurl = line;
                                    } else if (line.charAt(0)=='/' ) {
                                        int ifirst = url.indexOf('/', 9);//skip https://, http://
                                        forwardurl = url.substring(0, ifirst) + line;
                                    } else {
                                        int ilast = url.lastIndexOf('/');
                                        forwardurl = url.substring(0, ilast + 1) + line;
                                    }
                                }
                                dealedFirst = true;
                            }
                        }
                        if ("".equals(forwardurl)) {
                            int ilast = url.lastIndexOf('/');

                            RemoteServer.m3u8Content = removeMinorityUrl(url.substring(0, ilast + 1), content);
                            if (RemoteServer.m3u8Content == null)
                                startPlayUrl(url, headers);
                            else {
                                startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        final String finalforwardurl = forwardurl;
                        OkGo.<String>get(forwardurl)
                                .tag("m3u8-2")
                                .headers(hheaders)
                                .execute(new AbsCallback<String>() {
                                    @Override
                                    public void onSuccess(com.lzy.okgo.model.Response<String> response) {
                                        String content = response.body();
                                        int ilast = finalforwardurl.lastIndexOf('/');
                                        RemoteServer.m3u8Content = removeMinorityUrl(finalforwardurl.substring(0, ilast + 1), content);

                                        if (RemoteServer.m3u8Content == null)
                                            startPlayUrl(finalforwardurl, headers);
                                        else {
                                            startPlayUrl("http://127.0.0.1:" + RemoteServer.serverPort + "/m3u8", headers);
                                            //Toast.makeText(getContext(), "已移除视频广告", Toast.LENGTH_SHORT).show();
                                        }
                                    }

                                    @Override
                                    public String convertResponse(okhttp3.Response response) throws Throwable {
                                        return response.body().string();
                                    }

                                    @Override
                                    public void onError(com.lzy.okgo.model.Response<String> response) {
                                        super.onError(response);
                                        startPlayUrl(url, headers);
                                    }
                                });
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }

                    @Override
                    public void onError(com.lzy.okgo.model.Response<String> response) {
                        super.onError(response);
                        startPlayUrl(url, headers);
                    }
                });
    }

    void startPlayUrl(String url, HashMap<String, String> headers) {
        LOG.i("playUrl:" + url);
        if (autoRetryCount > 0 && url.contains(".m3u8")) {
            url = "http://home.jundie.top:666/unBom.php?m3u8=" + url;//尝试去bom头再次播放
        }
        String finalUrl = url;
        if (mActivity == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            stopParse();
            if (mVideoView != null) {
                mVideoView.release();

                if (finalUrl != null) {
                    try {
                        int playerType = mVodPlayerCfg.getInt("pl");
                        if (playerType >= 10) {
                            VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
                            String playTitle = mVodInfo.name + " " + vs.name;
                            setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                            boolean callResult = false;
                            long progress = getSavedProgress(progressKey);
                            callResult = PlayerHelper.runExternalPlayer(playerType, requireActivity(), finalUrl, playTitle, playSubtitle, headers, progress);
                            setTip("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (callResult ? "成功" : "失败"), callResult, !callResult);
                            return;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    hideTip();
                    PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg, corePlayResult);
                    mVideoView.setProgressKey(progressKey);
                    if (headers != null) {
                        mVideoView.setUrl(finalUrl, headers);
                    } else {
                        mVideoView.setUrl(finalUrl);
                    }
                    mVideoView.start();
                    mController.resetSpeed();
                }
            }
        });
    }

    private void initSubtitleView() {
        com.kukuqi.tvbox.osc.player.PlaybackTracks.bind(mVideoView, mController.mSubtitleView);
        mController.mSubtitleView.setPlaySubtitleCacheKey(subtitleCacheKey);
        String cached = (String) CacheManager.getCache(MD5.string2MD5(subtitleCacheKey));
        if (cached != null && !cached.isEmpty()) mController.mSubtitleView.setSubtitlePath(cached);
        else if (playSubtitle != null && !playSubtitle.isEmpty()) mController.mSubtitleView.setSubtitlePath(playSubtitle);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.playResult.observeForever(mObserverPlayResult);
    }

    private final Observer<JSONObject> mObserverPlayResult= new Observer<JSONObject>() {
        @Override
        public void onChanged(JSONObject info) {
            if (info != null) {
                try {
                    JSONObject core = info.optJSONObject("coreResult");
                    corePlayResult = com.kukuqi.tvbox.engine.bean.Result.objectFrom(core == null ? info.toString() : core.toString());
                    corePlayResult.setKey(sourceKey);
                    sourceSkips.reset();
                    if (corePlayResult.hasMsg()) Toast.makeText(requireContext(), corePlayResult.getMsg(), Toast.LENGTH_LONG).show();
                    if (mVodInfo != null) {
                        if (!corePlayResult.getArtwork().isEmpty()) mVodInfo.pic = corePlayResult.getArtwork();
                        if (!corePlayResult.getDesc().isEmpty()) mVodInfo.des = android.text.Html.fromHtml(corePlayResult.getDesc(), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
                    }
                    final com.kukuqi.tvbox.engine.bean.Result qualities = corePlayResult;
                    mVideoView.setQualityOptions(qualities, index -> {
                        if (!isAdded() || mVideoView == null || corePlayResult != qualities) return;
                        if (index == qualities.getUrl().getPosition()) return;
                        qualityResumePosition = mVideoView.getCurrentPosition();
                        qualityResumePlaying = mVideoView.isPlaying();
                        nextEpisode.clear();
                        qualities.getUrl().set(index);
                        autoRetryCount = 0;
                        playCoreSelection();
                    });
                    progressKey = info.optString("proKey", null);
                    playSubtitle = info.optString("subt", /*"https://dash.akamaized.net/akamai/test/caption_test/ElephantsDream/ElephantsDream_en.vtt"*/"");
                    subtitleCacheKey = info.optString("subtKey", null);
                    HashMap<String, String> headers = null;
                    webUserAgent = null;
                    webHeaderMap = null;
                    if (info.has("header")) {
                        try {
                            JSONObject hds = new JSONObject(info.getString("header"));
                            Iterator<String> keys = hds.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                if (headers == null) {
                                    headers = new HashMap<>();
                                }
                                headers.put(key, hds.getString(key));
                                if (key.equalsIgnoreCase("user-agent")) {
                                    webUserAgent = hds.getString(key).trim();
                                }
                            }
                            webHeaderMap = headers;
                        } catch (Throwable th) {

                        }
                    }
                    playerRequestHeaders = headers == null ? new HashMap<>() : new HashMap<>(headers);
                    playCoreSelection();
                    com.google.gson.JsonElement comments = info.has("danmaku") && !info.isNull("danmaku")
                            ? com.google.gson.JsonParser.parseString(info.toString()).getAsJsonObject().get("danmaku") : null;
                    mVideoView.setDanmakuContext(comments, mVodInfo == null ? "" : mVodInfo.name,
                            mVodInfo == null ? "" : mVodInfo.playNote);
                } catch (Throwable th) {
                    LogUtils.e(th.toString());
//                        errorWithRetry("获取播放信息错误", true);
//                        Toast.makeText(mContext, "获取播放信息错误1", Toast.LENGTH_SHORT).show();
                }
            } else {
                errorWithRetry("获取播放信息错误", true);
//                    Toast.makeText(mContext, "获取播放信息错误", Toast.LENGTH_SHORT).show();
            }
        }
    };

    public void setData(Bundle bundle) {
//        mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
        mVodInfo = App.getInstance().getVodInfo();
        sourceKey = bundle.getString("sourceKey");
        sourceBean = ApiConfig.get().getSource(sourceKey);
        initPlayerCfg();
        play(false);
    }

    private void initData() {
        /*Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {

        }*/
    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", (sourceBean.getPlayerType() == -1) ? (int) Hawk.get(HawkConfig.PLAY_TYPE, 1) : sourceBean.getPlayerType());
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            if (mController.onKeyEvent(event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideoView != null && (!(getActivity() instanceof DetailActivity)
                || !((DetailActivity) getActivity()).keepsPlayingInBackground())) {
            mVideoView.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVideoView != null) {
            mVideoView.resume();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) {
            if (mVideoView != null) {
                mVideoView.pause();
            }
        } else {
            if (mVideoView != null) {
                mVideoView.resume();
            }
        }
        super.onHiddenChanged(hidden);
    }

    @Override
    public void onDestroyView() {
        nextEpisode.close();
        if (sourceViewModel != null) sourceViewModel.cancelPlay();
        super.onDestroyView();
        //手动注销
        sourceViewModel.playResult.removeObserver(mObserverPlayResult);

        EventBus.getDefault().unregister(this);
        if (mVideoView != null) {
            mVideoView.setQualityOptions(null, null);
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        com.kukuqi.tvbox.engine.player.extractor.Source.get().stop();

    }

    private VodInfo mVodInfo;
    private JSONObject mVodPlayerCfg;
    private String sourceKey;
    private SourceBean sourceBean;

    public void playNext(boolean isProgress) {
        boolean hasNext;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(requireContext(), "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            mVodInfo.playIndex++;
        }
        play(false);
    }

    public void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(requireContext(), "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        play(false);
    }

    private int autoRetryCount = 0;

    boolean autoRetry() {
        if (loadFoundVideoUrls != null && loadFoundVideoUrls.size() > 0) {
            autoRetryFromLoadFoundVideoUrls();
            return true;
        }
        if (autoRetryCount < 1) {
            autoRetryCount++;
            play(false);
            return true;
        } else {
            autoRetryCount = 0;
            return false;
        }
    }

    void autoRetryFromLoadFoundVideoUrls() {
        String videoUrl = loadFoundVideoUrls.poll();
        HashMap<String, String> header = loadFoundVideoUrlsHeader.get(videoUrl);
        playUrl(videoUrl, header);
    }

    void initParseLoadFound() {
        loadFoundCount.set(0);
        loadFoundVideoUrls = new LinkedList<String>();
        loadFoundVideoUrlsHeader = new HashMap<String, HashMap<String, String>>();
    }

    public void play(boolean reset) {
        if (mVodInfo == null) return;
        qualityResumePosition = -1;
        corePlayResult = null;
        sourceSkips.reset();
        if (mVideoView != null) mVideoView.setQualityOptions(null, null);
        VodInfo.VodSeries vs = mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH_NOTIFY, mVodInfo.name + "&&" + vs.name));
        String playTitleInfo = mVodInfo.name + " " + vs.name;
        setTip("正在获取播放信息", true, false);
        mController.setTitle(playTitleInfo);

        stopParse();
        initParseLoadFound();
        if (mVideoView != null) mVideoView.release();
        String subtitleCacheKey = mVodInfo.sourceKey + "-" + mVodInfo.id + "-" + mVodInfo.playFlag + "-" + mVodInfo.playIndex + "-" + vs.name + "-subt";
        String progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex + vs.name;
        //重新播放清除现有进度
        if (reset) {
            CacheManager.delete(MD5.string2MD5(progressKey), 0);
            CacheManager.delete(MD5.string2MD5(subtitleCacheKey), 0);
        }
        com.kukuqi.tvbox.engine.bean.Result cached = nextEpisode.take(new com.kukuqi.tvbox.osc.core.EpisodePreloader.Key(
                sourceKey, mVodInfo.id, mVodInfo.playFlag, vs.url));
        sourceViewModel.getPlay(sourceKey, mVodInfo.playFlag, progressKey, vs.url, subtitleCacheKey, reset ? null : cached);
    }

    private void preloadNext(long duration, long position) {
        if (mVideoView == null || mVodInfo == null || !mVideoView.isPlaying()) return;
        if (!(mVideoView.getMediaPlayer() instanceof com.kukuqi.tvbox.osc.player.CorePlayer core)) return;
        if (!Hawk.get(HawkConfig.NEXT_EPISODE_PRELOAD, true)) {
            nextEpisode.clear();
            if (core.manager() != null) core.manager().clearPreload();
            return;
        }
        long skipEnd = mVodPlayerCfg == null ? 0 : mVodPlayerCfg.optLong("et", 0) * 1000;
        long untilNext = duration - position - skipEnd;
        if (duration < 30000 || position < 5000 || untilNext > 30000 || untilNext < 0) {
            nextEpisode.clear();
            if (core.manager() != null) core.manager().clearPreload();
            return;
        }
        if (core.manager() == null || !core.manager().canPreloadNext() || corePlayResult == null
                || corePlayResult.getDrm() != null) return;
        List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        int index = mVodInfo.playIndex + 1;
        if (episodes == null || index >= episodes.size()) return;
        VodInfo.VodSeries episode = episodes.get(index);
        if (TextUtils.isEmpty(episode.url) || episode.url.startsWith("magnet:") || episode.url.startsWith("thunder:")
                || episode.url.startsWith("ed2k:") || episode.url.startsWith("file:")) return;
        String key = sourceKey, flag = mVodInfo.playFlag;
        int quality = corePlayResult.getUrl().getPosition();
        long start = mVodPlayerCfg == null ? 0 : Math.max(0, mVodPlayerCfg.optLong("st", 0) * 1000);
        var config = com.kukuqi.tvbox.engine.api.config.VodConfig.get();
        var selectedParse = com.kukuqi.tvbox.engine.bean.Parse.objectFrom(com.kukuqi.tvbox.engine.App.gson().toJsonTree(config.getParse()));
        var parseLines = config.getParses().stream().map(item -> com.kukuqi.tvbox.engine.bean.Parse.objectFrom(
                com.kukuqi.tvbox.engine.App.gson().toJsonTree(item))).toList();
        nextEpisode.request(new com.kukuqi.tvbox.osc.core.EpisodePreloader.Key(key, mVodInfo.id, flag, episode.url), () -> {
            com.kukuqi.tvbox.engine.bean.Result result = com.kukuqi.tvbox.osc.core.CoreBridge.preload(key, flag, episode.url);
            if (quality >= 0 && quality < result.getUrl().getValues().size()) result.getUrl().set(quality);
            // Multiple qualities need their original parsing metadata when switching later.
            if (result.getUrl().isMulti() && (result.needParse() || !result.getPlayUrl().isEmpty())) return null;
            return com.kukuqi.tvbox.engine.player.parse.PreloadResolver.resolve(result, selectedParse, parseLines);
        }, result -> {
            if (mVideoView == null || mVideoView.getMediaPlayer() != core || core.manager() == null || core.manager().isReleased()) return;
            Bundle extras = new Bundle();
            extras.putBoolean(com.kukuqi.tvbox.engine.player.exo.ExoMediaSourceFactory.PRELOAD_CACHE_KEY, true);
            core.manager().preload(com.kukuqi.tvbox.engine.player.media.PlaySpec.from(result, key,
                    new androidx.media3.common.MediaMetadata.Builder().setExtras(extras).build()), start);
        });
    }

    private final com.kukuqi.tvbox.osc.core.SkipPolicy sourceSkips = new com.kukuqi.tvbox.osc.core.SkipPolicy();
    private boolean applySourceSkips(long duration, long position) {
        if (corePlayResult == null || mVideoView == null || !mVideoView.isPlaying()) return false;
        boolean hasNext = mVodInfo != null && mVodInfo.seriesMap.get(mVodInfo.playFlag) != null
                && mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        long intro = mVodPlayerCfg == null ? 0 : mVodPlayerCfg.optLong("st", 0);
        long outro = mVodPlayerCfg == null ? 0 : mVodPlayerCfg.optLong("et", 0);
        var decision = sourceSkips.progress(corePlayResult.getSkips(), position, duration, intro, outro, hasNext);
        if (decision == null) return false;
        if (decision.next()) playNext(false); else mVideoView.seekTo(decision.position());
        return true;
    }
    private com.kukuqi.tvbox.engine.bean.Result corePlayResult;
    private long qualityResumePosition = -1;
    private boolean qualityResumePlaying;

    private void playCoreSelection() {
        if (corePlayResult == null) return;
        String url = corePlayResult.getUrl().v();
        String prefix = corePlayResult.getPlayUrl();
        if (corePlayResult.needParse()) {
            boolean useParse = (prefix.isEmpty() && ApiConfig.get().getVipParseFlags().contains(corePlayResult.getFlag())) || corePlayResult.getJx() == 1;
            initParse(corePlayResult.getFlag(), useParse, prefix, url);
        } else {
            mController.showParse(false);
            playUrl(prefix + url, new HashMap<>(corePlayResult.getHeader()));
        }
    }
    private com.kukuqi.tvbox.engine.player.parse.ParseJob coreParseJob;
    private String playSubtitle;
    private String subtitleCacheKey;
    private String progressKey;
    private String parseFlag;
    private String webUrl;
    private String webUserAgent;
    private Map<String, String> webHeaderMap;
    private Map<String, String> playerRequestHeaders = new HashMap<>();
    private Map<String, String> parseHeaders(ParseBean parse) {
        Map<String, String> result = new HashMap<>(playerRequestHeaders);
        result.putAll(parse.getHeaders()); return result;
    }

    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        parseFlag = flag;
        webUrl = url;
        ParseBean parseBean = null;
        mController.showParse(useParse);
        if (useParse) {
            parseBean = ApiConfig.get().getDefaultParse();
        }
        {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.get().getParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
            if (parseBean == null) {
                parseBean = new ParseBean();
                parseBean.setType(0);
                parseBean.setUrl(playUrl);
            }
        }
        doParse(parseBean);
    }

    JSONObject jsonParse(String input, String json) throws JSONException {
        com.kukuqi.tvbox.osc.util.ParseResult parsed = com.kukuqi.tvbox.osc.util.ParseResult.read(json, null);
        JSONObject result = new JSONObject();
        result.put("url", parsed.url);
        result.put("header", new JSONObject(parsed.headers));
        return result;
    }

    void stopParse() {
        parseGeneration++;
        if (coreParseJob != null) { coreParseJob.stop(); coreParseJob = null; }
        if (automaticParser != null) { automaticParser.stop(); automaticParser = null; }
        mHandler.removeMessages(100);
        stopLoadWebView(false);
        OkGo.getInstance().cancelTag("json_jx");
        OkGo.getInstance().cancelTag("auto_jx");
        if (parseThreadPool != null) {
            try {
                parseThreadPool.shutdown();
                parseThreadPool = null;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    ExecutorService parseThreadPool;
    private int parseGeneration;

    private com.kukuqi.tvbox.osc.player.AutomaticParser automaticParser;
    private void autoParse() {
        ParseBean automatic = new ParseBean();
        automatic.setType(4);
        doParse(automatic);
    }

    private void doParse(ParseBean pb) {
        nextEpisode.clear();
        if (mVideoView != null && mVideoView.getMediaPlayer() instanceof com.kukuqi.tvbox.osc.player.CorePlayer core
                && core.manager() != null) core.manager().clearPreload();
        stopParse();
        if (pb == null) { setTip("当前配置没有解析线路", false, true); return; }
        setTip(pb.getType() == 0 ? "正在嗅探播放地址" : "正在解析播放地址", true, false);
        final int generation = parseGeneration;
        com.google.gson.JsonObject selected = new com.google.gson.JsonObject();
        selected.addProperty("name", pb.getName());
        selected.addProperty("url", pb.getUrl());
        selected.addProperty("type", pb.getType());
        try { selected.add("ext", com.google.gson.JsonParser.parseString(pb.getExt())); }
        catch (Exception ignored) { }
        com.kukuqi.tvbox.engine.bean.Result request = corePlayResult == null
                ? new com.kukuqi.tvbox.engine.bean.Result()
                : com.kukuqi.tvbox.engine.bean.Result.objectFrom(corePlayResult.toString());
        request.setKey(sourceKey);
        request.setFlag(parseFlag);
        request.setUrl(webUrl);
        request.setHeader(playerRequestHeaders);
        coreParseJob = com.kukuqi.tvbox.engine.player.parse.ParseJob.create(new com.kukuqi.tvbox.engine.impl.ParseCallback() {
            @Override public void onParseSuccess(Map<String,String> headers, String url, String from) {
                if (generation == parseGeneration && isAdded()) playUrl(url, new HashMap<>(headers));
            }
            @Override public void onParseError() {
                if (generation == parseGeneration && isAdded()) errorWithRetry("解析错误，请切换线路或重试", false);
            }
        }).start(request, com.kukuqi.tvbox.engine.bean.Parse.objectFrom(selected));
    }

    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return url;
        }
    }

    private WebView mSysWebView;
    private final Map<String, Boolean> loadedUrls = new HashMap<>();
    private LinkedList<String> loadFoundVideoUrls = new LinkedList<>();
    private HashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new HashMap<>();
    private final AtomicInteger loadFoundCount = new AtomicInteger(0);

    void loadWebView(String url) {
        if (mSysWebView == null) {
            mSysWebView = new MyWebView(mContext);
            configWebViewSys(mSysWebView);
            loadUrl(url);
        } else {
            loadUrl(url);
        }
    }

    void loadUrl(String url) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                if (webUserAgent != null) {
                    mSysWebView.getSettings().setUserAgentString(webUserAgent);
                }
                //mSysWebView.clearCache(true);
                if (webHeaderMap != null) {
                    mSysWebView.loadUrl(url, webHeaderMap);
                } else {
                    mSysWebView.loadUrl(url);
                }
            }
        });
    }

    void stopLoadWebView(boolean destroy) {
        if (mActivity == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {

            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.loadUrl("about:blank");
                if (destroy) {
//                        mSysWebView.clearCache(true);
                    mSysWebView.removeAllViews();
                    mSysWebView.destroy();
                    mSysWebView = null;
                }
            }
        });
    }

    public String getFinalUrl(){
        return TextUtils.isEmpty(mCurrentUrl) || !RegexUtils.isURL(mCurrentUrl) ?"":mCurrentUrl;
    }

    public void showCastDialog() {
        if (mVideoView == null || !(mVideoView.getMediaPlayer() instanceof com.kukuqi.tvbox.osc.player.CorePlayer)) {
            ToastUtils.showShort("播放地址尚未准备好，请开始播放后重试");
            return;
        }
        com.kukuqi.tvbox.osc.player.CorePlayer core = (com.kukuqi.tvbox.osc.player.CorePlayer) mVideoView.getMediaPlayer();
        com.kukuqi.tvbox.engine.player.PlayerManager manager = core.manager();
        if (manager == null || manager.getUrl() == null || manager.getUrl().isEmpty()) {
            ToastUtils.showShort("播放地址尚未准备好，请稍后重试");
            return;
        }
        String title = mVodInfo == null ? "TVboxOSC" : mVodInfo.name;
        if (mVodInfo != null && mVodInfo.seriesMap.get(mVodInfo.playFlag) != null &&
                mVodInfo.playIndex >= 0 && mVodInfo.playIndex < mVodInfo.seriesMap.get(mVodInfo.playFlag).size()) {
            title += " " + mVodInfo.seriesMap.get(mVodInfo.playFlag).get(mVodInfo.playIndex).name;
        }
        com.kukuqi.tvbox.engine.bean.CastVideo video = new com.kukuqi.tvbox.engine.bean.CastVideo(
                title, manager.getUrl(), manager.getPosition(), manager.getHeaders());
        com.kukuqi.tvbox.osc.ui.dialog.CastDialog.show(requireActivity(), video, mVideoView::pause);
    }

    boolean checkVideoFormat(String url) {
        try {
            if (url.contains("url=http") || url.contains(".html")) {
                return false;
            }
            if (sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck()) {
                    return sp.isVideoFormat(url);
                }
            }
            return VideoParseRuler.checkIsVideoForParse(webUrl, url);
        } catch (Exception e) {
            return false;
        }
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayFragment.this);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        requireActivity().addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
//        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* 添加webView配置 */
        //设置编码
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
//         settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        SysWebClient mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String click = sourceBean.getClickSelector();
            LOG.i("onPageFinished url:" + url);

            if (!click.isEmpty()) {
                String selector;
                if (click.contains(";")) {
                    if (!url.contains(click.split(";")[0])) return;
                    selector = click.split(";")[1];
                } else {
                    selector = click.trim();
                }
                String js = "$(\"" + selector + "\").click();";
                LOG.i("javascript:" + js);
                mSysWebView.loadUrl("javascript:" + js);
            }
        }

        WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = Boolean.TRUE.equals(loadedUrls.get(url));
            }

            if (!ad) {
                if (checkVideoFormat(url)) {
                    loadFoundVideoUrls.add(url);
                    loadFoundVideoUrlsHeader.put(url, headers);
                    LOG.i("loadFoundVideoUrl:" + url);
                    if (loadFoundCount.incrementAndGet() == 1) {
                        url = loadFoundVideoUrls.poll();
                        mHandler.removeMessages(100);
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (!TextUtils.isEmpty(cookie))
                            headers.put("Cookie", " " + cookie);//携带cookie
                        playUrl(url, headers);
                        stopLoadWebView(false);
                    }
                }
            }

            return ad || loadFoundCount.get() > 0 ?
                    AdBlocker.createEmptyResource() :
                    null;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
//            WebResourceResponse response = checkIsVideo(url, new HashMap<>());
            return null;
        }

        @Nullable
        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("shouldInterceptRequest url:" + url);
            HashMap<String, String> webHeaders = new HashMap<>();
            Map<String, String> hds = request.getRequestHeaders();
            if (hds != null && hds.keySet().size() > 0) {
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k, " " + hds.get(k));
                    }
                }
            }
            return checkIsVideo(url, webHeaders);
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
        }
    }

    public MyVideoView getPlayer() {
        return mVideoView;
    }
    public VodController getController() {
        return mController;
    }

}
