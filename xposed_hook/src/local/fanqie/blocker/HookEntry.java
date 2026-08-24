package local.fanqie.blocker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET = "com.dragon.read";
    private static final String[] UPDATE_MARKERS = {
        "ichannel.snssdk.com/check_version", "ichannel.snssdk.com/service/2/app_alert_check",
        "toblog.ctobsnssdk.com/service/2/app_alert_check", "i.snssdk.com/api/cold_start/appupdate",
        "/check_version/", "/app_alert_check/", "/appupdate/"
    };
    private static final String[] VIDEO_MARKERS = {
        "/reading/media/video/", "/reading/bookapi/video_tab/", "/video/openapi/",
        "/vod/get_play_info", "/video/play/", "/video/urls/", "/video_api/get_video_pgc/",
        "aweme.snssdk.com", "test-aweme.snssdk.com", "video.snssdk.com",
        "v3-video.", "v6-video.", "v9-video.", "v11-video.", "v26-video."
    };
    private static final String[] VIDEO_CLASS_MARKERS = {
        "com.dragon.read.pages.video", "com.dragon.read.pages.detail.video",
        "com.dragon.read.pages.bookmall.VideoTab", "com.dragon.read.ad.exciting.video",
        "com.ss.android.excitingvideo", "com.bytedance.sdk.openadsdk.activity.base.TTVideo",
        "com.bytedance.sdk.openadsdk.activity.base.TTReward", "com.bytedance.sdk.openadsdk.activity.base.TTFullScreen"
    };

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        hookUpdateController(lp.classLoader);
        hookDialogs();
        hookActivities();
        hookVideoTab(lp.classLoader);
        hookTextEntries();
        hookUrls();
        hookIntentRouting();
        hookMediaPlayer();
        XposedBridge.log("FanqieBlocker: hooks active in " + lp.processName);
    }

    private static void hookUpdateController(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("com.dragon.read.update.d", cl);
        if (c == null) return;
        XposedBridge.hookAllMethods(c, "a", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                boolean listener = false, manualCheck = false;
                for (Object a : p.args) {
                    if (a instanceof Integer) manualCheck = true;
                    if (a != null && a.getClass().getName().contains("OnUpdateStatusChangedListener")) listener = true;
                }
                if (listener && manualCheck) p.setResult(null);
            }
        });
    }

    private static void hookDialogs() {
        XposedBridge.hookAllMethods(Dialog.class, "show", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (stackHas("com.dragon.read.update") || stackHas("com.ss.android.update")) p.setResult(null);
            }
        });
    }

    private static void hookActivities() {
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Activity a = (Activity)p.thisObject;
                if (isVideoClass(a.getClass().getName())) a.finishAndRemoveTask();
            }
        });
    }

    private static void hookVideoTab(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("com.dragon.read.pages.bookmall.VideoTabFragment", cl);
        if (c == null) return;
        for (Method m : c.getDeclaredMethods()) {
            if (!View.class.isAssignableFrom(m.getReturnType())) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Object v = p.getResult();
                    if (v instanceof View) ((View)v).setVisibility(View.GONE);
                }
            });
        }
    }

    private static void hookTextEntries() {
        XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                TextView tv=(TextView)p.thisObject;
                CharSequence cs=tv.getText();
                if (cs == null) return;
                String s=cs.toString().replace(" ", "").trim();
                if (s.equals("??") || s.equals("???") || s.equals("???") || s.equals("???") ||
                    s.equals("????") || s.equals("???")) {
                    tv.setVisibility(View.GONE);
                    ViewParent parent=tv.getParent();
                    if (parent instanceof View) ((View)parent).setVisibility(View.GONE);
                }
            }
        });
    }

    private static void hookUrls() {
        XposedBridge.hookAllConstructors(URL.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                StringBuilder b=new StringBuilder();
                for (Object a:p.args) if (a!=null) b.append(a.toString());
                if (blockedUrl(b.toString())) p.setThrowable(new MalformedURLException("blocked by FanqieBlocker"));
            }
        });
        XposedBridge.hookAllMethods(Uri.class, "parse", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length>0 && p.args[0] instanceof String && blockedUrl((String)p.args[0])) p.setResult(Uri.EMPTY);
            }
        });
    }

    private static void hookIntentRouting() {
        XposedBridge.hookAllMethods(Activity.class, "startActivity", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length == 0 || !(p.args[0] instanceof Intent)) return;
                Intent i=(Intent)p.args[0];
                String x=String.valueOf(i.getData())+" "+String.valueOf(i.getComponent())+" "+String.valueOf(i.getType());
                String l=x.toLowerCase(Locale.ROOT);
                if (blockedUrl(l) || l.contains("application/vnd.android.package-archive") || isVideoClass(l)) p.setResult(null);
            }
        });
    }

    private static void hookMediaPlayer() {
        try {
            Class<?> mp=Class.forName("android.media.MediaPlayer");
            XposedBridge.hookAllMethods(mp, "setDataSource", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    StringBuilder b=new StringBuilder(); for(Object a:p.args) if(a!=null)b.append(a.toString());
                    if (blockedUrl(b.toString()) || videoStack()) p.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }

    private static boolean blockedUrl(String value) {
        String s=value==null?"":value.toLowerCase(Locale.ROOT);
        for(String x:UPDATE_MARKERS) if(s.contains(x)) return true;
        for(String x:VIDEO_MARKERS) if(s.contains(x)) return true;
        return s.contains(".mp4") || s.contains(".m3u8") || s.contains("video_feed");
    }
    private static boolean isVideoClass(String name) {
        for(String x:VIDEO_CLASS_MARKERS) if(name.contains(x)) return true;
        return false;
    }
    private static boolean videoStack() {
        for(StackTraceElement e:Thread.currentThread().getStackTrace()) if(isVideoClass(e.getClassName())) return true;
        return false;
    }
    private static boolean stackHas(String marker) {
        for(StackTraceElement e:Thread.currentThread().getStackTrace()) if(e.getClassName().contains(marker)) return true;
        return false;
    }
}
