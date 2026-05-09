package me.psikuvit.cashClash.util;

public class FormatUtils {

    private FormatUtils() {
        throw new AssertionError("Nope.");
    }

    public static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    public static String formatMillis(long millis) {
        if (millis <= 0) {
            return "0:00";
        }
        return formatTime((int) Math.ceil(millis / 1000.0));
    }
}
