# Milestone 6 — performance and stability

## Method and baseline (2026-10-23)

Physical Fire TV Stick 4K (2023), AFTKM/karat, Fire OS 8.1.8.5, API 30.
The initial executable includes the verified native Settings fix. Baseline APK SHA-256:
`28e83e6086e2a20e7447dc6a87f90603a5ee268ec9950638bc58eb9539377e0e`.
No resolution, refresh-rate, global animation, permission or Home-routing settings changes.

`tools/profile_fireos.py` records two runs of each workload, without screenshots or tracing
inside the measured windows. Resets gfxinfo then records aggregate stats plus recent framestats.
CPU is process utime+stime over elapsed wall time, expressed as percent of **one CPU core**,
using the device's CLK_TCK=100. PSS is dumpsys meminfo, measured after the workload. No power
or battery claims are inferred from CPU. Each scenario requests an app-only restart and has
four seconds to settle. Existing Home on Fire can relaunch during force-stop/start, so a
reported start of 0 ms is intent delivery, **not a cold startup**. This same method is used
before and after; independent startup/stability checks are described later.

Workloads: idle=15 seconds; dock=two sweeps right five/left five; navigation=select Home,
select Apps, Back, select native Settings, wait two seconds and reopen Material Home;
grid=Menu, two sweeps down five/up five, Back; app-return=three explicit YouTube launches,
two seconds foreground, Home through existing Home on Fire, one second settling. The shell
`input` process startup adds substantial time per key: dock/grid are repeated presses, not a
hardware-key-repeat latency benchmark. App-return measures lifecycle/routing rather than
selection-to-content time. A separate rapid-input check follows outside these comparisons.

