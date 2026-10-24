package com.rampey.materialtv;

import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;

/** Pure local-day weather freshness math, shared by the Android repository and host tests. */
final class WeatherPolicy {
    static final long REFRESH_MS = 15 * 60 * 1000L;

    static String localDate(long wallTimeMs, ZoneId zone) {
        return Instant.ofEpochMilli(wallTimeMs).atZone(zone).toLocalDate().toString();
    }

    static boolean stale(long fetchedAtMs, String forecastDate, ZoneId zone, long nowMs) {
        long age = nowMs - fetchedAtMs;
        return fetchedAtMs <= 0 || forecastDate == null || !forecastDate.equals(localDate(nowMs, zone))
                || age < 0 || age >= REFRESH_MS;
    }

    static long remaining(long fetchedAtMs, String forecastDate, ZoneId zone, long nowMs) {
        if (stale(fetchedAtMs, forecastDate, zone, nowMs)) return 0;
        long refreshDelay = REFRESH_MS - (nowMs - fetchedAtMs);
        LocalDate nextDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1);
        long midnightDelay = nextDay.atStartOfDay(zone).toInstant().toEpochMilli() - nowMs;
        return Math.min(refreshDelay, midnightDelay);
    }

    static double position(double current, double min, double max) {
        if (!Double.isFinite(current) || !Double.isFinite(min) || !Double.isFinite(max) || min > max)
            return Double.NaN;
        if (min == max) return 0.5;
        return Math.max(0, Math.min(1, (current - min) / (max - min)));
    }
}
