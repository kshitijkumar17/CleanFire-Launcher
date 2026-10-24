# Development log

## 2026-10-23 — Milestone 1: audit complete

- Inspected all launcher Java files, manifest, resources, vendored color library, build,
  install and uninstall scripts; working tree initially clean at `eccdc92`.
- Added `FIREOS_PORTING.md` with architecture, API findings, permissions and planned changes.
- Generated upstream R and compiled **all** upstream Java and vendor code with JDK 17 against
  SDK 30 in `/tmp/material-fireos-audit`: passed (deprecation notes only). No API 31+ replacement needed.
- Toolchain: SDK platforms 30/35/37 available; build tools 35.0.0/36.0.0 available; JDK 17.
  Upstream's default SDK/build tools 34 are absent.
- Connected physical device reports API 30, Fire OS 8.1.8.5. Existing Home on Fire accessibility
  registration observed; no settings changed. Both launcher categories contain installed apps.
- Build/install not yet performed. Visual, navigation and optional integration tests pending.

## 2026-10-23 — Milestone 2: minimal port built and installed

- Changed `AndroidManifest.xml`: application ID `dev.karat.materialhome`, label Material Home,
  explicit original Java component names; removed Home intent filter and accessibility registration.
- Changed `build.sh`: SDK 30 compilation, min SDK/D8 API 30, existing target 34, installed build
  tools 35.0.0 default, output `build/material-home-fireos.apk`.
- Changed `HomeActivity.java`: shared deduplicated discovery across both launcher categories,
  HDMI disabled, null launch intent/media manager guards, Back exits Home and Apps Back returns Home.
- Added `install-fireos.sh` / `uninstall-fireos.sh`: explicit ADB serial, isolated fork operations,
  no system settings, sensitive grants or Home routing changes.
- Build and APK signing verification passed. aapt2 verified identity, min 30, target 34 and only
  optional PACKAGE_USAGE_STATS permission. NewApi/InlinedApi lint passed (no issues).
- Installed via `install-fireos.sh` on physical API 30 Fire OS 8.1.8.5 stick: Success.
  `am start -W`: Status ok; activity resumed; first launch wait 1503 ms.
- Initial full-resolution screenshot: 1920 × 1080, original dock, typography, app artwork,
  focused label/ring and app-colored background rendered. No UI rewrite.
- Device captures/settings baselines stored under ignored `artifacts/fireos/`.
- Remaining: complete navigation, visual/performance, launch/return and isolation verification.

## 2026-10-23 — Milestone 3: visual checks passed; performance acceptance open

- Changed `HomeActivity.java`, `EqualizerBadge.java`, `WavyProgress.java`: track transient
  value animators, cancel view animation callbacks on pause, suspend custom frame scheduling;
  preserve dock focus on resume; make shape indexing safe for all package-name hashes.
- Physical captures cover six dock states, three navigation tabs and six Apps/scroll states.
  Inspected full-resolution captures: original layout, palettes, shape variants, clock/font,
  banner/icon fallbacks and remote focus navigation rendered successfully.
- Rebuilt/reinstalled executable changes: signing/build passed; cold launch Status ok,
  TotalTime 1296 ms / WaitTime 1308 ms. API lint with explicit manifest min SDK 30 passed.
- Apple TV foreground/background check: launcher gfxinfo total frames remained 0 across
  five seconds, confirming no continuous Home rendering in that check.
- Navigation gfxinfo: 3063 frames, 2951 janky (96.34%), p50 18 ms / p95 30 ms, 23 missed
  frame deadlines. Independent 12-second idle sample: 726 frames, 537 janky (73.97%),
  p50 17 ms / p95 19 ms. **Zero-jank requirement failed; do not call this performance-ready.**
- UIAutomator dump could not obtain idle state while continuous upstream drift ran; used
  real D-pad input and full-resolution screenshots instead. No accessibility service enabled.

## 2026-10-23 — Milestone 4: investigation complete, optional enablement deferred

- Read-only checks: usage app-op remains default/rejected; notification permission absent;
  original Amazon screensaver remains selected and DreamManager exists.
- Notification-listener settings and dream-settings intents have no activity handler.
  Usage-access settings resolves to an Amazon CTS dummy activity, with no usable consent
  flow verified. No grants or system settings writes performed.
- Now Playing, actual usage-based ordering and custom dreaming remain untested and optional.
- Added `NOTICE`; updated `build.sh` to include attribution/licenses/provenance in APK assets.
  Preserved original LICENSE/vendor/font notices; README now points fork users to safe setup.
- `.gitignore` excludes local device screenshots and diagnostics under `artifacts/`.

## 2026-10-23 — Milestone 5: physical functional checks passed; performance open

- D-pad Select launched Apple TV from dock, Netflix from Apps, and Fire TV's YouTube from
  Apps; each application's expected activity became resumed (`launch-results.json`).
  No content playback, login, purchase or DRM test was performed.
- Reopening Material Home after each switch returned a functional original interface.
  A Back press within Apple TV stayed in Apple TV; this is app-controlled behavior.
- Pressing Home returned to existing Karat Home. Compared before/after secure settings:
  accessibility list/enabled, notification-listener list, screensaver selection/enabled
  unchanged. HOME resolver unchanged. No entry for the fork in crash-buffer log.
- Final notices-only rebuild and installation passed; cold launch Status ok, TotalTime
  1283 ms / WaitTime 1295 ms. Verified final resumed activity and full-resolution screenshot.
- Final artifact `build/material-home-fireos.apk`: 4,081,327 bytes; SHA-256
  `887887b04fcf20635469e1594f9d2bf4a570105e1c8088a7fdbb2276f8ae6e81`.
  Installed base APK hash matches. Signature verification and all license asset checks passed.
- `bash -n` on build and isolated Fire OS scripts, API lint and `git diff --check` passed.
- Remaining limitations: foreground zero-jank acceptance failed; hardware handset feel/repeat,
  optional services, real usage sorting and screensaver rendering untested. No Home routing
  configured for the new app. Some streaming-app screenshot captures were empty/black;
  launch assertions use resumed activity records. No upstream scripts/root/privileged grants.
- Changes remain local; no commits, PR, release, stock app removal or launcher configuration changes.

## 2026-10-23 — Settings button fix for Fire OS 8

- Inspected the nav callback and generic `launch()` helper. The callback used only
  `Settings.ACTION_SETTINGS`; the generic helper catches launch exceptions but cannot
  identify a successfully started dummy activity. Its application-launch behavior is unchanged.
- Reproduced on the physical Fire TV using D-pad/Select events and Logcat. Actual activity
  dispatch (uid 10266, Material Home) was `android.settings.SETTINGS` to
  `com.amazon.tv.settings.v2/.compliance.CTSDummySettingsHandler`. Logs then show a Home
  transition, Amazon MainSettingsActivity, and an existing Home-service relaunch of Material
  Home. The original Settings selection did not stay on the native Settings UI.
- Resolution diagnosis: querying without MATCH_DEFAULT_ONLY lists exported
  `com.amazon.tv.launcher/.ui.SettingsActivity` with priority 100. Its installed manifest
  filter lacks CATEGORY_DEFAULT. Real implicit activity starts require default matching;
  `resolve-activity --query-flags 65536 -a android.settings.SETTINGS` instead selects
  the CTS dummy. Neither MAIN/LAUNCHER nor MAIN/LEANBACK_LAUNCHER exposes a front door in
  `com.amazon.tv.settings.v2`.
- Inspected the installed Amazon launcher APK's manifest, read-only: native Settings entry
  `com.amazon.tv.launcher/com.amazon.tv.launcher.ui.MainSettingsActivity` is explicitly
  exported and has no activity permission guard. It advertises ACTION_MAIN with DEFAULT
  and `android.intent.category.LEANBACK_SETTINGS`. Its Amazon MAINSETTINGS action lacks
  DEFAULT, so prefer the advertised TV Settings category for implicit launches.
