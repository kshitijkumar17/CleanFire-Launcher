package com.rampey.materialtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Keyless, location-aware weather; all HTTPS and JSON work stays off the UI thread. */
final class WeatherRepository {
    private static final String TAG = "MaterialHomeWeather";
    private static final long MIN_LOCATION_INTERVAL_MS = 60_000L;
    private static final long MAX_LOCATION_RETRY_MS = 32 * 60_000L;
    private static final long MAX_WEATHER_RETRY_MS = 2 * 60 * 60_000L;
    private static final int MAX_BYTES = 128 * 1024;

    static final class Weather {
        final double tempC, feelsLikeC, minC, maxC;
        final int chanceOfRain, code, isDay;
        final String forecastDate, locationKey;
        final long fetchedAt;

        Weather(double tempC, double feelsLikeC, double minC, double maxC, int chanceOfRain,
                int code, int isDay, String forecastDate, String locationKey, long fetchedAt) {
            this.tempC = tempC;
            this.feelsLikeC = feelsLikeC;
            this.minC = minC;
            this.maxC = maxC;
            this.chanceOfRain = chanceOfRain;
            this.code = code;
            this.isDay = isDay;
            this.forecastDate = forecastDate;
            this.locationKey = locationKey;
            this.fetchedAt = fetchedAt;
        }

        boolean hasRange() { return Double.isFinite(minC) && Double.isFinite(maxC) && minC <= maxC; }
        boolean stale(IpLocationProvider.Location location) {
            return WeatherPolicy.stale(fetchedAt, forecastDate, location.zone(), System.currentTimeMillis());
        }
    }

    interface Listener { void onWeather(Weather weather); }

    private final SharedPreferences cache;
    private final IpLocationProvider locations;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection connection;
    private Future<?> pending;
    private boolean fetching, closed, forceLocation, locationUnavailable;
    private int generation, locationFailures, weatherFailures;
    private long nextLocationAttemptAt, nextWeatherAttemptAt, lastLocationAttemptAt;
    private IpLocationProvider.Location location;
    private Weather latest;

    WeatherRepository(Context context) {
        locations = new IpLocationProvider(context);
        location = locations.cached();
        cache = context.getApplicationContext().getSharedPreferences("open_meteo_weather", Context.MODE_PRIVATE);
        locationFailures = cache.getInt("location_failures", 0);
        weatherFailures = cache.getInt("weather_failures", 0);
        locationUnavailable = location == null && locationFailures > 0;
        nextLocationAttemptAt = cache.getLong("next_location_attempt", 0);
        nextWeatherAttemptAt = cache.getLong("next_weather_attempt", 0);
        lastLocationAttemptAt = cache.getLong("last_location_attempt", 0);
        if (location != null && location.cacheKey().equals(cache.getString("location_key", ""))) {
            try { latest = parse(cache.getString("response", ""), location, cache.getLong("fetched_at", 0)); }
            catch (JSONException ignored) { /* A bad cache cannot break Home startup. */ }
        }
    }

    Weather cached() { return latest; }
    IpLocationProvider.Location location() { return location; }
    boolean locationUnavailable() { return locationUnavailable; }

    long millisUntilRefresh() {
        if (closed) return Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        long locationDelay;
        if (forceLocation || location == null || location.expired(now)) {
            long allowed = Math.max(nextLocationAttemptAt,
                    forceLocation ? lastLocationAttemptAt + MIN_LOCATION_INTERVAL_MS : 0);
            locationDelay = Math.max(0, allowed - now);
        } else {
            locationDelay = Math.max(0, location.fetchedAt + IpLocationProvider.CACHE_MS - now);
        }
        long weatherDelay = Long.MAX_VALUE;
        if (location != null) {
            long freshness = latest == null ? 0 :
                    WeatherPolicy.remaining(latest.fetchedAt, latest.forecastDate, location.zone(), now);
            weatherDelay = Math.max(freshness, Math.max(0, nextWeatherAttemptAt - now));
        }
        return Math.min(locationDelay, weatherDelay);
    }

