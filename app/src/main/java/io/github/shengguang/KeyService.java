package io.github.shengguang;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

/**
 * 只处理音量键。
 * 防抖：只吞掉松开后极短时间内又出现的按下，其余仍交给系统。
 * 接管：两个键都吞掉，由本应用按细调等级改音量，酷比的音量面板不会被触发。
 * 这个服务也让进程常驻，全局音效才不会随进程消失。
 */
public class KeyService extends AccessibilityService {
    private static final long BOUNCE_MS = 45, REPEAT_DELAY_MS = 400, REPEAT_MS = 80;
    private static final int STEP = 2;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final long[] lastUp = new long[2];
    private final boolean[] swallowingUp = new boolean[2];
    private int repeatDir;
    private TextView pill;

    private final Runnable repeat = new Runnable() {
        @Override
        public void run() {
            step(repeatDir);
            main.postDelayed(this, REPEAT_MS);
        }
    };
    private final Runnable hidePill = this::removePill;

    @Override
    protected void onServiceConnected() {
        Sound.get(this).warm();
        Log.i(Sound.TAG, "KeyService connected, mode=" + Sound.get(this).mode());
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        int mode = Sound.get(this).mode();
        if (mode == Sound.MODE_OFF) return false;

        int k = code == KeyEvent.KEYCODE_VOLUME_UP ? 0 : 1;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() > 0) return mode == Sound.MODE_TAKEOVER || swallowingUp[k];
            boolean bounce = lastUp[k] != 0 && e.getEventTime() - lastUp[k] < BOUNCE_MS;
            swallowingUp[k] = bounce;
            if (bounce) return true;
            if (mode == Sound.MODE_TAKEOVER) {
                repeatDir = k == 0 ? 1 : -1;
                step(repeatDir);
                main.removeCallbacks(repeat);
                main.postDelayed(repeat, REPEAT_DELAY_MS);
                return true;
            }
            return false;
        }
        lastUp[k] = e.getEventTime();
        boolean swallowed = swallowingUp[k];
        swallowingUp[k] = false;
        if (mode == Sound.MODE_TAKEOVER) {
            main.removeCallbacks(repeat);
            return true;
        }
        return swallowed;
    }

    private void step(int dir) {
        Sound s = Sound.get(this);
        showPill("音量 " + s.setLevel(s.level() + dir * STEP));
    }

    private void showPill(String s) {
        if (pill == null) {
            pill = new TextView(this);
            pill.setTextColor(Color.WHITE);
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            float d = getResources().getDisplayMetrics().density;
            pill.setPadding((int) (20 * d), (int) (10 * d), (int) (20 * d), (int) (10 * d));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(24 * d);
            g.setColor(0xE0202022);
            pill.setBackground(g);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (48 * d);
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(pill, lp);
            } catch (RuntimeException ex) {
                Log.w(Sound.TAG, "overlay", ex);
                pill = null;
                return;
            }
        }
        pill.setText(s);
        main.removeCallbacks(hidePill);
        main.postDelayed(hidePill, 1200);
    }

    private void removePill() {
        if (pill != null) {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(pill);
            } catch (RuntimeException ignored) {
            }
            pill = null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        removePill();
        super.onDestroy();
    }
}
