package io.github.shengguang;

/**
 * 把 0–100 的细调等级换算成「系统音量档 + 数字增益」。
 *
 * 档与档之间总是取低一档再往上补，这样音效层失效时只会变小声。
 * 唯一的例外是 0 和 1 档之间：0 档就是静音，无法往上补，只能取 1 档再往下压。
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

    /** dbPerIndex[i] 是系统第 i 档的衰减（dB）。读不到或不递增的值会被替换成固定步长。 */
    Curve(double[] dbPerIndex) {
        db = dbPerIndex.clone();
        for (int i = 2; i < db.length; i++) {
            if (Double.isNaN(db[i]) || Double.isInfinite(db[i]) || db[i] <= db[i - 1]) {
                db[i] = (Double.isNaN(db[i - 1]) || Double.isInfinite(db[i - 1]) ? 0 : db[i - 1]) + DEFAULT_STEP_DB;
            }
        }
    }

    Point at(int level) {
        int max = db.length - 1;
        if (level <= 0) return new Point(0, 0);
        if (level >= LEVELS) return new Point(max, 0);
        double p = level * (double) max / LEVELS;
        int i = (int) p;
        double f = p - i;
        if (i == 0) return new Point(1, (int) Math.round(-(1 - f) * BOTTOM_RANGE_DB * 100));
        double boost = f * (db[i + 1] - db[i]);
        if (Double.isNaN(boost) || boost < 0) boost = 0;
        return new Point(i, (int) Math.round(Math.min(boost, MAX_BOOST_DB) * 100));
    }

    /** 系统音量档对应的细调等级（用于跟随系统当前音量，不改变响度）。 */
    int levelForIndex(int index) {
        int max = db.length - 1;
        return Math.max(0, Math.min(LEVELS, (int) Math.ceil(index * (double) LEVELS / max)));
    }
}