    boolean refreshIfStale(Listener listener) {
        if (closed || fetching || millisUntilRefresh() > 0) return false;
        long now = System.currentTimeMillis();
        boolean lookup = (forceLocation || location == null || location.expired(now))
                && now >= nextLocationAttemptAt
                && (!forceLocation || now - lastLocationAttemptAt >= MIN_LOCATION_INTERVAL_MS);
        boolean weatherDue = location != null && now >= nextWeatherAttemptAt
                && (latest == null || latest.stale(location));
        if (!lookup && !weatherDue) return false;
        if (lookup) forceLocation = false;
        fetching = true;
        int request = ++generation;
        IpLocationProvider.Location previousLocation = location;
        Weather previousWeather = latest;
        if (lookup) {
            lastLocationAttemptAt = now;
            cache.edit().putLong("last_location_attempt", now).apply();
        }
        pending = worker.submit(() -> {
            IpLocationProvider.Location nextLocation = previousLocation;
            Weather nextWeather = previousWeather;
            String response = null;
            boolean locationSucceeded = false, locationFailed = false;
            boolean weatherSucceeded = false, weatherFailed = false;
            if (lookup) {
                try {
                    IpLocationProvider.Location detected = IpLocationProvider.fetch();
                    locationSucceeded = true;
                    boolean changed = IpLocationProvider.materiallyDifferent(previousLocation, detected);
                    nextLocation = changed ? detected : previousLocation.renewed(detected.fetchedAt);
                    if (changed) nextWeather = null;
                } catch (IOException | JSONException | RuntimeException failure) {
                    locationFailed = true;
                    Log.w(TAG, "Location refresh failed (" + failure.getClass().getSimpleName() + ")");
                }
            }
            if (nextLocation != null && (nextWeather == null || nextWeather.stale(nextLocation))
                    && (locationSucceeded || System.currentTimeMillis() >= nextWeatherAttemptAt)) {
                try {
                    response = fetch(nextLocation);
                    nextWeather = parse(response, nextLocation, System.currentTimeMillis());
                    if (!nextWeather.forecastDate.equals(WeatherPolicy.localDate(
                            System.currentTimeMillis(), nextLocation.zone())))
                        throw new JSONException("Forecast local day mismatch");
                    weatherSucceeded = true;
                } catch (IOException | JSONException | RuntimeException failure) {
                    weatherFailed = true;
                    Log.w(TAG, "Weather refresh failed (" + failure.getClass().getSimpleName() + ")");
                }
            }
            IpLocationProvider.Location resultLocation = nextLocation;
            Weather resultWeather = nextWeather;
            String resultResponse = response;
            boolean geoOk = locationSucceeded, geoBad = locationFailed;
            boolean weatherOk = weatherSucceeded, weatherBad = weatherFailed;
            main.post(() -> {
                if (closed || request != generation) return;
                fetching = false;
                pending = null;
                long completed = System.currentTimeMillis();
                if (geoOk) {
                    location = resultLocation;
                    locations.save(location);
                    locationUnavailable = false;
                    locationFailures = 0;
                    nextLocationAttemptAt = 0;
                    cache.edit().putInt("location_failures", 0).putLong("next_location_attempt", 0).apply();
                    if (previousLocation == null || !previousLocation.cacheKey().equals(location.cacheKey())) {
                        latest = null;
                        cache.edit().remove("response").remove("location_key").remove("fetched_at")
                                .putLong("next_weather_attempt", 0).putInt("weather_failures", 0).apply();
                        weatherFailures = 0;
                        nextWeatherAttemptAt = 0;
                    }
                } else if (geoBad) {
                    locationUnavailable = location == null;
                    locationFailures = Math.min(locationFailures + 1, 6);
                    nextLocationAttemptAt = completed + retryDelay(60_000L, locationFailures, MAX_LOCATION_RETRY_MS);
                    cache.edit().putInt("location_failures", locationFailures)
                            .putLong("next_location_attempt", nextLocationAttemptAt).apply();
                }
                if (weatherOk && location != null && resultWeather.locationKey.equals(location.cacheKey())) {
                    latest = resultWeather;
                    weatherFailures = 0;
                    nextWeatherAttemptAt = 0;
                    cache.edit().putString("response", resultResponse)
                            .putString("location_key", location.cacheKey())
                            .putLong("fetched_at", resultWeather.fetchedAt)
                            .putInt("weather_failures", 0).putLong("next_weather_attempt", 0).apply();
                } else if (weatherBad) {
                    weatherFailures = Math.min(weatherFailures + 1, 5);
                    nextWeatherAttemptAt = completed + retryDelay(WeatherPolicy.REFRESH_MS,
                            weatherFailures, MAX_WEATHER_RETRY_MS);
                    cache.edit().putInt("weather_failures", weatherFailures)
                            .putLong("next_weather_attempt", nextWeatherAttemptAt).apply();
                }
                listener.onWeather(latest);
            });
        });
        return true;
    }

