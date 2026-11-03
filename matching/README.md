# Genuine same-product review/import

## Why this file starts empty
`same-product-matches.csv` has 17 headers and **no invented links**. The independent bundled sources establish no reliable all-three match. Do not join a Puma shoe, another Puma shoe and a Puma shirt just because all three stores have the brand. A review file cannot supply an observation absent from the underlying dataset.

## Workflow
1. Legally obtain records for the **same manufacturer model and exact variant**, from the stores you need. Keep source amounts, dates, URLs, attribution and missing values intact. Never copy one store’s price into another store.
2. Export source IDs/fingerprints using the desktop button or the command below. Inspect full source titles/descriptions/specifications and manufacturer/retailer evidence. Retailer SKU equality alone is not enough.
3. Add mapping rows only after reviewing identity. Each canonical variant has one/two/three rows, one per represented store. A two-store group is allowed but never appears under the all-three filter. A missing third store stays missing.
4. Validate the entire mapping file. A bad row rejects the whole set; the app does not silently retain some invalid file rows or substitute similarity candidates.
5. CSV mode: save the file, then Reload source. MySQL mode: import it transactionally, then Reload source.

### Commands (PowerShell, project folder)

```powershell
$env:TULITA_DATA_SOURCE = 'csv'
mvn -q compile exec:java '-Dexec.mainClass=tulita.matching.ReviewToolsMain' '-Dexec.args=--export matching/record-index.csv'
mvn -q compile exec:java '-Dexec.mainClass=tulita.matching.ReviewToolsMain' '-Dexec.args=--validate matching/same-product-matches.csv'
```

The generated index is for inspection; it is **not a mapping file**. It includes original titles, prices, variant metadata, source URLs and observation fingerprints. Formula-leading spreadsheet text is escaped in this export only; source records are not changed. Treat the exported index as read-only. Do not recompute hashes in Excel or edit source amounts to pass validation.

To use a different mapping path in CSV mode:

```powershell
$env:TULITA_MATCHES_PATH = 'C:\your-folder\reviewed-matches.csv'
```

To import genuine reviewed links into MySQL after both schemas/source import are ready:

```powershell
mvn -q compile exec:java '-Dexec.mainClass=tulita.matching.ReviewToolsMain' '-Dexec.args=--import-mysql matching/same-product-matches.csv'
```

Import always validates against the **database’s** observations, regardless of the CSV/DB browsing toggle. It replaces the mapping set in one transaction, rechecks each full fingerprint under row locks, never changes source prices, and rolls back on failure. Empty imports are refused to protect existing mappings. MySQL browsing reads mapping tables, not the CSV file. SELECT-only users can browse; import needs DML on the mapping tables plus SELECT on source tables. MySQL 8 is required for locking syntax.

## Exact columns
Keep these 17 headers exactly once; use UTF-8 CSV (a BOM is accepted), ISO dates and proper CSV quoting for commas/newlines. No price columns belong in this file.

| Column | Meaning |
|---|---|
| `canonical_id` | Stable manufacturer product identity, max 80 chars; same ID retains the same name/brand/model/category across variants |
| `canonical_name` | Human-readable canonical product name, max 500 |
| `brand` | Explicit manufacturer brand, not unknown; max 200 |
| `model` | Manufacturer model phrase/code, max 200; must occur as a complete normalized phrase/code in **every** linked source title |
| `category` | Canonical product category, max 200 |
| `variant_key` | Stable exact-variant key, max 160; include applicable size/colour/storage/pack/fit differences |
| `size` | Reviewed exact size including system, e.g. UK vs US; max 150 |
| `color` | Reviewed colour; max 150 |
| `capacity` | Reviewed storage/RAM/volume/weight units when applicable; max 150 |
| `quantity` | Reviewed pack quantity when applicable; max 150 |
| `gender` | Reviewed fit/gender group when applicable; max 150 |
| `store` | Exactly `Amazon`, `Flipkart` or `Myntra` |
| `record_id` | Exact existing ID from exported source index, including store prefix |
| `record_sha256` | Exact 64-character fingerprint from that index |
| `evidence_url` | HTTPS evidence URL without embedded credentials; human-supplied, not automatically fetched/verified |
| `reviewed_on` | Actual nonfuture review date, `YYYY-MM-DD` |
| `review_note` | At least 20 characters explaining manufacturer identity and exact variant evidence; not simply “same brand” |

All canonical fields must agree exactly across rows in a group. The normalized variant key is part of its group identity. Changing only size while reusing the same variant key is an error. One source record cannot appear twice or be reused across variants; choose one observation for each store/variant.

Required identity fields cannot be blank/unknown. Leave nonapplicable variant attributes blank rather than inventing them. Fashion size/colour/fit and phone storage/colour require evidence. Known source metadata and explicit title colour/gender/units cannot be overwritten. The app does not convert units (e.g. 1L to 1000ml), infer size from “model wearing”, infer a manufacturer code from a Myntra SKU, or infer identity from cheap prices. Conservative validation may reject valid aliases/foreign-language attributes; improve the source/evidence rather than weakening the same-product claim.

A reviewer may supply missing canonical attributes only when actual evidence establishes them. The application checks structural consistency, not the truth of the reviewer’s claim or the remote URL. “Reviewed mapping” is not an independently certified match.

The SHA binds the review to source identity, title/category/path, description/specifications, brand, amounts/rating/date/retailer URL and provenance/variant fields. Decimal scale alone does not change it. Changed observations invalidate reviews. Export/review again after source changes.

## If the matching source observations are not bundled
You must first obtain suitable genuine data under appropriate permissions/licences. This edition supplies no live scraping or automatic data-discovery service.

`TULITA_MULTI_DATASET_PATH` can point to a normalized ZIP containing **`catalog.csv`**, using exactly the existing source catalog’s columns:

```
store,source_key,source_url,license,date_note,source_record_id,product_id,title,category,category_path,brand,price,mrp,rating,observed_at,url,availability,size,color,gender,quantity,description,specifications
```

Use the same consistent metadata per `source_key`; each `(store,source_record_id)` must be unique. The application constructs `record_id = store + '-' + source_record_id`. Prices are INR numeric observations, not currency-converted guesses; use blank unknown amounts/ratings/dates. Maintain actual per-row source dates and per-source capture notes/attribution. Keep legally required notices beside your new dataset. Do not overwrite the original bundled data/manifest and imply your added rows were part of those sources.

In CSV mode export/review against this selected ZIP. In MySQL mode run `MultiImportMain` with the same selected ZIP first, then export/review/import against the DB. Source import replaces the source tables and removes obsolete links through foreign-key cascade; canonical definitions without links do not appear in the catalog. Re-import the reviewed set afterward.

No complete real three-store example is shipped because none was established. Synthetic positive-path fixtures are confined to `src/test`, explicitly labelled TEST FIXTURE ONLY, and are never offered as genuine prices.
