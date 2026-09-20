package dev.malik.lcftbhook.client;

/** A simple pulsing alpha value shared by every "blinking" overlay in the mod, so they all pulse in sync. */
public final class BlinkUtil {
    private static final long PERIOD_MILLIS = 900L;
    private static final int MIN_ALPHA = 70;
    private static final int MAX_ALPHA = 220;

    private BlinkUtil() {
    }

    public static int alpha() {
        double phase = (System.currentTimeMillis() % PERIOD_MILLIS) / (double) PERIOD_MILLIS;
        double wave = 0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI);
        return MIN_ALPHA + (int) Math.round((MAX_ALPHA - MIN_ALPHA) * wave);
    }
}