- Changed only the Settings action in `src/com/rampey/materialtv/HomeActivity.java`:
  `openSystemSettings()` tries ACTION_MAIN + LEANBACK_SETTINGS first, then ACTION_SETTINGS.
  Uses the category string because SDK 30 does not expose a public Intent constant for it.
  No device/package/component names are hardcoded. Each attempt catches ActivityNotFoundException
  and SecurityException, logs failures with tag `MaterialHome`, and a final error is logged
  if neither request can be launched. Successful dispatch is logged too.
- Home/Apps callbacks, generic application launch helper, UI, manifest and permissions are
  unchanged by this fix. No root, accessibility automation, grants or system-package changes.
- `./build.sh`: passed after replacing the unavailable SDK constant with the advertised category
  string. APK signature verification passed. `./install-fireos.sh` used `adb install -r`:
  Success; cold start Status ok, TotalTime 1380 ms / WaitTime 1392 ms.
- Physical-device navigation regression test used **ADB-injected** D-pad/Select events:
  Home selection returned the dock; Apps selection displayed the original grid; Settings
  opened the native Amazon screen with Network, My Fire TV, Applications, Display & Sounds,
  etc. MainSettingsActivity remained resumed across a further five-second observation.
  Logcat confirms ACTION_MAIN + LEANBACK_SETTINGS launched from Material Home's uid.
  Reopened Material Home successfully and focused Settings for the hardware-remote check.
- Inspected 1920 × 1080 screenshots of all three nav focus states, Home and Apps results,
  and native Settings. Evidence: ignored `artifacts/fireos/settings-fix/`, including
  `logcat-reproduction.txt`, installed-manifest dump, `logcat-fixed.txt`,
  `navigation-results.json`, PNG captures and `api-lint.txt`.
- SDK 30 NewApi/InlinedApi lint: no issues. `git diff --check`: passed.
  Updated APK `build/material-home-fireos.apk` SHA-256:
  `28e83e6086e2a20e7447dc6a87f90603a5ee268ec9950638bc58eb9539377e0e`;
  installed base APK hash matches. This supersedes the earlier port artifact hash.
- Physical Fire TV remote test: user pressed Select on the focused Settings tab and confirmed
  “Yes, native Settings opens.” An additional MaterialHome launch log at device time 01:38:23
  and a subsequent resumed-activity readback independently confirmed MainSettingsActivity.
  This completes the requested physical-handset check; the earlier automated test remains
  explicitly labeled ADB input. Fallback/error branches are implemented and reviewed, not
  forced on the TV, as that would require changing available system destinations.

## 2026-10-23 — Milestone 6: profiling, animation ownership and return stability

### Baseline and diagnosis

- Reviewed HomeActivity, CookieDrawable, Motion, Focus, ButtonGroup, ShapeScene, Screensaver,
  WavyProgress and EqualizerBadge; inventoried ValueAnimator/ObjectAnimator/view animations,
  Handler callbacks and display-frame invalidation. No screensaver feature changes made.
- Added `tools/profile_fireos.py` and `PERFORMANCE_REPORT.md`. Two screenshot-free runs of
  idle, dock sweeps, Home/Apps/Settings, grid scrolling and YouTube/Home returns captured
  gfxinfo reset + framestats, process CPU and PSS. Raw evidence stays under ignored artifacts.
- Fresh 15-second idle baseline: 0% jank in both runs, ~24.35% one-core CPU. Dock/grid retain
  high framework jank classifications. UI work is sub-millisecond at median; RenderThread
  queueing, buffer waits and morph-related hardware-layer work dominate measured latency.
- Captured separate supported atrace scheduling/gfx/view traces; some frequency/trace-marker
  permission warnings appeared, but valid app and scheduler events were recorded without root.
  See the report for bounded-tail timing interpretation and the GPU timestamp limitations.

### Implementation and experiments

- `CookieDrawable.java`: cache polar outline endpoints; interpolate the original polygon
  vertices per frame, avoiding repeated trigonometry without changing shape sampling/design.
- `HomeActivity.java`: cancel superseded shape/title/corner/ring animators, continue ring alpha
  correctly during rapid reversals, keep ring/card corners concentric, reuse gradient colors,
  avoid redundant same-app morphs, reset interrupted view properties offscreen, preserve focus
  on resume/new intent, clean up detached tiles, bound/cancel palette warmup, clear loader
  references and use elapsed-time loader rotation, reset arrival start delays.
- `EqualizerBadge.java` / `WavyProgress.java`: remove two arrays allocated per equalizer draw;
  require attachment/visibility/foreground for recurring drawing and reset progress timebase.
  No media capability, grants, usage ordering or screensaver work introduced.
- Tested two layer-policy alternatives. Direct paths substantially reduced peak PSS but
  doubled idle CPU and worsened missed deadlines; caching only at rest also worsened
  deadlines. Rejected both. Original continuous hardware-layer policy is retained.
- The original Motion, Focus, ButtonGroup, ShapeScene, Screensaver, Settings implementation,
  generic app-launch helper, manifest, package ID and signing key are unchanged by this milestone.

### Performance and stability evidence

- Full untraced two-run comparison is in PERFORMANCE_REPORT.md. Mean navigation classification
  improved ~75.02%→52.62%; idle stays 0%. Dock/grid did not show a general frame-rate gain;
  dock CPU/deadline counts increased modestly. Do not claim zero-jank or a global FPS improvement.
- A separate traced sweep reduced median animation span 0.465→0.347 ms, with essentially
  unchanged upload counts and persistent buffer waits. This is a limited animation-work
  improvement, not a frame-rate cure or battery/power measurement.
- Controlled baseline/final `am start -S -W` from YouTube foreground: all six starts COLD;
  median TotalTime 847→784 ms. Baseline was temporarily reinstalled for comparison; final
  package restored using update installs throughout. No app data was cleared or uninstalled.
- Twelve alternating Netflix/YouTube + Home cycles retained the same PID, 1 Activity,
  1 ViewRootImpl and 271 Views. PSS after cycles 4/8/12: 67558/64729/62400 KB; no large
  growing retained-memory trend observed. This bounded test is not proof against every leak.
- Background check: 0→0 launcher frames over five seconds in YouTube, process CPU 0.582%
  of one core. After twelve returns, 15-second idle: 919 frames, 0.22% jank, p50/p95 5/7 ms,
  1 deadline miss, 20.69% one-core CPU; no runaway callback workload appeared.
- Exercised a 23-event rapid key burst, all six dock focus positions, Home/Apps/Settings,
  scrolling, Menu/Back, Apple TV Select and Home return. Reviewed full-resolution captures.
- Screenshot review found a final focus-condition bug: cached dock refresh synthesized first
  tile focus even when a nav tab retained focus, causing a redundant retint after Settings.
  Changed that fallback to run only when there is no current focus. Follow-up checks below.

### Final focus fix, device acceptance and deliverable

- Follow-up cached-dock condition installed and tested: navigation reruns jank 52.49/45.79%,
  p95 19/19 ms, 4/0 missed deadlines; app-return reruns 8.33/54.66%, p95 40/23 ms, 11/5
  misses. Phase-sensitive results are reported as measured, without a universal FPS claim.
- Three additional native Settings→Back cycles preserve the Settings focus plus Apple TV
  title/palette. The original Settings method and generic launch helper remain byte-for-byte
  unchanged relative to this milestone's starting source snapshot.
- Full rendering/stress/controlled-start results used preceding rendering-test APK
  `a2f674262983180bff3f8008f9ce5c5c0a48c6d513b2619ad6d29e8c96f544b3`;
  final APK differs only in that cached-focus predicate and has the affected-workload reruns.
  PERFORMANCE_REPORT.md distinguishes the tested artifacts and preserves all original results.
- User confirmed all checked handset functions work normally, covering rapid D-pad, Alexa,
  Spotify, Bluetooth remote/audio and Wi-Fi streaming. Read-only dumps confirm Wi-Fi connected
  and Bluetooth enabled/connected. This user confirmation is not a measured audio/voice test.
- Before/after snapshots match for accessibility, notification-listener, screensaver state,
  global animation scales and display mode. Home on Fire routing still reaches Material Home.
  No stock package, Home on Fire configuration, system permission or display setting changed.
