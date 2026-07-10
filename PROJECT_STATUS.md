# ExplApp Mirror - Project Status

## Current stage

The project is now in the advanced DLNA testing stage. The app supports richer device discovery, device details, multi-file media queue, local media serving, HTTP Range support, DLNA playback commands, volume control when supported, and an improved APK build workflow.

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
- DLNA AVTransport controller.
- DLNA RenderingControl volume command.
- APK build workflow.
- APK artifact upload in GitHub Actions.
- APK download placeholder page.

## Latest workflow improvement

- `.github/workflows/build-apk.yml`
  - Still builds `apk/explapp-mirror-debug.apk`.
  - Now also uploads the APK as a GitHub Actions artifact named `explapp-mirror-debug-apk`.
  - This gives a second download path even if committing the APK back to the repository does not happen.

## Build status note

Checks and workflow runs are not visible yet for the latest connector-created commits. This can happen when repository workflows are not triggered automatically by commits made through the app/API path. The workflow is ready and can be triggered manually from GitHub Actions using `Build APK Direct`.

## Current limitation

The app is now close to real TV playback testing, but remaining work depends on the first APK build and device test:

- Run or inspect the GitHub Actions build.
- Fix any compile error reported by Actions.
- Test with the G-Guard screen and the DLNA receiver on the same Wi-Fi.
- Tune DLNA metadata if a specific TV rejects playback.
- Add Chromecast SDK route later.
- Screen mirroring remains a separate advanced phase because Miracast support is restricted on Android.

## Next step

1. Trigger `Build APK Direct` from GitHub Actions if it does not start automatically.
2. Download `explapp-mirror-debug-apk` artifact or the direct APK in `/apk`.
3. Install on Android phone.
4. Test discovery, queue playback, volume, and pause/resume/stop against the actual TV.
5. Use the TV response/errors to tune DLNA commands.

## Approximate progress

- Project setup: 100%
- Device discovery: 97%
- Device details parsing: 90%
- Connection testing: 90%
- Device UI: 92%
- Media selection: 95%
- Playback queue: 85%
- Local media server: 88%
- HTTP Range video support: 80%
- DLNA playback attempt: 72%
- Playback control: 70%
- Volume control: 60%
- APK workflow: 85%
- Chromecast route: 15%
- Screen mirroring: 0%

Overall project progress: about 78%.
