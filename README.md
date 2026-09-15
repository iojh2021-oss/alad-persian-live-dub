# ALAD Persian Live Dub

Android real-time English → Persian (Farsi) AI dubbing for YouTube and other Android apps.

## How it works

`YouTube/app playback → Android AudioPlaybackCapture → 16 kHz PCM → Gemini Live API → Persian 24 kHz PCM → AudioTrack`

The app does not read subtitles. It captures the other app's playback audio through Android's MediaProjection/AudioPlaybackCapture APIs, sends it to Gemini Live, and plays the generated Persian speech while requesting audio ducking for the original media.

## Gemini Live integration

This fork uses the current `gemini-3.1-flash-live-preview` model and the documented WebSocket Live API format. Input is raw 16-bit PCM at 16 kHz and Gemini audio output is raw 16-bit PCM at 24 kHz. Persian translation is enforced through the Live API system instruction rather than the old `translationConfig` used by the original ALAD-Mobile implementation.

Session resumption handles from `sessionResumptionUpdate.newHandle` are retained and reused after reconnects.

## Build

GitHub Actions builds a debug APK on every push to `main` and uploads it as `alad-persian-live-dub-debug`.

## Run

1. Create a Gemini API key in Google AI Studio.
2. Install the APK.
3. Enter the API key.
4. Tap **Start dubbing** and approve audio/screen capture.
5. Open the official YouTube Android app and play an English video.
6. Stop dubbing when finished.

For personal use, an API key can be stored locally in the app. For a public production release, use a backend-issued ephemeral token instead of shipping a long-lived API key in a client.

## Source

The project is based on the open-source architecture of `navidseyedain/ALAD-Mobile`, with the Gemini Live transport migrated to the current API and the app simplified around Persian live dubbing.
