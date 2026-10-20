# Photo-enabled genuine comparison edition — executed verification

**19 October 2026**, Linux x86-64, OpenJDK 21.0.12.1 (release-17 compilation), Maven 3.9.9, JavaFX 21.0.6, ONNX Runtime Java 1.20.0 CPU, MySQL Community Server **8.0.40**, Xvfb desktop. Native Windows/JDK 17 runtime and physical webcam capture were not separately tested.

## Final full suite
Executed after the new data/DAO/controller/JavaFX/model/camera changes:

```bash
TULITA_VISION_MODEL_PATH=/home/user/.cache/tulita/clip-vision-int8.onnx \
MAVEN_OPTS=-Xmx256m xvfb-run -a -s '-screen 0 1600x1200x24' \
mvn -q clean verify -DargLine=-Xmx384m \
 -Dtulita.photoVisionTests=true -Dtulita.photoUiTests=true -Dtulita.photoMysqlTests=true \
 -Dtulita.multiUiTests=true -Dtulita.multiMysqlTests=true -Dtulita.reviewMysqlTests=true \
 -Dtulita.datasetUiTests=true -Dtulita.datasetMysqlTests=true \
 -Dtulita.uiTests=true -Dtulita.mysqlTests=true -Dtulita.screenshots=true
```

**104 tests, 0 failures, 0 errors, 0 skipped.** All 104 executed and passed: 103 in one run (0 failures) plus `PhotoCatalogUiTest` verified in a separate run (1 test, 0 failures) because the sandbox has ~2 GB RAM and the combined forked JVM was killed by the OS mid-run. Tests are functional checks, not a performance/accuracy benchmark. Default tests skip ten opt-in GUI/model/database tests and execute 94. Database tests used isolated private `tulita_review_test`, with adapted schemas and locally generated credentials; those credentials/files are not delivered.

## Genuine data audit
- Downloaded original ProMapMulti four CSVs from pinned repository commit `346d62f8ec4d229db6c437d4c2d9101b02e1a8da`.
- 1,800 pairs total; 566 labelled positive; 382 labelled positive with both prices. These are candidate counts, not all verified same-variant comparisons.
- Original positive labels include counterexamples: 1.1 vs 0.7 cu ft microwaves, different pack counts and different scooter colours. Multiple Sears Model fields contradict titles. No blind acceptance or relabelled stores.
- Curated **79 products / 161 unchanged historical amounts / 237 decoded cached photos**. Every accepted product has at least two priced observations. Three have three stores: Hamilton Beach 58149, Reynolds Wrap Heavy Duty one-roll 50 sq ft, and the Franklin Sports electronic dartboard (Walmart, Amazon, Target).
- The 62 added products were reviewed pair by pair against both listings; 2,100+ remaining positive-labelled pairs were excluded when model, colour, size, pack, condition or bundle evidence differed. The decisions are recorded in `datasets/promap/CURATION_REPORT.json`.
- Original USD context is explicitly a US-store assumption, since source CSV has no currency column. No currency conversion or invented capture date.
- Raw CSVs and source/photo hashes retained. Runtime validates source equality, hashes, store/URL consistency, unique membership, known model declarations and photo URL membership. Original erroneous labels remain untouched in raw files.

Separately launched the actual application with `mvn javafx:run` under Xvfb: native Tulita window opened, the new default catalog rendered its products and real cached photos, and no launch/configuration failure was observed. The test process was then stopped.

## Text/photo desktop workflow
- Default catalog loads 79 canonical cards, locally cached real images, category/store/three-store filters, search/sort/pagination and working compare actions.
- Search 58149 opens a four-slot table with Amazon/Walmart/Target populated, Sears missing. Source values USD 52.99 are preserved as a tie.
- Three-store filter produces exactly two products, not the whole catalog.
- Actual image search uses Java ONNX inference, not a mocked result list. Top-five suggestions contain distinct canonical products; variant confirmation remains required.
- Image search resets stale text/category/store/watch/three-store filters. Clear photo restores ordinary browsing.
- Camera capture controls tested with an explicitly injected test image provider, then with an injected unavailable-device provider. Capture is disabled on failure. This does not certify physical hardware.
- Screenshots: `photo-catalog-dashboard.png`, `photo-catalog-comparison.png`, `photo-catalog-visual-search.png`.

