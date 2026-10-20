# Tulita — Smart E-Commerce Price Comparison System
**Compare Smart. Shop Better. Save More.**

Java 17/21 · JavaFX 21 · MySQL 8/JDBC · Maven · MVC · Advanced OOP college project.

## Important: this is the strict same-product correction
The previous edition allowed arbitrary records to be selected from three stores. **That is not same-product comparison, and those controls have been removed.**

The new comparison key is **one canonical manufacturer model + one exact reviewed variant**, linked to original Amazon, Flipkart and Myntra source records. Similar titles or the same brand do not create membership. Each comparison has three fixed store slots. Unmapped stores remain missing.

**Data limitation:** the bundled 52,491 genuine historical records come from independent datasets. An audit found zero normalized titles and zero extracted explicit model-like tokens common to all three sources. This does not prove there is no latent overlap, but establishes no reliable three-way identity group. Consequently the distributed review file is intentionally header-only: **0 reviewed groups / 0 three-store groups**. The software cannot create missing genuine observations. The user chose genuine-only data, so no aligned fictional prices have been substituted.

“Available in all 3 datasets” means **reviewed records exist in all three datasets**, not that the product is currently in stock. The default view therefore starts empty. Unchecking it allows all source records to be searched; browsing does not approve a match.

| Source | Bundled records | Snapshot context |
|---|---:|---|
| Amazon India | 20,000 | Sample from a 2023 dataset; exact row dates unknown |
| Flipkart | 20,000 | 2015–2016 observations |
| Myntra | 12,491 | 2021 dataset; exact row dates unknown |

No live API, retailer scraping, paid account, API key or Python/Node backend is required.

## 1. Run in Windows / VS Code — easiest CSV path
1. Install **JDK 21** (JDK 17 also targets the source), **Maven 3.9+**, and **VS Code** with **Extension Pack for Java**. Maven and Java must be on PATH. Restart VS Code after installation.
2. Extract the whole ZIP. Open the **`tulita` folder containing `pom.xml`** in VS Code. Do not open only `src` or a single Java file. Keep `datasets`, `matching` and `src` beside `pom.xml`.
3. Open **Terminal → New Terminal** (PowerShell). Check:

```powershell
java -version
mvn -version
```

Both must use JDK 17 or 21, not Java 11. A separate JavaFX SDK is unnecessary; Maven downloads the matching dependencies.

4. Run:

```powershell
$env:TULITA_MODE = 'multistore'
$env:TULITA_DATA_SOURCE = 'csv'
mvn clean test
mvn javafx:run
```

The first build needs internet for Maven dependencies, not for price retrieval. Subsequent use reads the bundled ZIP locally. Alternatively double-click **`run-multistore.bat`**. Do not use a Java file’s individual Run button or `java -jar`.

On Linux/macOS with a desktop and JDK/Maven installed:

```bash
TULITA_MODE=multistore TULITA_DATA_SOURCE=csv mvn javafx:run
```

## 2. Use the corrected workflow
1. The checked **Available in all 3 datasets (reviewed links)** filter shows an honest zero-result explanation for the bundled data.
2. Uncheck it to browse/search the original source records. Search any words present in this finite catalog, e.g. `Puma`, `watch`, `Samsung`, or a manufacturer model. This is not a live full-store search.
3. Store/category filters, sorting, pagination and a session watchlist work. The watchlist resets on exit.
4. Click **Compare this exact variant**. An unmapped record shows only its own source observation; the other two store slots are missing. Even a similar/model-code candidate is not automatically added.
5. Once genuine reviewed links are supplied, a canonical variant gets **one card**, rather than three unrelated cards. The comparison displays only its mapped observations, their original amounts/dates and the review evidence.
6. **Export source IDs** creates a CSV with exact record IDs and SHA-256 fingerprints for the review process. It does not generate matches. Use **Reload source** after saving a valid mapping CSV or importing mappings into MySQL.

![Strict default: no invented three-store matches](docs/same-product-empty.png)
![An unmapped source record does not borrow other-store prices](docs/same-product-missing.png)

### Adding genuine same-product groups
Read **[matching/README.md](matching/README.md)**. It explains all 17 mapping columns, identity/variant requirements, export, validation, import and source replacement. **Adding mapping rows cannot compensate for an absent store observation.** If the current sources lack the same product, suitable legally obtained genuine source records must be added first. Do not map different products to make the all-three view nonempty.

## 3. Use MySQL 8 instead of CSV
CSV mode is the quickest way to inspect the app. The project also implements actual MySQL persistence/import; no silent fallback is used.

### A. Create tables
Install/start MySQL **8.0+**. In MySQL Workbench, open and execute in this order:

1. **`multistore-schema.sql`** — original historical observations and source provenance.
2. **`same-product-schema.sql`** — canonical variants and reviewed identity links.

