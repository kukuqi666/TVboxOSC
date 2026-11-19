# Runtime APKs and downloaded spiders call this ABI outside the app's call graph.
-keep class com.github.catvod.** { *; }
# 崩溃收集的公开诊断接口供宿主和设备回归共同访问。
-keep class cat.ereza.customactivityoncrash.** { *; }
# 独立 QuickJS 模块链接宿主提供的共享接口，名称和默认方法必须保持稳定。
-keep interface com.kukuqi.quickjs.host.Host { *; }
-keep class com.kukuqi.quickjs.host.Host$* { *; }
-keep class com.kukuqi.tvbox.engine.App { *; }
-keep class com.kukuqi.tvbox.engine.bean.** { *; }
-keep class com.kukuqi.tvbox.engine.runtime.** { *; }
-keep class com.kukuqi.tvbox.engine.api.SiteApi { public *; }
-keep class com.kukuqi.tvbox.engine.api.config.VodConfig { public *; }
-keep class com.kukuqi.tvbox.engine.api.loader.BaseLoader { public *; }
-keep class com.kukuqi.tvbox.engine.player.parse.ParseJob { public *; }
-keep interface com.kukuqi.tvbox.engine.impl.ParseCallback { *; }
-keep class com.kukuqi.tvbox.engine.utils.Sniffer { public *; }
# The separately compiled Thunder APK links these host types by their original names.
-keep class com.kukuqi.tvbox.engine.player.extractor.Source { *; }
-keep interface com.kukuqi.tvbox.engine.player.extractor.Source$Extractor { *; }
-keep class com.kukuqi.tvbox.engine.exception.ExtractException { *; }
-keep class com.kukuqi.tvbox.engine.utils.ResUtil { *; }
-keep class com.kukuqi.tvbox.engine.utils.UrlUtil { *; }
-keep class com.kukuqi.tvbox.engine.R$string { public static int core_error_play_timeout; }
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }

# Existing UI uses reflective ViewBinding and serialized screen/storage models.
# Keep the small host stable while R8 removes unused dependency code and resources.
-keep class com.kukuqi.tvbox.osc.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.thoughtworks.xstream.** { *; }
-keep class com.orhanobut.hawk.** { *; }
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclasseswithmembers,includedescriptorclasses class * { native <methods>; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }
-keep class org.jupnp.** { *; }
-keep class org.seamless.** { *; }
-keep class org.simpleframework.xml.** { *; }
-keepclassmembers class * { @org.simpleframework.xml.* <fields>; }
-keep class com.kingja.loadsir.** { *; }
-keep class com.gyf.immersionbar.** { *; }
# TV list layouts instantiate their manager by its XML name and constructors.
-keep public class com.owen.tvrecyclerview.widget.* extends androidx.recyclerview.widget.RecyclerView$LayoutManager {
    public <init>(...);
}
-keep class com.google.android.exoplayer2.ext.** { *; }
-keep class androidx.media3.exoplayer.ext.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.decoder.mpegh.** { *; }
-keep class androidx.media3.mpvplayer.** { *; }
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
# WorkManager persists merger class names and creates them reflectively.
-keep class * extends androidx.work.InputMerger { public <init>(); }

# Keep the media-browser parcel/API contract and the embedded HTTP server extensible.
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.MediaBrowserServiceCompat** { *; }
-keep class fi.iki.elonen.NanoHTTPD** { *; }
-keep class com.blankj.utilcode.util.SPUtils { *; }
-keep class androidx.media3.common.MediaMetadata$Builder { *; }
-keep class androidx.media3.common.text.Cue { *; }
-keep class androidx.media3.common.text.CueGroup { *; }
-keep class androidx.media3.datasource.DataSpec$Builder { *; }
-keep class androidx.media3.datasource.DataSpec { *; }
-keep interface androidx.media3.datasource.DataSource { *; }
-keep interface androidx.media3.datasource.DataSource$Factory { *; }
-keep interface androidx.media3.datasource.cache.Cache { *; }
# Device diagnostics inspect preload ownership, headers and cache persistence.
-keep class com.kukuqi.tvbox.engine.player.exo.ExoPlayerSession { *; }
-keep class com.kukuqi.tvbox.engine.player.exo.ExoMediaSourceFactory { *; }
-keep class com.kukuqi.tvbox.engine.player.PlayerManager { *; }
-keep class com.kukuqi.tvbox.engine.player.subtitle.AssNative { *; }
-keepclassmembers class com.kukuqi.tvbox.engine.player.subtitle.AssSubtitleDecoder { public static *** isAvailable(...); }
-keep class com.kukuqi.tvbox.engine.player.parse.PreloadResolver { public *; }
# Keep the small playback-settings and cast API callable by device diagnostics.
-keep,allowobfuscation class com.kukuqi.tvbox.engine.setting.PlayerSetting {
    public static boolean isLibass();
    public static void putLibass(boolean);
}
-keep,allowobfuscation class com.kukuqi.tvbox.engine.setting.SubtitleSetting {
    public static int getSecondaryMode();
    public static void putSecondaryMode(int);
}
-keep,allowobfuscation class com.kukuqi.tvbox.engine.dlna.DLNACastManager {
    public static *** get();
    public void init(android.content.Context);
    public void release(android.content.Context);
}
-keep,allowobfuscation class com.kukuqi.tvbox.engine.dlna.DLNACast { public *; }
-keep class com.kukuqi.tvbox.engine.player.media.** { *; }
-keep interface com.kukuqi.tvbox.engine.player.engine.PlayerEngine { *; }
-keep class com.kukuqi.tvbox.engine.player.engine.PlayerEngine$* { *; }
-keep interface androidx.media3.exoplayer.source.preload.PreloadManagerListener { *; }
-keepclassmembers class androidx.media3.exoplayer.source.preload.BasePreloadManager { public *; }
-keepclassmembers class androidx.media3.exoplayer.source.preload.DefaultPreloadManager { public *; }

# Desktop-only/optional integration points in libraries; no broad missing-class suppression.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.naming.**
-dontwarn javax.xml.stream.**
-dontwarn sun.misc.**
-dontwarn org.codehaus.stax2.**
-dontwarn org.xmlpull.mxp1.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# XStream uses DomDriver here; these alternative XML/converter backends are optional.
-dontwarn com.ctc.wstx.stax.**
-dontwarn java.beans.**
-dontwarn net.sf.cglib.proxy.**
-dontwarn nu.xom.**
-dontwarn org.codehaus.jettison.**
-dontwarn org.dom4j.**
-dontwarn org.jdom.**
-dontwarn org.jdom2.**
-dontwarn org.joda.time.**
-dontwarn org.kxml2.io.KXmlParser
# Android UPnP transport does not use the desktop HTTP/Jetty or OSGi adapters.
-dontwarn com.sun.net.httpserver.**
-dontwarn org.eclipse.jetty.**
-dontwarn org.osgi.framework.**
-dontwarn org.osgi.service.**