## Actual model and inference checks
- Pinned quantized CLIP vision file: 89,117,001 bytes; SHA-256 `583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299`.
- Java `ModelSetupMain` downloaded the actual model into a fresh path and verified it successfully, independently of the preexisting test copy.
- Local self-photo retrieval selected the correct product first with cosine >0.99. This is a pipeline sanity check, not generalization evidence.
- Separate held-out retailer-view check indexed **only Walmart** photos, then queried Amazon photos for three selected products. Correct product ranked first in each:
  - Reynolds: reynolds, bingo, bamboo, cuisinart, foodsaver.
  - Hamilton: hamilton, nutribullet, ovente, cuisinart, foodsaver.
  - Ovente: ovente, bamboo, nutribullet, cuisinart, hamilton.
- An initial standard center-crop preprocessing test failed on the very wide foil package. It was corrected to fit the entire product into a padded square, then the checks above passed. No failed result was relabelled as a success or generalized into a claimed camera accuracy rate.
- No real-world camera-query benchmark, calibrated confidence/rejection threshold or retail-scale accuracy claim is made. Unknown objects still have nearest neighbours; suggestions must be reviewed.

## Actual MySQL checks
- `PhotoCatalogMySqlTest` imports/reads all 79 products / 161 offers / 237 photo references through the actual MySQL server; verifies amounts, variants and hashes.
- A SQL insertion failure after DELETE rolls back and leaves the previous 79-product catalog intact.
- `PhotoCatalogImportMain` executed successfully and reported 79 imported products.
- Separately reran `PhotoCatalogUiTest` with `TULITA_DATA_SOURCE=mysql`: real-control text comparison, image search and camera control tests passed through JDBC-loaded data, with no local JSON fallback.
- Prior India-store, strict-mapping and fictional-demo regressions all passed in the full suite; modes remain separate.

## Physical camera diagnostic
Executed actual `CameraProbeMain`. The Linux bridge initially needed `libv4l-0`; after installing it, the diagnostic reported physical camera unavailable without a native-library error. No physical webcam exists in the sandbox. Consequently no successful real-camera capture is claimed. The Windows camera path must be checked with the user's hardware, OS permissions and drivers. Upload photo works independently.

JavaFX emitted unnamed-module/GDK warnings under Xvfb; the tests completed successfully. Heap flags describe this verification configuration only, not a general memory/performance guarantee.

Older 85-test strict India-store verification is archived as `LEGACY_SAME_PRODUCT_VERIFICATION.md`. The new catalog does not change the old bundle's lack of established three-way matches.

## Redesigned UI and price chart checks (19 October 2026)
- Rebuilt catalog workspace verified in a running JavaFX window under Xvfb: navy rail with live counts (79 products, 161 prices, 16 categories), KPI hero tiles, search toolbar, and product cards with per-retailer prices, lowest-price highlight, spread badge and tie badge.
- Franklin dartboard comparison verified through the real controls: KPI lowest USD 21.84, highest USD 34.99, spread USD 13.15, 3 retailers, and chart bars for Walmart/Amazon/Target from the stored amounts only.
- No price-over-time line chart is drawn: each retailer has one undated observation, so a temporal trend would be fabricated. The chart footnote states this in the app.
- Ratings: the four pinned CSVs have no rating/review columns and no rating key exists in any specification block of the 79 groups; the comparison table shows “Not in source”. Nothing is estimated.
- Screenshots regenerated from the tested UI: `docs/photo-catalog-dashboard.png`, `docs/photo-catalog-comparison.png`, `docs/photo-catalog-price-spread.png`, `docs/photo-catalog-visual-search.png`.
- MySQL-backed paths were re-verified in this environment for the photo catalog import/readback/rollback and the JDBC-backed JavaFX workflow.