- Final build/update/signing/installed-hash verification passed. APK size 4,085,423 bytes;
  SHA-256 `e52cc175e7237f692795b66c99558b1891b375489f669d73bb4fd95c7ade34ee`.
  Same signer certificate as baseline, package ID/min API/target unchanged. API lint and diff
  validation passed. Source changes remain local; no commit, PR, upload or publication.
- Remaining: dock/grid pipeline jank and variable buffer timing; high foreground graphics
  cache PSS; long-term leak/soak testing beyond the bounded cycles; unmeasured photon-level
  input latency and power. Optional integrations remain outside this milestone.

## UI refinements — app focus, tile labels and navigation tabs (2026-10-23)

- Files changed: `HomeActivity.java`, `ButtonGroup.java`, this log and `FIREOS_PORTING.md`.
- Physical screenshot diagnosis: Apple TV's banner contains transparent pixels at its rounded corners. The card's `palette.containerHigh` backing showed through them as small colored wedges inside the focused ring. Banner cards now use a transparent backing; icon-only cards keep their tonal background and the existing clipped rounded outline, glow and focus motion.
- Removed the per-tile caption view and its focus animations from both dock and grid. App names remain in app data, content descriptions and the existing large Home heading. The grid's bottom margin is reduced from 16 to 10 dp. The dock status row is gone while its marks are hidden, so no fixed text-height gap remains; active marks still appear below the tile.
- `ButtonGroup` now gives each tab a fixed 48 dp pill height with unchanged width and 24 dp radius. Focus changes colors only; the former 1.05 scale is removed. Selected and focused pills have the same measured bounds, with stable icon and text alignment.
- `./build.sh` passed. Updated APK installed with `adb install -r`; SHA-256 `a4817852b39d5d0797d2b9626804ad3cc816f9329e8fac530f851982dd143284`.
- Tested on AFTKM: captured all six dock positions, first five Apps columns and three grid rows at 1920×1080 under ignored `artifacts/fireos/ui-refinement/`. Reviewed Apple TV and grid focus screenshots: no colored corner wedges, clear ring, no captions or leftover label row. Captured Home/Apps/Settings focused pills; colors differ as designed while geometry stays fixed. Native Settings opened, Back returned, an Apps-grid app (AirScreen) and a dock app (Plex) launched, and Home on Fire returned to Material Home. Crash buffer had no fork entry; `git diff --check` passed. No extra animation loop or permission was added.
- Remaining: the large Home heading still shows the focused app name by design. Live media/HDMI status marks were not activated during this visual check. No new frame-rate claim is made.

## Milestone 7 — Preferences and app customization (2026-10-23)

- Changed `HomeActivity.java`, added `HomePreferences.java` and `res/drawable/ic_tune.xml`; updated this log and `FIREOS_PORTING.md`. Existing Java Views, package ID, signing, Settings intent, Home on Fire route and Milestone 6 animation ownership remain intact.
- Four-tab nav adds Preferences between Apps and Settings. Its native ScrollView page uses the existing palette, font, animated background and fixed-height focus pills. Six numbered slots show installed icons and names; Select offers an alphabetic app picker or Move. Selecting an already pinned app offers a swap. In Move mode Left/Right swaps adjacent slots, Select confirms, Back restores the pre-move draft.
- `HomePreferences` stores schema version, six package references, hidden-package set and migration marker as JSON arrays in app-private SharedPreferences; one editor commit writes both settings. Initial migration captures the previous usage-ranked dock order, deduplicates, fills from installed launchable apps and saves it once. Afterward the dock reads saved order; missing package references remain saved, are named in Preferences and receive runtime-only installed-app replacements. Restore Home Defaults deliberately recomputes the original automatic selection. The optional usage permission remains because initial migration and explicit default restoration still use that code; no access grant was changed.
- Apps visibility is independent from Home selection. All launchable apps remain listed in Preferences with icon, name and Visible/Hidden state. The Apps grid filters hidden packages and sorts visible labels with locale Collator PRIMARY plus package-name tie-break. New packages appear visible by default, saved hidden-package references survive uninstall/reinstall. Show All, confirmed Hide All and confirmed defaults apply to the draft only until Save. Back/Menu and tab departures offer Save, Discard and Continue Editing for dirty drafts. Preferences remains reachable via its nav tab even if all Apps entries are hidden.
- Built with `./build.sh` and installed as an update using `./install-fireos.sh <adb-serial>` on AFTKM. On-device full-resolution captures are under ignored `artifacts/fireos/milestone7/`. Verified initial six match the prior dock; Preferences tab and page open; Move right swaps slots and Back cancels; a saved swap survives a process restart and was then reversed and saved, restoring the original dock order. Hid AirScreen in the Apps draft, saved and restarted: it disappeared from Apps while remaining in Home slot 6; unhid and saved, restoring its alphabetical first position. Native Settings still resumed `com.amazon.tv.launcher/.ui.MainSettingsActivity`; Back returned to Material Home. No app package was uninstalled or changed.
- The missing-app slot was implemented and compiled but was not exercised end-to-end on the live device. No benchmark improvement is claimed; Preferences metadata loads on entry and views rebuild only on edits, with no new frame loop. Performance and long-running memory should be profiled again if the installed-app list becomes very large.
- Additional physical checks on the final build: opened the alphabetic picker and selected an already pinned app; the swap dialog identified both slots. Applying that swap to the draft and choosing Discard left the saved dock unchanged. Confirmed Hide All marks Apps entries hidden in the draft, then discarded it. No live package was uninstalled or hidden at the Android level.
- Final picker check: replacing slot 1 with a non-pinned launchable app updated the draft slot; Discard restored Plex. Final APK SHA-256 `29692cc1c9126fc7fe0519e388a08f936593ecbdd861c43bc15e6f496d01bbed`; final update install passed. `git diff --check` passed, launcher resumed after testing, and the device crash buffer had no fork entry.

## Preferences page visual redesign (2026-10-23)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. The page title/subtitle and the old numbered Home rows, centered action dialog, Restore Home Defaults, visibility list and three bulk actions were removed. The page now starts with Home Apps, followed by App Visibility. The four-tab bar, clock, palette, background motion and saved-preference model remain.
- Home Apps reuses the Home dock's `tile()` renderer and computed six-tile width in one horizontal row, with artwork, shape, ring and focus motion shared with Home. Short Select is deferred to key release and enters Move; Left/Right swaps adjacent package IDs, Select saves, and Back restores the pre-move list. A cancellable long-press timeout plus Android key-repeat/long-press events opens a compact, anchored Replace popup without entering Move. Pending callbacks are removed on focus loss, view detach, rebuild and pause.
- Replace opens a focusable, scrollable tiled picker with locale-sorted visible launchable apps, excluding apps in the other five slots. Its height follows row count up to the screen-safe cap; Back leaves the slot unchanged. App Visibility uses the same tile renderer in six columns, including hidden apps in alphabetical position. Select changes only the focused tile's artwork to/from a desaturated, reduced-alpha state; its focus and accessible name/state remain. Save/Cancel and the existing unsaved-change prompt still govern visibility drafts; a confirmed Home move or replacement saves immediately.
- Built using `./build.sh`, installed with `adb install -r` on AFTKM, Fire OS 8.1.8.5. The user confirmed on the physical Fire TV remote that holding Select opens Replace without Move and that the picker opens and navigates with the D-pad. ADB key events verified short Select, an actual Right swap, Back cancellation, the long-press repeat path, picker Back, visibility toggle in place with focus retained, multirow scrolling, Apps navigation, native Amazon Settings (`com.amazon.tv.launcher/.ui.MainSettingsActivity`) and Home-key return via the existing Home on Fire route. Full-resolution device captures are under ignored `artifacts/fireos/preferences-redesign/`.
- Earlier test actions had changed the device's saved Home order and hidden set. A temporary, update-only recovery path restored the six apps recorded before the redesign test (Plex, Home on Fire, Karat Home, Projectivy Launcher, Apple TV, AirScreen) and cleared the test hidden set. That path was removed from final source and APK. Final Home capture after a process restart shows those six in order, and Apps again shows AirScreen. No app package or system setting was changed.
- Final APK SHA-256 `d68a991b8f681a58078c2a1cb4ef93dbb77a8d143302489e9e8602e33ad66b2c`; the installed base APK hash matches. Final update install, `git diff --check` and crash-buffer check passed. A replacement of Plex with Alexa Smart Home changed only slot 1 and survived a process restart; replacing that slot with Plex restored the original order. Saving AirScreen as hidden removed it from Apps while retaining it in the Home dock; the gray Preferences tile survived restart; saving it visible again restored it as the first Apps tile. No new permission, dependency or animation loop was added. No new quantitative frame-time comparison was made.

