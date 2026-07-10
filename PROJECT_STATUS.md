# ExplApp Mirror - Project Status

## Current stage

The project has moved from media selection into the first real playback attempt stage for DLNA / UPnP devices, and now has a GitHub Actions workflow prepared to build an APK.

## Completed

- Android project skeleton.
- Local network scanner.
- SSDP / UPnP discovery scanner.
- Device type detection for Chromecast, DLNA, UPnP, AnyView and unknown devices.
- Connection tester with common media/cast ports.
- Device list UI.
- Per-device connection test action.
- Image picker action per device.
- Video picker action per device.
- Initial MediaSender routing layer.
- Local temporary media server.
- Initial DLNA AVTransport controller.
- APK build workflow.
- APK download placeholder page.

## Added in this stage

- `LocalMediaServer.kt`
  - Starts a temporary HTTP server inside the Android app.
  - Serves the selected image/video through a local Wi-Fi URL.
  - Supports GET and HEAD requests.
  - Sends content type and content length when available.

- `DlnaController.kt`
  - Reads the DLNA device description URL when available.
  - Searches for AVTransport service.
  - Builds the correct control URL.
  - Sends SetAVTransportURI.
  - Sends Play.

- `MediaSender.kt`
  - Starts the local media server after selecting an image/video.
  - Generates a local media URL.
  - Attempts DLNA playback automatically for DLNA / UPnP and possible AnyView devices.
  - Shows whether the DLNA attempt succeeded or needs device-specific improvements.

- `.github/workflows/build-apk.yml`
  - Builds the Android APK using Java 17 and Gradle.
  - Copies the APK to `apk/explapp-mirror-debug.apk`.
  - Updates `DOWNLOAD_APK.md`.
  - Commits the APK back to the repository after a successful build.

## Current limitation

The DLNA playback path is now implemented as a first attempt, but real TV behavior can vary by brand. Some screens require:

- Different DLNA metadata.
- HTTP Range support for video playback and seeking.
- Separate rendering control commands.
- Chromecast SDK for Chromecast devices.
- Miracast-specific support for pure screen mirroring.

## Next step

Run the workflow and inspect the build result. If the build succeeds, test the APK on a phone and a TV on the same Wi-Fi network.

Then improve the local media server and DLNA controller by adding:

1. HTTP Range support for videos.
2. Better DLNA metadata for image vs video.
3. Stop / pause / resume commands.
4. A clearer device details screen showing protocol, services, and last test result.
5. Chromecast SDK route for Chromecast devices.

## Approximate progress

- Project setup: 100%
- Device discovery: 95%
- Connection testing: 90%
- Device UI: 80%
- Media selection: 80%
- Local media server: 70%
- DLNA playback attempt: 45%
- APK workflow: 70%
- Chromecast route: 15%
- Playback control: 15%
- Screen mirroring: 0%

Overall project progress: about 60%.
