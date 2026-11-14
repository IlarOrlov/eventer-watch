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
