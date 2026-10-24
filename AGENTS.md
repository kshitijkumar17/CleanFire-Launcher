# AGENTS.md

Guidance for coding agents (and people) working on CleanFire Launcher, the Fire OS fork of Material TV Launcher. Read this before changing the UI.
Installation, build and troubleshooting details also live here; keep the README focused on the launcher.

## What this is

A buttery smooth launcher for Android TV / Google TV: the user's most-used apps, colors that follow the focused app,
Material 3 Expressive design. It is **only a launcher**: no debloat tooling or AI. The Fire OS fork
uses HTTPS for its weather widget; the upstream notes below still describe the original Now Playing feature.

## Hard rules

- **Weather is the only network use in this Fire OS fork.** `INTERNET` is used for keyless HTTPS
  public-IP geolocation (`ipdata.free`) and Open-Meteo forecasts. Weather never uses Android location permission.
  The Settings panel may request optional foreground location access solely to show the connected Wi-Fi name,
  which Android 11 otherwise redacts. Never use that permission for weather or tracking.
- **No user-specific app lists or device identifiers in code.** The dock comes from on-device usage stats and
  installed apps; HDMI devices come from HDMI-CEC names. Weather city and coordinates come from the device's
  public-IP geolocation response and are cached only in app-private preferences.
- **No brand logos we don't own.** Console tiles use Material icons on a tone of the brand hue, never a logo.
- **Nothing animates while another app is on screen.** Stop every loop in `onPause`; resume in `onResume`.
- **Smooth on weak TVs** (4 × Cortex-A73 at 1.39 GHz, 2.4 GB RAM): animations run on display frames
  (`postOnAnimation`, time-based), cost only while Home is visible, and must keep 0 janky frames in `dumpsys gfxinfo`.
- **Plain Java Views, no Gradle, no dependencies** beyond the vendored `material-color-utilities`. Build with
  `./build.sh`. No Compose (too heavy for the target TVs).
- **Test on a real TV** with full-resolution screenshots (`adb exec-out screencap -p`) of every focus state you touch.

## Material 3 Expressive, as used here

**Color** (`Palette.java`): `SchemeFidelity` from the focused app banner's seed (`QuantizerCelebi` + `Score`, the
same as Android wallpaper color). Use roles in their intended pairs: `primary/onPrimary`, `surface*` for backgrounds,
`inverseSurface/inverseOnSurface` for focused fills and ink, `primaryContainer` for the background shape. Never
hard-code white/black for focus. Everything tinted cross-fades with the default effects curve on retint.

**Shape** (`Focus.java`): cards 32 dp, media (tiles, pictures, banners, album art) 20 dp, buttons fully round
(pill). The Now Playing card is a pill with a radius of half its measured height. Focused tiles tighten their
corners (×0.62) and the ring stays concentric (radius + ring offset).
The background shape (`CookieDrawable`) morphs only between library shapes: Cookie 4/6/7/9/12, Clover 4- and
8-leaf, Sunny, Soft burst (MaterialShapes names); one per app, morphing on focus.

**Motion** (`Motion.java`): Expressive springs via the spec's fallback curves.
Spatial (movement, scale, shape) may overshoot: fast 350 ms for small components, default 500 ms for larger ones.
Effects (color, opacity) never overshoot: fast 150 ms, default 200 ms. Do not add other curves or durations.

**Focus** (TV): scale 1.10 with the fast spatial curve, a 3 dp ring 6 dp outside the tile that fades in, a glow in
the app's own color (`setOutlineSpotShadowColor`), neighbors shift 8 dp aside (standard button group). Buttons and
nav fill with `inverseSurface` on focus.

**Type**: Google Sans Flex with `'ROND' 0` (never rounded), `opsz` 24 for UI and 48 for display. The focused app
name settles into its weight; the clock's weight follows the time of day and swells briefly on each new minute.

