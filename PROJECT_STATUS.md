# ExplApp Mirror - Project Status

## Current stage

The project is now a hybrid casting app: DLNA/UPnP for receivers and media-capable TVs, plus a Miracast / AnyView entry path for screens that rely on Android's wireless display feature.

## Completed

- Android project skeleton.
- Local network scanner.
- SSDP / UPnP discovery scanner.
- Device description parsing.
- Device type detection for Chromecast, DLNA, UPnP, AnyView and unknown devices.
- Rich `CastDevice` model with manufacturer, model, UUID, service URLs, DLNA support and volume support.
- Connection tester with common media/cast ports.
- Device list UI with real device details.
- Device selection state.
- Multi-image picker.
- Multi-video picker.
- Playback queue.
- Previous / play / next controls.
- Pause / resume / stop controls.
- Volume up/down controls when `RenderingControl` is available.
- Local temporary media server.
- HTTP Range support for videos.
- Local server diagnostics: request count, last client IP, method, path, range, HTTP status and bytes sent.
- DLNA AVTransport controller.
- DLNA RenderingControl volume command.
- Better DLNA metadata for images vs videos.
- In-app diagnostics panel.
- Miracast / AnyView launcher path.
- Wi-Fi Direct launcher path.
- Mirroring availability summary inside the UI.
- APK build workflow.
- APK artifact upload in GitHub Actions.
- APK download placeholder page.

## Latest hybrid mode improvements

- `MirroringLauncher.kt`
  - Opens the best available Android mirroring screen.
  - Tries Wi-Fi Display, Cast, Wireless Display, Settings WifiDisplay Activity, Wireless Settings, Wi-Fi Settings and Main Settings.
  - Adds a separate Wi-Fi Direct path for screens that require pairing through Wi-Fi Direct.
  - Shows an availability summary so the app tells the user what the phone exposes.

- `MainActivity.kt`
  - Adds a third top-level button: `فتح Wi‑Fi Direct`.
  - Shows a clear hybrid mode: DLNA for media, AnyView/Miracast for full screen mirroring.
  - Keeps DLNA queue and playback controls separate from screen mirroring.

## Important Android limitation

Android generally does not allow a normal third-party app to start Miracast silently without user confirmation. The app can open the correct system page and guide the user to choose the display, but the final connection must usually be accepted through Android's system UI.

## How to choose the path

- Use `بحث DLNA` for Ghazal receiver and any TV/receiver that appears as DLNA/UPnP.
- Use `فتح مرآة الشاشة / AnyView` for G-Guard and screens that show AnyView Cast.
- Use `فتح Wi‑Fi Direct` if the screen needs Wi-Fi Direct pairing before mirroring.

## Next step

1. Build and install the updated APK.
2. Test Ghazal through DLNA with diagnostics.
3. Test G-Guard through AnyView/Mirroring and Wi-Fi Direct buttons.
4. If the phone opens the correct mirror page, use Android system UI to select G-Guard.
5. If the phone does not expose mirroring settings, keep DLNA as the media path and note that Miracast may be disabled by the phone manufacturer.

## Approximate progress

- Project setup: 100%
- Device discovery: 97%
- Device details parsing: 90%
- Connection testing: 90%
- Device UI: 95%
- Media selection: 95%
- Playback queue: 85%
- Local media server: 92%
- HTTP Range video support: 82%
- Diagnostics: 85%
- DLNA playback attempt: 76%
- Playback control: 70%
- Volume control: 65%
- Miracast / AnyView entry path: 65%
- Wi-Fi Direct entry path: 60%
- APK workflow: 85%
- Chromecast route: 15%
- Direct silent screen mirroring: limited by Android system permissions

Overall project progress: about 86%.
