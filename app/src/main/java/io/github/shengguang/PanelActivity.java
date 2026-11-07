package io.github.shengguang;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** 声光面板：音量和亮度各一个滑块，都是单向设置，只有手指拖动才会改系统值。 */
public class PanelActivity extends Activity {
    private static final int INK = 0xFFF2F2F7, MUTED = 0xFF8E8E93, CARD = 0xFF2C2C2E, ACCENT = 0xFFFFD60A;
    // 应用本身没有联网权限：更新交给浏览器下载，系统安装器安装
    private static final String RELEASES = "https://github.com/hhoangxuan44-spec/shengguang/releases/latest";
    private static final String APK = RELEASES + "/download/shengguang.apk";

    private Sound sound;
    private SeekBar soundBar, lightBar;
    private TextView soundValue, lightValue, routeInfo;
    private final TextView[] modeBtn = new TextView[3];
    private TextView shadeBtn;
    private LinearLayout notes;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        sound = Sound.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(22), px(20), px(22), px(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(28));
        bg.setColor(0xFF1C1C1E);
        root.setBackground(bg);

        // 音量
        soundValue = text("", 15, MUTED);
        root.addView(header("音量", soundValue));
        routeInfo = text("", 13, MUTED);
        root.addView(routeInfo);
        soundBar = slider(Curve.LEVELS, ACCENT, p -> {
            sound.setLevel(p);
            showSound();
        });
        root.addView(sliderRow(soundBar, d -> {
            sound.setLevel(sound.level() + d);
            soundBar.setProgress(sound.level());
            showSound();
        }));

        // 亮度
        lightValue = text("", 15, MUTED);
        LinearLayout lh = header("亮度", lightValue);
        lh.setPadding(0, px(14), 0, 0);
        root.addView(lh);
        lightBar = slider(Light.MAX, Color.WHITE, this::setLight);
        root.addView(sliderRow(lightBar, d -> {
            int s = clampLight(lightBar.getProgress() + d * 5);
            lightBar.setProgress(s);
            setLight(s);
        }));

        LinearLayout presets = row();
        for (int i = 0; i < Light.PRESET_NAMES.length; i++) {
            final int idx = i;
            TextView t = chip(Light.PRESET_NAMES[i]);
            t.setOnClickListener(v -> {
                int s = clampLight(Light.preset(this, idx));
                lightBar.setProgress(s);
                setLight(s);
            });
            t.setOnLongClickListener(v -> {
                Light.savePreset(this, idx, lightBar.getProgress());
                Toast.makeText(this, "已存为「" + Light.PRESET_NAMES[idx] + "」", Toast.LENGTH_SHORT).show();
                return true;
            });
            presets.addView(t, weight());
        }
        root.addView(presets);

        // 音量键
        TextView keys = text("音量键", 15, INK);
        keys.setPadding(0, px(16), 0, px(4));
        root.addView(keys);
        LinearLayout modes = row();
        String[] names = {"系统默认", "只防抖", "细调接管"};
        for (int i = 0; i < modeBtn.length; i++) {
            final int m = i;
            modeBtn[i] = chip(names[i]);
            modeBtn[i].setOnClickListener(v -> {
                sound.setMode(m);
                refresh();
            });
            modes.addView(modeBtn[i], weight());
        }
        root.addView(modes);

        shadeBtn = chip("");
        shadeBtn.setOnClickListener(v -> {
            sound.setShadeSliders(!sound.shadeSliders());
            refresh();
        });
        LinearLayout shadeRow = row();
        shadeRow.addView(shadeBtn, weight());
        root.addView(shadeRow);

        notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        root.addView(notes);

