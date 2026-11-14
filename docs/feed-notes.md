# Eventer feed discovery (2026-11-13)

Method: read the site's own JS bundles (`/js/min_apps/eventerSiteApp.min.js`, `purchase.min.js`) and probed the public endpoints they call. Chrome was not installed, so no live network capture. Requests were single and spaced 1-1.5s apart.

## Public, no login (HTTP 200)
| Endpoint | What it returns | Size |
|---|---|---|
| `GET /sliders/list.js` | JSON array of 14 homepage sliders; ~142 event slides `{objectRef (event id), url "/vsh2f", linkName, title, subTitle "date \| venue", isVisible}` | ~97 KB |
| `GET /events/explainNames/<code>.js` | Full event: `event._id`, `name`, `status`, `ticketPlatform.isHidden/isPending`, `schedule.start/end`, `location`, `linkName`, plus `jsonLdData` | ~10 KB |
| `GET /events/<eventId>/dataForSale.js` | Checkout settings (guest fields etc.), no availability | ~1 KB |
| `GET /sitemap.xml` -> `sitemap_1..13.xml` | ~135,000 URLs, ~96,000 are event short codes (`/vsh2f`), all history, no `lastmod` | ~1 MB x13 |

## Not public
- `GET /api/events/<id>/ticketTypes` -> 401 Unauthorized (the frontend calls it with a session; it is where ticket types, quantities and sale windows live).
- `/links/explainLink/<code>.js` -> 403 without the right path form (`/links/explainLink/<code>/events.js` works).

## Findings that affect the design
1. **New-event detection:** `/sliders/list.js` is cheap enough for 30s polling, but it is the *curated homepage*; a brand-new event may not be featured on it (unverified). The sitemap is complete but ~13 MB and has no dates, too heavy for 30s polling.
2. **"Sale opened" detection:** no public field was found that says tickets are on sale. `event.status` (=1 on a live event), `ticketPlatform.isHidden`, `ticketPlatform.isPending` exist, but their meaning for pre-sale events is unverified. Ticket-level availability is behind auth.
3. Event page `https://www.eventer.co.il/<code>` is the URL to open; it is a JS-rendered page.

## Decision needed
See conversation: the design's "event flips to on-sale" signal is not available from public data. Options recorded there.

## Pre-sale event comparison (2026-11-14)
`nye2027` (sale not open yet) vs a live event (`vsh2f`): `/events/explainNames/<code>.js` has the same
`status`, `ticketPlatform.isHidden` and `isPending`; no public boolean differs between pre-sale and on-sale.
The event does appear in `/sliders/list.js`, so new-event detection works for it.
`docs/fixtures/nye2027-before-sale.explainNames.json` is a snapshot taken before sale; diff it against a fresh
fetch once sales open to find a field that changes.

## Per-event checkout forms differ
`/events/<id>/dataForSale.js` -> `settings` holds `guestInfoFields` (name, sid, phone, email, age, gender),
`namePerTicket`, `allowMultipleTickets`, `guestQuestions` (types seen: boolean, igProfile; more exist).
Most events need only name, ID, phone, email; some add gender, birth date, per-ticket details and extra questions.

## "Mesibata" series (organizer events) — findings 2026-11-14
- Events such as `mesibatapijama`, `mesibatapijama2/3`, `mesibatanostalgia*` are **hidden from the marketplace**
  (`ticketPlatform.isHidden = true`), so `/sliders/list.js` never lists them. They exist only by direct link.
- They do appear in the sitemap; the newest URLs sit at the end of `sitemap_13.xml`.
- `GET /events/explainNames/<code>.js` answers 200 for a real link and 403 for an unknown one, so a single
  small request can test whether a guessed link exists.
- Checkout settings for the series (`mesibatapijama`): required name, ID, phone, email, gender; per-ticket
  details; up to 10 tickets; age limit 28; two required questions (a promotional-message consent checkbox and a
  "smokers?" radio). Payment is a hosted PayPlus page.
- Ticket types and sale windows come from `/api/events/<id>/ticketTypes`, which needs a session (401 otherwise).
  Not probed further: that needs a decision on how the app should obtain a session (see conversation).
- Note: the public event JSON for organizer events also exposes the organizer's payment-provider credentials.
  They are deliberately not stored or used anywhere in this project; consider telling Eventer.
