# CleanFire Launcher — Fire OS 8 port

Current app label: **CleanFire Launcher**. Earlier entries record the historical
Material Home label used before the branding update; package ID and Java classes are unchanged.

## Milestone 1 — compatibility audit (2026-10-23)

Baseline: upstream `eccdc92` (v0.1.4). Target: Android 11 / API 30, Fire OS 8.
The connected test device reports API 30 and Fire OS 8.1.8.5.

### Architecture and dependencies

The application is plain Java Views. `HomeActivity` constructs the Home dock, Apps grid,
clock, remote focus behavior, palette transitions and media card. `ButtonGroup`, `Focus`,
`Motion`, `CookieDrawable`, `EqualizerBadge` and `WavyProgress` implement the existing visuals.
`Palette` uses vendored Java material-color-utilities (`QuantizerCelebi`, `Score`,
`SchemeFidelity`), not Android 12 wallpaper colors. Google Sans Flex is bundled in assets.
There is no Compose, Kotlin, Gradle, Google Play Services or runtime network dependency.
`Screensaver`/`ShapeScene` implement a separate DreamService. `MediaListener` is a notification
listener used to authorize media-session access. `HomeKeyService` intercepts Home through accessibility.

### API audit and changes required

| Area | Finding | Port decision |
| --- | --- | --- |
| Build | aapt2 min SDK 31 and D8 min API 31; compile/target 34 | Lower both minimums to 30 only after API audit. Compile against SDK 30 to prevent accidental newer API references; retain target 34. |
| Android 12+ APIs | All upstream Java, including vendor sources, compiled successfully against **android-30/android.jar** after generating R with aapt2. No API 31+ symbols found. | No rendering fallback required. No RenderEffect, Android 12 SplashScreen or system dynamic-color resources are used. |
| Java runtime | JDK 17 compilation; vendor uses collections, streams and functions supported on API 30. | Retain D8 lambda/language desugaring; validate on device. JDK version is not Android API level. |
| Visuals | PathInterpolator (21), postOnAnimation (16), variable Typeface.Builder (26), outline shadow colors (28), GradientDrawable.getCornerRadius (24) | Preserve upstream styling, fonts, geometry and motion. |
| Discovery | Manifest already queries MAIN + LEANBACK_LAUNCHER and LAUNCHER; grid queries both, dock fallback only Leanback | Use the same deduplicated TV + standard launcher queries for both. Retain Leanback-first launch intent with standard fallback. No QUERY_ALL_PACKAGES. |
| HDMI | TvInputManager/CEC passthrough expects television input hardware | Disable discovery, callbacks and input intents in this stick port; keep original implementation available in source. |
| Identity | Manifest package and relative components use upstream ID | Use `dev.karat.materialhome`; retain upstream Java namespace with explicit component class names and generated R import. Give the fork a distinct app label. |
| Home interception | Accessibility service consumes Home and launches on boot | Remove its manifest registration and HOME intent filter. Launch explicitly or from installed-app UI. Do not change existing Home routing. |
| Usage ordering | PACKAGE_USAGE_STATS requires special user access | Keep optional declaration and graceful installed-app fallback. No automatic app-op grants. |
| Media | Notification access gates MediaSessionManager; missing service/denied access must be tolerated | Retain optional listener declaration; no grants, no notification reading. Check service availability. |
| Screensaver | DreamService exists before API 30, Fire OS selection support uncertain | Keep service available, do not select or enable it automatically. |
| Lifecycle | Existing continuous drift stops on pause, but transient animators and media waves can outlive pause | Stop transient animators and explicitly suspend custom progress/equalizer frame scheduling while paused. |

### Permissions and installation trust boundary

No INTERNET, root, signature/privileged uses-permission or package-removal capability is needed.
BIND_NOTIFICATION_LISTENER_SERVICE and BIND_DREAM_SERVICE are **service guards**, not privileges
requested by this app. PACKAGE_USAGE_STATS remains optional. The accessibility service will not
be registered in the port. No boot receiver is added.