    void requestNetworkLocationRefresh() { if (!closed) forceLocation = true; }

    private static long retryDelay(long base, int failures, long maximum) {
        return Math.min(maximum, base << Math.min(Math.max(0, failures - 1), 5));
    }

    void cancel() {
        generation++;
        fetching = false;
        if (pending != null) pending.cancel(true);
        pending = null;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    void close() {
        closed = true;
        cancel();
        worker.shutdownNow();
    }

    private String fetch(IpLocationProvider.Location place) throws IOException {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + place.latitude
                + "&longitude=" + place.longitude
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=precipitation_probability"
                + "&daily=temperature_2m_min,temperature_2m_max"
                + "&timezone=" + URLEncoder.encode(place.timezone, "UTF-8")
                + "&temperature_unit=celsius&forecast_days=1";
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setInstanceFollowRedirects(false);
        connection = request;
        request.setConnectTimeout(5000);
        request.setReadTimeout(5000);
        try {
            int status = request.getResponseCode();
            if (status != 200) throw new IOException("Open-Meteo HTTP " + status);
            try (InputStream stream = request.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (bytes.size() + size > MAX_BYTES) throw new IOException("Open-Meteo response too large");
                    bytes.write(buffer, 0, size);
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            request.disconnect();
            if (connection == request) connection = null;
        }
    }

    static Weather parse(String response, IpLocationProvider.Location place, long fetchedAt) throws JSONException {
        JSONObject root = new JSONObject(response);
        JSONObject current = root.getJSONObject("current");
        double temp = requiredTemperature(current, "temperature_2m");
        double feels = requiredTemperature(current, "apparent_temperature");
        int code = current.getInt("weather_code");
        int dayFlag = current.getInt("is_day");
        if (dayFlag != 0 && dayFlag != 1) throw new JSONException("Invalid day flag");
        String currentTime = current.getString("time");
        if (!currentTime.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*"))
            throw new JSONException("Invalid current local time");
        String today = currentTime.substring(0, 10);
        JSONObject daily = root.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        JSONArray minimums = daily.getJSONArray("temperature_2m_min");
        JSONArray maximums = daily.getJSONArray("temperature_2m_max");
        double min = Double.NaN, max = Double.NaN;
        for (int i = 0; i < dates.length(); i++) {
            if (!today.equals(dates.getString(i))) continue;
            min = minimums.getDouble(i);
            max = maximums.getDouble(i);
            break;
        }
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new JSONException("Missing daily temperature range");
        JSONObject hourly = root.getJSONObject("hourly");
        JSONArray hours = hourly.getJSONArray("time");
        JSONArray probabilities = hourly.getJSONArray("precipitation_probability");
        int rain = -1;
        String currentHour = currentTime.substring(0, 13);
        boolean matchedHour = false;
        for (int i = 0; i < hours.length(); i++) {
            if (!hours.getString(i).startsWith(currentHour)) continue;
            matchedHour = true;
            if (i < probabilities.length() && !probabilities.isNull(i)) {
                double chance = probabilities.getDouble(i);
                if (Double.isFinite(chance) && chance >= 0 && chance <= 100) rain = (int) Math.round(chance);
            }
            break;
        }
        if (!matchedHour) throw new JSONException("Current forecast hour missing");
        return new Weather(temp, feels, min, max, rain, code, dayFlag, today, place.cacheKey(), fetchedAt);
    }

    private static double requiredTemperature(JSONObject object, String name) throws JSONException {
        double value = object.getDouble(name);
        if (!Double.isFinite(value)) throw new JSONException("Invalid " + name);
        return value;
    }
}
