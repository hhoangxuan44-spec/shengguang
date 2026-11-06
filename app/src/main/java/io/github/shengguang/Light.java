package io.github.shengguang;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

/**
 * 把「亮度 + 系统极暗」合成一个 0–1000 的刻度。
 *
 *   300–1000（亮）  关闭自适应亮度，按感知曲线映射到 screen_brightness 1–255；极暗关闭
 *   0–300（暗）     screen_brightness 固定为 1，极暗强度从 0 平滑升到 100
 */
final class Light {
    private Light() {
    }

    static final int MAX = 1000, SPLIT = 300;
    static final String SECURE_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    static final String[] PRESET_NAMES = {"白天", "阅读", "夜间"};
    private static final int[] PRESET_DEFAULT = {850, 560, 120};
    private static final double GAMMA = 2.0;
    private static final String K_ACTIVE = "reduce_bright_colors_activated", K_LEVEL = "reduce_bright_colors_level";

    /** 刻度 → 系统亮度 1–255 */
    static int brightnessFor(int s) {
        if (s <= SPLIT) return 1;
        double t = (Math.min(s, MAX) - SPLIT) / (double) (MAX - SPLIT);
        return 1 + (int) Math.round(254 * Math.pow(t, GAMMA));
    }

    /** 刻度 → 极暗强度 0–100 */
    static int levelFor(int s) {
        if (s >= SPLIT) return 0;
        return (int) Math.round(100.0 * (SPLIT - Math.max(s, 0)) / SPLIT);
    }

    /** 系统亮度 / 极暗强度 → 刻度（反过来读当前状态） */
    static int scaleFor(int brightness, boolean dimActive, int level) {
        if (dimActive && level > 0) return (int) Math.round(SPLIT - level * SPLIT / 100.0);
        double t = Math.pow(Math.max(0, Math.min(254, brightness - 1)) / 254.0, 1.0 / GAMMA);
        return SPLIT + (int) Math.round(t * (MAX - SPLIT));
    }

    static boolean canWrite(Context c) {
        return Settings.System.canWrite(c);
    }

    static boolean canSecure(Context c) {
        return c.checkSelfPermission(SECURE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static int read(Context c) {
        ContentResolver r = c.getContentResolver();
        try {
            int b = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128);
            boolean on = Settings.Secure.getInt(r, K_ACTIVE, 0) == 1;
            return scaleFor(b, on, Settings.Secure.getInt(r, K_LEVEL, 0));
        } catch (RuntimeException e) {
            return 700;
        }
    }

    /** 应用刻度。没有 WRITE_SETTINGS 时返回 false；没有 WRITE_SECURE_SETTINGS 时只改亮度。 */
    static boolean apply(Context c, int s) {
        s = Math.max(0, Math.min(MAX, s));
        if (!canWrite(c)) return false;
        ContentResolver r = c.getContentResolver();
        try {
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, brightnessFor(s));
            if (canSecure(c)) {
                int lv = levelFor(s);
                if (lv > 0) {
                    Settings.Secure.putInt(r, K_LEVEL, lv);
                    Settings.Secure.putInt(r, K_ACTIVE, 1);
                } else if (Settings.Secure.getInt(r, K_ACTIVE, 0) == 1) {
                    Settings.Secure.putInt(r, K_ACTIVE, 0);
                }
            }
            return true;
        } catch (RuntimeException e) {
            Log.w(Sound.TAG, "light", e);
            return false;
        }
    }

    static int preset(Context c, int i) {
        return c.getSharedPreferences("light", Context.MODE_PRIVATE).getInt("preset" + i, PRESET_DEFAULT[i]);
    }

    static void savePreset(Context c, int i, int s) {
        c.getSharedPreferences("light", Context.MODE_PRIVATE).edit().putInt("preset" + i, s).apply();
    }
}
