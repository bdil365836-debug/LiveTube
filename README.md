 # LiveTube

A minimal Android YouTube player built for podcasts: add channels and playlists to a **Library**, tap any video, and keep listening with the screen off — or switch to **audio-only** to save data and battery.

```
Paste a link  →  Play  →  Close your phone →  keeps playing
```

## Features

- **Library** — save channels and playlists with a `+`; browse them by title + thumbnail
- **Pagination** — infinite scroll through a channel/playlist feed, or **Load all** to cache every page
- **Audio-only mode** — plays just the audio stream (no video) to save bandwidth and battery. Default on.
- **Background playback** — a Media3 foreground service keeps playing with the screen off, with a media notification (play / pause / stop) and lock-screen media controls
- **Video downloads** — save the currently playing or pasted YouTube video to the device's Downloads folder using Android's system download manager
- **Auto-play next** — optional toggle to keep playing through a channel/playlist queue
- **Never AI-dubbed** — when a video has multi-language AI dubbing, only the original language audio track is played
- **Live streams** — plays YouTube live streams, with periodic refresh so the URL doesn't expire
- **Direct media URLs** — the Player tab also accepts raw podcast/audio URLs

## Requirements

- Android 8.0+ (API 26+); works best on Android 10+
- A YouTube link to start with :)

## Install

Download the latest `app-release.apk` from the [Releases page](https://github.com/20plays/LiveTube/releases), allow "Install unknown apps" for your browser/download manager, and open the APK.

On first launch, grant the notification permission so the playback notification shows up. Android 8 and 9 also ask for legacy storage permission the first time you save a video to Downloads.

## Build from source

```bash
# JDK 17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # path depends on your system
./gradlew assembleRelease
```

The signed release APK is produced at `app/build/outputs/apk/release/app-release.apk`.

> **Signing:** signing secrets are *not* in this repository (see `.gitignore`). To produce a release build locally you need your own keystore with a `keystore.properties` file at the repo root:
>
> ```properties
> storeFile=keystore/livetube.jks
> storePassword=<password>
> keyAlias=<alias>
> keyPassword=<password>
> ```
>
> Keep that keystore backed up — upgrading an installed app requires signing with the same key.

## Manual GitHub releases

The repository includes a **Build and Release APK** GitHub Action that you can start manually from the Actions tab. It builds the signed release APK, verifies its signature, creates a Git tag from `versionName`, publishes a GitHub Release with generated release notes, and attaches both the APK and a SHA-256 checksum.

### One-time signing setup

The release action uses a single repository secret named `ANDROID_SIGNING_BUNDLE_BASE64`. It contains both `keystore.properties` and `keystore/livetube.jks`, so there is only one value to configure.

Create the bundle on Linux:

```bash
mkdir -p /tmp/livetube-signing/keystore
cp keystore.properties /tmp/livetube-signing/
cp keystore/livetube.jks /tmp/livetube-signing/keystore/
tar -C /tmp/livetube-signing -czf - keystore.properties keystore/livetube.jks | base64 -w 0
```

Add the resulting one-line value under **Settings → Secrets and variables → Actions** as `ANDROID_SIGNING_BUNDLE_BASE64`.

The workflow also verifies the release APK against LiveTube's expected signing-certificate SHA-256 fingerprint before publishing. Never commit the keystore, signing bundle, or passwords to the repository.

### Publishing a release

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Merge that change into `main`.
3. Open **Actions → Build and Release APK → Run workflow**.
4. Select `main`, optionally mark it as a pre-release, and run it.

For `versionName = "1.1"`, the workflow creates tag `v1.1` and uploads `LiveTube-v1.1.apk`. It refuses to overwrite an existing tag, so every release needs a new version.

## Usage

- **Library tab** — tap `+`, paste a channel or playlist URL, it's validated and saved. Tap an item to open its feed; tap a video to play it.
- **Player tab** — paste any YouTube link (or a direct media URL) and press **Play**. Toggle **Audio only** to strip video, and **Auto-play next** to keep a list playing.
- **Download** — while a YouTube video is playing, or after pasting its URL, press **Download**. LiveTube resolves the best single-file stream containing both video and audio and hands it to Android's download manager. The file appears in Downloads and Android shows system download progress.

### Download quality

YouTube often serves higher resolutions as separate video-only and audio-only streams. LiveTube intentionally does not merge those tracks yet; downloads use the highest-quality stream that already contains both video and audio. This keeps downloads reliable and avoids bundling a media transcoder.

Live streams cannot be downloaded yet.

## Privacy

- No accounts, no telemetry, no ads. Everything is stored on-device (Room database).
- Requires network access to YouTube to fetch feeds and streams.
- Downloads are handled by Android's built-in download manager.

## Disclaimer

This app plays and downloads YouTube content through unofficial extraction of stream URLs and isn't endorsed by YouTube/Google. Background playback, audio-only streaming, and downloading YouTube content may not be permitted by YouTube's Terms of Service and may also be restricted by copyright — only download content you have the right to save. The extraction technique can break if YouTube changes its internals.

## License

See the `LICENSE` file, if present. This project's public code is provided as-is for personal use
