package io.github.shengguang;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 下拉面板打开时盖在屏幕下方的两条滑块。第三方磁贴不能放滑块，这是最接近的做法。
 * 收起信号万一丢失，无操作一段时间后也会自己消失，不会一直挡在屏幕上。
 */
final class ShadeSliders {
    private static final long AUTO_HIDE_MS = 30_000;
    private static final int ACCENT = 0xFFFFD60A, TRACK = 0xFF48484A, INK = 0xFFF2F2F7, MUTED = 0xFF8E8E93;

    private final Context context;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable autoHide = this::hide;
    private LinearLayout root;
    private SeekBar soundBar, lightBar;
    private TextView soundValue, lightValue;

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

    private void rearm() {
        main.removeCallbacks(autoHide);
        main.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    private void refresh() {
        Sound s = Sound.get(context);
        int level = s.level();
        soundBar.setProgress(level);
        soundValue.setText(String.valueOf(level));
        int scale = Light.read(context);
        lightBar.setProgress(scale);
        showLight(scale);
    }

    private void showLight(int scale) {
        int dim = Light.levelFor(scale);
        lightValue.setText(dim > 0 ? "极暗 " + dim : String.valueOf(Light.brightnessFor(scale)));
    }

    private boolean build() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(20), px(14), px(20), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(24));
        bg.setColor(0xF01C1C1E);
        root.setBackground(bg);

        soundValue = value();
        soundBar = bar(Curve.LEVELS, ACCENT);
        soundBar.setOnSeekBarChangeListener(new Change(p -> {
            soundValue.setText(String.valueOf(Sound.get(context).setLevel(p)));
        }));
        root.addView(row("音量", soundBar, soundValue));

        lightValue = value();
        lightBar = bar(Light.MAX, Color.WHITE);
        lightBar.setOnSeekBarChangeListener(new Change(p -> {
            int s = Light.canSecure(context) ? p : Math.max(p, Light.SPLIT);
            if (s != p) lightBar.setProgress(s);
            Light.apply(context, s);
            showLight(s);
        }));
        root.addView(row("亮度", lightBar, lightValue));

        // 横屏时酷比的控制中心占右侧约 30%，滑块放到左边的空白处，不挡它的按钮
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        boolean landscape = context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int width = Math.min(landscape ? (int) (screen * 0.62) : screen - px(32), px(560));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, -2,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | (landscape ? Gravity.LEFT : Gravity.CENTER_HORIZONTAL);
        if (landscape) lp.x = px(40);
        lp.y = px(24);
        try {
            wm.addView(root, lp);
            return true;
        } catch (RuntimeException e) {
            Log.w(Sound.TAG, "overlay", e);
            root = null;
            return false;
        }
    }

    private LinearLayout row(String name, SeekBar bar, TextView value) {
        LinearLayout r = new LinearLayout(context);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(context);
        t.setText(name);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(INK);
        r.addView(t);
        r.addView(bar, new LinearLayout.LayoutParams(0, px(48), 1f));
        r.addView(value, new LinearLayout.LayoutParams(px(58), -2));
        return r;
    }

    private SeekBar bar(int max, int color) {
        SeekBar s = new SeekBar(context);
        s.setMax(max);
        s.setSplitTrack(false);
        s.setProgressTintList(ColorStateList.valueOf(color));
        s.setThumbTintList(ColorStateList.valueOf(color));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
        s.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) rearm();
            return false;
        });
        return s;
    }

    private TextView value() {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(MUTED);
        t.setGravity(Gravity.END);
        return t;
    }

    private int px(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private interface OnUser {
        void run(int progress);
    }

    /** 只响应手指拖动，程序设置进度时不写系统值。 */
    private final class Change implements SeekBar.OnSeekBarChangeListener {
        private final OnUser action;

        Change(OnUser action) {
            this.action = action;
        }

        @Override
        public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
            if (fromUser) {
                action.run(p);
                rearm();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar bar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
        }
    }
}