These scripts do not insert fictional matches. Keep the database name `tulita` unless you deliberately adapt both scripts and the JDBC URL. The optional older `schema.sql` and `dataset-schema.sql` are not needed for the new workflow.

As an administrator create a local account (choose your own password):

```sql
CREATE USER 'tulita_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_OWN_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE ON tulita.multi_sources TO 'tulita_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON tulita.multi_records TO 'tulita_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON tulita.canonical_variants TO 'tulita_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON tulita.same_product_links TO 'tulita_app'@'localhost';
```

If the account already exists, skip CREATE USER. Do not broadly grant remote `%` access. A separate SELECT-only account on these four tables is sufficient for browsing after imports.

### B. Configure locally
In PowerShell, from the project folder:

```powershell
Copy-Item application.properties.example application.properties
```

Edit `application.properties` in VS Code. Set your `db.user` and `db.password`; check `db.url`. Do not share/commit this private file. Example defaults are for local student development; TLS must be configured for a remote database. Environment variables override this file; remove stale `TULITA_DB_*` variables if your edits appear ignored.

### C. Import the original source catalog

```powershell
mvn -q compile exec:java '-Dexec.mainClass=tulita.dataset.MultiImportMain'
```

Or run `import-multistore.bat`. This transaction **replaces** the imported source catalog; it is not an incremental merge. Replacing records cascades away old reviewed links. Re-review/re-import mappings afterward. Original bundled CSV files are not edited.

The distributed mapping CSV is empty, so there is nothing to import initially. Empty mapping imports are deliberately refused rather than erasing existing reviews. Once you have genuine reviewed links:

```powershell
mvn -q compile exec:java '-Dexec.mainClass=tulita.matching.ReviewToolsMain' '-Dexec.args=--import-mysql matching/same-product-matches.csv'
```

### D. Launch

```powershell
$env:TULITA_MODE = 'multistore'
$env:TULITA_DATA_SOURCE = 'mysql'
mvn javafx:run
```

Or run **`run-multistore-mysql.bat`**. All four tables must exist, even when there are zero review rows. Missing tables/credentials/imports produce an error with Retry, never a demo/CSV/similarity fallback.

## 4. Identity and price safeguards
- Reviewed membership, not heuristic search candidates, controls comparisons.
- A complete canonical model phrase/code must occur in every source title. A retailer-only SKU is not a manufacturer model. Review aliases absent from titles are conservatively rejected in this edition.
- Review links require a source ID, matching store, exact observation SHA-256, HTTPS evidence URL, nonfuture review date and substantive note. URLs/notes are supplied by a human; the app does not independently verify them online.
- One record cannot be reused across variants; at most one observation per store/variant. Conflicting canonical definitions, brands, model qualifiers or recorded variant attributes are rejected atomically.
- Explicit title colour/gender/capacity cannot be overridden by a canonical declaration. Fashion variants require reviewed size/colour/fit; phone variants require capacity/colour. Unknown fields are not proof of agreement. Human-supplied missing attributes need actual evidence.
- English attribute extraction is conservative. Other scripts, aliases and unit conversions may need better source data; unknown attributes are not guessed. These checks reduce mistakes but do not replace careful human review.
- Mapping files/tables contain **no prices**. Prices, MRP, ratings and source dates stay on the source observations; money uses `BigDecimal`.
- Delivery, current stock and final payable total remain unknown. The smallest reviewed historical item price may be labelled as such, **not** as a current or delivered-price winner. Different snapshot years do not establish actual shopping savings.
- Source URLs can have expired. Artwork is illustrative, not retailer product photography.