## Home-tab long-press app management (2026-10-23)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. Only Home dock tiles receive a Select key recognizer. A short `DPAD_CENTER`/`ENTER` press is held until key-up, then invokes the existing tile click/launch path. A cancellable long-press timeout or Android long-press/repeat event opens the context menu; the matching key-up is consumed. Focus loss, view detach and Activity pause cancel pending timers. Apps-tab tiles keep their original handling.
- Explicit `NORMAL`, `CONTEXT_MENU`, `MOVING` and `REPLACEMENT_PICKER` states own the transient Home interaction. The two-action popup is centered above the focused tile from its measured screen bounds, clamped inside the side safe area, narrower than the tile, and separated by 10 dp. It uses the focused app's dynamic surface colors, compact pills and elevation; Back restores focus. Move overlays a centered two-way mark, swaps neighboring package IDs in a draft only, ignores out-of-range/repeated directional events, and commits to the existing `HomePreferences` store only on Select. Back/Menu cancel. Menu/picker state is dismissed on pause or page change.
- Replace uses a six-column scrollable overlay of installed, launchable, visible, deduplicated apps sorted by locale-aware label with package-name tie-break. Selecting a new app replaces exactly one slot. Selecting an app already in Home swaps the two positions. Both paths save the existing Home order immediately and refocus the changed slot. Back leaves saved preferences untouched. A screenshot check exposed stale Home title/colors after picker cancellation; focus is now cleared and restored on the originating dock tile so its palette/title settle correctly.
- `./build.sh` passed and the APK was installed as an update on AFTKM. The user confirmed with the physical Fire TV remote that a short Select launches and a long Select opens Move/Replace without launching. ADB D-pad tests and full-resolution captures under ignored `artifacts/fireos/home-dock-management/` verified first/last tile popup positioning, both menu actions, Move's centered mark and focus-following swap, first/last boundaries, Select commit, Back cancellation, picker navigation/cancellation, new-app replacement, duplicate-app swap, Preferences synchronization, restart persistence, alphabetic Apps, native Settings and Home-key return. All temporary test reorder/replacement changes were reversed to the six-app order seen at the start of this milestone's final build.
- Final APK SHA-256 `069798216d99a5317b26f04c18f112a20a3bc2f7d4eb22740d184db618c9e435`; installed base APK hash matches. `git diff --check` and device crash-buffer check passed. No package ID, signer, permission, system setting or external launcher configuration was changed. No new quantitative frame-time benchmark was performed.
- The user also confirmed on the physical remote that Move → one shift → Back and Replace → picker focus movement → Back both return to the same six-app order without launching an app. A post-check device capture shows the six-app dock unchanged.

## Home dock popup and picker visual polish (2026-10-23)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. The Move/Replace popup now uses 35 dp action pills with smaller type and a rounded, elevated window background that casts a visible shadow. Its actions and anchor behavior are unchanged.
- The Move cue is a 30 dp badge positioned by the selected tile's measured screen bounds as a sibling of the dock scroll view. It sits above the focus ring with a clear gap, remains centered after a draft swap and leaves with Move mode; the artwork is unobstructed.
- The replacement picker reserves 24 dp inner padding and 8 dp tile margins, disables content clipping, and uses a rounded elevated window background. Focus rings remain fully visible at the first, second-row and right-edge tiles while the overlay casts a shadow.
- `./build.sh` passed, and the update installed on AFTKM with `adb install -r`. Full-resolution device captures are under ignored `artifacts/fireos/home-dock-polish/` (`menu.png`, `move.png`, `move-shift.png`, `picker-first.png`, `picker-second-row.png`, `picker-right-edge.png`, `restored-home.png`). ADB D-pad/Select/Back exercised opening the menu, entering Move, shifting once, cancelling, opening Replace, traversing the picker and cancelling. The saved six-app order was unchanged; no app was launched or replaced during these visual checks.
- Apps tab still opens and Back returns Home (`apps.png`, `final-home.png`); the Activity remained resumed. Final APK SHA-256 `8688674681c0606e719b476d1f7b47bce1ff16eb2f5df95759e7330fc4bfad03` matches the installed base APK. `git diff --check` passed, and the sampled device log had no launcher fatal exception. The updated visuals were verified through ADB on the physical Fire TV display; this pass did not repeat a human remote inspection or a frame-time benchmark.

## Home menu button spacing follow-up (2026-10-23)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md` after reviewing `shot4.png`. The outer popup remains 80 dp tall and retains its measured width, anchor and shadow. Move and Replace now use 27 dp-high pills, 12 sp labels, 12 dp horizontal and 9 dp vertical menu padding, plus 8 dp between actions.
- `./build.sh` passed and the APK installed as an update on AFTKM. Full-resolution device screenshots under ignored `artifacts/fireos/home-menu-spacing/` show both focused button states, Replace's picker, Move mode and the restored Home dock. ADB Select/Back traversed each action and cancelled without changing the saved six-app order. The sampled log contained no launcher fatal exception; `git diff --check` passed.
- Installed base APK SHA-256 matches local build: `a71b352554a3465f0385af6310a3d89630b6283c489df8e4d7e971258a7c19e7`. This pass used ADB key events and device screenshots; human remote inspection and frame-time benchmarking were not repeated.

## Home menu label alignment follow-up (2026-10-23)

- `HomeActivity.java` now sets `Gravity.CENTER` on the Home popup's Move and Replace buttons, overriding the shared preference button's left-aligned text. Button sizes, spacing, menu dimensions, colors and actions are unchanged.
- `./build.sh` passed; the APK installed as an update on AFTKM. Full-resolution captures of both focused states are under ignored `artifacts/fireos/home-menu-centered-text/`. Both labels are centered in their pills, and Back dismissed the popup. The installed base APK SHA-256 matches the build: `fe13dd748e5833668fac37b6e755a3cce8f66b315a1463ff082cb8cbb0d469ff`. `git diff --check` passed.

## Weather widget v1 — Pune (2026-10-23)

