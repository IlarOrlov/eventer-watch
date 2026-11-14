# EventerWatch (Stage 1)

Android app that polls eventer.co.il every ~30 s in a foreground service. When a new event appears
(or an event flips to on-sale, best effort) it rings a full-screen alarm and opens the event page.
Stage 2 (assisted checkout) is not built; it will always stop at payment, CAPTCHA and 3DS.

## Build

    ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug

APK: `app/build/outputs/apk/debug/app-debug.apk` (sideload; min SDK 26).

## Install on the Galaxy S23

1. `adb install -r app-debug.apk` (or copy the APK to the phone and open it).
2. Open the app and work through the **Setup** checklist: notifications, full-screen alarms,
   battery optimization, and the Samsung steps (remove from "Sleeping apps"/"Deep sleeping apps",
   turn off "Put unused apps to sleep", set battery to Unrestricted).
3. Tap **Test alarm** with the screen locked: the screen should wake, the alarm should sound and
   the event page should open.
4. Tap **Start watching**. The first check seeds silently; later checks alert.

## How it works

- Source: `GET /sliders/list.js` (public homepage sliders). See `docs/feed-notes.md`.
- `core/` is pure Kotlin (parser, diff, backoff, health, poller, loop) with JVM tests.
- `app/` is the Android shell (service, alarm, snapshot store, UI).
- Empty feeds, Cloudflare pages and garbage count as failures, never as "all events removed".
  After 5 consecutive failures a distinct "Watcher is BLIND" alarm fires; a recovery notice follows.

## Known limits

- Only events featured on the homepage sliders are visible to the watcher. A brand-new event that
  is not featured yet will be missed until it is.
- There is no public "tickets on sale" field. `onSale` is derived from slide visibility, so
  "sale opened" alerts are weak. Providing a link to a pre-sale event would let this be verified.
- Because of exponential backoff, "blind" fires about 7-8 minutes after the feed breaks.

## Acceptance status

| Check | Status |
|---|---|
| Unit tests (46) | passing |
| Debug APK builds | yes |
| Test alarm on locked device | **not yet done** (no device attached during development) |
| 10-minute live run / airplane-mode blind test | **not yet done** |
| 24-hour run on the S23 | **not yet done** (final acceptance; record result here) |
