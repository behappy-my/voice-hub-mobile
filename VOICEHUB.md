# Voice Hub Android fork

Upstream source: `music-assistant/mobile-app`, fixed at
`f64a15da1604226aac9f2dbe36ca2dbc3b914d19` (development version 0.14.0).
This is a custom build, not an official Music Assistant release.

## Install

Package `io.music_assistant.client.voicehub`, label **Voice Hub 音乐**.
It installs alongside the official app. Connect to `https://music.my1005.cn`
and log in again. Open Library → 有声书 for categories and collections.
The **列表** button preserves the original flat library.
Use only one app's local player at a time during comparison.

Version `0.14.0-voicehub.2` (code 16) uses the approved Voice Hub artwork:
an open book, sound ribbons and a play symbol on midnight blue. The original
generated PNG is retained unchanged in `drawable-nodpi/voicehub_icon_art.png`;
Android applies an 18 dp inset inside the 108 dp adaptive icon layer for circular
and squircle masks. Android 9+ supports adaptive icons, so obsolete density-specific
Music Assistant launcher bitmaps are removed. A matching alpha-only vector serves
Android 13 themed icons and notification small icons. The Play Store artwork is
also updated. Generation used the built-in image tool with the direction:
"modern high-contrast open book and sound-wave emblem, cyan-to-violet gradient,
central play symbol, midnight background, no text".

This update keeps the same application ID and private release signing certificate;
install it directly over `0.14.0-voicehub.1` to retain login and player settings.

The bookshelf consumes authenticated `voicehub/spoken/shelves` on the customized
MA server. It uses native navigation, artwork proxy, detail screens and player
commands. It does not mirror the web UI in a WebView or download the catalog's
source audio. Book/podcast IDs remain distinct, groups expand before playback.

Two reconnect defects are fixed: a single connection attempt's timeout enters
the retry policy rather than cancelling Sendspin's supervisor, and WebRTC/data
channel cleanup completes even after cancellation. Real phone background and
car Bluetooth behavior still needs physical validation.

## Build

JDK 21, Android platform 37.0, build-tools 36.0.0; Gradle via the repository wrapper.

```sh
./gradlew :androidApp:assembleDebug
./gradlew testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:lintDebug
./gradlew :androidApp:assembleSelfSignedRelease
```

Release signing uses private properties at
`~/.config/voicehub-mobile/signing.properties`, or the path in
`VOICEHUB_SIGNING_PROPERTIES`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Keep the certificate for future updates. Never commit private keys or properties.
Release APK is arm64, non-debuggable and resource/code shrunk. Debug APK uses
the normal debug certificate and is for local development only; do not mix its
signature with the release install.

A sideloaded Android Auto app may require enabling Android Auto developer
settings → unknown sources. Ordinary Bluetooth controls use the media session
and do not require Android Auto. This package changes notification/shortcut
routing to itself so it cannot inadvertently control the installed official app.

## Validation

Server: stable MA 2.10.4, schema 65, frontend 2.17.297, aiosendspin 9.1.1.
Shared tests cover live bookshelf response decoding, paged book/podcast identity,
stale request protection, group return navigation and recoverable errors.
Android rendering tests cover category → edition group → native detail ID and
an explicit original-list escape on an unsupported server.
Connection regression tests cover repeated attempt timeouts and closure from an
already cancelled coroutine; upstream host tests are retained.

Local validation (2026-09-30): 200 Sendspin, 581 shared and 130 Android
unit/rendering tests pass; lint reports zero errors and seven existing
compatibility/network/packaging warnings. See docs/voicehub-android-validation.json.
No tests play audio through the physical phone or car.
