package dev.pregen.i18n;

/** Formats durations as {@code HH:MM:SS}, with a leading day count ({@code 2d 03:04:05}) when needed. */
public final class Durations {

    private Durations() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds < 0) {
            return "--:--:--";
        }
        long days = totalSeconds / 86_400;
        long hours = totalSeconds % 86_400 / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        String clock = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        return days > 0 ? days + "d " + clock : clock;
    }
}
