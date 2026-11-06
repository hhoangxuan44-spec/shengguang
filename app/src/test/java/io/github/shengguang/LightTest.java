package io.github.shengguang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LightTest {
    @Test
    public void segmentsMeetAtSplit() {
        assertEquals(1, Light.brightnessFor(Light.SPLIT));
        assertEquals(0, Light.levelFor(Light.SPLIT));
        assertEquals(255, Light.brightnessFor(Light.MAX));
        assertEquals(100, Light.levelFor(0));
    }

    @Test
    public void scaleRoundTrips() {
        for (int s = 0; s <= Light.MAX; s += 50) {
            int back = Light.scaleFor(Light.brightnessFor(s), Light.levelFor(s) > 0, Light.levelFor(s));
            assertTrue("scale " + s + " → " + back, Math.abs(back - s) <= 25);
        }
    }
}
