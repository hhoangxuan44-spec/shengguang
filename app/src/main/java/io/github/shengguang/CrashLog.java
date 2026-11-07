package io.github.shengguang;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 记下最近一次崩溃，面板里显示出来。无障碍服务崩溃后系统可能把它关掉，
 * 应用没有联网权限，这是唯一能知道原因的办法。
 */
final class CrashLog {
    private CrashLog() {
    }

    private static boolean installed;

    static synchronized void install(Context c) {
        if (installed) return;
        installed = true;
        Context app = c.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                String trace = w.toString();
                if (trace.length() > 3000) trace = trace.substring(0, 3000);
                String time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
                // 进程马上要退出，必须同步写入
                prefs(app).edit().putString("last", time + "  v" + version(app) + "\n" + trace).commit();
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(t, e);
        });
    }

    static String last(Context c) {
        return prefs(c).getString("last", null);
    }

    static void clear(Context c) {
        prefs(c).edit().remove("last").apply();
    }

    private static long version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("crash", Context.MODE_PRIVATE);
    }
}
