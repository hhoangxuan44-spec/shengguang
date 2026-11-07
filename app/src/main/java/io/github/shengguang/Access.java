package io.github.shengguang;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * 无障碍服务的开关。酷比的无障碍设置页不列出第三方服务，用户没地方手动打开，
 * 所以有 WRITE_SECURE_SETTINGS 时由应用自己写系统设置打开。
 * 同时作为「应用已更新」的广播接收器：覆盖安装后，之前开着的服务自动重新打开。
 */
public class Access extends BroadcastReceiver {
    private static final String PREFS = "access", WANTED = "wanted";

    static boolean isOn(Context c) {
        String v = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (v == null) return false;
        ComponentName me = new ComponentName(c, KeyService.class);
        for (String s : v.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    /** 服务连上时记一笔：以后被关掉（例如更新后），可以自动重新打开。 */
    static void markWanted(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(WANTED, true).apply();
    }

    static boolean wanted(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(WANTED, false);
    }

    /**
     * 是否被系统标成受限应用（从浏览器、文件管理器安装时会这样）。受限时写设置会被系统拒绝，
     * 只能用 adb 解除。读不到时当作不受限。
     */
    static boolean restricted(Context c) {
        try {
            android.app.AppOpsManager ops = c.getSystemService(android.app.AppOpsManager.class);
            int mode = ops.unsafeCheckOpNoThrow("android:access_restricted_settings",
                    android.os.Process.myUid(), c.getPackageName());
            return mode != android.app.AppOpsManager.MODE_ALLOWED && mode != android.app.AppOpsManager.MODE_DEFAULT;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static final String UNRESTRICT_CMD = "adb shell cmd appops set io.github.shengguang ACCESS_RESTRICTED_SETTINGS allow";

    /** 把本应用的服务加进已开启列表，不动其他应用的服务。成功返回 true。 */
    static boolean enable(Context c) {
        if (!Light.canSecure(c)) return false;
        if (isOn(c)) return true;
        ContentResolver r = c.getContentResolver();
        String me = new ComponentName(c, KeyService.class).flattenToString();
        try {
            String v = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            v = v == null || v.isEmpty() ? me : v + ":" + me;
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, v);
            Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(Sound.TAG, "accessibility service enabled by app");
            return true;
        } catch (RuntimeException e) {
            Log.w(Sound.TAG, "enable accessibility", e);
            return false;
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()) && wanted(c)) enable(c);
    }
}
