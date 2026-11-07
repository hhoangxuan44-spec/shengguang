package io.github.shengguang;

/**
 * 把 0–100 的细调等级换算成「系统音量档 + 数字增益」。
 *
 * 扬声器和有线耳机：档与档之间取低一档再往上补。系统音量在音效之前生效，补完也不会超过高一档，
 * 音效层失效时只会变小声。
 *
 * 蓝牙（绝对音量）：系统音量交给耳机自己处理，送出去的信号始终是满幅的，往上补会削顶失真。
 * 所以反过来取高一档再往下压，代价是音效层失效时会比目标大不到一档。
 *
 * 两种方式在 0 和 1 档之间都一样：0 档就是静音，只能取 1 档再往下压。
 */
final class Curve {
    static final int LEVELS = 100;
    private static final double BOTTOM_RANGE_DB = 30, MAX_BOOST_DB = 15, DEFAULT_STEP_DB = 2;

    static final class Point {
        final int index, gainMb;

        Point(int index, int gainMb) {
            this.index = index;
            this.gainMb = gainMb;
        }
    }

    private final double[] db;
    private final boolean fromAbove;

    Curve(double[] dbPerIndex) {
        this(dbPerIndex, false);
    }

    /**
     * dbPerIndex[i] 是系统第 i 档的衰减（dB）。读不到或不递增的值会被替换成固定步长。
     * fromAbove 为 true 时只往下压，不往上补。
     */
    Curve(double[] dbPerIndex, boolean fromAbove) {
        this.fromAbove = fromAbove;
        db = dbPerIndex.clone();
        for (int i = 2; i < db.length; i++) {
            if (Double.isNaN(db[i]) || Double.isInfinite(db[i]) || db[i] <= db[i - 1]) {
                db[i] = (Double.isNaN(db[i - 1]) || Double.isInfinite(db[i - 1]) ? 0 : db[i - 1]) + DEFAULT_STEP_DB;
            }
        }
    }

    boolean fromAbove() {
        return fromAbove;
    }

    /** 相邻两档之间的平均差值（dB），用于在面板上说明。 */
    double stepDb() {
        int max = db.length - 1;
        return max >= 2 ? (db[max] - db[1]) / (max - 1) : 0;
    }

    Point at(int level) {
        int max = db.length - 1;
        if (level <= 0) return new Point(0, 0);
        if (level >= LEVELS) return new Point(max, 0);
        double p = level * (double) max / LEVELS;
        int i = (int) p;
        double f = p - i;
        if (i == 0) return new Point(1, (int) Math.round(-(1 - f) * BOTTOM_RANGE_DB * 100));
        if (f == 0) return new Point(i, 0);
        double gap = db[i + 1] - db[i];
        if (Double.isNaN(gap) || gap < 0) gap = 0;
        if (fromAbove) return new Point(i + 1, (int) -Math.round((1 - f) * gap * 100));
        return new Point(i, (int) Math.round(Math.min(f * gap, MAX_BOOST_DB) * 100));
    }

    /** 系统音量档对应的细调等级（用于跟随系统当前音量，不改变响度）。 */
    int levelForIndex(int index) {
        int max = db.length - 1;
        double l = index * (double) LEVELS / max;
        return Math.max(0, Math.min(LEVELS, (int) (fromAbove ? Math.floor(l) : Math.ceil(l))));
    }
}