- Changed `HomeActivity.java`, `AndroidManifest.xml`, `build.sh`, `.gitignore`, `README.md`, `AGENTS.md`, this log and `FIREOS_PORTING.md`; added `WeatherRepository.java` and `WeatherIcon.java`. The Home-only Now Playing card was replaced in its existing 380 dp upper-right area by a borderless two-line weather widget. The old media listener service is no longer registered; its unused Java implementation remains in source for a later isolated removal. No notification listener is enabled by this change.
- The build copies `WEATHERAPI_KEY` or the gitignored root `weatherapi.key` into the local APK's assets without printing it. The build reports a missing key and the UI displays `Weather unavailable` without one. `INTERNET` is newly declared solely for HTTPS WeatherAPI requests; no location, network-state, root or privileged permission was added. `Pune,India` is the only query, and the UI names Pune. A bundled key can be extracted from an APK; this is a personal-build solution, not a public distribution pattern.
- `WeatherRepository` requests `https://api.weatherapi.com/v1/current.json` with `key`, `q=Pune,India`, `aqi=yes` on a single background executor, 5-second connect/read timeouts and a 64 KiB response cap. It validates city, condition and finite Celsius temperature, accepts missing air quality, persists the last successful response and timestamp in app-private SharedPreferences, suppresses concurrent requests, refreshes only after 30 minutes, and limits failed retry attempts to once per five minutes while foregrounded. Pause cancels an active fetch. A stale cached response remains visible with a stale label after network/API/JSON failure; without cache the widget is unavailable.
- `current.air_quality.us-epa-index` is treated as the documented US EPA **category** 1–6, never a numeric AQI reading or pollutant concentration. A small colored dot conveys the category; the muted text gives Good, Moderate, Unhealthy for sensitive groups, Unhealthy, Very Unhealthy or Hazardous. Missing/out-of-range values show `Air quality unavailable`. The static `WeatherIcon` covers clear day/night, partly cloudy, cloudy, rain, thunder, mist/fog/haze and a generic fallback; no weather imagery is downloaded.
- `./build.sh` compiled successfully; update install on AFTKM succeeded. The full-resolution live screenshot under ignored `artifacts/fireos/weather-v1/live-home-final.png` shows `Smoky haze, Pune`, 25°C and `Air Quality Unhealthy`. An independent WeatherAPI response on the development Mac returned Pune, 24.5°C, condition code 1036, nighttime and US EPA category 4 at the same time; rounding and category mapping match the device UI. No weather reading is hardcoded.
- A process restart with an intentionally invalid test key still displayed the fresh persisted cache (`cached-with-invalid-key.png`). A temporary one-millisecond expiry test build with an invalid key showed cached weather plus the stale-data line after an API error (`stale-after-api-error.png`); that interval and key were reverted before the final build. This tested HTTP failure with a stale cache, not a Wi-Fi-disconnected TV. The final build again uses the local real key and 30-minute interval. A YouTube dock launch resumed `com.amazon.firetv.youtube/dev.cobalt.app.MainActivity`; Home returned to `dev.karat.materialhome/com.rampey.materialtv.HomeActivity` with weather visible.
- A fresh-start, 15-second idle `gfxinfo` sample on the weather build reported 906 frames, 0 janky, p50/p95 6/8 ms and 0 missed Vsync. This is a single idle sample, not a full before/after performance matrix. Remaining checks: no-cache unavailable and physically disconnected Wi-Fi were not exercised on the personal device, since clearing app data would erase preferences and disabling Wi-Fi would cut the only ADB link.
- Final APK `300c6de529f437a783a4f9e9d41f3d33e3664d29641c5038c6ea8db7ea1fc441` matches the installed base APK. `aapt2 dump permissions` lists only `INTERNET` and the pre-existing `PACKAGE_USAGE_STATS`; the local key file remains ignored by Git. Final full-resolution captures show Home weather, Apps with the widget hidden, the unchanged Home Move/Replace menu, and the unchanged picker. ADB confirmed Move and Replace can be entered and cancelled, native Amazon Settings resumes, Back returns to Material Home, and the device crash sample contains no launcher fatal exception. The saved Home order was not edited.
- The repeatable two-run idle profile on that final APK reported 920/917 frames, 18.48%/0% Android-classified jank, p50/p95 6/18 and 6/8 ms, 1/0 deadline misses, 0 missed Vsync in both, 26.32%/25.20% of one CPU core, and 134,910/133,756 KiB PSS. Prior Milestone 6 baseline idle runs used ~24–25% core, 0% classified jank and ~125–150 MiB PSS. The first run's render/buffer timing was phase sensitive; UI-thread p95 stayed below 0.51 ms. The widget adds no frame callback, but these measurements do not establish an across-the-board frame-time improvement or prove the absence of regression in every workload. Raw profile evidence is under ignored `artifacts/fireos/weather-v1/profile/`.

## Weather widget right alignment (2026-10-23)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. The weather widget keeps its 380 dp area and vertical position, but its right margin is now the 48 dp page inset plus the tab bar's 6 dp right padding. Its headline, details and stale-data line pack to the right edge; the weather icon stays immediately beside the condition text.
- `./build.sh` passed and the update installed on AFTKM. Full-resolution device capture `artifacts/fireos/weather-right-align/home.png` shows both visible weather lines ending at the tab group's right edge. The installed APK SHA-256 matches the local build: `f6477b445724c2477f2b17400c38559dc94bfd592133d09e0743a17e778dd9d9`. `git diff --check` and sampled crash-log check passed. No weather request, cache, navigation or app-selection behavior changed.

## Premium weather range and 15-minute refresh (2026-10-24)

- Changed `HomeActivity.java` and `WeatherRepository.java`; added `TemperatureRangeView.java`, `WeatherPolicy.java` and `tools/WeatherPolicyTest.java`; updated this log and `FIREOS_PORTING.md`. The widget remains a borderless Java Views overlay aligned to the top tab strip. The icon sits at the left edge and current temperature at the right edge of a 220 dp top row; two equal flexible gaps space the icon, Pune and temperature, with a subtle separator in the second gap. The 220 dp second row shows rounded min/max labels and a narrow range track. AQI and condition text are absent. The user confirmed the final spacing on the physical TV.
- `TemperatureRangeView` caches anti-aliased Paints and RectF bounds. It draws a 3.5 dp rounded muted track, palette-derived warm fill and a 9 dp current-temperature dot with a subtle halo. The unrounded doubles determine `(current-min)/(max-min)`, clamped to `[0,1]`; labels alone are rounded. Equal min/max centers the dot, invalid values hide the track, and too-small measured bounds skip drawing. It has no animation loop and invalidates only when values or colors change.
- `WeatherRepository` now requests HTTPS `forecast.json` for `q=Pune,India&days=1&aqi=no&alerts=no`. It validates current Celsius temperature, day flag, condition code and a finite ordered `mintemp_c`/`maxtemp_c` pair on the forecast day matching `location.localtime`'s Pune date. It rejects a response whose forecast date differs from the device's current Pune date before replacing the app-private JSON cache. The old `current.json` cache can still supply current temperature/icon while the first forecast loads. Malformed/HTTP/offline responses retain the last good cache. No key is in source or logs.
- The default freshness interval is 15 minutes. Persisted cache age uses wall-clock milliseconds plus Pune's local date; in-process retry throttling uses `SystemClock.elapsedRealtime()`. One main-thread `Handler` check is scheduled for the earlier of the 15-minute boundary or Pune midnight while `HomeActivity` is resumed. The network request uses the existing single background executor and is never concurrent. `onPause` removes the check and cancels an in-flight request; a canceled request may retry on the next visible resume. Completed failures wait 15 minutes before retrying. No new service, permission or focus-triggered request was added.
- `./build.sh`, `git diff --check`, and 14 host-side `WeatherPolicyTest` assertions passed. The assertions cover the 15-minute boundary, immediate/fresh/legacy cache, backward clock change, Pune midnight, out-of-range and equal-temperature positions, and invalid values. A **temporary 5-second interval test build** on AFTKM showed forecast requests succeed at 23:59:19 and 23:59:24 without overlap; it was replaced by the final 15-minute build. The final 15-minute interval was not observed by waiting a full interval on the physical TV.
- Full-resolution physical-device screenshots under ignored `artifacts/fireos/weather-range/` show the final Home weather UI and color changes on different focused apps, Apps and Preferences with weather hidden, the Home Move/Replace menu, and the forecast rollover. A request around 00:00 produced a JSON validation failure and kept the previous day's 21°–33° cache; by 00:01 the API's 2026-10-24 forecast appeared as 22°–32° after a new process launch. The host API query independently reported the same date and raw 21.7°C/32.2°C values. This confirms date-based staleness and replacement after a valid response, while the exact source of the first midnight response failure was not established. The 15-minute failure retry means an invalid response at rollover can leave yesterday's values visible until the next allowed request.
- ADB D-pad opened Apps, a tap opened Preferences and native `com.amazon.tv.launcher/.ui.MainSettingsActivity`, Back returned to Material Home, a YouTube dock tile launched, Home returned to Material Home, and an ADB long-press opened the Home Move/Replace menu. These checks did not edit the saved app order or visibility. The user confirmed the final weather spacing on the TV. No launcher fatal entry appeared in the sampled crash buffer. Final installed base APK SHA-256 matches local `build/material-home-fireos.apk`: `43733b0b67b9b043978966b70a283cff24602d9e4b6ba259a9b9429aa5f767cd`.
- Remaining limits: no live Wi-Fi-disconnected probe was run because ADB uses that connection; failure retention was confirmed by the midnight invalid-response case. No exact full 15-minute soak or before/after frame benchmark was run for this UI change. Existing Home on Fire routing was observed returning from an app but its configuration was not modified.
- A final 10-second Home idle sample after `dumpsys gfxinfo ... reset` rendered 611 frames with 0 classified janky frames, p50/p95 6/12 ms, 0 missed Vsync and 0 frame-deadline misses. This is an idle sample, not a navigation benchmark; the temperature view has no frame callback.