## 5. Source provenance and licences
1. [PromptCloud Flipkart Products](https://www.kaggle.com/datasets/PromptCloudHQ/flipkart-products): publisher-listed CC BY-SA 4.0.
2. [Asaniczka Amazon India Products 2023](https://www.kaggle.com/datasets/asaniczka/amazon-india-products-2023-1-5m-products): publisher-listed ODC-By 1.0; this bundle samples 20,000 of 1,589,160 records.
3. [Nirokey / CrawlFeeds Myntra](https://www.kaggle.com/datasets/nirokey/myntra-fashion-products): publisher-listed CC0 1.0.

Full notices, hashes, sampling and transformations are in `datasets/multistore/ATTRIBUTION.md`, `MANIFEST.json` and bundled licence texts. Source components remain identifiable. Preserve relevant attribution/share-alike/source conditions on redistribution. These licences do not imply retailer endorsement or clearance of every underlying third-party content item. No retailer images are redistributed. Noto font licences are retained with the bundled fonts.

## 6. Architecture / Advanced OOP
JavaFX view → MVC controller → `SameProductService` → CSV/JDBC DAOs → source observations + reviewed canonical membership.

- Immutable records: `CanonicalProduct`, `ReviewedLink`, `MatchedProduct`, `DatasetProduct`, provenance/snapshot models.
- `SameProductService`: atomic identity validation, canonical grouping, strict all-three filter, grouped search/sort/watch lookup. `CrossStoreMatcher` is used only as a negative conflict guard here; its suggestions do not grant membership.
- `DatasetController`: background I/O/search/export, debounce, generation guards, lifecycle shutdown.
- `DatasetView`, `SameProductComparisonView`, `DatasetDetailView`: working controls, one-page rendering and original source details/link actions.
- DAO polymorphism (`DatasetDao`), interchangeable CSV/JDBC observation sources, dedicated reviewed CSV/JDBC DAOs, prepared statements, resource management and transactional batch import/rollback.
- Original mock store-parser interfaces and private-field domain classes are retained in the separate demo. Collections, comparators, exceptions, records, JavaFX inheritance and executor concurrency demonstrate practical OOP without an artificial product hierarchy.

No web stack, login, payments, checkout, live API, scraper, ML or background monitoring is included.

## 7. Configuration / separate legacy modes

| Variable | Purpose |
|---|---|
| `TULITA_MODE` | `multistore` (default), `dataset` (old Flipkart-only), `demo` (fictional), `mysql` (fictional demo DB) |
| `TULITA_DATA_SOURCE` | `csv` / `mysql` for historical modes |
| `TULITA_MULTI_DATASET_PATH` | Normalized catalog ZIP; default `datasets/multistore/catalog.csv.zip` |
| `TULITA_MATCHES_PATH` | Reviewed CSV; default `matching/same-product-matches.csv` (CSV mode only) |
| `TULITA_DATASET_PATH` | Old single-source CSV |
| `TULITA_DB_URL`, `TULITA_DB_USER`, `TULITA_DB_PASSWORD` | Private JDBC settings |

The older `run-demo.bat` / `run-mysql.bat` use clearly labelled fictional products and known-total arithmetic. `run-dataset*.bat` retain single-source browsing. **None are used to fill missing genuine three-store matches.** Historical strict comparison is the default multistore mode.

## 8. Tests and verification

```powershell
mvn clean verify
```

85 tests discovered: 78 execute by default; seven opt-in desktop/MySQL tests skip. New tests include no-review/no-match behavior, genuine-bundle zero groups, canonical deduplication, missing stores, model/colour/capacity conflicts, fingerprints, source reuse, CSV/BOM handling, export and pagination.

Desktop check (requires graphical desktop):

```powershell
mvn test '-Dtest=MultiStoreUiTest' '-Dtulita.multiUiTests=true'
```

Optional DB tests are destructive to their test data. **Never run them against your working database.** The new `ReviewedMySqlTest` additionally requires a dedicated URL containing `tulita_review_test`, schema copies for that database, and `-Dtulita.reviewMysqlTests=true`. Other legacy opt-in flags: `tulita.mysqlTests`, `tulita.datasetMysqlTests`, `tulita.multiMysqlTests`, `tulita.uiTests`, `tulita.datasetUiTests`. See [executed verification](docs/VERIFICATION.md).

## 9. Troubleshooting
| Problem | Fix |
|---|---|
| Zero default results | Expected: no reviewed three-store groups are bundled. Uncheck the filter to browse; add genuine evidence-backed records/mappings to populate it. |
| Three stores show different products | That old selection path is removed. Use this corrected ZIP, not the previous three-store ZIP. |
| Model exists in only one/two stores | Missing slots are intentional. You need genuine matching source observations, not another similar product. |
| Mapping rejected | Check exported ID/hash, exact model phrase, per-store uniqueness, consistent canonical fields and recorded variant attributes. No partial mapping set is accepted. |
| Invalid release 17 / Java 11 | Fix JAVA_HOME/PATH; `mvn -version` must use JDK 17/21. |
| No pom.xml | Run in the extracted `tulita` folder containing it. |
| JavaFX runtime missing | Use `mvn javafx:run`. |
| Missing dataset or matching file | Extract the complete ZIP; keep `datasets` and `matching` beside `pom.xml`. |
| MySQL setup error | Run both new schemas, grant the four table permissions, configure credentials, import source catalog. |
| Mapping works in CSV but not DB | MySQL reads its own mapping tables. Import against the DB’s exact observations; it does not read the mapping CSV at launch. |
| Current model/price absent | These are finite historical samples, not current store APIs. |
| Unknown MRP/rating/delivery | Intentionally unknown, not zero/free. |
| Properties ignored | Environment overrides properties; batch scripts force their named mode/source. |

Native Windows execution and JDK 17 runtime were not available here; the project compiled with release 17 and ran on Linux JDK 21. `mvn package` creates a normal JAR, not an offline native installer/fat JAR.
