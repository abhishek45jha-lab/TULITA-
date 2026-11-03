# Historical three-store catalog — source notices

This is a collective, normalized educational catalog. Its three source components remain independently identifiable by `source_key` and store. It does not imply retailer endorsement, current accuracy, permission to scrape, or ownership of product trademarks/images. Prices are taken from the source records, not invented.

## Flipkart component — 20,000 records

- Creator: **PromptCloud / PromptCloudHQ**.
- Dataset: **Flipkart Products**.
- Source: https://www.kaggle.com/datasets/PromptCloudHQ/flipkart-products
- Publisher-listed licence: **CC BY-SA 4.0**. Full text: `../LICENSE-CC-BY-SA-4.0.txt`.
- Original crawl range: 2015-12-01 through 2016-06-28 (UTC row timestamps retained).
- Original unmodified CSV remains in the parent `datasets/` directory. Normalized Flipkart rows are an adaptation of that dataset; retain attribution, change notices and the CC BY-SA share-alike conditions when redistributing that component.

## Amazon component — 20,000-record sample

- Creator: **Asaniczka / asaniczka**.
- Dataset: **Amazon India Products 2023 (1.5M Products)**.
- Source: https://www.kaggle.com/datasets/asaniczka/amazon-india-products-2023-1-5m-products
- Publisher-listed licence: **Open Data Commons Attribution License (ODC-By) 1.0**. Full text: `LICENSE-ODC-BY-1.0.txt`.
- Notice: this component contains information from the Amazon India Products 2023 database, made available under the Open Data Commons Attribution License. Preserve its attribution and licence notices. The database licence does not itself license all underlying third-party product content.
- Only a deterministic 20,000-ASIN **derived sample** is bundled, not the 1,589,160-row source download. The original complete source is available at the link above.
- Sample preference: titles with alphanumeric tokens also present in the other catalogs, then recognised explicit title-prefix brand buckets (max 350 per brand), then a seed-17 reservoir sample. This is sampling, **not proof of product identity** or a benchmark.
- Publisher dataset year: **2023**. Exact per-row observation dates are absent and remain unknown. Some source titles/categories are Hindi. Original text is retained, not silently translated.
- No reported brand column exists; a brand is filled only when a recognised explicit brand prefix appears in the source title. Otherwise it remains blank. Missing/zero placeholder list price, price or unrated rating becomes unknown; the source has no shipping or variant fields.

## Myntra component — 12,491 records

- Kaggle publisher: **Nirokey / nirokey**. Original collection credited by publisher to **CrawlFeeds**.
- Dataset: **Myntra_Fashion_Products**.
- Source: https://www.kaggle.com/datasets/nirokey/myntra-fashion-products
- Publisher-listed licence: **CC0 1.0**. Full text: `LICENSE-CC0-1.0.txt`.
- Publisher reports last extraction on **2021-07-29**. There is no per-row timestamp: it remains unknown, not assigned that exact date.
- The public CSV contains 12,491 observations, including repeated SKUs. Global record IDs include source row numbers to preserve separate observations.
- Size, MRP, rating and delivery are not supplied and remain unknown. Myntra's `mpn` equals its own SKU in this file; it is **not** used as a cross-store manufacturer model identifier.
- Saved product URLs are constructed from the reported Myntra SKU and are labelled historical/unverified destinations.

## Normalization/change notice

Prepared for Tulita on 2026-10-18 (Asia/Calcutta). All source content was downloaded as published datasets; Tulita makes no scraping requests to retailers. No prices, reviews, delivery charges, missing store offers, variant sizes or exact dates were generated.

`catalog.csv.zip` contains one normalized UTF-8 CSV with 52,491 rows: 20,000 Flipkart, 20,000 Amazon and 12,491 Myntra. Every row retains source URL/licence/date context. Descriptions are excerpts capped at 2,000 characters and Flipkart raw specifications at 4,000. Root categories are derived from source categories, or title keywords where absent. Original image URLs are omitted; no retailer photographs are redistributed. Missing numerical values are blank/unknown. Source-specific stock flags mean **at capture**, not today.

The collection does not purport to relicense all components under one universal licence. Preserve the separate source licences and conditions, especially CC BY-SA requirements on adapted Flipkart data and ODC-By attribution on Amazon data. For public redistribution or commercial use, review the source terms and underlying rights. Attribution is not a statement that all source data is accurate or that all third-party rights have been cleared.

Hashes, source download hashes and sampling/change details are recorded in `MANIFEST.json`.