## Apps tab inline visibility editor and labels (2026-10-24)

- Changed `HomeActivity.java`; added `res/drawable/ic_visibility_off.xml`; updated this log and `FIREOS_PORTING.md`. The Apps page heading now has a focusable 48 dp tune button at the far right. The ordinary grid still lists only saved-visible launchable apps, now with one-line Google Sans Flex names beneath each tile. The Home dock and its label behavior are unchanged.
- Selecting the tune button switches the same Apps grid into visibility edit mode. It shows all installed launchable apps in the existing alphabetical order. Saved-hidden artwork is desaturated and dimmed, with a small high-contrast visibility-off badge over the tile. Select toggles the focused app's visibility in place, without launching it or rebuilding the grid. The button's selected color and accessible description indicate edit/save state; badge, button and label colors follow the focused app's palette.
- Edits use an in-memory hidden-package draft and the existing `HomePreferences.save(home, hidden)` atomic preference commit. Selecting the tune button again saves and returns to the normal filtered grid. Home, Apps, Preferences, Back/Menu, native Settings and `onPause` also commit and close edit mode before leaving; no confirmation dialog or new service/permission was added. An unchanged draft causes no write. The normal Apps tile continues to launch its app.
- `./build.sh` passed and the APK installed as an update on AFTKM. Full-resolution captures under ignored `artifacts/fireos/` cover normal labels, the all-apps editor, a focused hidden tile, focused edit button, visibility toggle, and saved/filtered states. An initially hidden test app was made visible with D-pad Select, saved with the tune button, and remained visible after a process restart. It was then hidden and saved through Back; the same app was also made visible and saved through Home and Settings. Its original hidden choice was restored after each check. Native Amazon Settings opened and Back returned to the launcher. From normal Apps mode, an app tile launched its own activity and the launcher resumed. The sampled crash buffer showed no launcher fatal exception; `git diff --check` passed.
- No new network, location, root or privileged permission is involved. D-pad testing used ADB key events on the physical Fire TV; no new human remote confirmation or long navigation benchmark was performed for this change.
- Two reset 10-second idle samples with Apps visible rendered 606 frames each and reported 561/445 Android-classified janky frames, p50 17 ms in both, but 0 missed Vsync, 0 slow UI-thread frames and 0 frame-deadline misses. This misses the repository's strict zero-classified-jank target. Existing `PERFORMANCE_REPORT.md` documents similar phase-sensitive rendering/buffer timing; this small static-view change was not accompanied by a full before/after trace, so no cause or performance improvement is claimed.
- The user also confirmed the final Apps edit flow with the physical Fire TV remote: focus the tune button, open the editor, toggle a tile, and press Back; the grey badge and saved visibility behaved as expected.

## Apps tune button, Back behavior and focused-row visibility (2026-10-24)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. The tune button is 38 dp instead of 48 dp, with a smaller 16 dp visible icon and matching round background. Back in Apps edit mode now saves the hidden-app draft, rebuilds the filtered grid and focuses the tune button while remaining on Apps. Back in normal Apps mode still opens Home.
- Focused Apps cards now request a scroll adjustment using the full tile holder height, which includes the app-name label; a 14 dp viewport inset leaves room for the 1.10-scaled focus ring. The existing card focus listener, animation and palette handling remain intact. No app ordering, Home dock, permission or network behavior changed.
- `./build.sh` passed and the update installed on AFTKM. Full-resolution physical-device captures under ignored `artifacts/fireos/apps-row-*.png` show the smaller button, Back retaining Apps, a full focused bottom row and label in normal mode, a full focused top row after scrolling back, and a full focused lower row and label in edit mode. A visible test app was toggled hidden, saved with Back while Apps stayed open, then toggled visible and saved with Back to restore its original setting. Normal-mode Back still returned to Home; the launcher resumed with no sampled fatal crash entry. These focus and navigation checks used ADB input on the physical TV; human remote inspection was not repeated.
- The installed base APK and local `build/material-home-fireos.apk` have matching SHA-256 `4ef1dcbc795398131ca03284209fe2c1e334ad660576e47a64569b89a3cc6233`. `git diff --check` passed. No new frame-time benchmark was run; the earlier Apps idle jank limitation remains open.

## Fire TV Settings side panel (2026-10-24)

- Changed `HomeActivity.java`, `AndroidManifest.xml`, `res/values/values.xml`, and added `res/anim/settings_panel_in.xml`, `res/anim/settings_panel_out.xml`, `res/interpolator/settings_spatial_default.xml`, and `res/interpolator/settings_spatial_fast.xml`. Updated `AGENTS.md`, `README.md`, this log and `FIREOS_PORTING.md`. The Settings tab now opens a palette-colored, right-side modal with Wi-Fi, Bluetooth and All settings rows. It dims Home/Apps, slides in/out with the existing spatial curves, supports D-pad focus and Back dismissal, and saves an active Apps visibility edit before opening.
- Package resolution on AFTKM (Fire OS 8.1.8.5, API 30) found `Settings.ACTION_WIFI_SETTINGS` at exported `com.amazon.tv.settings.v2/.tv.network.NetworkActivity`, Fire OS `android.settings.CONTROLLERS_SETTINGS` at exported `com.amazon.tv.settings.v2/.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity`, and the `LEANBACK_SETTINGS` category at `com.amazon.tv.launcher/.ui.MainSettingsActivity`. Generic `Settings.ACTION_BLUETOOTH_SETTINGS` did not resolve. Each row uses the supported intent first, an exported component or Settings-home fallback next, catches activity/security failures, and logs complete launch failure. On-device navigation opened the native Network page, Controllers & Bluetooth Devices/Other Bluetooth Devices route, and native Settings home; Back returned to Material Home.
- The panel obtains a connected Bluetooth audio name using `AudioManager` and can show an active Fire TV remote as a fallback. Wi-Fi SSID was redacted before runtime location access. The user explicitly authorized a foreground location prompt and granted “While using the app”; the actual network name then appeared in the panel. A denied/unavailable name has a fallback label. `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` are normal permissions; optional `ACCESS_FINE_LOCATION` is used only to read the SSID. Weather continues to use fixed Pune data and is the only network client. No root, privileged, accessibility, system package, or Home on Fire setting was changed.
- `./build.sh` succeeded; `adb install -r` updated the physical AFTKM. Full-resolution screenshots under ignored `artifacts/fireos/settings-panel-*.png` cover the three panel focus states, the real SSID and connected Bluetooth name, Settings destinations, Back dismissal, and opening Settings from Apps edit mode. ADB navigation confirmed that opening Settings from an Apps visibility edit saves/exits the edit and Back returns to normal Apps. The user confirmed with the physical Fire TV remote that all three rows and destinations work. The sampled crash buffer was empty; `git diff --check` passed. Local and installed APK SHA-256 match: `5fdab1dea467338c930d9462541f58cffbc3c3ee4134d15017f0f53552a6d15b`.
- Remaining limits: a connected non-audio Bluetooth accessory is not enumerated without additional Bluetooth access; the panel shows connected audio or an active remote instead. No Wi-Fi-disconnect or permission-denial device trial was run, and no new frame-time benchmark was made. The earlier Apps idle jank observation remains open.

## Three-tab navigation and equal Settings row gaps (2026-10-24)

