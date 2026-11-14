# EventerWatch Stage 1 Implementation Plan

**Goal:** An Android app that polls eventer.co.il every 30 seconds in a foreground service and, on a new event or an event starting to sell, rings a full-screen alarm and opens the event page.

**Architecture:** A pure-Kotlin `core` module (parser, diff, backoff, health, poller) with all logic unit-tested against a captured feed fixture, wrapped by a thin Android `app` module (foreground service, notifications, storage, onboarding UI). Anything that can be tested on the JVM lives in `core`.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL), JUnit 5, OkHttp, kotlinx.serialization (or the JSON form the feed turns out to use), kotlinx.coroutines, Android SDK (min 26, target latest).

**Spec:** `docs/specs/2026-11-13-eventer-watch-design.md`

## Global Constraints

- Polling interval: 30 seconds with small random jitter (±3s); one request at a time; never faster.
- Backoff on errors/429/Cloudflare challenge: exponential, capped at 10 minutes.
- Alert on ALL events; no filtering.
- First run seeds the snapshot silently (no alarm).
- Detect: (a) new event id, (b) existing event `onSale` false → true.
- If N=5 consecutive polls fail or the response shape changes: distinct "watcher is blind" alert.
- Min SDK 26; Kotlin; OkHttp + coroutines; honest User-Agent (`EventerWatch/1.0`).
- Target device Samsung Galaxy S23 (Android 14+, One UI); onboarding must cover Samsung's battery features.
- No CAPTCHA/queue/Cloudflare bypass. No checkout automation in Stage 1.
- Sideloaded APK; no Play Store.

## Review Focus

- Feed returns 200 with an empty list or a Cloudflare challenge page: must read as "blind", never as "all events removed", and must not trigger an alarm storm when the real feed returns.
- Event first seen already on sale: exactly one alert, not two.
- Event disappears then reappears: no new alert unless it is a new id or flips to on-sale.
- Many new events in one poll: one alarm that opens the newest/first event; the rest listed in the app, no alarm-per-event.
- Hebrew/RTL titles and URLs with non-ASCII characters survive parse, storage, and intent launch.

---

### Task 1: Repo, scaffold, and feed discovery

**Files:**
- Create: Gradle project with modules `core` (JVM) and `app` (Android), `.gitignore`
- Create: `docs/feed-notes.md`, `core/src/test/resources/feed-sample.json` (or `.html`)

**Interfaces:**
- Produces: `docs/feed-notes.md` stating the exact feed URL, method, required headers, the field holding event id/title/url, and the field meaning "on sale"; a real captured sample at `core/src/test/resources/feed-sample.json`; a second fixture `feed-cloudflare-challenge.html` and `feed-empty.json`.

- [ ] **Step 1:** `git init` in `/home/feanor/Desktop/eventer-watch`; confirm the Android SDK and JDK are installed (`java -version`, `sdkmanager --list_installed`); if missing, stop and tell the user what to install.
- [ ] **Step 2:** Open eventer.co.il in the chrome-devtools MCP, list network requests, and identify the request that returns the event list and the per-event sale status. Record it in `docs/feed-notes.md`. Verify it with a plain HTTP request (no cookies from a logged-in session).
- [ ] **Step 3:** If no usable feed exists, STOP and report to the user with the fallback (sitemap diff, weaker sale detection) before going further.
- [ ] **Step 4:** Scaffold the Gradle project (`core` JVM library with JUnit 5; `app` Android application, `minSdk 26`) and save the fixtures.
- [ ] **Step 5:** Run `./gradlew :core:test` — Expected: BUILD SUCCESSFUL (no tests yet). Commit.

### Task 2: Parser

**Files:**
- Create: `core/src/main/kotlin/watch/Model.kt`, `core/src/main/kotlin/watch/FeedParser.kt`
- Test: `core/src/test/kotlin/watch/FeedParserTest.kt`

**Interfaces:**
- Produces:
  - `data class Event(val id: String, val title: String, val url: String, val onSale: Boolean)`
  - `sealed interface ParseResult { data class Ok(val events: List<Event>) : ParseResult; data class ShapeChanged(val reason: String) : ParseResult }`
  - `object FeedParser { fun parse(body: String): ParseResult }`

