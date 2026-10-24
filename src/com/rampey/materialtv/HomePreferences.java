package com.rampey.materialtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Package-only launcher choices. A single editor commit keeps both lists in sync. */
final class HomePreferences {
    private static final int SCHEMA = 1;
    private final SharedPreferences prefs;
    HomePreferences(Context context) {
        prefs = context.getSharedPreferences("material_home_choices", Context.MODE_PRIVATE);
    }
    boolean migrated() { return prefs.getBoolean("home_apps_migrated", false); }
    List<String> home() { return read("home_apps_order"); }
    Set<String> hidden() { return new LinkedHashSet<>(read("hidden_apps")); }
    void save(List<String> home, Set<String> hidden) {
        prefs.edit().putInt("preferences_schema_version", SCHEMA)
                .putString("home_apps_order", encode(home))
                .putString("hidden_apps", encode(hidden))
                .putBoolean("home_apps_migrated", true).commit();
    }
    private List<String> read(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!TextUtils.isEmpty(value) && !out.contains(value)) out.add(value);
            }
        } catch (JSONException ignored) { }
        return out;
    }
    private static String encode(Collection<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array.toString();
    }
}
