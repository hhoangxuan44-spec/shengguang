package io.github.shengguang;

import android.media.audiofx.DynamicsProcessing;
import android.util.Log;

/**
 * 全局输出（session 0）上的数字增益。
 * 实测这台机器上 LoudnessEnhancer 和 Equalizer 对 session 0 没有效果，
 * DynamicsProcessing 必须用 FREQUENCY 模式，TIME 模式会打开失败。
 */
final class Engine {
    private DynamicsProcessing effect;

    /** 打开音效，失败返回错误说明，成功返回 null。 */
    synchronized String open() {
        release();
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, false, 0, false, 0, false).build();
            effect = new DynamicsProcessing(1000, 0, cfg);
            effect.setInputGainAllChannelsTo(0f);
            effect.setEnabled(true);
            return effect.hasControl() ? null : "没有控制权（被其他音效占用）";
        } catch (Throwable e) {
            Log.w(Sound.TAG, "open", e);
            release();
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    synchronized boolean setGainMb(int mB) {
        if (effect == null) return false;
        try {
            effect.setInputGainAllChannelsTo(mB / 100f);
            return true;
        } catch (Throwable e) {
            Log.w(Sound.TAG, "setGain " + mB, e);
            return false;
        }
    }

    synchronized boolean isOpen() {
        return effect != null;
    }

    synchronized void release() {
        if (effect != null) {
            try {
                effect.setEnabled(false);
                effect.release();
            } catch (Throwable ignored) {
            }
        }
        effect = null;
    }
}
