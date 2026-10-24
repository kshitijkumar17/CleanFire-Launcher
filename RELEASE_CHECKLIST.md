# CleanFire Launcher 1.0.0 release checklist

Prepared 2026-10-24 for manual review. **Candidate APK exists; publication remains on hold** until the maintainer backs up the signing identity and accepts the limits below. No GitHub repository, push, or Release was created.

## Completed and verified

- Audited Java, manifest, resources, scripts, docs, tests, vendored code, tracked history, and ignore rules. Temporary per-file audit: local ignored `artifacts/release-audit.md`.
- Removed `install.sh`, `uninstall.sh`, `res/drawable/icon.xml`, `res/drawable/banner.xml`, `docs/home-playing.png`, and `docs/screensaver.png` after confirming no current build/runtime references. `./build.sh` passed after the cleanup.
- Kept the manifest package `dev.karat.materialhome`, API 30 minimum, Java names, Home on Fire configuration, stock Fire TV launcher, user preferences, Apache 2.0 upstream notice, material-color-utilities license, and Google Sans Flex license.
- Kept ipdata.co / Open-Meteo attribution beside the README Home screenshot and in its weather section. The Home widget keeps its two-line layout. Current HTTPS clients are keyless, bounded, and active only while Home is visible. `WeatherPolicyTest` passed 16 checks; an on-device process restart showed the cached forecast and city.
- Replaced the upstream-focused README with current Fire OS instructions and the actual 1920×1080 AFTKM Home capture at `docs/images/home.png` (399 KiB, inspected for personal data; it shows app artwork and approximate city only).
- Added `./build.sh --debug` and `--release`; no-argument debug remains supported. Release mode uses `javac -g:none`, D8 `--release`, and a required persistent keystore outside Git. No minification/obfuscation was enabled.
- Built and signed `release/CleanFire-Launcher-v1.0.0.apk`, versionName 1.0.0 / versionCode 184. `apksigner verify --verbose --print-certs` passed with one v3 signer. Manifest has no `android:debuggable=true`; application ID, CleanFire label, square icon and 16:9 banner resolve in `aapt2 dump badging`. APK includes license/notice assets and no WeatherAPI key asset.
- Release SHA-256: `14f625b39ee11d1271dc94fcf7c8e77db1c9b91324da79d12cd0c804ad0ed67a`. Certificate SHA-256 fingerprint: `712df78b2c801ab1dd1b083fcb082291e348b55f58907c471877bb3980c5f26f`. `release/SHA256SUMS.txt` verifies the APK.
- Release identity was created once at `~/.config/cleanfire-launcher/release.jks`; its ignored password file is `~/.config/cleanfire-launcher/release-signing.env`. Both are mode 0600. Neither is in the repository. **Back up both securely before publishing.**
- `bash -n` for the active scripts, host Java policy checks, `git diff --check`, archive inspection, and local source/path/IP/credential scans passed. Scanned all 22 reachable Git commits for common key/token/private-key patterns; no match was found. No history rewrite was performed.
- Debug build versionCode 184 installed as an update on AFTKM without clearing data. Direct launch and six Home apps worked; short Select launched YouTube, long Select showed Move/Replace, Apps edit mode showed hidden badges, Back returned to Apps, and All Settings opened native `com.amazon.tv.launcher/.ui.MainSettingsActivity`. Full-resolution test captures are ignored under `artifacts/fireos/release-audit-*`.
- Controlled 15-second AFTKM idle profile: 915 frames, 0% janky frames, 0 deadline misses; the sampled dock sweep: 1482 frames, 90.01% framework-classified janky frames, 8 deadline misses. These results align with the earlier `PERFORMANCE_REPORT.md`; no general zero-jank claim is made.

## Retained despite uncertainty

`HomeKeyService.java`, `MediaListener.java`, `EqualizerBadge.java`, `WavyProgress.java`, `TemperatureRangeView.java`, and `res/xml/home_key_service.xml` are dormant upstream/earlier-port implementation; their components are not enabled in the current manifest. `Screensaver.java`/`ShapeScene.java` remain because the dream service is declared. `vendor_mcu.sh`, test/profiling tools, porting logs, vendored color code, and font/legal files remain for contributors or attribution. Local ignored `weatherapi.key` is historical and unused; it was not deleted from the owner's machine.