Reproduce (use the device's existing ADB serial and an installed streaming component):

```sh
python3 tools/profile_fireos.py --serial <serial> --out artifacts/fireos/milestone6/<label> \
  --streaming-component <package/activity>
```

Raw evidence is local under ignored `artifacts/fireos/milestone6/`. Capture screenshots only
outside benchmark windows. Existing optional integrations remain ungranted and are not enabled
for this milestone. Screensaver source was reviewed but is not modified or benchmarked.

### Baseline: both runs

| Run | Scenario | Seconds | Frames | Janky % | p50 / p95 ms | Deadline misses | CPU % core | PSS MiB | Start ms* |
| --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| 1 | idle | 15.11 | 914 | 0.0 | 6 / 7 | 0 | 24.55 | 150.4 | 1293 |
| 1 | dock | 25.72 | 1541 | 85.79 | 17 / 27 | 10 | 34.1 | 345.7 | 0 |
| 1 | navigation | 15.88 | 774 | 76.49 | 17 / 28 | 5 | 23.05 | 214.1 | 0 |
| 1 | grid | 26.14 | 1522 | 93.96 | 23 / 34 | 50 | 44.99 | 324.3 | 823 |
| 1 | app-return | 14.03 | 172 | 54.07 | 16 / 32 | 8 | 6.49 | 69.0 | 864 |
| 2 | idle | 15.07 | 911 | 0.0 | 6 / 7 | 0 | 24.15 | 125.4 | 2740 |
| 2 | dock | 24.7 | 1479 | 90.33 | 18 / 32 | 11 | 38.71 | 339.0 | 0 |
| 2 | navigation | 16.3 | 786 | 73.54 | 17 / 26 | 10 | 24.91 | 201.2 | 1593 |
| 2 | grid | 26.21 | 1524 | 93.44 | 19 / 34 | 52 | 45.48 | 320.6 | 882 |
| 2 | app-return | 14.1 | 193 | 88.08 | 17 / 28 | 7 | 6.1 | 71.1 | 874 |

\* See the Home on Fire restart race above; these are not all cold starts.

### Baseline diagnosis

Both fresh idle runs report **0% jank**, unlike the earlier long-running idle sample's 73.97%.
The earlier sample cannot serve as the sole before/after reference. Repeated dock/grid work
still reports high aggregate jank, reproducibly. These are framework classifications, not
measured percentages of visibly dropped frames.

The bounded last-120-frame dock sample reports UI work p50 0.39–0.60 ms, p95 2.01–2.32 ms,
SyncQueued→SyncStart p50 8.08–12.25 ms, render-command-start→swap p50 10.61–12.58 ms and
buffer dequeue p50 4.87–6.88 ms. Median intended-vsync spacing is 16.68 ms, with p95 about
16.70–16.72 ms. The data points toward RenderThread/buffer backpressure and phase-dependent
pipeline latency rather than persistent long Java UI frames. This is an inference from timing
boundaries, not proof of a GPU execution bottleneck. Buffer wait intervals overlap broader
render intervals; do not add them together. GPU completion timestamps on this Fire build can
be older than the current frame; they are not treated as reliable GPU execution durations.
Aggregate histogram percentiles and bounded-tail percentiles have different denominators.

Dock PSS grows to ~339–346 MiB, and HWUI reports ~85–86 MiB of scratch textures. Shape morphs
perform repeated trigonometry and invalidate an explicit offscreen hardware layer; overlapping
morph/title/corner animators and interrupted view animations also warrant targeted changes.

References: [Android dumpsys](https://developer.android.com/tools/dumpsys),
[UI jank profiling](https://developer.android.com/studio/profile/jank-detection),
[AOSP VSync and pipeline offsets](https://source.android.com/docs/core/graphics/implement-vsync).

## Targeted experiments

A separate supported `atrace` capture used gfx/view/sched/am/wm/input, with frequency probes
also requested. The device printed trace-marker/frequency permission warnings, but produced a
valid ~71 MB trace with scheduling and app tracing events. No elevated access was attempted.
Host parsing of paired B/E events for the launcher PID found: 713 dequeues (p50 3.89 ms,
p95 12.36 ms), 713 queueBuffer spans (p50 2.67 ms, p95 8.78 ms), 298 layer flushes
(p50 3.23 ms, p95 11.66 ms), 370 texture uploads, and 89 measure/layout passes. Nested span
sums overlap and must not be interpreted as separate CPU totals. There are explicit
1120 × 1120 ImageView offscreen-layer draw events. Together with framestats, this identifies
buffer/driver waits and morph-related layer work; it does not establish GPU shader saturation.
`baseline-trace.summary.json` records the host summary; raw traces remain local and ignored.
Perfetto service discovery was also available, but no separate Perfetto recording is claimed.

### Experiment A — direct background path throughout (rejected)

Cached shape endpoint trigonometry, cancelled superseded morph/title/corner animators and
reused the two gradient colors. Also removed the background's explicit hardware layer.
One identical idle/dock/grid run: idle CPU **46.99%** (baseline 24.15–24.55%), dock PSS
97.5 MiB (baseline 339–346), but dock deadline misses **63** (baseline 10–11) and grid
misses **107** (baseline 50–52). Idle jank 19.82%, dock 90.23%, grid 95.40%.
The memory savings do not justify the CPU/deadline regressions. Layer removal is rejected.

### Experiment B — direct path during morph, cached layer at rest (rejected)

Retained hardware caching outside morphs, disabled it during each morph, and added the
lifecycle/focus ownership fixes below. One idle/dock/grid run: idle CPU 24.35%, idle jank 0%;
dock CPU 33.82%, PSS 120.8 MiB, but **47** deadline misses; grid CPU 40.88%, PSS 137.0 MiB,
**74** deadline misses. These exceed baseline deadline misses, despite memory savings.
The final implementation therefore retains the original continuous hardware-layer policy.
The reported `TotalTime` of 225190 ms in this experiment's idle-start raw output is inconsistent
with command wall time and the existing Home-routing restart race; it is not reported as a
measured cold startup or a regression in launch latency.

## Retained changes

- `CookieDrawable`: compute polar endpoint coordinates only when a morph target changes.
  Each frame interpolates the same 241 polygon vertices; sampling, shape depth, bounds,
  material colors and motion curves are preserved. No bitmap or drawable allocation per frame.
- `HomeActivity`: one owner per shape/title/corner/ring animation. Superseded animators cancel
  before replacement; ring fades continue from their actual alpha and their corners follow
  the card throughout the focus spring. Reuse gradient colors and skip morphing to the same app.
- Settle interrupted view properties offscreen on pause: the clock/title cannot remain
  transparent and tiles cannot remain half pressed. Retain the last attached focus target
  across returns, including new intents from existing Home on Fire; explicit Home/Apps actions
  retain their existing behavior. Clear tile registries on detach and activity teardown.
- Palette warmup has one worker, stops between items when paused/interrupted, resumes missing
  work as needed, and is interrupted on destruction. No new optional access or usage features.
- Loader references/end actions clear on hide; rotation uses elapsed animator time rather
  than adding degrees per callback. Dock arrival delays reset after arrival or interruption.
- `EqualizerBadge`: immutable wave constants replace two allocations per draw. Equalizer and
  progress invalidation explicitly require attachment, visibility and foreground activity;
  progress timebase resets on resume. This changes only existing drawing/lifecycle internals;
  no Now Playing capability is enabled or introduced. No Screensaver/ShapeScene changes.

## Rendering comparison — same five scenarios, two runs each

The rendering-test APK restores continuous hardware caching. These runs use the same profiler, key
sequence, settling interval and device settings as the baseline. Native Settings and app-return
include intentional background intervals. View focus on return is now retained, so navigation
also measures the intended removal of redundant focus/morph work rather than an identical
post-return view state. No screenshots/traces ran during either comparison window.

| Run | Scenario | Seconds | Frames | Janky % | p50 / p95 ms | Deadline misses | CPU % core | PSS MiB | Start ms* |
| --- | --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| 1 | idle | 15.13 | 915 | 0.0 | 6 / 8 | 0 | 24.32 | 124.6 | 0 |
| 1 | dock | 24.81 | 1477 | 87.0 | 19 / 32 | 19 | 38.98 | 342.0 | 0 |
| 1 | navigation | 15.52 | 744 | 53.23 | 16 / 26 | 4 | 24.03 | 222.9 | 0 |
| 1 | grid | 26.62 | 1545 | 93.66 | 22 / 34 | 56 | 45.82 | 316.7 | 863 |
| 1 | app-return | 14.36 | 209 | 58.37 | 16 / 23 | 7 | 8.15 | 76.8 | 1025 |
| 2 | idle | 15.09 | 917 | 0.0 | 6 / 7 | 0 | 24.52 | 134.8 | 0 |
| 2 | dock | 25.01 | 1493 | 88.95 | 18 / 32 | 14 | 39.22 | 343.6 | 0 |
| 2 | navigation | 16.34 | 798 | 52.01 | 16 / 30 | 3 | 23.26 | 210.0 | 0 |
| 2 | grid | 26.84 | 1561 | 93.98 | 21 / 34 | 52 | 45.23 | 321.7 | 864 |
| 2 | app-return | 14.01 | 240 | 87.92 | 17 / 32 | 9 | 7.56 | 76.4 | 874 |

\* Startup column has the same routing-race caveat; separate controlled cold starts follow.

Two-run arithmetic means below summarize the runs; mean reported percentiles are not pooled
percentiles and are not used to claim statistical significance from two samples.

| Scenario | Janky % before → after | p50 / p95 ms before → after | Deadline misses before → after | CPU % core before → after | PSS MiB before → after |
| --- | --- | --- | --- | --- | --- |
| idle | 0.00 → 0.00 | 6.0 / 7.0 → 6.0 / 7.5 | 0.0 → 0.0 | 24.35 → 24.42 | 137.9 → 129.7 |
| dock | 88.06 → 87.97 | 17.5 / 29.5 → 18.5 / 32.0 | 10.5 → 16.5 | 36.41 → 39.10 | 342.4 → 342.8 |
| navigation | 75.02 → 52.62 | 17.0 / 27.0 → 16.0 / 28.0 | 7.5 → 3.5 | 23.98 → 23.65 | 207.6 → 216.4 |
| grid | 93.70 → 93.82 | 21.0 / 34.0 → 21.5 / 34.0 | 51.0 → 54.0 | 45.23 → 45.52 | 322.4 → 319.2 |
| app-return | 71.08 → 73.14 | 16.5 / 30.0 → 16.5 / 27.5 | 7.5 → 8.0 | 6.29 → 7.86 | 70.0 → 76.6 |

**Interpretation:** idle cost is essentially unchanged. Navigation reports fewer janky frames
and missed deadlines, but dock/grid frame performance is not materially improved; dock CPU
and missed deadlines are higher in these final samples. App-return CPU/frame counts also rise;
foreground duration varies with Home on Fire and application scheduling, so that aggregate
window is not a precise per-frame cost comparison. No general FPS, memory or power improvement
is claimed. The large memory savings in experiments A/B were deliberately rejected because
of their deadline regressions. Zero-jank acceptance during navigation remains **unmet**.

The retained changes bound animation ownership and avoid redundant calculation/allocation,
but do not remove the dominant buffer/RenderThread waits. A future rendering change needs a
separate controlled GPU/driver investigation; changing global display or animation settings
would conceal rather than solve this result. This milestone's performance outcome is a
specific pipeline diagnosis and lifecycle/interaction fixes, not a claimed frame-rate cure.

### Independent system trace and controlled startup comparison

The same extra ten-key dock sweep was traced after optimization, outside the benchmark windows.
The animation span median fell from 0.465 to 0.347 ms (p95 1.154 to 1.142 ms); mean animation
span duration fell from 0.508 to 0.457 ms across 714/743 samples. This is a limited measurement
of reduced animation work in one traced sweep, not a general frame-rate gain. Texture uploads
were essentially unchanged (370/369) and layer flush counts 298/301. RenderThread DrawFrame
median stayed near a refresh period (16.46/16.64 ms); dequeue median increased 3.89→5.39 ms.
This reinforces the remaining buffer/pipeline bottleneck and explains why shaving animation
math did not cure the aggregate jank metric. Trace overhead and differing phase alignment
mean these traced numbers must not be substituted for the untraced scenario results.

For a cleaner startup comparison, temporarily reinstalled the saved baseline APK, then the
final APK, using the same package/signing key and `adb install -r` throughout. With YouTube
foreground, `am start -S -W` force-started only Material Home three times per version. Every
start reported COLD. Baseline TotalTime: **1117, 847, 844 ms**; final: **859, 773, 784 ms**.
Medians: **847→784 ms**, within this small controlled sample. Host command wall times were
1559/1218/1246 ms and 1244/1269/1149 ms respectively. No statistically significant startup
speedup is asserted. The final APK was restored and launched after the comparison.

## Lifecycle and stability checks

Twelve alternating YouTube/Netflix foreground-and-Home cycles ran in one launcher process.
Home on Fire returned to Material Home on every Home press. Process PID stayed unchanged;
meminfo retained **1 Activity, 1 ViewRootImpl and 271 Views** at cycles 0/4/8/12. PSS was
200.5/66.0/63.2/60.9 MiB, and Java heap 4.2/4.1/5.5/4.8 MiB. The initial PSS includes warm
rendering caches that get released after foreground app switches; this is not claimed as
an optimization's memory saving. No increasing view/activity count or large retained-memory
trend appeared in this bounded test. It does not prove absence of every possible leak.

While YouTube stayed foreground, launcher gfxinfo remained **0→0 frames** across five seconds.
Process CPU over that interval was 0.582% of one core, including cleanup/system activity;
no claim of absolute zero background CPU is made. After all twelve returns, a same-process
15-second idle check rendered 919 frames, 2 janky (0.22%), p50/p95 5/7 ms, 1 missed deadline,
and 20.69% of one core. No growing frame-rate/CPU workload indicative of duplicate animation
loops appeared. The activity owns one drift callback and removes it before reposting on
resume; timed callbacks are removed on pause and all Handler callbacks on destroy. Existing
media authorization/listener behavior remains unchanged and unenabled.

Detached tile cleanup cancels its owned corner/ring animators and clears registries; custom
indicator invalidation requires attached/shown/foreground state. These indicator lifecycle
paths were reviewed and compiled, but live Now Playing indicator behavior was not activated
or claimed tested. No screensaver, usage ordering or media feature work was introduced.

## Final focus-condition correction and installed APK

Screenshot review after the full rendering matrix exposed one remaining lifecycle issue:
`fillDock()`'s cached branch requested the first tile whenever the dock itself lacked focus,
even if a nav tab was focused. That queued a first-app retint during return from Settings.
Changed the fallback to run only when `getCurrentFocus() == null`. The working Settings
intent/implementation is untouched. Three Settings→Back cycles on the installed correction
kept Settings focused and retained the previous Apple TV title/palette, verified in full-size
screenshots (`visual-focus-fix/`) and resumed-activity records.

The full matrix, traced sweep, controlled-start comparison and 12-cycle stress test above
used rendering-test APK SHA-256
`a2f674262983180bff3f8008f9ce5c5c0a48c6d513b2619ad6d29e8c96f544b3`.
The final correction changes only the cached-dock focus predicate; render algorithms, layer
policy, animation cleanup and Settings implementation are identical. After rebuilding and
updating, repeated the two affected workloads twice with the same profiler:

| Run | Scenario | Frames | Janky % | p50 / p95 ms | Deadline misses | CPU % core | PSS MiB |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: |
| 1 | navigation | 764 | 52.49 | 16 / 19 | 4 | 24.07 | 192.3 |
| 1 | app-return | 228 | 8.33 | 5 / 40 | 11 | 7.49 | 71.5 |
| 2 | navigation | 760 | 45.79 | 13 / 19 | 0 | 23.43 | 207.2 |
| 2 | app-return | 247 | 54.66 | 16 / 23 | 5 | 7.75 | 72.9 |

These follow-up results show reduced redundant return work and phase-sensitive buffer timing,
not proof of a general FPS improvement. Original untraced results remain above, unaltered.
The final head has both focused-workload reruns and three Settings-return checks; the broader
unchanged-render-path and bounded stress results are explicitly tied to the preceding APK.

**Installed artifact:** `build/material-home-fireos.apk`, 4,085,423 bytes, SHA-256
`e52cc175e7237f692795b66c99558b1891b375489f669d73bb4fd95c7ade34ee`.
Installed base APK hash matches. Package `dev.karat.materialhome`, min/compile API 30 and
target 34 remain unchanged. The APK signer certificate matches the baseline. Build, signing,
update install, API lint and diff checks passed. No permissions were added.

### Remote, appearance and system isolation

Full-resolution 1920 × 1080 captures outside performance windows cover all six dock focus
states, all three nav tabs, grid/top/five downward scroll states, Apple TV selection and
preserved focus after Home, and native Settings/Back. A rapid 23-key burst settled on the
expected third dock item with a visible focus ring/name; Home/Apps, Menu, Back and Select
continued working. No photon-level or physical handset input-latency measurement is claimed;
shell key-process startup makes command wall time unsuitable for that measurement.

The user subsequently confirmed **all checked functions work normally** in response to the
handset checklist covering rapid D-pad navigation, Alexa voice, Spotify playback, Bluetooth
remote/audio and Wi-Fi streaming. This is user confirmation, not an instrumented voice/audio
benchmark. Read-only device checks also showed Wi-Fi connected, Bluetooth enabled/connected
and no fork crash-log entry. The existing Home on Fire route resumed Material Home throughout
all scripted Home checks. Alexa, Spotify, radio services and system packages were not modified.

Before/after comparisons confirm unchanged accessibility registration/enabled state,
notification-listener registration, dream selection/enabled state, window/transition/animator
scales and display mode. Manifest and Settings method remain unchanged. No root, privileged
app permissions, Home-role change, Home on Fire configuration change, playback service grants
or system-package mutation was used. Battery/power effects were not measured.

### Animation ownership inventory

| Work | Scheduling/ownership | Pause/detach/destroy handling |
| --- | --- | --- |
| Background rotation | One display-frame Runnable; same 3 degrees/second | Remove before posting on resume; stop/remove on pause. |
| Clock/date | One 15-second Handler tick, bounded weight/opacity transitions | Remove tick; end/cancel transitions and normalize opacity offscreen. |
| Focus settle / app prebuild | One debounced 120 ms settle / 1500 ms prebuild | Remove on pause; pending focus settle resumes once, all Handler work removed on destroy. |
| Shape/title/corner/ring | Bounded spatial/effects animators, one owner per property | Cancel superseded work; end on pause; tile-owned work cancels on detach. |
| Palette fades / nav | Existing cached palettes; effects animator / ButtonGroup ownership | End on pause; ButtonGroup also stops on detach. |
| Delayed loader | One 300 ms callback plus owned repeat animator | Remove/cancel and clear references/end actions on pause/hide. |
| Palette warmup | At most one worker; cached seeds | Check pause/interruption between items; interrupt on destroy. |
| Equalizer / wavy progress | Existing draw-frame invalidation, attachment/visibility/foreground gates | No recurring draw while paused/detached; no optional media access enabled. |
| HDMI refresh callbacks | Disabled in this Fire OS port | No input-monitor registration. |
| Screensaver / ShapeScene | Separate DreamService; reviewed only | No changes or activation in this milestone. |

There is no new direct Choreographer loop or external input interception. The app's main
remaining limitation is driver/buffer scheduling under dock/grid transitions, with meaningful
HWUI layer/texture cost. Zero-jank navigation, broad FPS gains and lower power consumption
are not claimed. No new feature or reduced-motion mode was added to mask that limitation.
