# Executed verification — strict same-product correction

Verified 19 October 2026 (Asia/Kolkata), Linux, OpenJDK 21.0.12.1, Maven 3.9.9, JavaFX 21.0.6, release-17 compilation. Desktop controls exercised under Xvfb. JDBC tested against actual MySQL Community Server **8.0.40**, not an in-memory replacement. Windows and JDK 17 runtime were not separately tested.

## Final complete suite
Executed after the canonical comparison, UI replacement, fingerprint locking and explicit title size/pack validation changes:

```bash
MAVEN_OPTS=-Xmx384m xvfb-run -a -s '-screen 0 1600x1200x24' \
  mvn -q clean verify -DargLine=-Xmx384m \
  -Dtulita.multiUiTests=true -Dtulita.multiMysqlTests=true \
  -Dtulita.reviewMysqlTests=true -Dtulita.datasetUiTests=true \
  -Dtulita.datasetMysqlTests=true -Dtulita.uiTests=true \
  -Dtulita.mysqlTests=true -Dtulita.screenshots=true
```

**85 tests, 0 failures, 0 errors, 0 skipped.** Exit status 0. This is a functional verification, not a performance benchmark. Default tests discover the same 85 tests with seven opt-in desktop/database tests skipped.

Database tests used a private, isolated `tulita_review_test` database with adapted schema copies and a locally generated account/password. No private application.properties or database files are included in the delivery. Do not run destructive optional integration tests against a working database.

## What was checked
- Full genuine source catalog loads: 52,491 rows, Amazon 20,000 / Flipkart 20,000 / Myntra 12,491.
- Header-only distributed mapping file loads as zero approved links/groups. No automatic same-brand/title/model suggestion creates group membership.
- Synthetic positive cases are explicitly TEST FIXTURE ONLY and confined to test Java files. They establish mechanics, not genuine available three-store prices.
- One card per reviewed canonical variant; all-three filtering, grouped search/watch/pagination, original recorded amounts unchanged.
- Duplicate/reused source observations, wrong store/ID/model/colour/capacity/title pack/title size system, stale hashes, inconsistent definitions, insecure evidence URLs and invalid mapping headers are rejected.
- UTF-8 BOM support and source-index export with hashes.
- Strict default desktop view shows zero reviewed all-three results; old compare-selected/pick controls are absent.
- Unchecking the filter exposes actual searchable source records. A real Titan source record with a provisional similar model elsewhere remains unmapped: exactly one populated store row and two missing rows. No candidate price is substituted.
- Positive three-row canonical comparison rendering was checked using test-only fixtures, not presented as actual matched data. Source details actions remain available for real populated rows.
- Actual MySQL source import/read round trip for all 52,491 rows, prior import rollback regression, and original demo/single-source regressions.
- Reviewed-link JDBC import/read round trip; SQL decimal scale does not invalidate hashes.
- SQL failure after deletion inside mapping replacement rolls back the previous mapping set. Empty replacement is refused.
- Changing an observed source field invalidates review loading/re-import; mapping import does not change source prices. Source replacement cascades away obsolete links.
- CSV and MySQL CLI validation each reported zero bundled review groups, without similarity fallback.
- Separately ran the strict desktop test with `TULITA_DATA_SOURCE=mysql`: passed with the same honest empty/missing-source behavior (before the final title-size/pack additions; final full suite additionally covered those guards).

JavaFX emitted unnamed-module/GDK warnings in the test environment; tests completed successfully. These were not represented as native-platform certification.

## Genuine-data overlap audit
Executed against the actual bundled ZIP, not fixtures:

| Measure | Result |
|---|---:|
| Source observations | 52,491 |
| Normalized titles shared across all three stores | 0 |
| Extracted model-like tokens shared across all three stores | 0 |
| Distributed reviewed links | 0 |
| Distributed reviewed three-store variants | 0 |

Audit normalization uses Unicode NFKC/lowercase with punctuation removed and whitespace collapsed. Model-like tokens contain at least five characters, at least two digits and a letter, excluding simple capacity/weight units. This conservative inventory audit is **not proof that latent identity overlap is impossible** and is not automatic human verification. No reliable three-way group has been established, so no such mappings or prices are fabricated.

Exact counts/hash/test summary are in `verification-results.json`. The internal catalog CSV SHA-256 remains `f27498caf88eb37360c807743b9fd05b52fcbeeb2c168e9e3af581adf82df8d0`; its containing ZIP SHA-256 is `e363742673db3620b34a10039aff4bc00fe985aa85f84f23492da8a9ecbb9033`.

## Screenshots
- `same-product-empty.png`: genuine-data default zero-match state.
- `same-product-browse.png`: source browsing after disabling the all-three filter.
- `same-product-missing.png`: genuine unmapped record, no substitute listings.

Older `dashboard.png` / `comparison.png` belong to the separate clearly fictional legacy demo; `dataset-*.png` belong to the prior single-source mode. Screenshots of the rejected arbitrary-selection workflow were removed.

## Remaining limits
- Genuine aligned source records/review evidence are still needed to populate an actual three-store comparison. The code fix does not supply missing genuine data.
- Human review notes/URLs are not independently verified by the app. Conservative English title checks may reject legitimate aliases or unit spellings; no automatic conversion/inference is claimed.
- No live price/stock/delivery verification, no current best-deal claim, and no native installer/offline dependency bundle.
