# 声光

把平板的音量和亮度调得更细。不需要 root，不联网，不替换桌面或系统界面。

- **音量**：0–100 级细调（系统档位加一层数字增益），音量键防抖或接管。
- **亮度**：一个滑块连续调亮度和系统「极暗」，带三个预设。
- **入口**：快捷设置磁贴打开小面板。

只在酷比魔方掌玩 mini2（Android 14）的扬声器上验证过。

## 授权

装好后在面板里开启无障碍服务并批准「修改系统设置」，再用电脑执行一次：

```
adb shell pm grant io.github.shengguang android.permission.WRITE_SECURE_SETTINGS
```

## 编译

推送到 main 后由 GitHub Actions 编译并发布。需要两个仓库 Secret：`SIGN_KEYSTORE_B64`（keystore 的 base64）和 `SIGN_PASSWORD`。
