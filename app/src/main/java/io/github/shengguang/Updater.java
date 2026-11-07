package io.github.shengguang;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 由声光自己安装浏览器下载好的新版本。
 *
 * 在浏览器或文件管理器里点安装包，系统会把来源记成「下载的文件」，把应用标成受限，
 * 受限的应用开不了无障碍服务（酷比的设置里也没有「允许受限设置」可点）。
 * 改由本应用通过 PackageInstaller 会话安装，来源不是下载文件，不会被这样标记。
 * 应用依旧没有联网权限，下载仍交给浏览器。
 */
public class Updater extends BroadcastReceiver {
    private static final String ACTION = "io.github.shengguang.INSTALL_RESULT";

    interface Done {
        void failed(String why);
    }

    /** 在后台线程里检查并安装；出错时在主线程回调。 */
    static void install(Context ctx, Uri uri, Done done) {
        Context c = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String err = doInstall(c, uri);
            if (err != null) main.post(() -> done.failed(err));
        }, "shengguang-install").start();
    }

    private static String doInstall(Context c, Uri uri) {
        File apk = new File(c.getCacheDir(), "update.apk");
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(apk)) {
                if (in == null) return "读不到这个文件";
                copy(in, out);
            }
            PackageManager pm = c.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getPath(), 0);
            if (info == null) return "这不是安装包";
            if (!c.getPackageName().equals(info.packageName)) return "这不是声光的安装包（" + info.packageName + "）";
            long current = pm.getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            if (info.getLongVersionCode() <= current) {
                return "这个安装包是 v" + info.getLongVersionCode() + "，不比当前的 v" + current + " 新";
            }

            PackageInstaller installer = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(c.getPackageName());
            int id = installer.createSession(params);
            try (PackageInstaller.Session s = installer.openSession(id)) {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = s.openWrite("base.apk", 0, apk.length())) {
                    copy(in, out);
                    s.fsync(out);
                }
                Intent result = new Intent(c, Updater.class).setAction(ACTION);
                PendingIntent pi = PendingIntent.getBroadcast(c, id, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                s.commit(pi.getIntentSender());
            }
            return null;
        } catch (Exception e) {
            Log.w(Sound.TAG, "install", e);
            return "安装失败：" + e.getMessage();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    /** 安装会话的结果：需要确认时弹出系统的安装确认，失败时提示原因。 */
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation") // 带类型的版本要 Android 13，应用最低支持 11
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "安装没有完成" + (msg == null ? "" : "：" + msg), Toast.LENGTH_LONG).show();
        }
    }
}
