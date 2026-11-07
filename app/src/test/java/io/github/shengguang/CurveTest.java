package io.github.shengguang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CurveTest {
    private static double[] table() {
        double[] d = new double[16];
        d[0] = Double.NEGATIVE_INFINITY;
        for (int i = 1; i < 16; i++) d[i] = -45 + (i - 1) * 3;
        return d;
    }

    @Test
    public void endsAreExact() {
        Curve c = new Curve(table());
        assertEquals(0, c.at(0).index);
        assertEquals(0, c.at(0).gainMb);
        assertEquals(15, c.at(100).index);
        assertEquals(0, c.at(100).gainMb);
    }

    @Test
    public void loudnessNeverGoesDownAsLevelRises() {
        double[] d = table();
        Curve c = new Curve(d);
        double last = Double.NEGATIVE_INFINITY;
        for (int l = 1; l <= 100; l++) {
            Curve.Point p = c.at(l);
            double out = d[p.index] + p.gainMb / 100.0;
            assertTrue("level " + l + ": " + out + " < " + last, out >= last - 0.01);
            last = out;
        }
    }

    @Test
    public void aboveFirstIndexOnlyBoostsSoFailureIsQuieter() {
        Curve c = new Curve(table());
        for (int l = 7; l < 100; l++) assertTrue("level " + l, c.at(l).gainMb >= 0);
    }

    @Test
    public void levelForIndexRoundTrips() {
        Curve c = new Curve(table());
        for (int i = 0; i <= 15; i++) assertEquals(i, c.at(c.levelForIndex(i)).index);
    }

    @Test
    public void fromAboveOnlyAttenuatesSoNothingClips() {
        Curve c = new Curve(table(), true);
        for (int l = 0; l <= 100; l++) assertTrue("level " + l, c.at(l).gainMb <= 0);
    }

    @Test
    public void fromAboveLoudnessNeverGoesDown() {
        double[] d = table();
        Curve c = new Curve(d, true);
        double last = Double.NEGATIVE_INFINITY;
        for (int l = 1; l <= 100; l++) {
            Curve.Point p = c.at(l);
            double out = d[p.index] + p.gainMb / 100.0;
            assertTrue("level " + l + ": " + out + " < " + last, out >= last - 0.01);
            last = out;
        }
    }

    @Test
    public void fromAboveMatchesBelowInLoudness() {
        double[] d = table();
        Curve below = new Curve(d), above = new Curve(d, true);
        for (int l = 1; l <= 100; l++) {
            Curve.Point a = below.at(l), b = above.at(l);
            assertEquals("level " + l, d[a.index] + a.gainMb / 100.0, d[b.index] + b.gainMb / 100.0, 0.02);
        }
    }

    @Test
    public void fromAboveLevelForIndexRoundTrips() {
        Curve c = new Curve(table(), true);
        for (int i = 0; i <= 15; i++) assertEquals(i, c.at(c.levelForIndex(i)).index);
    }

    @Test
    public void flatTableFallsBackToFixedSteps() {
        double[] d = new double[16];
        Curve c = new Curve(d, true);
        assertEquals(2.0, c.stepDb(), 1e-9);
        assertTrue(c.at(50).gainMb < 0);
    }
}