Reviewed `install.sh`: it installs upstream, grants notification/usage access, writes accessibility
settings, and launches upstream. Reviewed `uninstall.sh`: it edits accessibility, notification and
dream settings and uninstalls upstream. **Neither script is appropriate for this port and neither
was run.** Add isolated Fire OS scripts limited to installing/launching or uninstalling this fork.
Never disable/remove Amazon's launcher, change the HOME role, or modify Karat Home / Home on Fire.

Apache 2.0 LICENSE, vendor LICENSE and Google Sans Flex license/trademark notices must remain.

### Primary references

- [Amazon Fire OS overview](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html)
- [Android package visibility](https://developer.android.com/training/package-visibility/declaring)
- [Android RenderEffect (API 31)](https://developer.android.com/reference/android/graphics/RenderEffect)

The compile result above is local evidence; physical functionality is recorded below as tested,
not inferred from Android compatibility.

## Milestone 2 — minimal port

The first APK built and installed successfully on the connected physical device. Explicit
activity launch returned Status ok and the original screen rendered at 1920 × 1080.
`aapt2 dump badging` confirms package `dev.karat.materialhome`, min SDK 30, target SDK 34.
The source remains in `com.rampey.materialtv` to minimize divergence; use the full class name
when starting a component, not `dev.karat.materialhome/.HomeActivity`.

Build and install (JDK 17, SDK platform 30, build tools 35.0.0):

```sh
./build.sh
adb devices -l
./install-fireos.sh <adb-serial>
# Open again without changing any launcher settings:
adb -s <adb-serial> shell am start -W -n dev.karat.materialhome/com.rampey.materialtv.HomeActivity
# Remove only this fork if desired:
./uninstall-fireos.sh <adb-serial>
```

Use **CleanFire Launcher** from the installed apps UI. Home remains controlled by the user's existing
setup. Back from Apps returns to the dock; Back from the dock exits the activity. Menu toggles
Home/Apps. Up/down/left/right and Select use the upstream focus implementation.
Do not use upstream `install.sh` / `uninstall.sh` for this fork.

## Milestone 3 — visual compatibility and performance

The original rendering implementation is retained. Physical 1920 × 1080 captures cover
all six dock focus positions, all three nav tabs, the first Apps row and five downward
focus/scroll steps, plus Back to Home. App banners, icon fallbacks, variable font clock/title,
focus scales/rings, app palette changes and per-app background shapes render on API 30.
Menu toggles Apps/Home and directional navigation scrolls the five-column grid.

Transient value animators now finish on pause, ViewPropertyAnimator end actions are cancelled,
and equalizer/progress drawing explicitly stops scheduling frames while paused. The existing
shape drift and clock callbacks still stop/resume with the activity. A physical background
check while Apple TV was foreground reported **0 frames before and after five seconds**.

**Performance acceptance remains open.** During a capture-heavy navigation sample, gfxinfo
reported 2951/3063 janky frames (96.34%), median 18 ms, p95 30 ms, 23 frame deadlines missed.
A separate 12-second idle sample without captures still reported 537/726 (73.97%), median
17 ms, p95 19 ms. Display was 1920 × 1080 at 59.94 Hz. Thus the repository's zero-jank goal
has NOT passed. Existing continuous animation is preserved; no reduced frame-rate workaround
or system display setting change was applied. Further render-thread/buffer timing profiling
is needed before claiming smoothness acceptance. These are device reports, not an assertion
that the same percentage of frames was visibly dropped.

## Milestone 4 — optional integration investigation

| Integration | Verified on this device | Status |
| --- | --- | --- |
| Usage ordering | GET_USAGE_STATS stays `default`, with rejected query recorded | Installed-app fallback works; permission-dependent ordering untested. Standard usage-access settings intent resolves to an Amazon CTS dummy activity, not a confirmed usable grant screen. |
| Now Playing | Notification-listener permission not granted; startup succeeds with card hidden | Retained optional service/media code; metadata/playback untested. Standard notification-listener settings intent has no handler. |
| Screensaver | DreamService API compiles; DreamManager exists; Amazon screensaver remains selected | Standard dream settings intent has no handler; selection/rendering untested. Not enabled or selected. |

No access grants, accessibility registration, app-op changes or dream settings writes were
performed. Do not use ADB to enable these services as part of normal installation. Fire OS
may not expose a supported user consent/selection path; optional features can remain absent.

## Attribution and distribution

Original `LICENSE`, vendor sources/license/provenance, and Google Sans Flex font/OFL/trademark
notices remain intact. `NOTICE` identifies upstream and the fork's changes. The build also
packages Apache licenses, NOTICE and vendor provenance into `assets/licenses/` inside the APK;
font notices remain in `assets/fonts/`.

## Milestone 5 — physical device results

Tested on the user's connected Fire TV Stick 4K (2nd generation), reporting API 30 and
Fire OS 8.1.8.5. No emulator results are substituted for physical checks.

| Check | Result |
| --- | --- |
| Build, package identity, signing | Passed; debug-signed APK, min/compile 30, target 34. |
| Startup | Passed; final cold launch Status ok, TotalTime 1283 ms / WaitTime 1295 ms. No fork entry found in the device crash log buffer. |
| Remote navigation | Passed with ADB-injected real Android D-pad, Select, Menu and Back events; physical handset feel/long-press repeat remains for user confirmation. |
| Apps discovery/artwork | Passed; banners and icon fallbacks visible; standard-launcher-only entries appear alongside Leanback apps. |
| Selected streaming apps | Apple TV from dock, Netflix and Amazon's YouTube from Apps each became the resumed activity after Select. Content playback/login/DRM were not tested. |
| Return to Material Home | Passed by explicitly reopening the existing activity after switching apps; UI renders and accepts navigation again. One Back press inside Apple TV stayed inside Apple TV, so no claim is made that every app returns immediately on Back. |
| Home isolation | Home key returned to existing Karat Home. HOME resolver, accessibility service list/enabled flag, notification listener list, screensaver component and enabled flag matched pre-install snapshots. |
| Background rendering | 0 launcher frames across five seconds while Apple TV remained foreground. |
| Foreground frame acceptance | NOT PASSED; see milestone 3 measurements. |
| Optional services | Not granted or selected; actual optional media/usage/dream behavior untested. |

Final artifact: `build/material-home-fireos.apk`, 4,081,327 bytes.
SHA-256: `887887b04fcf20635469e1594f9d2bf4a570105e1c8088a7fdbb2276f8ae6e81`.
The installed base APK's SHA-256 matches this artifact. APK license assets and signing were
verified. The final rebuild only added distribution notices relative to the executable used
for navigation/launch/performance checks; final install, cold launch and screenshot were
verified again. Source changes are local, uncommitted; nothing was published or pushed.

Local evidence: `artifacts/fireos/` (ignored because screenshots, app inventory and diagnostics
can be personal), including `launch-results.json`, `api-lint.txt`, `gfx-navigation.txt`,
`gfx-idle.txt`, `gfx-background-before.txt`, `gfx-background-after.txt`, settings snapshots,
`final-build-verification.json` and full-resolution PNG captures. Some foreground streaming
apps returned empty/black screenshots; their launch result is based on resumed activity,
not on a successful content screenshot.

## Follow-up — native Settings button (2026-10-23)

Fixed Settings to prefer `android.intent.action.MAIN` with
`android.intent.category.LEANBACK_SETTINGS`, falling back to `android.settings.SETTINGS`
if the first launch is unavailable or denied. The target advertises this category on exported
`com.amazon.tv.launcher/.ui.MainSettingsActivity`, the native Amazon Settings interface.
Generic SETTINGS instead default-matches a CTS dummy on this Fire OS build. No package names,
new permissions, Home routing changes or accessibility automation are needed for the fix.

Build, in-place update install, Home/Apps regression and native Settings launch/stability passed
on the physical device using ADB D-pad/Select events and full-resolution screenshots. The user
also confirmed native Settings opens with Select on the physical Fire TV remote; an additional
launch log and resumed-activity readback confirmed it. See DEVELOPMENT_LOG.md for resolution details.
The current APK supersedes the initial port artifact above; SHA-256:
`28e83e6086e2a20e7447dc6a87f90603a5ee268ec9950638bc58eb9539377e0e`.

## Milestone 6 — performance and stability follow-up

See [PERFORMANCE_REPORT.md](PERFORMANCE_REPORT.md) for repeatable two-run profiling, all
measurements, rejected experiments, traces, remaining bottlenecks and validation limits.

The original Java Views architecture, identity, native Settings shortcut and Home on Fire
routing are retained. Shape endpoint math is cached, competing animators are cancelled, focus
and interrupted view properties are restored, worker/callback ownership is bounded, and custom
indicator drawing stops while detached/hidden/paused. No reduced-motion mode, media grants,
usage ordering or screensaver changes were introduced. Both alternative layer policies were
rejected for CPU/deadline regressions; the original hardware-layer policy remains.

Fresh idle profiling reports 0% jank; navigation classification improves, but dock/grid still
show high reported jank and no general frame-rate improvement. Android frame timing and a
supported system trace identify substantial RenderThread/buffer waits. The strict zero-jank
navigation target remains unmet. No resolution, refresh or global animation setting was changed.
Twelve same-process app/Home cycles retained one activity/root and stable view counts; no
large memory-growth trend appeared in that bounded check. Background launcher frames stayed zero.

The milestone 6 APK is installed as an update and matches the original signer. Final SHA-256:
`e52cc175e7237f692795b66c99558b1891b375489f669d73bb4fd95c7ade34ee`.
A final cached-dock focus correction prevents an unwanted first-app retint on Settings return;
three Settings/Back cycles preserve the previous app palette and focused Settings tab.
Affected-workload reruns and the preceding full rendering matrix are distinguished in the report.
The user confirmed normal checked remote, Alexa, Spotify, Bluetooth and Wi-Fi behavior.

## UI refinements (2026-10-23)

The Fire OS fork now hides dock/grid tile captions while preserving app names for accessibility and the Home heading. Transparent-corner TV banners no longer expose the tonal card backing inside the focus ring; icon-only cards retain that backing. Navigation pills share a fixed 48 dp height and 24 dp radius across active and focused states, with focus indicated by color rather than scale. The UI remains Java Views with the same app ID, Home on Fire routing and native Fire TV Settings intent. The update built and installed on AFTKM; device screenshot and launch results are recorded in `DEVELOPMENT_LOG.md`. No new permissions, system changes or integrations were introduced.

## Milestone 7 — Preferences (2026-10-23)

A four-tab Java Views navigation now opens an internal Preferences page. Home order and Apps visibility are stored as package-name JSON arrays in app-private, versioned SharedPreferences (`HomePreferences.java`); they never change Android packages. First-run migration records the prior dock's six usage-ranked launchable apps once. The saved dock order then controls Home, while missing packages remain in saved slots and receive runtime-only replacements. Apps uses a locale Collator and package-name tie-break after filtering the independently saved hidden set. Draft edits support Save, Cancel, default restore, duplicate-slot swap, D-pad Move mode, Hide All confirmation and unsaved-change prompts. The old optional usage permission is retained solely for first migration and explicit Home default restoration; no access was granted automatically.

The update was built and installed on AFTKM with the isolated Fire OS script. Device screenshots and test results are in `DEVELOPMENT_LOG.md`; the package ID, native Amazon Settings intent and Home on Fire configuration remain unchanged. The Preferences page loads app metadata when opened, with no new continuous drawing or polling loop.

## Preferences visual redesign (2026-10-23)

The Fire OS Preferences page now uses the existing Java `tile()` renderer for a six-item Home row, a tiled replacement picker and a six-column alphabetical visibility grid. Short Select enters a reversible Move mode; holding Select opens a one-action popup above the focused tile. The picker includes only visible, eligible launchable apps. Hidden artwork is desaturated in place and remains focusable. The previous reset and bulk-visibility controls are absent. App-private preference storage, Home on Fire routing, the Amazon Settings shortcut, package ID and permissions are unchanged. See `DEVELOPMENT_LOG.md` for final-build device screenshots, remote confirmation, test limits and APK hash.

## Home-tab app management (2026-10-23)

The six Home dock tiles now distinguish short Select, which uses the original launch handler, from held Select, which opens a two-action Move/Replace popup above the measured tile bounds. Move edits a transient six-package draft and commits through the existing `HomePreferences` store on Select; Back cancels it. Replace opens a six-column picker filtered to installed, launchable apps marked visible. Replacing with an app already on Home swaps its two positions. Preferences and Home therefore share one saved order. Popup and key timers are cleared on pause; no continuous rendering loop, permission, package identity, Settings integration or Home on Fire configuration changed. Device evidence, physical-remote confirmation and final APK hash are in `DEVELOPMENT_LOG.md`.

## Home dock visual polish (2026-10-23)

The Home Move/Replace popup has smaller action pills and an elevated rounded window shadow. The Move badge is smaller and sits above the selected tile rather than over its artwork; it follows draft swaps. The replacement picker has enough edge and inter-tile space for the existing 1.10 focus scale and 3 dp ring, with content clipping disabled and a shadow behind the whole overlay. The Java Views renderer, app-private preferences, permissions and Fire OS integrations are unchanged. Build and on-device screenshot checks are recorded in `DEVELOPMENT_LOG.md`.

The follow-up based on `shot4.png` keeps the popup's outer dimensions while reducing its two action pills to 27 dp high and 12 sp text, with 9 dp vertical and 12 dp horizontal menu padding and an 8 dp action gap. Both focus states and action paths were checked on AFTKM; details and the installed APK hash are in `DEVELOPMENT_LOG.md`.

The Home popup's two labels are now centered inside their existing pills; no other popup layout or behavior changed. Both focus states were captured on AFTKM, as recorded in `DEVELOPMENT_LOG.md`.

## Weather widget v1 — supersedes the media/network audit above (2026-10-23)

The Fire OS fork now replaces the former upper-right Now Playing card with a compact Pune weather widget. The new `WeatherRepository.java` is the only network client. Its HTTPS request is `GET https://api.weatherapi.com/v1/current.json?key=<local build key>&q=Pune,India&aqi=yes`; it parses `location.name`, `current.temp_c`, `current.condition.text/code`, `current.is_day` and optional `current.air_quality.us-epa-index`. [WeatherAPI's air-quality documentation](https://www.weatherapi.com/docs/air-quality/) defines that index as US EPA categories 1–6, so the widget displays category names rather than a fabricated numeric AQI. `WeatherIcon.java` draws static condition marks locally.

The root `weatherapi.key` file is gitignored. `build.sh` can also read `WEATHERAPI_KEY`; it emits a diagnostic when neither is present. The key is embedded in a personal APK asset and is extractable from that APK, so publicly distributed builds would need a different credential architecture. At this weather milestone, the manifest requested `INTERNET` for WeatherAPI only; the later Settings panel added the optional SSID permissions documented below. The old `MediaListener` service declaration was removed without changing existing notification-listener settings. No system package, Home on Fire route or stock launcher was changed.

App-private SharedPreferences holds the last successful JSON and fetch timestamp. Fresh data suppresses requests for 30 minutes across process restarts; stale data displays immediately and is refreshed once while foregrounded, with at least five minutes between failed attempts. HTTP/API, connectivity and parse errors retain cached data or show `Weather unavailable` when no cache exists. `onPause` cancels an in-flight request and no weather animation or polling loop runs behind another app. Build, physical-device screenshots, cache/API-error probes, navigation and bounded idle frame results are recorded in `DEVELOPMENT_LOG.md`.

The weather widget's content is now right-aligned with the top tab group using the same 48 dp page inset and 6 dp tab-bar padding. The area height, data behavior and icon-to-label spacing remain unchanged. A full-resolution AFTKM screenshot and installed APK hash are recorded in `DEVELOPMENT_LOG.md`.

## Premium weather range and lifecycle refresh (2026-10-24)

The Fire OS weather widget now uses [WeatherAPI's forecast endpoint](https://www.weatherapi.com/docs/forecast-api/) over HTTPS with `q=Pune,India`, `days=1`, and no AQI or alerts. `WeatherRepository.java` reads `current.temp_c` and the local day's `forecast.forecastday[].day.mintemp_c`/`maxtemp_c`; it matches `forecastday.date` to [the API location's `localtime`](https://www.weatherapi.com/docs/location-object/) before accepting the response. The source key still comes only from the gitignored local file or build environment. The old current-only cache remains displayable as a fallback until a valid forecast is fetched. Invalid JSON, missing values, HTTP/network errors and day mismatches never replace the last valid cache.

`WeatherPolicy.java` compares persisted wall-clock fetch time and the `Asia/Kolkata` date to a 15-minute freshness limit, including a midnight boundary and backward clock changes. `HomeActivity.java` owns one visible-only Handler callback, schedules its next check for the freshness or local-midnight boundary, and removes it in `onPause`. `WeatherRepository.java` permits one background request at a time and uses elapsed realtime for 15-minute failed-attempt throttling. There is no service or polling while Home is inactive. A rejected response at midnight can leave yesterday's cached range visible until the next allowed attempt; a valid new-day response replaces it.

`TemperatureRangeView.java` draws a 3.5 dp rounded track, muted remainder, palette-derived warm fill and 9 dp dot with subtle halo. Its position is the clamped double-precision `(current-min)/(max-min)` result; labels use rounded integers. The top row places the icon and temperature at opposite ends of a 220 dp span with equal flexible gaps around Pune. The range row uses the same width and right alignment. No condition or AQI text, network permission, location permission, service or package integration was added. Build, deterministic policy checks, a scaled active-refresh test, live midnight rollover screenshots, navigation regression and the installed APK hash are recorded in `DEVELOPMENT_LOG.md`.

A reset 10-second idle `gfxinfo` sample on the final installed build reported 611 frames, 0 classified janky frames, p50/p95 6/12 ms and no missed Vsync or frame deadlines. This does not replace a navigation or long-term performance profile.

## Apps tab inline visibility editing (2026-10-24)

The Apps page now shows app names beneath its existing tiles and a tune button at the right of “All apps.” In normal mode it still filters by the package-name hidden set in `HomePreferences.java`. In edit mode it enumerates the same installed launchable apps without that filter, greys hidden artwork and overlays a local `ic_visibility_off.xml` vector badge. Select toggles only a draft hidden set; it never changes Android package visibility or launches an app in edit mode. Closing the editor with the tune button or Back, or navigating away via Home, Apps, Preferences, Menu, native Settings or activity pause commits the draft through the existing app-private preferences store. The Fire OS package ID, stock launcher, Home on Fire routing, weather client and permissions are unchanged. Build and physical-device screenshots, persistence checks, Settings navigation and app-launch regression are recorded in `DEVELOPMENT_LOG.md`.

The Apps follow-up reduces the tune button from 48 to 38 dp. Back while visibility editing now saves and returns to the normal Apps grid on the same tab; Back from normal Apps retains the original Home navigation. Focus-driven scrolling uses the focused tile's full holder height, including its label, plus an inset for the scaled focus ring, so the focused row remains visible at the viewport's top and bottom. This is a Java Views-only UI change with no new permission or service.

## Fire TV Settings side panel (2026-10-24)

The Settings tab opens a right-side, dimmed Java Views dialog with Wi-Fi, Bluetooth and All settings rows. The panel uses the existing palette and D-pad focus styling; Back dismisses it to the previous Home or Apps page. Opening it while editing Apps visibility saves the draft first. Its in/out window animations use the existing 500 ms default and 350 ms fast spatial motion curves. No launcher, Home on Fire or system package configuration is changed.

On AFTKM with Fire OS 8.1.8.5, `android.settings.WIFI_SETTINGS` resolves to the exported `com.amazon.tv.settings.v2/.tv.network.NetworkActivity` and opens the native Network screen. The Fire OS `android.settings.CONTROLLERS_SETTINGS` action resolves to the exported `com.amazon.tv.settings.v2/.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity` and opens Controllers & Bluetooth Devices, from which Other Bluetooth Devices is available. The standard `android.settings.BLUETOOTH_SETTINGS` action did not resolve on this device. All settings uses `MAIN` + `android.intent.category.LEANBACK_SETTINGS`, which resolves to `com.amazon.tv.launcher/.ui.MainSettingsActivity`; `android.settings.SETTINGS` remains its fallback. Each route catches unavailable or non-exported destinations, tries its fallback, and logs when none launches. Wi-Fi and Bluetooth ultimately fall back to the Settings home page if their own destinations fail.

The panel reads the current Wi-Fi SSID through Android's Wi-Fi APIs. This requires normal `ACCESS_WIFI_STATE`/`ACCESS_NETWORK_STATE` permissions and optional foreground `ACCESS_FINE_LOCATION` on this Fire OS build. The user explicitly requested the location prompt and granted “While using the app”; the actual SSID then appeared. Denial or redaction leaves a clear unavailable label. Location is not used by the fixed-city weather request, and no new network request is made. Bluetooth audio names come from connected `AudioDeviceInfo` outputs, with an active D-pad remote name as a fallback; the panel does not request Bluetooth scanning or pairing permissions. User-visible confirmation, build/install evidence and test limits are recorded in `DEVELOPMENT_LOG.md`.

## Three-tab navigation and Settings spacing (2026-10-24)

The top strip now contains Home, Apps and Settings. The Preferences tab was removed from the strip; its stored Home order and app-visibility data remain intact, and those controls are available through the Home tile menu and Apps tune button. The Settings dialog's return-focus index now points to the third tab. Its Wi-Fi, Bluetooth and All settings rows have equal 12 dp gaps. No Settings destination, package identity or permission changed. Final AFTKM screenshots and build/install results are in `DEVELOPMENT_LOG.md`.

## Compact weather details (2026-10-24)

The Home weather widget replaces the drawn temperature range with two text rows: `[condition icon] Pune | current °C` and `Feels like °C | daily low° - high° | rain icon daily rain chance%`. Both rows wrap their content and remain right-aligned with the tab group. Fixed margins keep the main icon, city and temperature separator compact. The rain mark reuses the local `WeatherIcon` renderer with a rain condition, so no image or animation is downloaded.

The existing forecast response already includes the necessary fields: [WeatherAPI's realtime object](https://www.weatherapi.com/docs/realtime-api/) supplies `current.feelslike_c`, and [its day object](https://www.weatherapi.com/docs/forecast-api/) supplies `forecastday.day.daily_chance_of_rain`. The latter is the day's probability rather than an hourly `chance_of_rain`. `WeatherRepository.java` reads both from the same cached/fetched JSON, validates the probability to 0–100, and displays `--` placeholders when an older cache lacks either value. The request URL, 15-minute refresh policy, fixed Pune query, permissions and Home-only visibility are unchanged. `TemperatureRangeView.java` and its historical policy tests remain in source but no range bar is instantiated. Physical-device captures and APK checks are recorded in `DEVELOPMENT_LOG.md`.

## Automatic location and keyless weather — supersedes fixed-city sections above (2026-10-24)

`IpLocationProvider.java` now requests `https://ipdata.free/` from the ordinary application UID using Android `HttpURLConnection`. It validates the returned city, two-letter country code, finite coordinates and IANA timezone. The five location fields and successful lookup time are saved in app-private `SharedPreferences`; no public IP, device ID or account data is saved or logged. Location refreshes after about 24 hours, and a debounced default-network change requests an earlier recheck. A location with the same city, country and timezone and less than 25 km coordinate drift keeps the existing weather identity. A material change invalidates the old-location weather cache before displaying the new city. Failed lookups keep the last valid location and use bounded retry delays. Without any valid location, Home shows `Detecting location…`, then `Weather unavailable` after a failed lookup; it never substitutes Pune.

`WeatherRepository.java` now requests the keyless [Open-Meteo forecast API](https://open-meteo.com/en/docs) with the detected latitude, longitude and timezone. It reads current temperature, apparent temperature, WMO weather code and day flag; matches today's min/max to the returned local `current.time` date; and selects precipitation probability for the matching current local hour, not the first hourly entry. Missing or null probability displays `--%`. Successful responses are cached under a key derived from the chosen location. Weather freshness remains 15 minutes or local midnight, whichever comes first. Location and weather failures have separate bounded exponential backoff; the visible Home activity owns all refresh scheduling, and `onPause` cancels pending work. Both HTTPS clients use 5-second connection/read timeouts, reject redirects and cap response size. `WeatherIcon.java` maps WMO codes to the existing local drawable style. The compact two-line widget now displays `City, CC` and current-hour rain chance. No key is needed or bundled; `build.sh` no longer copies the historical `weatherapi.key` asset.

The existing optional `ACCESS_FINE_LOCATION` permission remains solely for the Settings panel's Wi-Fi SSID, as the user requested. Weather does not call Android location APIs or request that permission. Geolocation is approximate: the service sees the device's public IP, and an ISP gateway or VPN may identify a different city. Both public services may change availability or impose limits; see [ipdata.free](https://ipdata.co/free) and [Open-Meteo terms](https://open-meteo.com/en/terms). Historical WeatherAPI/Pune entries above document prior builds and no longer describe the current APK. The Fire Stick connectivity probe, final APK/device checks and remaining test limits are in `DEVELOPMENT_LOG.md`.

## Icon-only focus corners (2026-10-24)

The shared `HomeActivity.tile()` renderer now omits the colored elevation shadow for icon-only app cards. That shadow had appeared as color patches inside the rounded focus-ring gap on apps without full-bleed banners. The change covers Home, the replacement picker and Apps while preserving focus scale, ring and palette animation. Banner cards keep their glow. AFTKM before/after captures and build results are recorded in `DEVELOPMENT_LOG.md`.

## CleanFire Launcher branding (2026-10-24)

The Fire OS fork's application and Leanback launch activity are labeled **CleanFire Launcher** and reference `cleanfire_icon.png` and `cleanfire_banner.png` from `res/drawable-nodpi/`. The separate screensaver label is `CleanFire Launcher Clock`. The custom `build.sh` compiles the whole `res/` tree and packages both resources; its signing identity, APK path, package ID and Java component names are unchanged. Fire OS App Details shows the new label and square icon, and `aapt2 dump badging` confirms the banner on the application and Leanback activity. Fire TV's app library retained the old name and icon after an update and reboot; a data-preserving `pm uninstall -k` / streamed install / reboot refreshed its entry. The stock "Your apps and channels" card now shows the new name and square icon, but that surface does not visibly use the separate wide banner. Verification details and the installed APK hash are in `DEVELOPMENT_LOG.md`.

## Public release preparation (2026-10-24)

Version 1.0.0/code 184 separates the historical debug build from a locally signed release build. The release certificate differs from existing debug installations, so the APK cannot update the AFTKM test installation in place; no current user app was removed. The old upstream install scripts and unused orange/gradient branding resources were removed from this fork after a successful rebuild. A real 1920×1080 Home screenshot and release review are in `docs/images/home.png` and `RELEASE_CHECKLIST.md`. Provider attribution is in the README beside that screenshot; the device widget remains two lines by user request. The source remains API 30 Java Views and keyless HTTPS weather; the optional Android location permission still serves only the Settings Wi-Fi SSID. Physical debug-build checks, signature/manifest evidence, and unresolved D-pad jank are recorded in `DEVELOPMENT_LOG.md`.