## Security and provider review

The manifest requests only `INTERNET`, network/Wi-Fi state, optional foreground location for the Settings Wi-Fi SSID, and usage access. No root, privileged, accessibility, or hidden-API dependency is declared. Current source uses only `https://ipdata.free/` and `https://api.open-meteo.com/v1/forecast` as network clients. It does not log IP addresses, SSIDs, keys, or app inventory. The old upstream scripts that could change settings were removed from the fork.

[ipdata.free](https://ipdata.co/free) currently permits 1,000 keyless lookups per calling IP daily and requests attribution near data; the README places it beside the Home screenshot and in the weather section. [Open-Meteo's free terms](https://open-meteo.com/en/terms) restrict the free endpoint to non-commercial use, with limits below 10,000 calls/day, 5,000/hour and 600/minute plus CC BY attribution. Review provider attribution requirements before publication, especially because the device widget no longer displays provider names. Approximate IP location is not guaranteed accurate, especially with VPNs.

## Implemented but untested or blocked

- The release-signed APK could not be installed as an update on the debug-signed AFTKM: Android signing identities differ. The working launcher was not uninstalled. Runtime tests above apply to the same source code in a debug-signed APK, **not** to the release signature on device. Fresh-install verification on a spare device or after an explicit migration plan remains outstanding.
- A fresh-install weather lookup and Home Move/Replace persistence were not repeated in this release pass because they would require clearing data or changing the owner's saved app order. Earlier AFTKM tests and live provider responses are in `DEVELOPMENT_LOG.md`; this pass verified cached weather and opened the management controls.
- The GitHub Actions workflow is written and its commands were run locally; no GitHub-hosted CI job has run because the new repository does not exist yet.
- Current physical Home-button routing was not retested because the owner temporarily changed their Home on Fire mapping. The separate Home on Fire accessibility service remained enabled/bound after the previous branding reinstall; this release did not alter it.
- Fire OS's app row visually uses the square icon inside a wide tile although the 16:9 banner is correctly packaged. Other device behavior is unknown.
- Zero janky frames under rapid D-pad movement is still unmet. The current measured dock sweep was 90.01% framework-classified janky, with 8 deadline misses. See `PERFORMANCE_REPORT.md` for pipeline analysis; a performance fix is separate work.
- The optional screensaver and native Wi-Fi/Bluetooth Settings destinations were not retested in this release pass; earlier device results are in `DEVELOPMENT_LOG.md`.

## Remaining user actions before publication

1. Back up `~/.config/cleanfire-launcher/release.jks` and its password securely, apart from the development Mac. Losing the key prevents future updates under this application ID.
2. Review the README screenshot and source for public privacy preferences. It contains approximate Pune weather and installed-app artwork, but no network name or account notification.
   Confirm redistribution rights for the supplied CleanFire logo assets before publication.
3. Decide how existing debug-signed testers will migrate. Android cannot install this release APK over their current app without a matching certificate; uninstalling can erase preferences and require Home on Fire to be checked again.
4. Run the GitHub Actions build after creating a new repository. Optionally test the release APK on a spare Fire TV or clean profile before publishing.
5. Create/push the new repository and GitHub Release manually. The release directory is ignored by Git; attach its APK, `SHA256SUMS.txt`, and `RELEASE_NOTES.md` to the Release. GitHub will generate source archives from the tag.

## Locations

| Item | Path or status |
|---|---|
| README | `README.md` |
| Real Home screenshot | `docs/images/home.png` |
| Candidate APK | `release/CleanFire-Launcher-v1.0.0.apk` |
| Checksums | `release/SHA256SUMS.txt` |
| Release notes | `RELEASE_NOTES.md` and `release/RELEASE_NOTES.md` |
| CI workflow | `.github/workflows/build.yml` (local validation only) |
| Current installed build | Debug-signed 1.0.0 / 184 on AFTKM |
