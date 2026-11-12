package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PipRatioTest {

    @Test
    public void commonVideos() {
        assertArrayEquals(new int[]{16, 9}, PipRatio.of(1920, 1080, 1f));
        assertArrayEquals(new int[]{4, 3}, PipRatio.of(320, 240, 1f));
        assertArrayEquals(new int[]{9, 16}, PipRatio.of(1080, 1920, 1f));
        assertArrayEquals(new int[]{1, 1}, PipRatio.of(500, 500, 1f));
    }

    @Test
    public void nonSquarePixels() {
        // anamorphic DVD: 720x576 shown as 16:9 (pixel 64:45)
        int[] r = PipRatio.of(720, 576, 64f / 45f);
        assertArrayEquals(new int[]{16, 9}, r);
        // a broken pixel ratio counts as square
        assertArrayEquals(new int[]{4, 3}, PipRatio.of(320, 240, 0f));
        assertArrayEquals(new int[]{4, 3}, PipRatio.of(320, 240, Float.NaN));
    }

    @Test
    public void outsideTheAllowedRange() {
        assertArrayEquals(new int[]{239, 100}, PipRatio.of(3000, 1000, 1f));
        assertArrayEquals(new int[]{100, 239}, PipRatio.of(400, 2000, 1f));
        // exactly 2.39:1 is allowed as it is
        assertArrayEquals(new int[]{239, 100}, PipRatio.of(2390, 1000, 1f));
    }

    @Test
    public void unknownSize() {
        assertNull(PipRatio.of(0, 0, 1f));
        assertNull(PipRatio.of(1920, 0, 1f));
        assertNull(PipRatio.of(-1, 1080, 1f));
    }
}
