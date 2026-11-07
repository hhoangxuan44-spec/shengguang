package io.github.shengguang;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** 细调音量的状态。进程里只有一份，面板、磁贴和按键服务共用。 */
final class Sound {
    static final String TAG = "ShengGuang";
    static final int MODE_OFF = 0, MODE_DEBOUNCE = 1, MODE_TAKEOVER = 2;

    private static Sound instance;

    static synchronized Sound get(Context c) {
        if (instance == null) instance = new Sound(c.getApplicationContext());
        return instance;
    }

    private final AudioManager am;
    private final SharedPreferences prefs;
    private final Engine engine = new Engine();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::checkRoute;
    private Route route;
    private Curve curve;
    private int level = -1, appliedIndex = -1;
    private Runnable listener;

    private Sound(Context c) {
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        prefs = c.getSharedPreferences("sound", Context.MODE_PRIVATE);
        // 旧版只有扬声器，存的等级归到扬声器名下
        if (prefs.contains("level")) {
            prefs.edit().putInt(levelKey(Route.SPEAKER), prefs.getInt("level", -1)).remove("level").apply();
        }
        useRoute(Route.current(am));
        am.registerAudioDeviceCallback(new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                scheduleRecheck();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
                scheduleRecheck();
            }
        }, main);
    }

    /** 某个输出每一档的系统衰减。蓝牙用绝对音量时这张表只是估计，读不到时曲线会退回固定步长。 */
    private static double[] dbTable(AudioManager am, int deviceType) {
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        double[] d = new double[max + 1];
        for (int i = 0; i <= max; i++) {
            try {
                d[i] = am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, i, deviceType);
            } catch (RuntimeException e) {
                d[i] = Double.NaN;
            }
        }
        return d;
    }

    private static String levelKey(Route r) {
        return "level:" + r.key;
    }

    private void useRoute(Route r) {
        route = r;
        curve = new Curve(dbTable(am, r.deviceType), r.external);
        level = -1;
        appliedIndex = -1;
        Log.i(TAG, "route=" + r.key + " external=" + r.external + " stepDb=" + curve.stepDb());
    }

    // 耳机连上后系统切换声音路径要一点时间，稍等再看
    private void scheduleRecheck() {
        main.removeCallbacks(recheck);
        main.postDelayed(recheck, 600);
    }

    /**
     * 输出换了：先把增益清零（扬声器上的往上补带到蓝牙会削顶），再把音效重新挂到新的输出上，
     * 然后恢复这个输出上次的等级。
     */
    private synchronized void checkRoute() {
        Route r = Route.current(am);
        if (r.key.equals(route.key)) return;
        engine.setGainMb(0);
        useRoute(r);
        warm();
        if (listener != null) listener.run();
    }

    synchronized Route route() {
        checkRoute();
        return route;
    }

    int mode() {
        return prefs.getInt("mode", MODE_TAKEOVER);
    }

    void setMode(int m) {
        prefs.edit().putInt("mode", m).apply();
    }

    boolean shadeSliders() {
        return prefs.getBoolean("shadeSliders", true);
    }

    void setShadeSliders(boolean on) {
        prefs.edit().putBoolean("shadeSliders", on).apply();
    }

    /** 面板打开时用它跟随按键带来的变化。 */
    synchronized void setListener(Runnable r) {
        listener = r;
    }

    /**
     * 提前打开音效，并恢复上次的等级。第一次打开后约 2–3 秒声音偏小，所以放在服务启动时做，
     * 不等到用户调音量。系统档位已被别处改过时不恢复，跟随系统。
     */
    synchronized void warm() {
        String err = engine.open();
        if (err != null) Log.w(TAG, "engine: " + err);
        int saved = prefs.getInt(levelKey(route), -1);
        if (saved >= 0 && curve.at(saved).index == am.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            setLevel(saved);
        } else {
            level();
        }
    }

    /** 当前等级。系统音量被别处改过时，跟随系统并清掉增益。 */
    synchronized int level() {
        checkRoute();
        int idx = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (level < 0 || idx != appliedIndex) {
            level = curve.levelForIndex(idx);
            appliedIndex = idx;
            engine.setGainMb(0);
        }
        return level;
    }

    synchronized int setLevel(int l) {
        checkRoute();
        l = Math.max(0, Math.min(Curve.LEVELS, l));
        if (!engine.isOpen()) {
            String err = engine.open();
            if (err != null) Log.w(TAG, "engine: " + err);
        }
        Curve.Point p = curve.at(l);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        // 顺序保证切换瞬间不会比目标更响：升档先调增益，降档先降档
        if (p.index > cur) {
            engine.setGainMb(p.gainMb);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, p.index, 0);
        } else {
            if (p.index < cur) am.setStreamVolume(AudioManager.STREAM_MUSIC, p.index, 0);
            engine.setGainMb(p.gainMb);
        }
        level = l;
        appliedIndex = p.index;
        prefs.edit().putInt(levelKey(route), l).apply();
        Log.i(TAG, "route=" + route.key + " level=" + l + " index=" + p.index + " gainMb=" + p.gainMb);
        if (listener != null) listener.run();
        return l;
    }
}
