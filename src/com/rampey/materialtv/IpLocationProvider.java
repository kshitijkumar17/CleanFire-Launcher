package com.rampey.materialtv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

/** Keyless public-IP location for weather; call fetch only from a background executor. */
final class IpLocationProvider {
    static final long CACHE_MS = 24 * 60 * 60 * 1000L;
    private static final String ENDPOINT = "https://ipdata.free/";
    private final SharedPreferences cache;

    static final class Location {
        final String city, country, timezone;
        final double latitude, longitude;
        final long fetchedAt;

        Location(String city, String country, double latitude, double longitude, String timezone, long fetchedAt) {
            this.city = city;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timezone = timezone;
            this.fetchedAt = fetchedAt;
        }

        String label() { return city + ", " + country; }
        ZoneId zone() { return ZoneId.of(timezone); }
        String cacheKey() {
            return country + "|" + city.toLowerCase(Locale.ROOT) + "|"
                    + Double.toString(latitude) + "|" + Double.toString(longitude);
        }
        boolean expired(long now) { return fetchedAt <= 0 || now < fetchedAt || now - fetchedAt >= CACHE_MS; }
        Location renewed(long now) { return new Location(city, country, latitude, longitude, timezone, now); }
    }

    IpLocationProvider(Context context) {
        cache = context.getApplicationContext().getSharedPreferences("ip_weather_location", Context.MODE_PRIVATE);
    }

    Location cached() {
        if (!cache.contains("city") || !cache.contains("country") || !cache.contains("latitude")
                || !cache.contains("longitude") || !cache.contains("timezone")
                || !cache.contains("fetched_at")) return null;
        try {
            return validated(cache.getString("city", ""), cache.getString("country", ""),
                    Double.longBitsToDouble(cache.getLong("latitude", 0)),
                    Double.longBitsToDouble(cache.getLong("longitude", 0)),
                    cache.getString("timezone", ""), cache.getLong("fetched_at", 0));
        } catch (JSONException | RuntimeException invalid) {
            return null;
        }
    }

    void save(Location location) {
        // One editor transaction keeps the fields and timestamp together across restarts.
        cache.edit().putString("city", location.city).putString("country", location.country)
                .putLong("latitude", Double.doubleToRawLongBits(location.latitude))
                .putLong("longitude", Double.doubleToRawLongBits(location.longitude))
                .putString("timezone", location.timezone).putLong("fetched_at", location.fetchedAt).apply();
    }

    static Location fetch() throws IOException, JSONException {
        HttpURLConnection request = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        request.setInstanceFollowRedirects(false);
        request.setConnectTimeout(5000);
        request.setReadTimeout(5000);
        try {
            int status = request.getResponseCode();
            if (status != 200) throw new IOException("Geolocation HTTP " + status);
            try (InputStream stream = request.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 32 * 1024) throw new IOException("Geolocation response too large");
                    bytes.write(buffer, 0, count);
                }
                return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8), System.currentTimeMillis());
            }
        } finally {
            request.disconnect();
        }
    }

    static Location parse(String response, long fetchedAt) throws JSONException {
        JSONObject json = new JSONObject(response);
        return validated(json.getString("city"), json.getString("country"),
                json.getDouble("latitude"), json.getDouble("longitude"),
                json.getString("timezone"), fetchedAt);
    }

    private static Location validated(String rawCity, String rawCountry, double latitude, double longitude,
            String rawTimezone, long fetchedAt) throws JSONException {
        String city = rawCity == null ? "" : rawCity.trim();
        String country = rawCountry == null ? "" : rawCountry.trim().toUpperCase(Locale.ROOT);
        String timezone = rawTimezone == null ? "" : rawTimezone.trim();
        if (city.isEmpty() || city.length() > 100 || !country.matches("[A-Z]{2}")
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new JSONException("Incomplete geolocation");
        try { ZoneId.of(timezone); }
        catch (DateTimeException invalid) { throw new JSONException("Invalid geolocation timezone"); }
        return new Location(city, country, latitude, longitude, timezone, fetchedAt);
    }

    static boolean materiallyDifferent(Location a, Location b) {
        if (a == null) return true;
        if (!a.city.equalsIgnoreCase(b.city) || !a.country.equals(b.country)
                || !a.timezone.equals(b.timezone)) return true;
        double lat = Math.toRadians(b.latitude - a.latitude);
        double lon = Math.toRadians(b.longitude - a.longitude);
        double arc = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.latitude)) * Math.cos(Math.toRadians(b.latitude))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 12742.0 * Math.asin(Math.min(1, Math.sqrt(arc))) > 25;
    }
}
