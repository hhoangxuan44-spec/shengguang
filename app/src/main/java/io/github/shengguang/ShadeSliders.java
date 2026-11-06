package io.github.shengguang;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * 下拉面板打开时显示的音量和亮度滑块。第三方磁贴不能放滑块，这是最接近的做法。
 * 横屏放左侧空白处（酷比的通知卡片占中间，控制中心在右边），竖屏放下方。
 * 收起信号万一丢失，无操作一段时间后也会自己消失，不会一直挡在屏幕上。
 */
final class ShadeSliders {
    private static final long AUTO_HIDE_MS = 30_000;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable autoHide = this::hide;
    private LinearLayout root;
    private SliderView sound, light;

    ShadeSliders(Context c) {
        context = c;
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    void show() {
        if (root == null && !build()) return;
        refresh();
        rearm();
    }

    void hide() {
        main.removeCallbacks(autoHide);
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
            }
            root = null;
        }
    }

    /** 下拉面板开着时转屏：按新方向重新摆放。 */
    void relayout() {
        if (root != null) {
            hide();
            show();
        }
    }

    private void rearm() {
        main.removeCallbacks(autoHide);
        main.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    private void refresh() {
        int level = Sound.get(context).level();
        sound.set(level, String.valueOf(level));
        int scale = Light.read(context);
        light.set(scale, lightLabel(scale));
    }

    private static String lightLabel(int scale) {
        int dim = Light.levelFor(scale);
        return dim > 0 ? "极暗" + dim : String.valueOf(Light.brightnessFor(scale));
    }

    private boolean build() {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        sound = new SliderView(context, landscape, 0, Curve.LEVELS, R.drawable.ic_note, v -> {
            int l = Sound.get(context).setLevel(v);
            sound.set(l, String.valueOf(l));
        }, this::rearm);
        int lightMin = Light.canSecure(context) ? 0 : Light.SPLIT;
        light = new SliderView(context, landscape, lightMin, Light.MAX, R.drawable.ic_sun, v -> {
            Light.apply(context, v);
            light.set(v, lightLabel(v));
        }, this::rearm);

        root = new LinearLayout(context);
        int thick = px(60), gap = px(12);
        WindowManager.LayoutParams lp;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (landscape) {
            root.setOrientation(LinearLayout.HORIZONTAL);
            int height = Math.min(px(320), dm.heightPixels * 3 / 5);
            root.addView(light, new LinearLayout.LayoutParams(thick, height));
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(thick, height);
            second.leftMargin = gap;
            root.addView(sound, second);
            lp = new WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    flags, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            lp.x = px(32);
        } else {
            root.setOrientation(LinearLayout.VERTICAL);
            root.addView(sound, new LinearLayout.LayoutParams(-1, thick));
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, thick);
            second.topMargin = gap;
            root.addView(light, second);
            lp = new WindowManager.LayoutParams(Math.min(dm.widthPixels - px(32), px(560)), -2,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.y = px(24);
        }
        try {
            wm.addView(root, lp);
            return true;
        } catch (RuntimeException e) {
            Log.w(Sound.TAG, "overlay", e);
            root = null;
            return false;
        }
    }

    private int px(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