- Changed `HomeActivity.java`, this log and `FIREOS_PORTING.md`. Removed Preferences from the top `ButtonGroup`, leaving Home, Apps and Settings. Updated the Settings dialog's return-focus tab index from 3 to 2. Kept the internal Preferences implementation and saved user choices untouched; Home tile Move/Replace and the Apps tune editor still provide the active customization controls. The Settings panel now uses the same 12 dp gap between Wi-Fi/Bluetooth and Bluetooth/All settings.
- `./build.sh` succeeded and `adb install -r` updated the AFTKM. Full-resolution captures under ignored `artifacts/fireos/three-tab-*.png` show the three-tab Home and Apps screens, the Settings panel, and the final equally spaced rows. ADB touch/key tests opened Apps and Settings and dismissed the panel with Back. The sampled crash buffer was empty; `git diff --check` passed. Local and installed APK SHA-256 match: `0851bf3bd7aeb347a2ec4cd8210f143a2bc7c89aa7c02262a9835b292a3ef5a7`. No new permission, network client, system setting or animation loop was added. A human remote check was not repeated for this small layout change.

## Compact weather details (2026-10-24)

- Changed `WeatherRepository.java`, `HomeActivity.java`, this log and `FIREOS_PORTING.md`. Replaced the Home widget's drawn range bar with a current-condition icon, Pune and current Celsius temperature on the first row, then `Feels like`, today's low–high temperatures, a locally drawn rain icon and the daily rain chance on the second row. Both rows wrap their content and align at the right edge of the tab group. The follow-up removed the divider between rain icon and percentage and tightened the main icon/city/temperature-divider margins. The old `TemperatureRangeView` is no longer instantiated; its source and historical policy test remain.
- The existing `forecast.json` response supplies `current.feelslike_c` and `forecast.forecastday[].day.daily_chance_of_rain`. The parser accepts missing values from older cache JSON with `--` placeholders and rejects out-of-range rain percentages. The fixed Pune query, 15-minute refresh, cache, HTTPS client, permissions and Home-only display behavior are unchanged; no extra request or animation loop was added.
- `./build.sh` passed and `adb install -r` updated AFTKM. Full-resolution device captures under ignored `artifacts/fireos/weather-details-*.png` show live feels-like, low/high and rain percentage values, the final compact spacing, an alternate app palette and weather hidden on Apps. ADB navigation changed Home focus and opened Apps without a sampled launcher crash. `git diff --check` passed. Final local and installed APK SHA-256 match: `968a0201d82dd9035be4a4cc722bbff36977fdd8f5c5fa2c1ef5809e18988f15`. A separate physical-remote viewing check and new frame-time benchmark were not performed.

## Automatic IP location and keyless Open-Meteo weather (2026-10-24)

