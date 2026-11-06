package io.github.shengguang;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/**
 * 和酷比控制中心的亮度、音乐滑块同一种外观：半透明浅灰的底、白色填充、圆角。
 * 按手指移动的距离增减，不会跳到手指的位置，只碰一下不改变数值。
 */
final class SliderView extends View {
    private static final int TRACK = 0x99C4C4C8, FILL = 0xF2FFFFFF, INK = 0xFF3A3A3C;

    interface OnChange {
        void changed(int value);
    }

    private final boolean vertical;
    private final int min, max;
    private final Drawable icon;
    private final OnChange listener;
    private final Runnable onTouch;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private int value;
    private String label = "";
    private float startPos;
    private int startValue;

    SliderView(Context c, boolean vertical, int min, int max, int iconRes, OnChange listener, Runnable onTouch) {
        super(c);
        this.vertical = vertical;
        this.min = min;
        this.max = max;
        this.listener = listener;
        this.onTouch = onTouch;
        density = c.getResources().getDisplayMetrics().density;
        icon = c.getDrawable(iconRes).mutate();
        icon.setColorFilter(INK, PorterDuff.Mode.SRC_IN);
    }

    void set(int v, String text) {
        value = Math.max(min, Math.min(max, v));
        label = text;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startPos = vertical ? e.getY() : e.getX();
                startValue = value;
                onTouch.run();
                return true;
            case MotionEvent.ACTION_MOVE:
                float delta = vertical ? startPos - e.getY() : e.getX() - startPos;
                float length = vertical ? getHeight() : getWidth();
                int v = Math.max(min, Math.min(max, startValue + Math.round(delta * (max - min) / length)));
                if (v != value) {
                    value = v;
                    listener.changed(v);
                }
                onTouch.run();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float radius = 16 * density;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(TRACK);
        paint.setColor(FILL);
        float f = (value - min) / (float) (max - min);
        if (vertical) canvas.drawRect(0, h * (1 - f), w, h, paint);
        else canvas.drawRect(0, 0, w * f, h, paint);
        canvas.restore();

        int size = Math.round(24 * density), inset = Math.round(14 * density);
        int left = vertical ? (w - size) / 2 : inset;
        int top = vertical ? h - inset - size : (h - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.draw(canvas);

        paint.setColor(INK);
        paint.setTextSize(13 * density);
        paint.setTextAlign(vertical ? Paint.Align.CENTER : Paint.Align.RIGHT);
        float x = vertical ? w / 2f : w - inset;
        float y = vertical ? inset - paint.ascent() : h / 2f - (paint.ascent() + paint.descent()) / 2;
        canvas.drawText(label, x, y, paint);
    }
}