**Components**: top-bar strip (`ButtonGroup`: 30 dp outer radius, 5 dp padding, 4 dp tab gaps, 24 dp tab radii).
The selected page has a `primary/onPrimary` pill; focused tabs use `inverseSurface/inverseOnSurface` and scale 1.05;
inactive tabs are transparent. Fills fade as focus and selection change. Now Playing uses a wavy progress indicator
(`WavyProgress`: moving wave while playing, flat when paused, grows in from a flat line); a morphing shape loader
appears when an app takes over 300 ms to open.

**Status marks go under the tile, never on the artwork**: the playing app gets an animated equalizer
(`EqualizerBadge`), an HDMI device that's on gets a green dot (Material green, tone 80). When the tile is focused,
the name appears beside the mark. The label row has a fixed height so the dock never jumps.

**Screensaver** (`Screensaver`, `ShapeScene`): generated Material You-style wallpapers (rounded library shapes in one
tonal palette, big anchors plus small accents, a left scrim for the clock), hue and brightness from the time of day,
a new arrangement every hour, drawn on display frames.

## Layout

960 × 540 dp canvas (TV baseline), 48 dp side margins (5% overscan safe area). Dock of six 16:9 tiles that exactly
fill the width; Now Playing card 380 dp on the right.

## Commits

Small, one topic each, plain messages. **No `Co-Authored-By` or other AI attribution trailers.**

## Build and install this Fire OS fork

Use JDK 17+, Android SDK platform 30 and Build Tools 35.0.0. `./build.sh` or
`./build.sh --debug` produces `build/material-home-fireos.apk` with the local debug key.
`./build.sh --release` requires a persistent signing identity through
`RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASS`, and `RELEASE_KEY_ALIAS` (optional
`RELEASE_KEY_PASS`) and produces `build/cleanfire-release.apk`. Keep signing
material outside the repository. See [README.md](README.md#build-from-source).

Connect ADB to the Fire TV and install only this fork with
`./install-fireos.sh <adb-serial>`. That script updates and opens CleanFire
Launcher; it does not alter Home-button routing, permissions, or stock packages.
`./uninstall-fireos.sh <adb-serial>` removes only this fork and may erase its
preferences. Home on Fire is optional and must be configured separately by
its owner. Never run the original upstream installation scripts for this fork.

## Privacy and verification

Weather requests use HTTPS to `ipdata.free` for approximate public-IP location
and `api.open-meteo.com` for forecasts. The optional foreground location
permission in Settings is used only for the Wi-Fi SSID. No API key is needed or
bundled. `aapt2 dump permissions build/material-home-fireos.apk` lists the
packaged permissions. The app does not request root or privileged permissions.

Before shipping, run `./build.sh --debug`,
`$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose build/material-home-fireos.apk`,
`bash -n build.sh install-fireos.sh uninstall-fireos.sh`, and `git diff --check`.
Test UI changes on the actual Fire TV and capture full-resolution screenshots.
See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for release gates.

## License and attribution

Apache 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Upstream:
[Material TV Launcher](https://github.com/0xRampey/material-tv-launcher) by
0xRampey and contributors. Bundled material-color-utilities is Apache 2.0;
Google Sans Flex is under the SIL Open Font License 1.1. Weather provider
attribution is in the README.

## Pull request reports

- Every pull request created or pushed by Codex must have exactly one detailed
  GitHub PR report comment. Do not commit the report as a repository file unless
  the user explicitly requests a file artifact.
- Start the comment with the stable marker `<!-- codex-pr-report -->`. Before
  posting, search existing PR comments for that marker. Create the comment when
  absent; edit the existing comment after subsequent pushes instead of adding a
  duplicate.
- Use GitHub-rendered HTML and Markdown in the comment. Include, as applicable:
  executive summary, end-to-end user flow, architecture and trust boundaries,
  feature change set, database migrations, security considerations, test and
  build evidence, deployment status, rollout notes, deferred scope, and a
  reviewer guide with primary files.
- The report must describe the tested PR head and distinguish deployed code from
  local or undeployed work. Never claim a test, migration, deployment, or check
  passed without verifying it.
- After creating or updating the report, read the PR back, verify that the marked
  comment exists, and give the user direct links to both the PR and report
  comment. Do not call PR work complete until this verification succeeds.
