# ProMap photo comparison subset — provenance and use

## Authors and source
Source: Kateřina Macková and Martin Pilát, *ProMap: Datasets for Product Mapping in E-commerce*, and *Extended ProMap datasets for product mapping*.

- Repository: https://github.com/kackamac/Product-Mapping-Datasets
- Pinned source commit: `346d62f8ec4d229db6c437d4c2d9101b02e1a8da`
- Paper: https://arxiv.org/abs/2309.06882
- Extended paper: https://doi.org/10.1007/s10660-024-09892-9
- Retrieved: 19 October 2026.

The authors' root README says: “All the datasets are released under an open licence and can be freely used by anybody.” Their original README files are retained as `AUTHOR_README.md` and `EXTENDED_AUTHOR_README.md`. The inspected repository did not supply a separately named, complete dataset licence text. **Do not represent this as CC0, MIT, or a blanket clearance of third-party retailer photographs.** Preserve authorship and original links; clarify additional redistribution/commercial image rights with the authors/rightsholders where necessary. Retailer/brand names and photographs do not imply endorsement. This academic prototype does not assert exclusive ownership of source data or images.

## Original records retained
Four untouched original CSV files are retained under `raw/`; their hashes are in `MANIFEST.json`. All original pair labels, including errors, remain unchanged there. Some source strings contain mojibake or HTML entities. They remain unchanged in raw files, offer titles/specifications/descriptions and evidence fingerprints. Short canonical display names/variant summaries were separately curated; they do not replace original text.

## Audit and selection
The four files contain 1,800 labelled pairs: 566 positive-labelled pairs, of which 382 have both numerical prices. These are candidate counts, not verified identities. Examples labelled positive include mismatched microwave sizes, colours or pack counts. Many examined Sears rows have contradictory Model fields. No Sears observations were accepted into this small curated subset; the app supports that store but does not manufacture its prices.

The bundled subset has **79 curated products, 161 original priced observations, 237 cached reference photos**, with **3 three-store groups**. `CURATION_REPORT.json` records each accepted source row and each rejected pair with its conflicting evidence. All products have at least two original prices. Selection favoured identical normalized titles with agreeing known specifications. The three three-store selections additionally inspect product/pack evidence:

- **Hamilton Beach 58149:** all three accepted titles identify 58149; the selected product includes the 40 fl oz jar and chopper. Walmart/Amazon descriptions identify black/stainless construction; Target supplies model 58149 in title/URL. Its conflicting Sears Model field was not accepted.
- **Reynolds Wrap Heavy Duty 50 sq ft:** all accepted titles identify the same heavy-duty roll size; Amazon description and Target quantity identify one roll. No length/pack conversions were used.

Other accepted pairs are source-reviewed, not independently authenticated online. Exact source file, parsed CSV record number, side, retailer URL and full row fingerprint are retained per offer. A CSV record number is not a physical text-line number because fields can contain newlines.

The schema has no price capture date column. “Date First Available” in specifications is **not** a price observation date. Capture dates are therefore blank/unknown. No runtime retailer pages, price APIs or scraping are used.

## Amounts and missing information
Original numeric prices are preserved. USD is assigned from the US retailer context (Amazon.com, Walmart.com, Target.com), **not** read from a source currency column, which does not exist. No INR conversion, delivery amount, current stock, current condition guarantee, MRP or rating is invented. The Galaxy A01 identity checks carrier/model/colour/storage; source condition is not consistently supplied, so it must not be presented as a current new-device deal. The minimum item amount is historical, not a current delivered-cost winner.

## Photos
Images were retrieved from the image URLs already supplied in the authors' CSVs, not by scraping new retailer pages. Up to two reference photos per accepted source listing were retained. Downloads were decoded to RGB, downscaled to maximum edge 640 px and saved as JPEG (quality 88). Each cached photo retains its original URL/store and new file SHA-256. Hash/URL membership checks prevent a different cached image silently replacing its reference. These checks do not independently verify what the original image depicts or clear all third-party rights.

Photos load locally at runtime. Downloading the optional model does not upload user images. Camera queries stay in memory and are not saved/uploaded by the app. Upload queries are read from the user's selected local files. Desktop opening of an old retailer URL is an explicit user action and can fail because historical links expire.

## Separate from the old catalog
`datasets/multistore` and its 52,491 India-store observations remain unmodified and separately labelled. Their incompatible records are not used to fill prices in this ProMap subset. Older fictional fixtures/demo are also separate. Test-only camera frames are not distributed as genuine user captures or price data.