- Changed `HomeActivity.java`, `WeatherRepository.java`, `WeatherIcon.java`, `WeatherPolicy.java`, `tools/WeatherPolicyTest.java`, `build.sh`, `AndroidManifest.xml`, `README.md`, `AGENTS.md`, this log and `FIREOS_PORTING.md`; added `IpLocationProvider.java`. The prior WeatherAPI implementation and key-asset copy are superseded. The package ID, existing signing identity, app preferences, stock launcher, Home on Fire route and Settings SSID permission were preserved. The user explicitly chose to keep optional foreground location access for the Wi-Fi name; weather itself never invokes Android location APIs.
- Before changing the weather UI, a temporary probe running under the normal `dev.karat.materialhome` UID on AFTKM logged HTTP 200 from `https://ipdata.free/`, a valid city/country/timezone/coordinate result, then HTTP 200 and required forecast fields from `https://api.open-meteo.com/v1/forecast`. The probe did not log the public IP and was removed from the final source/APK. A Mac-side request had returned HTTP 403, so the device result was necessary. Neither endpoint needs an app API key.
- `IpLocationProvider` validates the five required location fields, caches them with a 24-hour timestamp, and compares later detections using city, country, timezone and a 25 km drift threshold. A 5-second debounced default-network change triggers a recheck. Weather cache entries are location-keyed; changed locations discard old-city weather before rendering. `WeatherRepository` keeps the 15-minute/local-midnight weather schedule, matches hourly rain probability to the current local hour, and uses separate bounded exponential backoff for geolocation and forecast errors. Both clients use HTTPS, bounded reads and 5-second timeouts on one worker. Startup and D-pad navigation do not wait for network requests. Historical fixed-Pune/WeatherAPI entries above describe prior APKs only.
- `./build.sh`, `git diff --check` and 16 host `WeatherPolicyTest` checks passed. `adb install -r` updated the physical Fire TV Stick 4K AFTKM. The final full-resolution Home capture at `artifacts/fireos/auto-weather-final-home.png` shows the detected city and two-letter country code, current and feels-like Celsius readings, daily low/high, condition and rain icons, and a current-hour 49% rain chance. The displayed values came from the live device requests and cache, not constants. `artifacts/fireos/auto-weather-final-apps.png` shows the alphabetical Apps grid and no Home weather; `artifacts/fireos/auto-weather-final-settings.png` shows the unchanged Settings panel. Earlier captures `auto-weather-cache-restart.png` and `auto-weather-dock-menu.png` verify cached values after an app process restart and the Home Move/Replace menu. ADB launched YouTube from the Home dock and Home returned to `HomeActivity`; no launcher fatal entry appeared in the sampled crash buffer. The user also confirmed that the weather city, country, temperature and rain details look readable and Home, Apps and Settings work with the physical remote.
- APK permissions remain `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, the pre-existing optional `ACCESS_FINE_LOCATION` for Settings Wi-Fi SSID, and `PACKAGE_USAGE_STATS`. No WeatherAPI key asset was found in the APK. Final local and installed base APK SHA-256 both equal `15371f31a0ec51eccd237751335c004a5554517592ac92180cdb51841f1230d5`.
- Limits: a fresh data-cleared install, full 15-minute active interval, 24-hour location expiry, network switch, Wi-Fi outage and device reboot were not exercised, to avoid erasing user preferences or interrupting the ADB connection. The live device probe and process restart cover automatic detection and cache persistence without those destructive cases. A 10-second idle `dumpsys gfxinfo` sample after reset reported 888 frames, 646 Android-classified janky frames (72.75%), p50/p95 17/22 ms, zero missed Vsync and zero missed frame deadlines. The background renderer was active during that sample; this does not satisfy the AGENTS.md zero-jank target and merits a separate performance investigation before claiming it does. No comparative pre-change sample was taken for this weather patch.

## Icon-only tile corner cleanup (2026-10-24)

- Changed `HomeActivity.java` and this log. For apps with no full-bleed TV banner, the focused card no longer raises a colored elevation shadow. On these icon-only cards the shadow had shown through the rounded focus-ring gap as small colored corner patches, visible on Amazon Kids in `shot9.png`. The shared `tile()` renderer applies the fix to Home, the Home replacement picker and Apps. Full-bleed banner tiles retain their focus glow; icon-only tiles retain their scale, corner transition, ring, palette retint and D-pad behavior. No app-specific package rule or artwork replacement was added.
- `./build.sh` passed and `adb install -r` updated the physical AFTKM. Full-resolution after-state captures under ignored `artifacts/fireos/shot9-after-home-icon.png`, `shot9-after-apps-kids.png` and `shot9-after-picker-icon.png` show a focused Amazon Kids icon tile in each view without the colored corner patches. ADB D-pad navigation opened the Home Move/Replace menu and picker, moved focus to the icon tile, then Back dismissed the picker without replacing an app. `git diff --check` passed and the sampled crash buffer contained no launcher fatal entry. Local and installed APK SHA-256 match: `65f5e79070b43157717d0789232fc78865fb485fda8879fbd01380758a052052`. A separate human remote check was not requested for this visual-only adjustment.

## CleanFire Launcher name and artwork (2026-10-24)

- Changed `AndroidManifest.xml`, `res/values/values.xml`, `README.md`, `AGENTS.md`, `NOTICE`, `FIREOS_PORTING.md` and this log; added the supplied `res/drawable-nodpi/cleanfire_icon.png` and `cleanfire_banner.png`. The application and exported TV launcher activity now use the `CleanFire Launcher` string, square icon and wide banner. The screensaver's Settings label now reads `CleanFire Launcher Clock`. The package ID remains `dev.karat.materialhome`, all Java component names are unchanged, and the existing `build.sh` signing/output path and Home on Fire configuration were not changed. The README and NOTICE retain explicit upstream Material TV Launcher attribution and licenses. Older log entries preserve their historical name and test evidence.
- `./build.sh` passed with the unchanged resource compile/link pipeline. The APK contains both drawable-nodpi PNGs; `aapt2 dump badging` reports `CleanFire Launcher` for the application and both standard/Leanback launch entries, the new square icon for both, and `cleanfire_banner.png` for the Leanback entry. `aapt2 dump xmltree` confirms application and activity banner declarations. `adb install -r` updated AFTKM without clearing app data. Fire OS's app-details screen visibly shows the new name and square flame-and-house icon in `artifacts/fireos/cleanfire-app-details.png`. An explicit start of Amazon's stock Home activity returned to the existing CleanFire Home activity through Home on Fire, showing the integration still works; `artifacts/fireos/cleanfire-home-after-update.png` shows the original launcher UI. The sampled crash buffer contains no launcher fatal entry. Local and installed base APK SHA-256 match: `62c82f472df6e41172e113409504c741da38ac66c151dbbd4e79d2062c7e0c48`.
- Follow-up on AFTKM: the stock Fire TV "Your apps and channels" row and full app library initially kept a stale "Material Home" entry with the old orange icon, even after an in-place update to version 183 and a reboot. Selecting that tile launched `dev.karat.materialhome`, while Android package metadata and native App Details already showed CleanFire Launcher and its new icon. A second full streamed `adb install --no-incremental -r` still left the old stock-library entry. `adb shell pm uninstall -k dev.karat.materialhome` followed immediately by `adb install --no-incremental build/material-home-fireos.apk` and a reboot refreshed the Fire TV library: a full-resolution screenshot at `artifacts/fireos/cleanfire-stock-library-after-fresh-install.png` shows **CleanFire Launcher** and the new flame-and-house icon. The `-k` flag retained the app's data/cache; the optional location grant remains granted, and the six Home dock apps have the same order in before/after screenshots. The Home on Fire accessibility service remains enabled and bound. No stock launcher data, Home on Fire configuration or system package was modified.
- The Fire TV "Your apps and channels" card displays the new square icon inside a wide tile; it does not render the separate banner image in that surface. The APK nevertheless declares and packages `cleanfire_banner.png` at both application and Leanback activity levels, as confirmed by `aapt2 dump badging` and the APK archive. Visual use of that banner by Fire OS has not been observed. Android TV documentation allows a device to choose either the icon or banner for an app row; this Fire OS surface appears to choose the icon. Final local and installed base APK SHA-256 match: `312ecd8a35188db30958127ea1386c1060ec5172ed5ba73964bb2b39f0803cf4`. `git diff --check` passed and the sampled crash buffer had no launcher fatal entry. The user temporarily changed their Home-button mapping to inspect stock Fire TV, so physical Home-button routing after the reinstall is not claimed as verified; the Home on Fire service remains enabled and bound.

## Public release preparation (2026-10-24)

- Audited current source, manifest, assets, scripts, documentation, licenses, tracked files, and 22 reachable commits. A temporary local audit is at ignored `artifacts/release-audit.md`. Removed upstream-only `install.sh`/`uninstall.sh`, the old orange/gradient `icon.xml`/`banner.xml`, and two upstream screenshots after confirming they are not current runtime references. `./build.sh` passed immediately after this cleanup. Dormant upstream Java classes and utilities were retained because their purpose is uncertain or useful to contributors.
- Changed `build.sh` to support explicit debug/release modes while keeping the historical no-argument debug path. Version 1.0.0 uses code 184, higher than installed code 183. Release mode uses D8 `--release`, no Java debug metadata, and a required persistent external signing key; it does not enable obfuscation. Updated `.gitignore`, `README.md`, `AGENTS.md`, `CONTRIBUTING.md`; added `CHANGELOG.md`, `RELEASE_NOTES.md`, `RELEASE_CHECKLIST.md`, `.github/workflows/build.yml`, and the actual 1920×1080 `docs/images/home.png`. The build packages attribution/licenses. Historical personal IP references in this log were replaced with `<adb-serial>`; the ignored local old WeatherAPI key was not read or deleted.
- Provider review found ipdata.free's keyless limit and visible-attribution requirement and Open-Meteo's non-commercial free-API terms. `HomeActivity.java` now adds a small palette-tinted `Location: ipdata.co · Weather: Open-Meteo` credit under the weather details. The credit was inspected in full-resolution Home captures; no additional network call or permission was added.
- `./build.sh --debug` built and `adb install --no-incremental -r` updated AFTKM without clearing app data. After a process restart, Home showed the cached city and forecast. ADB key events opened the Home Move/Replace menu; Back dismissed it. The Apps grid was alphabetical; tune showed hidden tiles and Back saved/returned to Apps. The Settings panel opened native Amazon Settings via All settings, Back returned to CleanFire, and a Home shortcut launched YouTube. The sampled crash buffer had no launcher fatal entry. The screenshot published in README shows only approximate Pune weather and app art, not the connected SSID/Bluetooth name from the ignored Settings capture.
- A controlled 15-second idle profile on AFTKM recorded 915 frames, 0% janky, 0 deadline misses. A D-pad sweep recorded 1482 frames, 90.01% framework-classified janky, 8 deadline misses, consistent with the pre-existing limitation in `PERFORMANCE_REPORT.md`; zero-jank navigation is not claimed. Raw captures/profiles remain ignored under `artifacts/fireos/`.
- Generated one local release signing identity outside Git at `~/.config/cleanfire-launcher/release.jks` with password stored separately in a mode-0600 local file. The certificate SHA-256 is `712df78b2c801ab1dd1b083fcb082291e348b55f58907c471877bb3980c5f26f`; it must be backed up securely before publication. `./build.sh --release` passed; `apksigner` v3 verification, `aapt2` identity/resources/permissions, APK notice/archive inspection, shell syntax, 16 host weather-policy checks, `git diff --check`, and `release/SHA256SUMS.txt` verification passed. Candidate `release/CleanFire-Launcher-v1.0.0.apk` SHA-256 is `4b0453c734a67d03a89a2aae309037c7185d24d2927bbd8f221efaf0b41b596a`.
- The release-signed APK was not installed on the debug-signed device because Android rejects a different certificate as an update and the task forbids uninstalling the working launcher. The GitHub Actions workflow is local-only until a new repository is created; no repository was pushed or GitHub Release created. Physical Home-button routing was not retested because the owner had temporarily changed Home on Fire mapping. See `RELEASE_CHECKLIST.md` for gates and limitations.

## Weather credit removal and README capture (2026-10-24)

- By user request, removed the `Location: ipdata.co · Weather: Open-Meteo` line and its View from `HomeActivity.java`; the weather widget again has only its temperature and detail rows. Provider links remain beside the Home screenshot and in the README weather/credits text. The provider's current attribution terms should be reviewed before publication because the device widget no longer displays provider names.
- `./build.sh --debug` passed; `adb install --no-incremental -r` updated the AFTKM without clearing data. Captured the live 1920×1080 Home screen with Prime Video focused and weather loaded at `docs/images/home.png` (399 KiB). Inspected the image: no provider line, network name, Bluetooth name, account detail, or debugging overlay appears. The README embeds this exact screenshot via a relative path.
- Rebuilt `./build.sh --release` with the same persistent certificate. `release/CleanFire-Launcher-v1.0.0.apk` now hashes to `14f625b39ee11d1271dc94fcf7c8e77db1c9b91324da79d12cd0c804ad0ed67a`; `release/SHA256SUMS.txt` was regenerated. The release APK remains uninstalled on the debug-signed device for the signature-mismatch reason above.
