package com.rampey.materialtv;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Host-side deterministic checks for refresh, rollover, and range placement. */
public final class WeatherPolicyTest {
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }

    private static long pune(String time) {
        return LocalDateTime.parse(time).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
    }

    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long fetched = pune("2026-10-23T12:00:00");
        check(!WeatherPolicy.stale(fetched, "2026-10-23", zone, fetched), "new cache");
        check(WeatherPolicy.remaining(fetched, "2026-10-23", zone, fetched) == 900000, "initial delay");
        check(!WeatherPolicy.stale(fetched, "2026-10-23", zone, fetched + 899999), "under 15 minutes");
        check(WeatherPolicy.stale(fetched, "2026-10-23", zone, fetched + 900000), "15-minute boundary");
        check(WeatherPolicy.stale(fetched, "2026-10-23", zone, fetched - 1), "clock moves backward");
        check(WeatherPolicy.stale(fetched, null, zone, fetched), "legacy cache has no forecast");
        long beforeMidnight = pune("2026-10-23T23:59:30");
        check(WeatherPolicy.remaining(beforeMidnight, "2026-10-23", zone, beforeMidnight) == 30000,
                "schedule at Pune midnight");
        check(WeatherPolicy.stale(beforeMidnight, "2026-10-23", zone, beforeMidnight + 30000),
                "yesterday's forecast stale even before 15 minutes");
        ZoneId newYork = ZoneId.of("America/New_York");
        long newYorkMidnight = LocalDateTime.parse("2026-10-23T23:59:30")
                .atZone(newYork).toInstant().toEpochMilli();
        check(WeatherPolicy.remaining(newYorkMidnight, "2026-10-23", newYork, newYorkMidnight) == 30000,
                "schedule at detected timezone midnight");
        check(WeatherPolicy.stale(newYorkMidnight, "2026-10-23", newYork, newYorkMidnight + 30000),
                "detected timezone date rollover");
        check(WeatherPolicy.position(25, 21, 33) == 1.0 / 3, "unrounded position");
        check(WeatherPolicy.position(20, 21, 33) == 0, "clamp low");
        check(WeatherPolicy.position(35, 21, 33) == 1, "clamp high");
        check(WeatherPolicy.position(21, 21, 21) == 0.5, "equal range center");
        check(Double.isNaN(WeatherPolicy.position(Double.NaN, 21, 33)), "reject NaN");
        check(Double.isNaN(WeatherPolicy.position(25, 33, 21)), "reject reversed range");
        check(Double.isNaN(WeatherPolicy.position(Double.POSITIVE_INFINITY, 21, 33)), "reject infinity");
        System.out.println("WeatherPolicyTest: 16 checks passed");
    }
}
