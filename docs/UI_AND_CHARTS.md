# Tulita UI, price charts and ratings

This edition ships a redesigned JavaFX workspace. Two of the requested additions — **star ratings** and a **price-over-time history** — are not supported by the bundled research data, so the app is explicit about the gap instead of inventing values.

## 1. What the new interface looks like
| Area | Implementation |
|---|---|
| Left rail | Midnight-navy rail with the Tulita mark, Discover / Watchlist navigation, and live “catalog at a glance” counts (products, recorded prices, categories). |
| Hero header | Gradient panel with the tagline, a one-line explanation of the data, and four KPI tiles: products, recorded prices, photos, three-store groups. |
| Search toolbar | Rounded white card: search field, Upload photo, Camera, Reload, then category / store / sort selectors and the “3+ stores only” filter. |
| Product cards | Photo tile with a store-count chip (and “PRICES TIE” chip when equal), watchlist heart, per-retailer price list with colour dots and the lowest amount in green, spread badge, and a compare action. |
| Comparison sheet | Large hero row, four KPI boxes (lowest, highest, spread, retailers), the recorded-price bar chart, the observation-evidence table with a Ratings column, reference photos and the review note. |
| Styling | All CSS is local (`src/main/resources/styles/tulita.css`) — no web fonts, no network assets, so the app looks identical offline. |

Screenshots regenerated from the running application: `docs/photo-catalog-dashboard.png`, `docs/photo-catalog-comparison.png`, `docs/photo-catalog-price-spread.png`, `docs/photo-catalog-visual-search.png`.

## 2. Ratings — not present in the source
The pinned ProMap CSV files contain exactly these columns:

`name1, short_description1, long_description1, specification1, image1, price1, id1, name2, short_description2, long_description2, specification2, image2, price2, id2, match, category, match_type, image_url1, image_url2`

There is **no rating column, review count, star value or review text**, and no rating-like key appears in any product specification block of the 79 curated groups (the only “rating” strings are electrical values such as *Battery Power Rating* and *Max Temperature Setting*).

Therefore:
- Cards show no stars, and the comparison table shows **“Not in source”** for every retailer.
- The application never estimates a rating from a title, an image or a review count elsewhere.
- `docs/VERIFICATION.md` records the check that established this.

**If you later obtain genuine ratings**, they must be stored with an evidence trail before being displayed. Suggested, non-shipped approach: add nullable `rating` / `rating_count` / `rating_source` / `rating_captured_at` columns to `photo_offers`, require the same source-hash validation as prices, and label unrated rows as unknown. Do not average across retailers or dates, and do not reuse a rating from a different pack or colour variant.

## 3. Price history — what the data can and cannot show
Every accepted product has **one recorded amount per retailer** and **no capture date or timestamp**. A “price rose/fell over time” line chart would therefore require invented dates or invented observations, which is excluded by the project’s genuine-data rule.

What the app shows instead, using only supplied numbers:
- **Recorded item price by retailer** — a bar chart per comparison, with the lowest recorded amount highlighted in green and retailer colours for Walmart, Amazon and Target.
- **Lowest / highest / spread** KPI boxes, e.g. dartboard USD 21.84 / 34.99 / 13.15.
- **Spread badge** on each card, e.g. “Spread USD 13.15 · 38% below highest”.
- A chart footnote stating that one undated observation per retailer cannot support a temporal trend.

If a dated price series is required later, the honest route is a time-stamped observation table (store, variant, amount, currency, captured_at, source URL, evidence hash) imported from a licensed source. The comparison chart already reads `offers`, so such rows would plot as a genuine line chart without changing the model beyond adding repeated observations.
