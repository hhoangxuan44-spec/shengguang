package io.github.shengguang;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.Arrays;
import java.util.List;

/** 媒体声音当前从哪里出去。每个输出各有一条音量曲线和一份记住的等级。 */
final class Route {
    final String key, label;
    final int deviceType;
    /** 蓝牙等由对方设备处理音量的输出：只往下压，避免满幅信号再往上补而削顶。 */
    final boolean external;

    private Route(String key, String label, int deviceType, boolean external) {
        this.key = key;
        this.label = label;
        this.deviceType = deviceType;
        this.external = external;
    }

    static final Route SPEAKER = new Route("speaker", "扬声器", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false);

    private static final AudioAttributes MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    static Route current(AudioManager am) {
        List<AudioDeviceInfo> devices = null;
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                devices = am.getAudioDevicesForAttributes(MEDIA);
            } catch (RuntimeException ignored) {
            }
        }
        if (devices == null || devices.isEmpty()) devices = Arrays.asList(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        AudioDeviceInfo best = null;
        for (AudioDeviceInfo d : devices) {
            if (rank(d.getType()) > (best == null ? 0 : rank(best.getType()))) best = d;
        }
        return best == null ? SPEAKER : of(best);
    }

    /** 同时有多个输出时，系统一般优先走排在前面的那个。 */
    private static int rank(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return 5;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return 4;
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return 3;
            case AudioDeviceInfo.TYPE_HDMI:
                return 2;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return 1;
            default:
                return 0;
        }
    }

    private static Route of(AudioDeviceInfo d) {
        int t = d.getType();
        String name = d.getProductName() == null ? "" : d.getProductName().toString().trim();
        switch (rank(t)) {
            case 5: {
                // 每副蓝牙耳机单独记住音量；地址读不到时按名字区分
                String id = d.getAddress() == null || d.getAddress().isEmpty() ? name : d.getAddress();
                return new Route("bt:" + id, name.isEmpty() ? "蓝牙" : "蓝牙 · " + name, t, true);
            }
            case 4:
                return new Route("wired", "有线耳机", t, false);
            case 3:
                return new Route("usb", "USB 音频", t, false);
            case 2:
                return new Route("hdmi", "HDMI", t, false);
            default:
                return SPEAKER;
        }
    }
}