        // 更新
        TextView ver = text("当前版本 " + versionName(), 15, INK);
        ver.setPadding(0, px(16), 0, px(4));
        root.addView(ver);
        LinearLayout update = row();
        TextView download = chip("下载最新版");
        download.setOnClickListener(v -> openUrl(APK));
        update.addView(download, weight());
        TextView changes = chip("看更新说明");
        changes.setOnClickListener(v -> openUrl(RELEASES));
        update.addView(changes, weight());
        root.addView(update);
        LinearLayout installRow = row();
        TextView install = chip("安装已下载的更新");
        install.setOnClickListener(v -> pickUpdate());
        installRow.addView(install, weight());
        root.addView(installRow);
        TextView how = text("先用「下载最新版」让浏览器下载，下载完回来点「安装已下载的更新」选中它。"
                + "不要在浏览器里直接点开安装：那样系统会把声光标成受限应用，无障碍服务会开不了。", 13, MUTED);
        how.setPadding(0, px(6), 0, 0);
        root.addView(how);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - px(32), px(520));
        getWindow().setLayout(width, -2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sound.setListener(() -> runOnUiThread(() -> {
            soundBar.setProgress(sound.level());
            showSound();
        }));
        refresh();
    }

    @Override
    protected void onPause() {
        sound.setListener(null);
        super.onPause();
    }

    private void refresh() {
        soundBar.setProgress(sound.level());
        showSound();
        lightBar.setProgress(Light.read(this));
        showLight(lightBar.getProgress());
        for (int i = 0; i < modeBtn.length; i++) {
            boolean on = i == sound.mode();
            paint(modeBtn[i], on ? ACCENT : CARD);
            modeBtn[i].setTextColor(on ? Color.BLACK : INK);
        }
        shadeBtn.setText(sound.shadeSliders() ? "下拉时显示滑块：开" : "下拉时显示滑块：关");
        notes.removeAllViews();
        String crash = CrashLog.last(this);
        if (crash != null) {
            TextView t = text("上次崩溃（可长按复制发给开发者）：\n" + crash, 11, MUTED);
            t.setTextIsSelectable(true);
            t.setMaxLines(12);
            t.setPadding(0, px(12), 0, px(6));
            notes.addView(t);
            TextView clear = chip("清除崩溃记录");
            clear.setOnClickListener(v -> {
                CrashLog.clear(this);
                refresh();
            });
            notes.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        }
        if (!Access.isOn(this)) {
            if (Light.canSecure(this) && Access.restricted(this)) {
                TextView t = text("音量键细调和下拉滑块需要无障碍服务，但系统把声光标成了受限应用（用浏览器或文件管理器安装就会这样），"
                        + "开不了。用 adb 执行下面这条解除，再点「一键开启」：\n" + Access.UNRESTRICT_CMD
                        + "\n以后用面板里的「安装已下载的更新」来装新版，就不会再被标成受限。", 13, MUTED);
                t.setTextIsSelectable(true);
                t.setPadding(0, px(12), 0, px(6));
                notes.addView(t);
                TextView b = chip("一键开启");
                b.setOnClickListener(v -> {
                    Access.enable(this);
                    v.postDelayed(this::refresh, 800);
                });
                notes.addView(b, new LinearLayout.LayoutParams(-1, -2));
            } else if (Light.canSecure(this)) {
                note("音量键细调和下拉滑块需要开启无障碍服务「声光」。这台机器的无障碍设置里找不到它，点下面直接开启。",
                        "一键开启", v -> {
                            if (!Access.enable(this)) Toast.makeText(this, "开启失败", Toast.LENGTH_SHORT).show();
                            // 系统绑定服务要一点时间
                            v.postDelayed(this::refresh, 800);
                        });
            } else {
                TextView t = text("音量键细调和下拉滑块需要开启无障碍服务「声光」。这台机器的无障碍设置里找不到它，"
                        + "请先用电脑执行下面最后一条授权命令，之后这里会出现「一键开启」。", 13, MUTED);
                t.setPadding(0, px(12), 0, 0);
                notes.addView(t);
            }
        }
        if (!Light.canWrite(this)) {
            note("调亮度需要「修改系统设置」权限。", "去授权", v -> startActivity(
                    new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }
        if (!Light.canSecure(this)) {
            TextView t = text("极暗需要用电脑授权一次，授权前亮度滑块停在 " + Light.SPLIT + " 以上：\nadb shell pm grant "
                    + getPackageName() + " " + Light.SECURE_PERMISSION, 13, MUTED);
            t.setTextIsSelectable(true);
            t.setPadding(0, px(12), 0, 0);
            notes.addView(t);
        }
    }

    private int clampLight(int s) {
        int lo = Light.canSecure(this) ? 0 : Light.SPLIT;
        return Math.max(lo, Math.min(Light.MAX, s));
    }

    private void setLight(int s) {
        int c = clampLight(s);
        if (c != s) lightBar.setProgress(c);
        Light.apply(this, c);
        showLight(c);
    }

    private void showSound() {
        soundValue.setText(String.valueOf(sound.level()));
        Route r = sound.route();
        routeInfo.setText(r.external
                ? "输出：" + r.label + "（由耳机调节音量，细调只往下压，不会削顶）"
                : "输出：" + r.label);
    }

    private static final int PICK_APK = 1;

    private void pickUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "先允许声光安装应用，再回来点一次", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        try {
            startActivityForResult(i, PICK_APK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有找到文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_APK || result != RESULT_OK || data == null || data.getData() == null) return;
        Toast.makeText(this, "正在检查安装包…", Toast.LENGTH_SHORT).show();
        Updater.install(this, data.getData(), why -> Toast.makeText(this, why, Toast.LENGTH_LONG).show());
    }

    private String versionName() {
        try {
            // 和发布页的「声光 vN」对得上
            return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有找到浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLight(int s) {
        int dim = Light.levelFor(s);
        lightValue.setText(dim > 0 ? "极暗 " + dim : "亮度 " + Light.brightnessFor(s));
    }

    // ------------------------------------------------------------ 界面小工具

    private interface IntAction {
        void run(int v);
    }

    private SeekBar slider(int max, int color, IntAction onUser) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setSplitTrack(false);
        s.setProgressTintList(ColorStateList.valueOf(color));
        s.setThumbTintList(ColorStateList.valueOf(color));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF48484A));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                if (fromUser) onUser.run(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        return s;
    }

    private LinearLayout sliderRow(SeekBar bar, IntAction nudge) {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(round("−", v -> nudge.run(-1)), new LinearLayout.LayoutParams(px(44), px(44)));
        r.addView(bar, new LinearLayout.LayoutParams(0, px(44), 1f));
        r.addView(round("+", v -> nudge.run(1)), new LinearLayout.LayoutParams(px(44), px(44)));
        return r;
    }

    private LinearLayout header(String title, TextView value) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 18, INK);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        h.addView(value);
        return h;
    }

    private void note(String msg, String action, View.OnClickListener l) {
        TextView t = text(msg, 13, MUTED);
        t.setPadding(0, px(12), 0, px(6));
        notes.addView(t);
        TextView b = chip(action);
        b.setOnClickListener(l);
        notes.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView round(String s, View.OnClickListener l) {
        TextView t = text(s, 20, INK);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(CARD);
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    private TextView chip(String s) {
        TextView t = text(s, 14, INK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(6), px(11), px(6), px(11));
        paint(t, CARD);
        return t;
    }

    private void paint(TextView t, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(14));
        g.setColor(color);
        t.setBackground(g);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, px(6), 0, px(2));
        return r;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(px(3), 0, px(3), 0);
        return lp;
    }

    private int px(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
