package com.kukuqi.tvbox.osc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonPrimitive;
import com.kukuqi.tvbox.osc.api.ApiConfig;
import com.kukuqi.tvbox.osc.base.App;
import com.kukuqi.tvbox.osc.player.MyVideoView;
import com.kukuqi.tvbox.osc.ui.activity.*;
import com.kukuqi.tvbox.osc.ui.dialog.VideoLinkDialog;
import com.kukuqi.tvbox.osc.ui.fragment.PlayFragment;
import com.kukuqi.tvbox.osc.util.*;
import com.orhanobut.hawk.Hawk;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** End-to-end behavior using a local HTTP source and a real H.264 video. */
public class FeatureRegressionTest extends InstrumentationTestCase {
    private static final String VIDEO = "/sdcard/TVboxOSC-regression/tvbox-regression.mp4";
    private final Map<String,Object> before = new HashMap<>();
    private final String[] keys = {HawkConfig.API_URL,HawkConfig.LIVE_URL,HawkConfig.HOME_API,HawkConfig.HOME_REC,HawkConfig.PLAY_TYPE,
            HawkConfig.BACKGROUND_PLAY_TYPE,HawkConfig.PRIVATE_BROWSING,HawkConfig.IJK_CODEC,"danmaku_load","danmaku_show","danmaku_auto",
            "danmaku_api_url","danmaku_spider_first","hot_search_cache","hot_search_time","poster_size","theme_color","live_playlist_url",
            "wallpaper_mode",HawkConfig.WALLPAPER_URL,"wallpaper_softness","home_hot","home_hot_day"};
    private final java.util.Queue<String> routes=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private NanoHTTPD server; private File config; private String base; private Activity active;
    private final AtomicInteger homeRequests = new AtomicInteger(), searchRequests = new AtomicInteger(), matchRequests = new AtomicInteger(), commentRequests = new AtomicInteger();
    @Override protected void setUp() throws Exception {
        super.setUp();
        for (String key: keys) if (Hawk.contains(key)) before.put(key,Hawk.get(key));
        server = new NanoHTTPD("127.0.0.1",0) {
            @Override public Response serve(IHTTPSession session) {
                String path=session.getUri();routes.add(path);
                if(path.equals("/video.mp4")) {
                    try { return newFixedLengthResponse(Response.Status.OK,"video/mp4",new FileInputStream(VIDEO),new File(VIDEO).length()); }
                    catch(Exception e) { return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","missing fixture"); }
                }
                if(path.equals("/comments.xml")) {commentRequests.incrementAndGet();return newFixedLengthResponse(Response.Status.OK,"application/xml","<i><d p='0,1,25,16777215'>真实弹幕回归验证</d><d p='3,5,25,16711680'>顶部弹幕</d></i>");}
                if(path.equals("/match")) {
                    if (session.getMethod() == Method.POST) {
                        try { session.parseBody(new HashMap<>()); }
                        catch (Exception e) { return newFixedLengthResponse(Response.Status.BAD_REQUEST,"text/plain",e.toString()); }
                    }
                    matchRequests.incrementAndGet();return newFixedLengthResponse(Response.Status.OK,"application/json","[{\"name\":\"测试\",\"url\":\""+base+"/comments.xml\"}]");
                }
                if(path.equals("/web-empty")) return newFixedLengthResponse("<html>no video</html>");
                if(path.equals("/web-video")) return newFixedLengthResponse(Response.Status.OK,"text/html","<html><video autoplay src='"+base+"/video.mp4?from=web'></video></html>");
                if(path.equals("/json-slow")) {try{Thread.sleep(3000);}catch(Exception ignored){}return newFixedLengthResponse(Response.Status.OK,"application/json","{\"url\":\""+base+"/video.mp4?from=json\"}");}
                if(path.equals("/api")) {
                    if(session.getParms().containsKey("wd")) searchRequests.incrementAndGet(); else homeRequests.incrementAndGet();
                    StringBuilder videos=new StringBuilder();
                    for(int i=0;i<60;i++) { if(i>0) videos.append(','); videos.append("{\"vod_id\":\"").append(i).append("\",\"vod_name\":\"测试影片 ").append(i).append("\",\"vod_pic\":\"\",\"vod_play_from\":\"mp4\",\"vod_play_url\":\"播放$").append(base).append("/video.mp4\"}"); }
                    return newFixedLengthResponse(Response.Status.OK,"application/json","{\"class\":[{\"type_id\":\"1\",\"type_name\":\"电影\"}],\"filters\":{\"1\":[{\"key\":\"year\",\"name\":\"年份\",\"value\":[{\"n\":\"全部\",\"v\":\"\"},{\"n\":\"2026\",\"v\":\"2026\"}]}]},\"page\":1,\"pagecount\":1,\"list\":["+videos+"]}");
                }
                if(path.equals("/first.m3u")||path.equals("/second.m3u")) return newFixedLengthResponse("#EXTM3U\n#EXTINF:-1,"+path+"\n"+base+"/video.mp4\n");
                return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","not found");
            }
        };
        server.start(); base="http://127.0.0.1:"+server.getListeningPort();
        config=new File(getInstrumentation().getTargetContext().getFilesDir(),"core-feature-fixture.json");
        Files.write(config.toPath(),("{\"sites\":[{\"key\":\"fixture\",\"name\":\"测试站点\",\"type\":1,\"api\":\""+base+"/api\",\"searchable\":1}],\"lives\":[{\"name\":\"线路一\",\"url\":\""+base+"/first.m3u\"},{\"name\":\"线路二\",\"url\":\""+base+"/second.m3u\"}]}").getBytes(StandardCharsets.UTF_8));
        onMain(()->{App.getInstance().isNormalStart=true; Hawk.put(HawkConfig.API_URL,"file://"+config.getAbsolutePath()); Hawk.put(HawkConfig.HOME_API,"fixture"); Hawk.put(HawkConfig.HOME_REC,1); Hawk.put(HawkConfig.PLAY_TYPE,2); Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE,0); Hawk.put(HawkConfig.PRIVATE_BROWSING,true);});
    }
    @Override protected void tearDown() throws Exception {
        onMain(()->{ if(active!=null)active.finish(); for(String key:keys) {if(before.containsKey(key))Hawk.put(key,before.get(key)); else Hawk.delete(key);} });
        if (active != null) waitFor(active::isDestroyed);
        getInstrumentation().waitForIdleSync();
        server.stop(); config.delete(); super.tearDown();
    }
    public void testLegacyHomepageMigratesToStationRecommendations() throws Exception {
        okhttp3.OkHttpClient original = com.lzy.okgo.OkGo.getInstance().getOkHttpClient();
        AtomicInteger doubanRequests = new AtomicInteger();
        com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original.newBuilder().addInterceptor(chain -> {
            if (!chain.request().url().host().equals("movie.douban.com")) return chain.proceed(chain.request());
            doubanRequests.incrementAndGet();
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(403).message("Forbidden").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("text/plain"), "Forbidden")).build();
        }).build());
        try {
            onMain(() -> {
                Hawk.delete(HawkConfig.HOME_REC);
                HomeRecommendationSettings.migrate();
                assertEquals(1, (int)Hawk.get(HawkConfig.HOME_REC));
                Hawk.put(HawkConfig.HOME_REC, 99);
                HomeRecommendationSettings.migrate();
                assertEquals(1, (int)Hawk.get(HawkConfig.HOME_REC));
                loadConfig();
                Hawk.put(HawkConfig.HOME_REC, 0);
                HomeRecommendationSettings.migrate();
                assertEquals(1, (int)Hawk.get(HawkConfig.HOME_REC));
            });
            MainActivity main = (MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            active = main;
            waitFor(() -> main.findViewById(R.id.tvHotList1) != null
                    && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter() != null
                    && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter().getItemCount() == 60);
            onMain(() -> assertEquals(View.GONE, main.findViewById(R.id.homeRecommendationStatus).getVisibility()));
            assertEquals(0, doubanRequests.get());
            capture("station-home-after-migration.png");
        } finally { com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original); }
    }

    public void testDisabledHomepageAndTwoOptionSettingsKeepPersistedIds() throws Exception {
        onMain(() -> {
            loadConfig();
            Hawk.put(HawkConfig.HOME_REC, 2);
            HomeRecommendationSettings.migrate();
            assertEquals(2, (int)Hawk.get(HawkConfig.HOME_REC));
        });
        MainActivity main = (MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        active = main;
        waitFor(() -> main.findViewById(R.id.mViewPager) != null
                && ((androidx.viewpager.widget.ViewPager)main.findViewById(R.id.mViewPager)).getAdapter() != null);
        onMain(() -> {
            assertEquals(1, ((androidx.viewpager.widget.ViewPager)main.findViewById(R.id.mViewPager)).getAdapter().getCount());
            assertNull(findFragment(main.getSupportFragmentManager(), com.kukuqi.tvbox.osc.ui.fragment.UserFragment.class));
            ((com.google.android.material.bottomnavigation.BottomNavigationView)main.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.navigation_dashboard);
        });
        waitFor(() -> main.findViewById(R.id.llHomeRec) != null);
        onMain(() -> main.findViewById(R.id.llHomeRec).performClick());
        waitFor(() -> node("主页内容显示") != null && node("站点推荐") != null);
        assertNull(node("豆瓣热播"));
        assertTrue(node("关闭").isChecked());
        assertFalse(node("站点推荐").isChecked());
        click(node("站点推荐"));
        waitFor(() -> Hawk.get(HawkConfig.HOME_REC, -1) == 1);
        waitFor(() -> node("站点推荐").isChecked());
        getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        waitFor(() -> node("主页内容显示") == null);
        // Opening again must select row 0 for persisted mode 1, not row 1 (disabled).
        onMain(() -> main.findViewById(R.id.llHomeRec).performClick());
        waitFor(() -> node("主页内容显示") != null);
        assertTrue(node("站点推荐").isChecked());
        assertFalse(node("关闭").isChecked());
        capture("home-mode-two-options.png");
        getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    }
    public void testBrowseRefreshSwitchesButtonAndReturnsToTop() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); active=main;
        onMain(()->main.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        waitFor(()->main.findViewById(R.id.tvHotList1)!=null && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter()!=null && ((RecyclerView)main.findViewById(R.id.tvHotList1)).getAdapter().getItemCount()==60);
        RecyclerView list=main.findViewById(R.id.tvHotList1); View button=main.findViewById(R.id.btn_live);
        onMain(()->assertEquals("链接播放",button.getContentDescription()));
        onMain(()->list.scrollToPosition(30)); waitFor(()->"回到顶部".contentEquals(button.getContentDescription())); capture("browse-top-button.png");
        onMain(button::performClick); waitFor(()->!list.canScrollVertically(-1));
        waitFor(()->"链接播放".contentEquals(button.getContentDescription()));
        int count=homeRequests.get(); swipe(list);
        waitFor(()->homeRequests.get()>count); waitFor(()->!((SwipeRefreshLayout)main.findViewById(R.id.swipeRefresh)).isRefreshing());
        onMain(button::performClick); waitFor(()->node("确定")!=null);
        androidx.appcompat.app.AlertDialog[] dialog={null};
        onMain(()->{
            com.kukuqi.tvbox.osc.ui.fragment.UserFragment home=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.UserFragment.class);
            VideoLinkDialog entry=(VideoLinkDialog)home.getChildFragmentManager().findFragmentByTag("video-link");
            dialog[0]=(androidx.appcompat.app.AlertDialog)entry.getDialog();
            android.widget.EditText input=dialog[0].findViewById(R.id.link_text);
            assertTrue(input.isSingleLine());
            input.setText("https://vd2.bdstatic.com/mda-sctj0f96cn79js4n/1080p/cae_h264/1774710384405617311/mda-sctj0f96cn79js4n.mp4");
            assertEquals(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM,
                ((com.google.android.material.textfield.TextInputLayout)dialog[0].findViewById(R.id.link_input)).getEndIconMode());
        });
        Thread.sleep(2000);capture("video-link-dialog.png");
        AccessibilityNodeInfo cancel=node("取消"); assertNotNull(cancel); cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    public void testLinkReallyPlaysAndDanmakuSurvivesFullscreenAndFallback() throws Exception {
        assertTrue(new File(VIDEO).isFile());
        try (okhttp3.Response response = com.lzy.okgo.OkGo.getInstance().getOkHttpClient().newCall(new okhttp3.Request.Builder().url(base+"/match").post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"),"{}")).build()).execute()) {
            String body=response.body().string();assertEquals(body,1,com.kukuqi.tvbox.osc.danmaku.DanmakuController.urls(com.google.gson.JsonParser.parseString(body)).size());
        }
        onMain(()->{Hawk.put("danmaku_load",true);Hawk.put("danmaku_show",true);Hawk.put("danmaku_auto",true);Hawk.put("danmaku_api_url",base+"/match");Hawk.put("danmaku_spider_first",true);});
        DetailActivity detail=(DetailActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),DetailActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sourceKey",VideoLinkDialog.SOURCE).putExtra("id",base+"/video.mp4"));active=detail;
        MyVideoView[] player={null};
        waitFor(()->{for(androidx.fragment.app.Fragment f:detail.getSupportFragmentManager().getFragments())if(f instanceof PlayFragment)player[0]=((PlayFragment)f).getPlayer();return player[0]!=null&&player[0].isPlaying();});
        onMain(()->player[0].setDanmakuContext(new JsonPrimitive(base+"/missing.xml"),"回归影片","第1集"));
        try { waitFor(()->player[0].getDanmakuCount()==2); } catch(AssertionError e) {throw new AssertionError("routes="+routes+", danmaku status="+player[0].getDanmakuStatus()+", count="+player[0].getDanmakuCount()+", matches="+matchRequests.get()+", comments="+commentRequests.get()+", load="+Hawk.get("danmaku_load",false)+", auto="+Hawk.get("danmaku_auto",false),e);}
        onMain(()->{player[0].seekTo(2000); player[0].startFullScreen();});
        waitFor(()->player[0].isFullScreen()); Thread.sleep(300);capture("video-danmaku-fullscreen.png");
        onMain(()->assertEquals(2,player[0].getDanmakuCount()));
        java.lang.reflect.Field callbackField = com.kukuqi.tvbox.engine.runtime.NodeRuntime.class.getDeclaredField("callback");
        callbackField.setAccessible(true);
        var callback = (com.github.catvod.runtime.NodeRuntimeApi.Callback) callbackField.get(com.kukuqi.tvbox.engine.runtime.NodeRuntime.get());
        assertNotNull(callback);
        assertTrue(com.google.gson.JsonParser.parseString(callback.onMessage("clearDanmu", new com.google.gson.JsonObject())).getAsJsonObject().get("success").getAsBoolean());
        onMain(() -> assertEquals(0, player[0].getDanmakuCount()));
        com.google.gson.JsonObject push = new com.google.gson.JsonObject(); push.addProperty("url", base + "/comments.xml");
        assertTrue(com.google.gson.JsonParser.parseString(callback.onMessage("danmuPush", push)).getAsJsonObject().get("success").getAsBoolean());
        waitFor(() -> player[0].getDanmakuCount() == 2);

    }
    public void testQualitySwitchKeepsPositionAndPausedState() throws Exception {
        DetailActivity detail = (DetailActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), DetailActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("sourceKey", VideoLinkDialog.SOURCE).putExtra("id", base+"/video.mp4"));
        active = detail;
        PlayFragment[] fragment = {null};
        waitFor(() -> { fragment[0] = findFragment(detail.getSupportFragmentManager(), PlayFragment.class); return fragment[0] != null && fragment[0].getPlayer() != null && fragment[0].getPlayer().isPlaying(); });
        MyVideoView video = fragment[0].getPlayer();
        org.json.JSONObject response = new org.json.JSONObject("{\"parse\":0,\"url\":\""+base+"/video.mp4\",\"proKey\":\"quality-regression\",\"coreResult\":{\"parse\":0,\"url\":[\"1080P\",\""+base+"/video.mp4\",\"720P\",\""+base+"/video.mp4?low\"]}}");
        java.lang.reflect.Field field = PlayFragment.class.getDeclaredField("sourceViewModel"); field.setAccessible(true);
        com.kukuqi.tvbox.osc.viewmodel.SourceViewModel model = (com.kukuqi.tvbox.osc.viewmodel.SourceViewModel)field.get(fragment[0]);
        onMain(() -> model.playResult.setValue(response));
        waitFor(video::isPlaying);
        onMain(() -> video.seekTo(5000)); waitFor(() -> video.getCurrentPosition() >= 4900);
        onMain(video::pause); waitFor(() -> !video.isPlaying());
        long[] pausedPosition = {0};
        onMain(() -> pausedPosition[0] = video.getCurrentPosition());
        long paused = pausedPosition[0];
        onMain(() -> com.kukuqi.tvbox.osc.ui.dialog.FeatureSettingsDialog.corePlayback(detail, video));
        waitFor(() -> node("清晰度 · 1080P ›") != null); click(node("清晰度 · 1080P ›"));
        waitFor(() -> node("720P") != null); click(node("720P"));
        waitFor(() -> video.getQualityOptions().getUrl().getPosition() == 1 && video.isInPlaybackState() && video.getCurrentPosition() >= paused-500 && !video.isPlaying());
        Thread.sleep(600);
        onMain(() -> { assertFalse(video.isPlaying()); assertTrue(Math.abs(video.getCurrentPosition()-paused) < 700); video.resume(); });
        waitFor(() -> video.isPlaying() && video.getCurrentPosition() > paused+100);
        onMain(() -> video.selectQuality(0));
        waitFor(() -> video.getQualityOptions().getUrl().getPosition() == 0 && video.isPlaying() && video.getCurrentPosition() >= paused);
    }

    public void testPlaybackOptionsDoNotMutateUntilEditedAndKeepPresetProfile() throws Exception {
        android.content.SharedPreferences preferences = com.github.catvod.utils.Prefers.getPrefers();
        Map<String, ?> before = new HashMap<>(preferences.getAll());
        try {
            onMain(() -> { Hawk.put(HawkConfig.PLAY_TYPE, 1); com.kukuqi.tvbox.engine.setting.VideoSetting.putPreset(2); });
            LocalPlayActivity local = (LocalPlayActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), LocalPlayActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("position",0).putExtra("videoList", "[{\"path\":\""+VIDEO+"\",\"displayName\":\"播放选项回归\"}]"));
            active = local;
            MyVideoView video = local.findViewById(R.id.player); waitFor(video::isPlaying);
            com.kukuqi.tvbox.osc.player.CorePlayer core = (com.kukuqi.tvbox.osc.player.CorePlayer)video.getMediaPlayer();
            com.kukuqi.tvbox.engine.player.effect.video.VideoEffectProfile profile = com.kukuqi.tvbox.engine.setting.VideoSetting.getProfile();
            Map<String, ?> opened = new HashMap<>(preferences.getAll());
            onMain(() -> com.kukuqi.tvbox.osc.ui.dialog.FeatureSettingsDialog.corePlayback(local,video));
            waitFor(() -> node("画面调节 ›") != null); capture("core-playback-options.png"); click(node("画面调节 ›"));
            waitFor(() -> node("画面预设 ›") != null); capture("core-video-effects.png");
            assertEquals("Opening a panel must preserve settings",opened,preferences.getAll());
            AccessibilityNodeInfo slider = slider("饱和度"); assertNotNull(slider);
            android.os.Bundle args = new android.os.Bundle(); args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 12);
            assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));
            waitFor(() -> com.kukuqi.tvbox.engine.setting.VideoSetting.getPreset() == 13);
            assertEquals(profile.getContrast(),com.kukuqi.tvbox.engine.setting.VideoSetting.getContrast(),.001f);
            assertEquals(profile.getBrightness(),com.kukuqi.tvbox.engine.setting.VideoSetting.getBrightness(),.001f);
            click(node("×"));
            onMain(() -> com.kukuqi.tvbox.osc.ui.dialog.FeatureSettingsDialog.corePlayback(local,video));
            click(scrollTo("音画／字幕同步 ›")); waitFor(() -> slider("音频延迟") != null);
            args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,115);
            assertTrue(slider("音频延迟").performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));
            waitFor(() -> core.manager().getAudioOffsetMs() == 1500);
            click(scrollTo("重置同步")); waitFor(() -> core.manager().getAudioOffsetMs() == 0 && core.manager().getTextOffsetMs() == 0);
        } finally {
            onMain(() -> {
                if(active != null) active.finish();
                android.content.SharedPreferences.Editor editor = preferences.edit().clear();
                for(Map.Entry<String, ?> entry : before.entrySet()) {
                    Object value = entry.getValue(); String key = entry.getKey();
                    if(value instanceof Boolean) editor.putBoolean(key,(Boolean)value);
                    else if(value instanceof Integer) editor.putInt(key,(Integer)value);
                    else if(value instanceof Long) editor.putLong(key,(Long)value);
                    else if(value instanceof Float) editor.putFloat(key,(Float)value);
                    else if(value instanceof String) editor.putString(key,(String)value);
                    else if(value instanceof Set) editor.putStringSet(key,(Set<String>)value);
                }
                editor.commit();
            });
        }
    }
    private AccessibilityNodeInfo slider(String description) {
        AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        return slider(root,description);
    }
    private AccessibilityNodeInfo slider(AccessibilityNodeInfo node,String description) {
        if(node == null) return null;
        if(description.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=slider(node.getChild(i),description); if(found!=null)return found; }
        return null;
    }
    public void testCategoryKeepsFilterAndTopActions() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=main;
        waitFor(()->main.findViewById(R.id.tvHotList1)!=null);
        onMain(()->((androidx.viewpager.widget.ViewPager)main.findViewById(R.id.mViewPager)).setCurrentItem(1,false));
        com.kukuqi.tvbox.osc.ui.fragment.GridFragment[] category={null};
        waitFor(()->{category[0]=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.GridFragment.class);return category[0]!=null&&category[0].getView()!=null&&((RecyclerView)category[0].getView().findViewById(R.id.mGridView)).getAdapter().getItemCount()==60;});
        View button=category[0].getView().findViewById(R.id.btn_filter);
        RecyclerView list=category[0].getView().findViewById(R.id.mGridView);
        waitFor(()->"筛选".contentEquals(button.getContentDescription()));
        onMain(button::performClick);waitFor(()->node("2026")!=null);capture("category-filter.png");
        click(node("2026"));
        int count=homeRequests.get();node("筛选").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->homeRequests.get()>count);
        onMain(()->list.scrollToPosition(30));waitFor(()->"回到顶部".contentEquals(button.getContentDescription()));capture("category-top.png");
        onMain(button::performClick);waitFor(()->!list.canScrollVertically(-1)&&"筛选".contentEquals(button.getContentDescription()));
        onMain(button::performClick);waitFor(()->node("2026")!=null);assertNull(node("请输入地址…"));
        node("筛选").performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
    public void testLinkFolderChooserPlaysContentUri() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=main;
        waitFor(()->main.findViewById(R.id.btn_live)!=null);
        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(main,main.getPackageName()+".fileprovider",new File(VIDEO));
        android.content.IntentFilter filter=new android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("video/*");
        android.app.Instrumentation.ActivityMonitor picker=getInstrumentation().addMonitor(filter,
                new android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)),true);
        android.app.Instrumentation.ActivityMonitor playback=getInstrumentation().addMonitor(LocalPlayActivity.class.getName(),null,false);
        try {
            onMain(()->main.findViewById(R.id.btn_live).performClick());waitFor(()->node("确定")!=null);
            onMain(()->{
                com.kukuqi.tvbox.osc.ui.fragment.UserFragment home=findFragment(main.getSupportFragmentManager(),com.kukuqi.tvbox.osc.ui.fragment.UserFragment.class);
                VideoLinkDialog dialog=(VideoLinkDialog)home.getChildFragmentManager().findFragmentByTag("video-link");
                dialog.requireDialog().findViewById(com.google.android.material.R.id.text_input_end_icon).performClick();
            });
            LocalPlayActivity local=(LocalPlayActivity)playback.waitForActivityWithTimeout(10000);assertNotNull(local);active=local;
            assertEquals(1,picker.getHits());MyVideoView player=local.findViewById(R.id.player);waitFor(player::isPlaying);
        } finally {
            if(picker.getHits()==0)getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            getInstrumentation().removeMonitor(picker);getInstrumentation().removeMonitor(playback);onMain(main::finish);
        }
    }
    public void testAboutSurfaceRevealsWallpaper() throws Exception {
        onMain(()->{Hawk.put("wallpaper_mode","custom");Hawk.put(HawkConfig.WALLPAPER_URL,"drawable://wallpaper_gradient_ocean");Hawk.put("wallpaper_softness",0);});
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AboutActivity.class.getName(),null,false);
        try {
            waitFor(()->main.findViewById(R.id.llAbout)!=null);onMain(()->main.findViewById(R.id.llAbout).performClick());
            AboutActivity about=(AboutActivity)monitor.waitForActivityWithTimeout(10000);assertNotNull(about);active=about;
            onMain(()->about.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitFor(()->node("Wi-Fi 后台更新")!=null);capture("about-translucent.png");
            onMain(()->{assertNull(about.findViewById(R.id.llVodApi));int color=androidx.core.content.ContextCompat.getColor(about,R.color.bg_about_panel);assertEquals(0x99,android.graphics.Color.alpha(color));});
            onMain(about::finish);waitFor(main::hasWindowFocus);active=main;
        } finally {getInstrumentation().removeMonitor(monitor);onMain(main::finish);}
    }
    public void testHotWordsUseOnly360RankProvider() throws Exception {
        okhttp3.OkHttpClient original = com.lzy.okgo.OkGo.getInstance().getOkHttpClient();
        AtomicInteger primary = new AtomicInteger(), fallback = new AtomicInteger();
        com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original.newBuilder().addInterceptor(chain -> {
            String host = chain.request().url().host();
            if (!host.equals("api.web.360kan.com") && !host.equals("movie.douban.com")) return chain.proceed(chain.request());
            if (host.equals("api.web.360kan.com")) {
                primary.incrementAndGet();
                assertEquals("https://www.360kan.com/rank/general", chain.request().header("Referer"));
            } else fallback.incrementAndGet();
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),
                            "{\"data\":[{\"title\":\"庆余年 第二季\"},{\"title\":\"三体\"},{\"title\":\"三体\"}]}" )).build();
        }).build());
        try {
            onMain(() -> { Hawk.delete("hot_search_cache"); Hawk.delete("home_hot"); });
            java.util.concurrent.atomic.AtomicReference<List<String>> words = new java.util.concurrent.atomic.AtomicReference<>();
            onMain(() -> HotSearch.load(true, (titles, status) -> words.set(titles)));
            waitFor(() -> words.get() != null);
            assertEquals(Arrays.asList("庆余年 第二季", "三体"), words.get());
            assertEquals(1, primary.get()); assertEquals(0, fallback.get());
            assertTrue(Hawk.<String>get("hot_search_cache").contains("庆余年 第二季"));
        } finally { com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original); }
    }

    public void testBlockedHotWordsStillAllowManualSourceSearch() throws Exception {
        okhttp3.OkHttpClient original = com.lzy.okgo.OkGo.getInstance().getOkHttpClient();
        AtomicInteger doubanRequests = new AtomicInteger();
        com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original.newBuilder().addInterceptor(chain -> {
            if (!chain.request().url().host().equals("movie.douban.com") && !chain.request().url().host().equals("api.web.360kan.com")) return chain.proceed(chain.request());
            if (chain.request().url().host().equals("movie.douban.com")) doubanRequests.incrementAndGet();
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(403).message("Forbidden").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("text/plain"), "Forbidden")).build();
        }).build());
        try {
            onMain(() -> { loadConfig(); Hawk.delete("hot_search_cache"); Hawk.delete("home_hot"); });
            FastSearchActivity search = (FastSearchActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), FastSearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            active = search;
            waitFor(() -> ((android.widget.TextView)search.findViewById(R.id.tv_hot_status)).getText().toString().contains("热门榜单访问受限"));
            onMain(() -> {
                ((android.widget.EditText)search.findViewById(R.id.et_search)).setText("测试影片");
                search.findViewById(R.id.iv_search).performClick();
            });
            waitFor(() -> searchRequests.get() > 0);
            assertFalse(Hawk.contains("hot_search_cache"));
            assertEquals(0, doubanRequests.get());
        } finally { com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original); }
    }

    public void testHotWordsCacheSurvives360FailureWithoutLegacyHomepageFallback() throws Exception {
        okhttp3.OkHttpClient original = com.lzy.okgo.OkGo.getInstance().getOkHttpClient();
        AtomicInteger requests = new AtomicInteger();
        com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original.newBuilder().addInterceptor(chain -> {
            if (!chain.request().url().host().equals("api.web.360kan.com")) return chain.proceed(chain.request());
            requests.incrementAndGet();
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(403).message("Forbidden").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("text/plain"), "Forbidden")).build();
        }).build());
        try {
            String cache = "[\"已缓存的360片名\"]";
            onMain(() -> {
                Hawk.put("hot_search_cache", cache);
                Hawk.put("hot_search_time", System.currentTimeMillis());
                Hawk.put("home_hot", "{\"subjects\":[{\"title\":\"旧豆瓣缓存\"}]}");
            });
            java.util.concurrent.atomic.AtomicReference<String> status = new java.util.concurrent.atomic.AtomicReference<>();
            onMain(() -> HotSearch.load(false, (words, message) -> {
                assertEquals(Collections.singletonList("已缓存的360片名"), words);
                status.set(message);
            }));
            assertNotNull(status.get());
            assertEquals(0, requests.get());
            onMain(() -> HotSearch.load(true, (words, message) -> {
                assertEquals(Collections.singletonList("已缓存的360片名"), words);
                status.set(message);
            }));
            waitFor(() -> status.get().contains("刷新失败"));
            assertEquals(1, requests.get());
            assertEquals(cache, Hawk.get("hot_search_cache", ""));
            onMain(() -> {
                Hawk.delete("hot_search_cache");
                status.set("");
                HotSearch.load(true, (words, message) -> { assertTrue(words.isEmpty()); status.set(message); });
            });
            waitFor(() -> status.get().contains("热门榜单访问受限"));
            assertEquals(2, requests.get());
        } finally { com.lzy.okgo.OkGo.getInstance().setOkHttpClient(original); }
    }

    public void testHotSearchClicksCompleteWordWithoutRecordingPrivateHistory() throws Exception {
        onMain(()->{Hawk.put("hot_search_cache","[\"庆余年 第二季\",\"三体\"]");Hawk.put("hot_search_time",System.currentTimeMillis());loadConfig();});
        Object history=Hawk.get(HawkConfig.HISTORY_SEARCH,null);
        FastSearchActivity search=(FastSearchActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),FastSearchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));active=search;
        waitFor(()->search.findViewById(R.id.fl_hot)!=null && ((android.view.ViewGroup)search.findViewById(R.id.fl_hot)).getChildCount()==2);capture("hot-search.png");
        onMain(()->((android.view.ViewGroup)search.findViewById(R.id.fl_hot)).getChildAt(0).performClick());
        waitFor(()->searchRequests.get()>0);
        onMain(()->assertEquals("庆余年 第二季",((android.widget.EditText)search.findViewById(R.id.et_search)).getText().toString()));
        assertEquals(history,Hawk.get(HawkConfig.HISTORY_SEARCH,null));
    }
    public void testPreferredLivePlaylistActuallyLoads() throws Exception {
        onMain(()->Hawk.put("live_playlist_url",base+"/second.m3u"));
        com.google.gson.JsonArray[] groups={null}; String[] error={null};
        onMain(()->LiveSourceLoader.load("file://"+config.getAbsolutePath(),(value,message)->{groups[0]=value;error[0]=message;}));
        waitFor(()->groups[0]!=null||error[0]!=null);
        assertNotNull(error[0],groups[0]);
        assertEquals("/second.m3u",groups[0].get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().get("name").getAsString());
    }
    public void testPlaylistSelectionMovesIntoLiveSourceWithoutLosingPlayback() throws Exception {
        String sourceUrl = "file://" + config.getAbsolutePath();
        onMain(() -> {
            loadConfig();
            Hawk.put(HawkConfig.LIVE_URL, sourceUrl);
            Hawk.put("live_playlist_url", "");
            SourceManager.record(sourceUrl, readConfig());
        });
        MainActivity main = (MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard));
        active = main;
        com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog picker = new com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog(main,
                com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog.LIVE, null);
        onMain(() -> new com.lxj.xpopup.XPopup.Builder(main).asCustom(picker).show());
        waitFor(() -> picker.findViewById(R.id.source_playlist) != null && picker.findViewById(R.id.source_playlist).getVisibility() == View.VISIBLE);
        onMain(() -> picker.findViewById(R.id.source_playlist).performClick());
        waitFor(() -> node("线路二") != null);
        click(node("线路二"));
        waitFor(() -> Hawk.get("live_playlist_url", "").equals(base + "/second.m3u"));
        com.google.gson.JsonArray[] groups = {null}; String[] error = {null};
        onMain(() -> LiveSourceLoader.load(sourceUrl, (value, message) -> { groups[0] = value; error[0] = message; }));
        waitFor(() -> groups[0] != null || error[0] != null);
        assertNotNull(error[0], groups[0]);
        assertEquals("/second.m3u", groups[0].get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().get("name").getAsString());
        capture("my-live-source-playlists.png");
        onMain(picker::dismiss);
        Thread.sleep(350);
        // Single playlists and wallpaper sources must not gain an irrelevant selector.
        onMain(() -> Hawk.put(HawkConfig.LIVE_URL, base + "/first.m3u"));
        for (int kind : new int[]{com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog.LIVE,
                com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog.WALLPAPER}) {
            com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog single = new com.kukuqi.tvbox.osc.ui.dialog.SourcePickerDialog(main, kind, null);
            onMain(() -> new com.lxj.xpopup.XPopup.Builder(main).asCustom(single).show());
            waitFor(() -> single.findViewById(R.id.source_playlist) != null);
            onMain(() -> assertEquals(View.GONE, single.findViewById(R.id.source_playlist).getVisibility()));
            onMain(single::dismiss);
            Thread.sleep(350);
        }
        assertEquals(sourceUrl, Hawk.get(HawkConfig.API_URL, ""));
    }
    public void testReadonlyModulesCanLoadAndReloadOnAndroid14() throws Exception {
        File source=new File(getInstrumentation().getTargetContext().getCacheDir(),"module-fixture.jar");
        File cache=new File(getInstrumentation().getTargetContext().getFilesDir(),"module-regression.jar");
        try {
            try(InputStream input=getInstrumentation().getContext().getAssets().open("fixture-spider.jar");OutputStream output=new FileOutputStream(source)) {
                byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
            }
            for(int attempt=0;attempt<2;attempt++) {
                SourceManager.copyLocal(android.net.Uri.fromFile(source).toString(),cache);
                assertTrue(new com.github.catvod.crawler.JarLoader().load(cache.getAbsolutePath()));
                assertFalse("Dynamic DEX must be read-only",cache.canWrite());
                assertNotNull(com.kukuqi.tvbox.engine.api.loader.BaseLoader.get().dex("file://"+cache.getAbsolutePath()));

            }
        } finally {source.delete();cache.delete();com.github.catvod.crawler.JsLoader.load();}
    }
    public void testBackupLeavesDatabaseUsableAndRejectsCorruptRestore() throws Exception {
        File backup=new File(getInstrumentation().getTargetContext().getCacheDir(),"room-regression.db"), corrupt=new File(getInstrumentation().getTargetContext().getCacheDir(),"room-corrupt.db");
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);
        assertTrue(com.kukuqi.tvbox.osc.data.AppDataManager.backup(backup));
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);
        Files.write(corrupt.toPath(),"SQLite format 3\u0000corrupt body".getBytes(StandardCharsets.US_ASCII));
        assertFalse(com.kukuqi.tvbox.osc.data.AppDataManager.restore(corrupt));
        assertTrue(com.kukuqi.tvbox.osc.data.AppDataManager.restore(backup));
        com.kukuqi.tvbox.osc.cache.RoomDataManger.getAllVodRecord(1);backup.delete();corrupt.delete();
    }
    public void testLocalBackgroundAndPipReallyContinuePlayback() throws Exception {
        for(int mode=0;mode<=2;mode++) {
            final int selected=mode;onMain(()->Hawk.put(HawkConfig.BACKGROUND_PLAY_TYPE,selected));
            LocalPlayActivity local=(LocalPlayActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),LocalPlayActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("position",0).putExtra("videoList","[{\"path\":\""+VIDEO+"\",\"displayName\":\"后台播放验证\"}]"));active=local;
            MyVideoView player=local.findViewById(R.id.player);waitFor(player::isPlaying);waitFor(local::hasWindowFocus);Thread.sleep(500);
            try(android.os.ParcelFileDescriptor command=getInstrumentation().getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")){try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)){while(input.read()!=-1){}}}
            if(mode==0)waitFor(()->!player.isPlaying());
            else {Thread.sleep(800);onMain(()->assertTrue("background mode="+selected+", state="+player.getCurrentPlayState()+", pip="+local.isInPictureInPictureMode(),player.isPlaying()));if(mode==2) {waitFor(local::isInPictureInPictureMode);capture("local-pip.png");}}
            onMain(local::finish);Thread.sleep(400);active=null;
        }
    }
    public void testAutomaticParserRacesAllWebLinesAgainstJson() throws Exception {
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        waitFor(()->main.findViewById(R.id.llModuleManager)!=null);
        assertNull(node("热门搜索"));
        com.kukuqi.tvbox.osc.bean.ParseBean json=new com.kukuqi.tvbox.osc.bean.ParseBean(),empty=new com.kukuqi.tvbox.osc.bean.ParseBean(),web=new com.kukuqi.tvbox.osc.bean.ParseBean();
        json.setUrl(base+"/json-slow?url=");empty.setUrl(base+"/web-empty?url=");web.setUrl(base+"/web-video?url=");
        String[] result={null};AtomicInteger wins=new AtomicInteger();com.kukuqi.tvbox.osc.player.AutomaticParser[] job={null};
        onMain(()->{job[0]=new com.kukuqi.tvbox.osc.player.AutomaticParser(main,url->url.contains("/video.mp4"),new com.kukuqi.tvbox.osc.player.AutomaticParser.Result(){public void success(String url,Map<String,String> headers){result[0]=url;wins.incrementAndGet();}public void failure(){result[0]="failed";}});job[0].start(Collections.singletonList(json),Arrays.asList(empty,web),"https://example.com/watch","");});
        try{waitFor(()->result[0]!=null);assertEquals(base+"/video.mp4?from=web",result[0]);Thread.sleep(3100);assertEquals(1,wins.get());}finally{onMain(()->job[0].stop());}
    }
    public void testMediaBrowserAndDlnaServicesAreDeclared() throws Exception {
        android.content.Context context=getInstrumentation().getTargetContext();
        android.content.pm.PackageManager pm=context.getPackageManager();
        android.content.pm.ServiceInfo media=pm.getServiceInfo(new android.content.ComponentName(context,com.kukuqi.tvbox.osc.service.PlayService.class),0);
        assertTrue(media.exported);assertTrue((media.getForegroundServiceType()&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)!=0);
        android.content.Intent browse=new android.content.Intent("android.media.browse.MediaBrowserService").setPackage(context.getPackageName());
        assertFalse(pm.queryIntentServices(browse,0).isEmpty());
        android.content.pm.ServiceInfo dlna=pm.getServiceInfo(new android.content.ComponentName(context,com.kukuqi.tvbox.engine.service.DLNACastService.class),0);
        assertFalse(dlna.exported);
        android.content.pm.ApplicationInfo app=pm.getApplicationInfo(context.getPackageName(),android.content.pm.PackageManager.GET_META_DATA);
        assertTrue(app.metaData.getInt("com.google.android.gms.car.application")!=0);
    }
    public void testMyPanelsApplySettingsAndAreScrollable() throws Exception {
        onMain(this::loadConfig);
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION,R.id.navigation_dashboard));active=main;
        waitFor(()->main.findViewById(R.id.llModuleManager)!=null);
        int[] entries={R.id.llVodApi,R.id.llLiveApi,R.id.llWallpaper,R.id.llModuleManager,R.id.llPlaybackSettings,R.id.llDanmakuSettings,R.id.llImageSize,R.id.llTheme,
                R.id.llPlay,R.id.llBackgroundPlay,R.id.llScale,R.id.llRender,R.id.llVideoPurify,R.id.llIjkCachePlay};
        onMain(()->{
            for(int id:entries)assertTrue(main.findViewById(id).hasOnClickListeners());
            for(String label:new String[]{"首页站点","直播线路","已保存来源","热门搜索","主题色彩"}) assertFalse("Redundant row: " + label, hasText(main.findViewById(R.id.my_scroll),label));
        });
        capture("my-settings-top.png");
        String pythonVersion = com.kukuqi.tvbox.engine.runtime.RuntimeModules.installed(getInstrumentation().getTargetContext(), com.kukuqi.tvbox.engine.runtime.RuntimeModule.PYTHON).versionName;
        String quickjsVersion = com.kukuqi.tvbox.engine.runtime.RuntimeModules.installed(getInstrumentation().getTargetContext(), com.kukuqi.tvbox.engine.runtime.RuntimeModule.QUICKJS).versionName;
        onMain(()->main.findViewById(R.id.llModuleManager).performClick());waitFor(()->node("Python · 已安装 " + pythonVersion)!=null);
        assertNotNull(scrollTo("内置：Exo / Media3、JAR Spider"));
        assertNotNull(scrollTo("MPV ·"));assertNotNull(scrollTo("QuickJS · 已安装 " + quickjsVersion));assertNotNull(scrollTo("Python · 已安装 " + pythonVersion));
        assertNull(node("Exo 2.18.7 · 内置"));assertNull(node("IJK · 内置"));capture("module-manager.png");node("×").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        waitFor(()->node("Python · 已安装 " + pythonVersion)==null);
        onMain(()->main.findViewById(R.id.llPlaybackSettings).performClick());waitFor(()->node("音频直通：关")!=null||node("音频直通：开")!=null);
        assertNotNull(node("高级播放设置"));
        AccessibilityNodeInfo panel = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        for(String duplicate:new String[]{"默认播放器","渲染方式","画面缩放","后台播放","广告过滤","播放缓存"})
            assertTrue("Duplicate basic setting: " + duplicate,panel.findAccessibilityNodeInfosByText(duplicate).isEmpty());
        assertNull(node("IJK播放器"));boolean passThrough=com.kukuqi.tvbox.engine.setting.DecodeSetting.isAudioPassThrough();
        capture("playback-settings.png");click(scrollTo(passThrough?"音频直通：开":"音频直通：关"));waitFor(()->com.kukuqi.tvbox.engine.setting.DecodeSetting.isAudioPassThrough()!=passThrough);node("×").performAction(AccessibilityNodeInfo.ACTION_CLICK);onMain(()->com.kukuqi.tvbox.engine.setting.DecodeSetting.putAudioPassThrough(passThrough));
        onMain(()->main.findViewById(R.id.llDanmakuSettings).performClick());waitFor(()->node("加载弹幕：关")!=null||node("加载弹幕：开")!=null);
        boolean was=Hawk.get("danmaku_load",false);node(was?"加载弹幕：开":"加载弹幕：关").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->Hawk.get("danmaku_load",false)!=was);capture("danmaku-settings.png");node("×").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        onMain(()->main.findViewById(R.id.llImageSize).performClick());waitFor(()->node("小")!=null);node("小").performAction(AccessibilityNodeInfo.ACTION_CLICK);waitFor(()->Hawk.get("poster_size",2)==0);
        onMain(()->assertTrue(Utils.getPosterSpanCount(main)>=3));
        onMain(()->((android.widget.ScrollView)main.findViewById(R.id.my_scroll)).fullScroll(View.FOCUS_DOWN));Thread.sleep(250);capture("my-settings-bottom.png");
    }
    public void testModuleCachesClearAndDecoderSettingPersists() throws Exception {
        onMain(this::loadConfig);
        MainActivity main = (MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_START_DESTINATION, R.id.navigation_dashboard));
        active = main;
        waitFor(() -> main.findViewById(R.id.llModuleManager) != null);
        java.io.File[] roots = {com.github.catvod.utils.Path.jar(), com.github.catvod.utils.Path.js(), com.github.catvod.utils.Path.py()};
        for (java.io.File root : roots) {
            java.io.File file = new java.io.File(root, "cache-cleanup-regression.tmp");
            Files.write(file.toPath(), new byte[]{1,2,3});
            assertTrue(file.setReadOnly());
        }
        onMain(() -> main.findViewById(R.id.llModuleManager).performClick());
        click(scrollTo("清除来源脚本缓存"));
        waitFor(() -> node("确认") != null);
        click(node("确认"));
        for (java.io.File root : roots) waitFor(() -> !new java.io.File(root, "cache-cleanup-regression.tmp").exists());
        assertNotNull(scrollTo("缓存已清除，点击可重载模块"));
        capture("module-cache-cleared.png");
        click(node("×"));
        onMain(() -> main.findViewById(R.id.llMediaCodec).performClick());
        waitFor(() -> node("硬解码") != null);
        click(node("硬解码"));
        waitFor(() -> "硬解码".equals(Hawk.get(HawkConfig.IJK_CODEC, "")));
        onMain(() -> main.findViewById(R.id.llMediaCodec).performClick());
        waitFor(() -> node("软解码") != null);
        click(node("软解码"));
        waitFor(() -> "软解码".equals(Hawk.get(HawkConfig.IJK_CODEC, "")));
        onMain(() -> main.findViewById(R.id.llLiveApi).performClick());
        waitFor(() -> node("从视频订阅选择") != null);
        assertNotNull(node("添加来源"));
        capture("saved-live-sources-direct.png");
        click(node("完成"));
    }
    private boolean hasText(View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) if(hasText(group.getChildAt(i),text)) return true;
        }
        return false;
    }
    private AccessibilityNodeInfo scrollTo(String text) throws Exception {
        for (int i = 0; i < 15; i++) {
            AccessibilityNodeInfo target = node(text);
            if (target != null && target.isVisibleToUser()) return target;
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) scrollForward(root);
            Thread.sleep(200);
        }
        fail("Missing visible option: " + text);
        return null;
    }
    private boolean scrollForward(AccessibilityNodeInfo root) {
        if (root.isScrollable() && root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child != null && scrollForward(child)) return true;
        }
        return false;
    }
    private void loadConfig(){try{java.lang.reflect.Method method=ApiConfig.class.getDeclaredMethod("parseJson",String.class,String.class);method.setAccessible(true);method.invoke(ApiConfig.get(),"file://"+config.getAbsolutePath(),readConfig());com.kukuqi.tvbox.engine.api.config.VodConfig.get().accept("file://"+config.getAbsolutePath(),"fixture",com.google.gson.JsonParser.parseString(readConfig()).getAsJsonObject());}catch(Exception e){throw new RuntimeException(e);}}
    private <T extends androidx.fragment.app.Fragment> T findFragment(androidx.fragment.app.FragmentManager manager,Class<T> type){for(androidx.fragment.app.Fragment fragment:manager.getFragments()){if(type.isInstance(fragment))return type.cast(fragment);if(fragment.isAdded()){T found=findFragment(fragment.getChildFragmentManager(),type);if(found!=null)return found;}}return null;}
    private String readConfig(){try{return new String(Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8);}catch(Exception e){throw new RuntimeException(e);}}
    private AccessibilityNodeInfo node(String text){AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow(); if(root==null)return null;for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(n.getText()==null?"":n.getText()))return n;return null;}
    private void click(AccessibilityNodeInfo item){assertNotNull(item);while(!item.isClickable()&&item.getParent()!=null)item=item.getParent();assertTrue(item.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void swipe(View list)throws Exception{int[]xy=new int[2];onMain(()->list.getLocationOnScreen(xy));long start=SystemClock.uptimeMillis();float x=xy[0]+list.getWidth()/2f,y=xy[1]+30;getInstrumentation().sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0));for(int i=1;i<=15;i++){Thread.sleep(20);getInstrumentation().sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,y+i*30,0));}getInstrumentation().sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y+450,0));}
    private interface Check{boolean ready();}
    private void waitFor(Check check)throws Exception{boolean[]ready={false};for(int i=0;i<150;i++){onMain(()->ready[0]=check.ready());if(ready[0])return;Thread.sleep(100);}fail("Timed out waiting for feature");}
    private void onMain(Runnable action){Throwable[]error={null};getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void capture(String name)throws Exception{Thread.sleep(500);getInstrumentation().waitForIdleSync();Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);try(FileOutputStream out=new FileOutputStream(new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