- [ ] **Step 1: Write failing tests** in `FeedParserTest`: `parsesSampleFeed` (event count and first event's id/title/url/onSale match `feed-sample.json`); `emptyListIsShapeChanged`; `cloudflareChallengeIsShapeChanged`; `garbageIsShapeChanged`; `hebrewTitleAndNonAsciiUrlSurvive`; `duplicateIdsKeepFirst`.
- [ ] **Step 2:** Run `./gradlew :core:test --tests '*FeedParserTest*'` — Expected: FAIL (unresolved reference).
- [ ] **Step 3:** Implement `Model.kt` and `FeedParser.parse` per `docs/feed-notes.md`. An empty event list is `ShapeChanged("empty")`, never `Ok(emptyList())`. URLs are made absolute against `https://www.eventer.co.il`.
- [ ] **Step 4:** Run the tests — Expected: PASS. Commit.

### Task 3: Diff

**Files:**
- Create: `core/src/main/kotlin/watch/Diff.kt`
- Test: `core/src/test/kotlin/watch/DiffTest.kt`

**Interfaces:**
- Consumes: `Event` (Task 2).
- Produces:
  - `sealed interface Alert { val event: Event; data class NewEvent(override val event: Event) : Alert; data class SaleOpened(override val event: Event) : Alert }`
  - `fun diff(previous: Map<String, Event>?, current: List<Event>): List<Alert>` (previous `null` = first run → empty list)
  - `fun snapshotOf(current: List<Event>, previous: Map<String, Event>?): Map<String, Event>` (keeps events that vanished so a reappearance isn't "new")

- [ ] **Step 1: Write failing tests:** `firstRunSeedsSilently`; `newEventAlerts`; `newEventAlreadyOnSaleIsOneAlert`; `notOnSaleToOnSaleAlerts`; `unchangedProducesNothing`; `vanishedThenReappearedIsNotNew` (use `snapshotOf` between polls); `reappearedAndNowOnSaleAlertsSaleOpened`; `manyNewEventsReturnedInFeedOrder`.
- [ ] **Step 2:** Run `./gradlew :core:test --tests '*DiffTest*'` — Expected: FAIL.
- [ ] **Step 3:** Implement `diff` and `snapshotOf`.
- [ ] **Step 4:** Run — Expected: PASS. Commit.

### Task 4: Backoff and health

**Files:**
- Create: `core/src/main/kotlin/watch/Backoff.kt`, `core/src/main/kotlin/watch/Health.kt`
- Test: `core/src/test/kotlin/watch/BackoffTest.kt`, `core/src/test/kotlin/watch/HealthTest.kt`

**Interfaces:**
- Produces:
  - `class Backoff(val baseMs: Long = 30_000, val capMs: Long = 600_000, val jitterMs: Long = 3_000) { fun nextDelayMs(consecutiveFailures: Int, random: kotlin.random.Random = Random.Default): Long }`
  - `enum class Outcome { OK, FAILED, SHAPE_CHANGED }`
  - `class Health(val blindAfter: Int = 5) { fun record(o: Outcome): HealthEvent?; val isBlind: Boolean }` where `enum class HealthEvent { WENT_BLIND, RECOVERED }`

- [ ] **Step 1: Write failing tests:** zero failures → 27–33s; delays double and cap at 600_000; `blindAfterFiveConsecutiveFailures` emits `WENT_BLIND` exactly once; `shapeChangeCountsAsFailure`; `okAfterBlindEmitsRecovered`; `okResetsCounter`.
- [ ] **Step 2:** Run `./gradlew :core:test --tests '*BackoffTest*' --tests '*HealthTest*'` — Expected: FAIL.
- [ ] **Step 3:** Implement both.
- [ ] **Step 4:** Run — Expected: PASS. Commit.

### Task 5: Poller

**Files:**
- Create: `core/src/main/kotlin/watch/Poller.kt`, `core/src/main/kotlin/watch/Ports.kt`
- Test: `core/src/test/kotlin/watch/PollerTest.kt`

**Interfaces:**
- Consumes: `FeedParser`, `diff`, `snapshotOf`, `Health`, `Outcome`, `Alert`.
- Produces:
  - `interface FeedFetcher { suspend fun fetch(): String }` (throws on network error / non-200 / 429)
  - `interface SnapshotStore { fun load(): Map<String, Event>?; fun save(s: Map<String, Event>) }`
  - `data class PollResult(val alerts: List<Alert>, val health: HealthEvent?, val outcome: Outcome)`
  - `class Poller(fetcher: FeedFetcher, store: SnapshotStore, health: Health) { suspend fun pollOnce(): PollResult }`

- [ ] **Step 1: Write failing tests with fakes:** `firstPollSeedsAndSavesNoAlerts`; `secondPollWithNewEventAlerts`; `fetchExceptionIsFailedAndSnapshotUntouched`; `emptyFeedDoesNotWipeSnapshot`; `afterEmptyFeedRealFeedProducesNoStorm` (snapshot survived, so only genuinely new events alert); `fiveFailuresReportWentBlind`; `recoveryReported`.
- [ ] **Step 2:** Run `./gradlew :core:test --tests '*PollerTest*'` — Expected: FAIL.
- [ ] **Step 3:** Implement `Poller.pollOnce`: fetch → parse → on `Ok` diff against stored snapshot, save `snapshotOf(...)`; on `ShapeChanged` or exception record failure and leave the snapshot alone.
- [ ] **Step 4:** Run — Expected: PASS. Commit.

### Task 6: Android adapters (fetcher, store)

**Files:**
- Create: `app/src/main/kotlin/watch/OkHttpFeedFetcher.kt`, `app/src/main/kotlin/watch/FileSnapshotStore.kt`
- Test: `app/src/test/kotlin/watch/FileSnapshotStoreTest.kt`

**Interfaces:**
- Consumes: `FeedFetcher`, `SnapshotStore`, `Event`.
- Produces: `class OkHttpFeedFetcher(client: OkHttpClient, feedUrl: String) : FeedFetcher` (sets `User-Agent: EventerWatch/1.0`, throws on non-2xx); `class FileSnapshotStore(file: java.io.File) : SnapshotStore` (JSON on disk, atomic write via temp file + rename).

- [ ] **Step 1: Write failing tests:** `roundTripPreservesHebrewTitles`; `missingFileLoadsNull`; `corruptFileLoadsNull`; and a MockWebServer test `non200Throws` and `sendsUserAgent`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest` — Expected: FAIL.
- [ ] **Step 3:** Implement both classes.
- [ ] **Step 4:** Run — Expected: PASS. Commit.

### Task 7: Alarm notifications

**Files:**
- Create: `app/src/main/kotlin/watch/Alarm.kt`; modify `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `Alert`, `HealthEvent`.
- Produces: `class Alarm(context: Context) { fun ensureChannels(); fun raise(alerts: List<Alert>); fun raiseHealth(e: HealthEvent) }`. `raise` posts ONE full-screen-intent notification on a max-importance channel using the alarm audio stream and vibration; tapping or full-screen launch opens the first alert's `event.url` via `ACTION_VIEW`; any further alerts appear in the notification's text, not as extra alarms.

- [ ] **Step 1:** Add manifest entries: `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`.
- [ ] **Step 2:** Write a Robolectric test `raiseWithThreeAlertsPostsOneNotification` and `raiseOpensFirstEventUrl` (assert the PendingIntent's intent data equals the first event URL); run `./gradlew :app:testDebugUnitTest --tests '*Alarm*'` — Expected: FAIL.
- [ ] **Step 3:** Implement `Alarm`.
- [ ] **Step 4:** Run — Expected: PASS. Commit.

### Task 8: Foreground service, boot, and controller

**Files:**
- Create: `app/src/main/kotlin/watch/WatchService.kt`, `app/src/main/kotlin/watch/BootReceiver.kt`; modify manifest

**Interfaces:**
- Consumes: `Poller`, `Backoff`, `Alarm`, `OkHttpFeedFetcher`, `FileSnapshotStore`.
- Produces: `class WatchService : Service` (foreground, type `specialUse`; persistent notification showing "Last check HH:mm:ss"; loop: `pollOnce` → `Alarm` for alerts/health → `delay(backoff.nextDelayMs(failures))`; `START_STICKY`; partial wake lock only around each poll); `object WatchState` exposing `StateFlow<WatchStatus>` (`running`, `lastCheckEpochMs`, `blind`, `recent: List<Alert>`); `BootReceiver` restarts the service if the user had it on.

- [ ] **Step 1:** Write a JVM-testable `WatchLoop` extraction test if logic leaks into the service: `loopWaitsBackoffDelayBetweenPolls` using a virtual-time test dispatcher; run `./gradlew :app:testDebugUnitTest --tests '*WatchLoop*'` — Expected: FAIL, then implement `WatchLoop` and the service until PASS.
- [ ] **Step 2:** Implement the service, receiver, and manifest declarations.
- [ ] **Step 3:** Run `./gradlew :app:assembleDebug` — Expected: BUILD SUCCESSFUL. Commit.

### Task 9: UI and onboarding

**Files:**
- Create: `app/src/main/kotlin/watch/MainActivity.kt` (+ layout/Compose)

**Interfaces:**
- Consumes: `WatchState`, `WatchService`, `Alarm`.
- Produces: one screen with start/stop, last-check time, a blind/healthy indicator, recent detections list, "Test alarm" button, and an onboarding checklist that shows each requirement as done/not-done with a button to fix it: notification permission, full-screen intent permission (Android 14+ settings page), battery-optimization exemption, and Samsung steps (remove from "Sleeping apps"/"Deep sleeping apps", turn off "Put unused apps to sleep", set battery to Unrestricted) as text instructions with a deep link to the app's battery settings.

- [ ] **Step 1:** Write a UI test `testAlarmButtonRaisesAlarm` and `checklistShowsMissingPermissions`; run — Expected: FAIL.
- [ ] **Step 2:** Implement the screen.
- [ ] **Step 3:** Run the tests — Expected: PASS. Commit.

### Task 10: Device verification

- [ ] **Step 1:** Run `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` — Expected: all green; APK at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2:** On a device or emulator, install the APK, complete onboarding, tap "Test alarm" with the screen locked — Expected: screen wakes, alarm sounds, event page opens.
- [ ] **Step 3:** Run against the live feed for 10 minutes; confirm the first poll alerts nothing, "Last check" advances every ~30s, and requests are never closer than ~27s (check logs).
- [ ] **Step 4:** Simulate a break (airplane mode for 3 minutes) — Expected: "watcher is blind" alarm after 5 failures and a recovery notice when back online.
- [ ] **Step 5:** Hand the APK to the user for the S23 with the battery-settings checklist; a 24-hour run on the real phone is the final acceptance, and the result goes in the repo `README`.
