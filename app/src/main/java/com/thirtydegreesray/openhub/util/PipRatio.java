package com.thirtydegreesray.openhub.util;

/**
 * The shape of the small video window (picture in picture). Android accepts 1:2.39 up to
 * 2.39:1; a video outside that range gets the nearest allowed shape. Pure Java, tested in
 * PipRatioTest.
 */
public final class PipRatio {

    private static final double MAX = 239.0 / 100.0;

    private PipRatio() {
    }

    /**
     * {width, height} for the window, or null while the size is not known yet.
     *
     * @param pixelRatio width / height of one pixel (1 for most videos)
     */
    public static int[] of(int width, int height, float pixelRatio) {
        if (width <= 0 || height <= 0) return null;
        double px = pixelRatio > 0 && !Float.isNaN(pixelRatio) && !Float.isInfinite(pixelRatio)
                ? pixelRatio : 1f;
        double ratio = width * px / height;
        if (ratio > MAX) return new int[]{239, 100};
        if (ratio < 1 / MAX) return new int[]{100, 239};
        int w = (int) Math.round(width * px);
        if (w <= 0) return null;
        int g = gcd(w, height);
        return new int[]{w / g, height / g};
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(a, 1);
    }
}
