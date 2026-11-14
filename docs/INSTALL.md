# Installing EventerWatch on a customer's phone

Do this once per phone, with the customer, about 15 minutes. Do it a day or more before an event drop, not minutes before.

## 1. Get the APK onto the phone

Send `EventerWatch-<version>.apk` by USB cable, Google Drive, or a messaging app. Open it on the phone.
Android asks you to allow installs from that app (Settings > "Install unknown apps"): allow it.
Google Play Protect may warn about an unknown developer: choose "Install anyway". The app is signed with your own key.
If an older EventerWatch is installed, update by installing the new APK over it (same signing key). Never uninstall first
unless you want to wipe the saved details.

## 2. Setup checklist (inside the app)

Open the app. Under **Setup**, press **Fix** on every item until all show a green tick:

| Item | Why |
|---|---|
| Notifications | alarms can show |
| Full-screen alarms | the screen wakes for an alarm |
| Display over other apps | the checkout can open by itself while the phone is in use |
| Battery optimization | the watcher keeps running |
| Brand-specific battery settings | see below, then press its button |

**Samsung:** Settings > Battery > Background usage limits: remove EventerWatch from "Sleeping apps" and "Deep sleeping apps",
turn off "Put unused apps to sleep". In the app's own Battery page choose **Unrestricted**.
**Xiaomi/Redmi:** Settings > Apps > Permissions > Autostart: allow EventerWatch; Battery saver: "No restrictions".
**Other brands:** search the settings for "battery", find EventerWatch, set it to unrestricted/no restrictions.

## 3. Buyer details

Tap **Buyer details**. The customer enters their own details (or you enter them in front of them, with their consent):
full Hebrew name, ID number, phone, email, gender, city, Facebook and Instagram links, the smokers answer, and the
number of tickets. Birth date and age only matter when an event asks for them. The app validates the ID number.
Details are stored encrypted on this phone only. To erase them: Settings > Apps > EventerWatch > Storage > Clear data.

Buying more than one ticket fills the same details into every guest form; some events reject duplicates.

## 4. Rehearse (do not skip)

1. **Preview in 10 s**: press it, lock the screen, wait. The screen must wake, the chime must play and the red alarm
   screen must appear. If not, a setup item above is missing.
2. **Buying > Open in checkout** with a live event link, for example `https://www.eventer.co.il/mesibatapijama`.
   Watch the banner at the top. It should select tickets, press continue, fill the form and stop at the payment page
   ("Payment page"). Close it there. The app never presses the final pay button.
   If it stops with "A question needs you", note the text: that is a question the app could not answer.

## 5. Go live

Tap **Start watching**. Leave Wi-Fi/mobile data on and do not force-stop the app. Check once a day that the status card
says "Watching Eventer" and "Last check" is recent. The phone should stay charged.

When a new event of the series appears the phone rings, opens the checkout and fills it. When the second, shorter chime
sounds, the app has stopped (payment, verification, or a question): finish it by hand, enter the card and approve 3DS.

## 6. Keep safe

Back up the signing key folder `~/.eventerwatch-signing/` from the computer that built the app. Without it, future
updates cannot be installed over existing installs.
