# Contributing

Thanks for helping. The launcher stays deliberately small: plain Java, no Gradle, no dependencies beyond the
vendored color library, with network use limited to keyless weather. It should run well on modest TVs.

## 1. Report a tested TV

The most useful contribution. Open an issue with:

- The exact model, Android version and firmware (`adb shell getprop ro.product.model`,
  `ro.build.version.release`, `ro.build.display.id`).
- Whether direct launch and optional Home on Fire routing worked, and anything odd.
- A screenshot of Home: `adb exec-out screencap -p > home.png`.

## 2. Improve the launcher

Fits the direction: better icon fallbacks, localization, and D-pad behavior at the edges.

Doesn't fit: recommendation rows, content feeds, ads, telemetry, or network uses beyond weather.

Rules of thumb:

- Nothing animates while another app is on screen; no polling or background services.
- Nothing specific to one person's TV in code: read it from the device (installed apps, usage).
- Use the shape and focus system in `Focus.java` (cards 32 dp, media 20 dp, pill buttons, the focus ring).
- Keep weather keyless and retain visible provider attribution. Do not add secrets, IP addresses, or signing material to Git.
- Test on a real TV, not only an emulator, and attach a before/after screenshot to the pull request.

Build with `./build.sh --debug` (see the README); release signing is separate.

## Pull requests

- One topic per pull request. Small is fine.
- Say which TV you tested on.
- By contributing you agree your work is released under the Apache License 2.0.
