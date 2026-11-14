# EventerWatch — design

## Intent
When organizers post an Eventer link in a WhatsApp group, it arrives long after tickets went on sale and the good ones are gone. The user wants an Android phone to watch eventer.co.il continuously and, the moment a new event appears or an event starts selling, wake the user and open that event's page so they can buy by hand.

## Goals
- Detect (a) a new event on the site and (b) an existing event changing from not-on-sale to on-sale.
- On detection: loud full-screen alarm (rings through silent/DND-capable alarm stream), then open the event page in the browser.
- Run unattended for days: survives screen-off, doze, app swipe-away, reboot.
- Alert on ALL events. No filtering in v1 (user filters by eye).

## Staging
The customer wants the bot to "do as much as it can". Built in two stages:
- **Stage 1 (this design, detailed below):** detect, alarm, open the event page.
- **Stage 2 (designed after Stage 1 works and a real checkout flow has been observed):** go as far through the purchase as is legitimately possible: choose the ticket type/quantity from preset preferences, fill in the buyer's saved details, proceed to payment. Payment uses the phone's own saved payment method / autofill where the checkout allows it; the app does not store raw card numbers itself. When a CAPTCHA, queue, 3-D Secure, or any other human check appears, the bot stops and hands over to the customer with the page ready.

## Non-goals
- In Stage 1: checkout, form filling, payment, ticket selection (Stage 2).
- At any stage: bypassing CAPTCHA, queues, or Cloudflare protections.
- Filtering/keywords, multiple users, iOS, Play Store release.

## Anti-goals (failure even if it "works")
- Silent failure: if the feed breaks or the service dies, the user must be told, not left believing they're covered.
- Hammering the site: no sub-10-second polling, no parallel requests.

## Constraints
- Eventer homepage is an AngularJS SPA behind Cloudflare; the HTML contains no event list, so events come from background requests. The exact feed is not yet identified.
- Sitemap (13 files, ~1000 URLs each, no lastmod) exists and is a fallback for new-event detection only.
- Android background execution limits; Android 14+ requires the user to grant full-screen-intent permission.
- Target device: Samsung Galaxy S23 (Android 14+, One UI). Samsung adds its own battery killers (Sleeping apps, "Put unused apps to sleep", Auto Optimize); onboarding must walk the user through exempting the app.

## Approaches considered
1. **Native Kotlin app, foreground service** — chosen by user. Most reliable for always-on use and strongest alarm.
2. Termux + Python — faster to write, but Android kills it and the alarm is weak.
3. Headless browser on rendered pages — heavy, battery-hungry, likelier to trip Cloudflare. Rejected.

## Design
- **Feed discovery (first task, a spike):** inspect the homepage's network requests to find the JSON feed behind the event list and what field says "on sale". If none is usable, fall back to polling the first listing pages' HTML/sitemap diff and accept that "sales opened" detection is weaker; tell the user before building on the fallback.
- **Core module (pure Kotlin, unit-tested):** parse feed → set of events {id, title, url, onSale}; diff against stored snapshot → list of alerts. First run seeds the snapshot silently (no alarm storm).
- **Service:** foreground service with persistent notification, polling loop every 30s with small random jitter, exponential backoff on errors/429/Cloudflare challenge. Snapshot persisted locally.
- **Alert:** high-priority channel, full-screen intent, alarm-stream sound, vibration, and launches the event URL. Tapping opens the page too.
- **Health:** if N consecutive polls fail or the response shape changes, raise a distinct "watcher is blind" alert. Persistent notification shows last successful check time.
- **Survival:** request battery-optimization exemption, boot receiver restarts the service, onboarding screen walks through the permissions.
- **UI:** one screen: start/stop, last check time, recent detections, a test-alarm button.

## Decided by me (check these)
- Name EventerWatch; Kotlin; min SDK 26; OkHttp + coroutines; plain local storage for the snapshot.
- 30s polling interval (user decision); honest app User-Agent.
- Sideload APK for installation.

## Left to the builder
- Exact feed and field names, build tooling details (the user's machine may need the Android SDK/Gradle set up), icon/layout polish.
- Test-device specifics beyond "Galaxy S23, probably" (the customer's exact model/Android version is unconfirmed).
