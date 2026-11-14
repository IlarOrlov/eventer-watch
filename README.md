# EventerWatch

Android app that polls eventer.co.il every ~30 s in a foreground service. When a new event appears
(or an event flips to on-sale, best effort) it rings a full-screen alarm and opens the event page.
For the "מסיבתה" party line it also finds new events the homepage never lists and opens an auto-filling checkout.
The checkout assistant always stops at payment, CAPTCHA/verification and any question it cannot answer.

## Build

    ./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease

Signed release APK: `app/build/outputs/apk/release/app-release.apk` (sideload; min SDK 26).
The signing key lives outside the repo in `~/.eventerwatch-signing/` (back it up; without it updates can't be installed over an existing install).
If a debug build is already installed, uninstall it first: debug and release use different signatures.

## Installing on a customer's phone

See [docs/INSTALL.md](docs/INSTALL.md).

## Install on the Galaxy S23

1. `adb install -r app-release.apk` (or copy the APK to the phone and open it).
2. Open the app and work through the **Setup** checklist: notifications, full-screen alarms,
   battery optimization, and the Samsung steps (remove from "Sleeping apps"/"Deep sleeping apps",
   turn off "Put unused apps to sleep", set battery to Unrestricted).
3. Tap **Preview in 10 s**, lock the screen, and wait: the screen should wake, the alarm should sound and
   the event page should open.
4. Tap **Start watching**. The first check seeds silently; later checks alert.

## How it works

- Source: `GET /sliders/list.js` (public homepage sliders). See `docs/feed-notes.md`.
- `core/` is pure Kotlin (parser, diff, backoff, health, poller, loop) with JVM tests.
- `app/` is the Android shell (service, alarm, snapshot store, UI).
- Empty feeds, Cloudflare pages and garbage count as failures, never as "all events removed".
  After 5 consecutive failures a distinct "Watcher is BLIND" alarm fires; a recovery notice follows.

## The "מסיבתה" series (auto-checkout)

- **Buyer details** are entered in the app (Buyer details). They are stored AES-GCM encrypted in the app's private
  storage using an Android Keystore key. They are never in this repository, never uploaded, and only ever injected
  into `eventer.co.il` pages.
- **Finding new events.** These events are hidden from Eventer's homepage, so three signals are combined:
  the newest sitemap file (every ~60 s), probing the next likely link names (`mesibatapijama4`, ...; a real link
  answers 200, unknown ones 403) a few per 30 s cycle, and the homepage feed filtered for the series.
  Nothing can promise to be first or to catch a link whose name breaks the pattern; the organizer controls that.
- **Checkout.** The alarm opens the event in an in-app browser. `app/src/main/assets/checkout.js` (tested with
  `cd checkout-js && npm install && npm test`) waits for tickets, selects the quantity, presses continue, fills every
  guest form and the event's questions, ticks the compulsory checkboxes and continues. It stops and rings at the
  payment page, at a CAPTCHA, and at any required question it cannot answer. It never touches payment frames.
- Because the live checkout could not be exercised in development, run a rehearsal on a real event link first
  (Buying > Open in checkout) on an event that is currently on sale and watch the banner; it never presses the final pay button.

## Known limits

- Only events featured on the homepage sliders are visible to the watcher. A brand-new event that
  is not featured yet will be missed until it is.
- There is no public "tickets on sale" field. `onSale` is derived from slide visibility, so
  "sale opened" alerts are weak. A pre-sale event (`nye2027`) showed no public field that changes when sales open (see `docs/feed-notes.md`).
- "Blind" fires about 2 minutes after the feed breaks; after that the check interval backs off to at most 10 minutes.

## Acceptance status

| Check | Status |
|---|---|
| Unit tests (Kotlin) and script tests (`checkout-js`) | passing |
| Signed release APK builds | yes |
| Alarm on locked device, blind/recovery, sounds | checked on the S23 |
| Series discovery against the live site | adapters tested with a mock server; the live sitemap and link probes were checked by hand |
| Waiting mode on a closed sale (`mesibatapijama`) | works on the S23 (page loads, nothing to buy, keeps waiting) |
| Auto-fill checkout on a live Eventer page | **not yet verified**: needs an event with tickets on sale (see docs/INSTALL.md) |
| 24-hour run | **not yet done**; record the result here |
