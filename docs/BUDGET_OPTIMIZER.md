# Tulita Budget Optimizer

A shopping-decision module that finds the best **combination of catalogue products** a shopper can
buy inside a stated budget. It is an extension of the existing Tulita JavaFX application: same
catalogue, same DAOs, same CSS, same test framework, no new runtime.

- **Java 17 source level on JDK 21**, JavaFX 21.0.6, Maven, JUnit 5 — unchanged.
- **No new dependency.** CSV export reuses Apache Commons CSV, JSON history reuses Jackson, the UI is
  JavaFX. Nothing was added to `pom.xml`.
- **No new database table.** The planner reads the catalogue through the same validated path as the
  comparison screens and stores saved plans as JSON next to the cached vision model.

---

## 1. How to open it

```powershell
run-catalog.bat          :: or:  TULITA_MODE=catalog TULITA_DATA_SOURCE=csv mvn javafx:run
```

In the left rail, click the new **◎ Budget planner** entry. **Discover** returns to the catalogue.
Both pages share one catalogue load; the planner never re-reads or re-validates anything itself.

## 2. What the screen does

| Step | Controls |
|---|---|
| 1 · Enter budget | Amount field with validation, quick-pick amounts, currency shown from the catalogue |
| 2 · Categories | One row per catalogue category: required toggle, min/max quantity, preferred brand, priority, minimum specification + value, optional target unit price |
| 3 · Preferences | Four recommendation modes and five preference weight sliders (normalised on submit) |
| 4 · Charges | Per-retailer delivery fee (charged once per shop) and a discount percentage with a minimum-subtotal threshold |
| Action | **Optimize My Budget**, **Save recommendation**, **Export CSV**, **Reset** |
| Results | Budget/total/remaining/utilisation KPIs, TULITA Recommendation Score, spending-by-category bar chart, per-product cards with photo, specs, score, explanation and trade-off, alternative baskets, and the data limitations |

Everything is computed from the loaded catalogue. There are no hardcoded recommendations, no
mock products and no placeholder numbers anywhere in the module.

## 3. Layout

```
tulita.budget
├── model        BudgetRequest (+ CategoryRequirement, MinimumSpec, Priority), PreferenceWeights,
│                RecommendationMode, ScoringCriterion, ProductCandidate, ProductScore,
│                SelectedProduct, BudgetBreakdown, BudgetRecommendation,
│                RecommendationAlternative, OptimizationResult, SavedBudgetRun
├── engine       BudgetOptimizationEngine (search), OptimizationConstraintValidator
├── service      ProductScoringService, CategorySpecProfiles, BudgetCalculationService,
│                RecommendationExplanationService, BudgetOptimizerService, BudgetExportService,
│                BudgetMoney
├── dao          BudgetHistoryDao, JsonBudgetHistoryDao
├── controller   BudgetOptimizerController  (worker thread → Platform.runLater)
└── view         BudgetOptimizerView
```

`model → engine/service → controller → view` mirrors the existing `photocatalog` layering. The view
contains no arithmetic; the engine contains no UI code; the service needs no database, so it is
tested without a screen or a server.

## 4. The optimisation algorithm

**Model.** One chosen product per category, repeated by quantity, under a total-cost limit. Delivery is
a fixed fee per *retailer*, so the transport cost depends on which shops the basket touches. That makes
this a multiple-choice knapsack with a set-dependent surcharge — strictly harder than the textbook
knapsack, and the reason a greedy "cheapest per category" pass is not correct.

**Search.** Pareto dynamic programming over per-category states:

```
state = (item subtotal in cents, retailer mask, score sum, spec sum, chosen options)
for each category:
    extend every state with every eligible option (skip option included for optional categories)
    discard states whose optimistic lower bound (subtotal + fees of retailers already used
        − maximum possible discount) exceeds the budget
    prune to the Pareto front: keep a state only if no cheaper-or-equal state is at least as good
        on both score objectives
```

All money is held in **integer paise/cents**, so the budget test is exact; the scoring arithmetic uses
`BigDecimal` at scale 2 with `HALF_UP` rounding in `BudgetCalculationService`, which is also what the
result screen and the CSV use — the three can never disagree.

**Why one front serves four modes.** The Pareto front keeps every non-dominated trade-off between cost,
weighted score and specification sum. Each mode is a comparator over that same front:

| Mode | Comparator (in order) |
|---|---|
| Best Value | weighted score ↓, total cost ↑, item count ↑ |
| Lowest Cost | total cost ↑, weighted score ↓, item count ↑ |
| Best Specifications | specification sum ↓, weighted score ↓, total cost ↑ |
| Balanced | `0.6 × mean product score + 0.4 × share of budget unspent` ↓, then score ↓, then cost ↑ |

So the two required modes genuinely optimise different objectives; the demo shows Lowest Cost picking a
cheaper basket with a lower score, and Best Value paying more for a higher one. When a single basket
wins both objectives it is shown once, never twice under two names.

**Complexity.** `O(categories × front size × options per category)` time, `O(front size)` memory. A
safety cap of 20 000 surviving states bounds a pathological request; when the cap is hit,
`OptimizationResult.exhaustiveSearch()` becomes `false` and both the screen and the CSV say the result
is a **bounded heuristic, not a proof of global optimality**. With the shipped catalogue the front stays
tiny (single or double digits), so the result is exhaustive and reported as such.

**Determinism.** Categories, candidates and options are explicitly sorted and every tie is broken by a
stable signature, so identical input always yields the identical basket (test 10).

**Self-check.** Before anything is displayed, `OptimizationConstraintValidator` re-verifies the produced
basket: every line in a requested category, quantities inside the allowed range, the minimum
specification met, every required category present and the total inside the budget. A violation throws
instead of rendering a wrong plan.

## 5. The TULITA Recommendation Score

```
score = Σ (normalised weight × normalised component) / Σ (normalised weight)
```

Two figures are shown, because they answer different questions:

* **average score** — the mean of the selected products' scores, ignoring priority and quantity;
* **weighted score for your priorities** — `scoreSum / Σ(quantity × category priority)`, which is the
  quantity **Best Value actually maximises**. A basket that adds an extra cheap item can lower the plain
  average while improving the weighted score, so showing only the average would make the modes look
  inconsistent (test 25 pins this invariant: Best Value can never lose to Lowest Cost on the metric it
  optimises, and Lowest Cost can never cost more).

They are **not** a claim of absolute product quality, and the UI labels them as
"TULITA Recommendation Score" throughout.

| Component | How it is measured | Missing data |
|---|---|---|
| Price | min–max inverted inside the category, or `target / price` capped at 1 when a target was set | never missing (a candidate without a usable price is excluded) |
| Specifications | `0.7 × mean of the normalised present metrics + 0.3 × attribute coverage` | neutral **0.5**, disclosed |
| Value for money | specification score per unit price, min–max normalised inside the category | neutral **0.5**, disclosed |
| Brand | `1.0` on a match, `0.0` otherwise | criterion dropped when no brand was requested |
| Warranty | normalised warranty length | criterion dropped for a category whose records carry no warranty |

Two rules keep the score honest:

* **Evidence is merged per curated variant.** Both retailers' listings describe the same physical product,
  so their extracted metrics are combined *before* scoring. A shop that simply documents more keys cannot
  make its own price look better than the other shop's price for the same item (test 16). Price, retailer
  and delivery stay per offer.
* **Units are converted, not guessed.** `180-day limited warranty` becomes 6 months, not 180 months
  (test 17); `1 year` becomes 12 months; GB/MB, oz/qt/l and lb/kg are converted the same way.

Metrics come from the retailers' own specification keys, mapped per category by
`CategorySpecProfiles` (e.g. `Hard Drive Capacity|Memory Capacity → Storage (GB)`,
`Watts|Wattage → Power (W)`, `Capacity|Volume → Capacity (oz)` with unit conversion). A category whose
records contain no warranty key drops the warranty weight and **says so** in the limitations list;
nothing is imputed.

## 6. Explanations and trade-offs

Every line carries an explanation assembled from the actual evaluation — cheapest eligible option,
highest score, mandatory minimum met (with the recorded value), preferred brand matched, at or below the
target price, quantity counted, or "no comparable specification recorded, scored neutrally". The
trade-off sentence names the cheapest eligible alternative in the same category, how much it saves and
how many score points it gives up. No claim is made that the data cannot support.

## 7. Money, charges and honesty

- `total = items + delivery − eligible discount`, all `BigDecimal` scale 2, `HALF_UP`.
- Delivery is charged **once per retailer**, so two products from one shop never pay twice (test 7).
- The discount applies only when the item subtotal reaches its threshold, and the screen explains when
  it did not.
- The catalogue records **no** shipping, promotion, availability, rating or capture-date data, so those
  inputs are labelled as **your own assumptions** wherever they appear. No savings percentage is shown,
  because there is no second baseline to compare against.
- **You can plan in \u20b9 (INR).** Step 1 has a currency selector and a rate box. The catalogue records
  prices in one currency (the bundled data is USD), so Tulita asks **you** for the rate to use — it never
  supplies, guesses or fetches one. `CurrencyConverter` applies the rate at exactly two edges:

  - the budget you type (INR → catalogue currency, `toCatalogue`) — this is the amount the optimiser uses;
  - the amounts shown back (catalogue currency → INR, `toDisplay`) — KPIs, cards, chart, CSV, printout.

  The engine, the arithmetic, the hard budget constraint and everything persisted stay in the catalogue
  currency, so a conversion can never change which basket wins. Conversion is monotonic, so a basket
  inside the budget cannot appear over it in INR (test 28). With no rate the app stays in the catalogue
  currency and says so; a blank, zero, negative or non-numeric rate is rejected rather than guessed
  (test 27), and the typed budget is echoed back exactly as typed (`INR 2,00,000.00`), never with
  round-trip noise. `BudgetMoney.format` prints Indian digit grouping: `INR 1,23,456.78` (test 26).
- **A catalogue already in INR needs no code at all** — the money path is currency-agnostic (test 23), and a
  request in a currency the catalogue does not use is refused with a message naming both (test 24).
- Amounts keep the source currency (`USD`) and are printed with Indian digit grouping
  (`USD 1,23,456.00`) — grouping is a display convention, never a re-labelling of the currency.

## 7a. Per-line charges, what-if exploration and printing

**Per-line charges (`LineCharges`).** The basket pays a delivery fee once per retailer and a discount on
the whole item subtotal, so a single product card cannot simply repeat the basket total. `BudgetCalculationService.allocate`
splits the delivery fee between that retailer's own lines in proportion to their value, splits the
eligible discount across all lines in proportion to value, and gives any rounding remainder to the
largest line — so the per-line final costs add up to the basket total **to the cent**
(tests 18–20, including a case where the shares do not divide evenly). Each card therefore shows
`item`, `delivery`, `discount` and a bold `final` figure, and every card links to the recorded retailer
page the price came from (the link is labelled "recorded link, may have expired", because Tulita cannot
check availability).

**Explore Alternatives (what-if).** `BudgetOptimizerService.whatIfOptions(request)` builds a list of
complete alternative requests — 10 % less money, 10 % more money, each other optimisation mode, making a
mandatory category optional, one less unit, any brand, relaxing a minimum specification, and dropping
your own delivery/discount assumptions. Clicking one:

1. writes the whole request back into the form (`view.applyRequest`), so the visible inputs always match
   the recommendation on screen;
2. re-runs the optimiser on the background worker thread — never on the JavaFX thread;
3. shows a **Changes since your last optimisation** card built by
   `BudgetOptimizerService.delta(previous, current)`: the signed change in the estimated total, the
   change in remaining money, the change in recommendation score, the item count, and each product that
   was swapped (old → new, with prices). If the basket genuinely did not move, the card says so instead
   of inventing a difference (test 22).

None of the options relaxes a mandatory rule *silently*: an option that drops a requirement says so in
its own label ("Make Computers optional"), and the last mandatory category can never be dropped, because
there would be nothing left to optimise.

**Saved recommendations.** `View` on any saved plan opens the stored detail — mode, timestamp, budget,
total, remaining, every line with its retailer, quantity, unit price, line total and observation date,
plus the limitations recorded at save time. The viewer only shows what was written at save time; it does
not re-price anything.

**Printing / PDF.** *Print / save as PDF* uses JavaFX's own `PrinterJob` on the same recommendation the
screen shows (totals, lines with charges and explanations, breakdown, limitations). Choosing a PDF
printer in the operating system's print dialog produces a PDF, so **no new dependency was added**. The
document is built in `buildPrintableDocument()` and is unit-testable without a printer; when the machine
reports no print service the app says exactly that instead of pretending to export.

## 8. Saving and exporting

- **Save recommendation** appends the plan to `~/.tulita/budget-recommendations.json`
  (override with `TULITA_BUDGET_STORE`). Products are stored by reference (id + retailer), never
  duplicated, and the catalogue files are never written to.
- **Export CSV** writes the plan on screen — including charges, discount, totals, scores, timestamps,
  alternatives and limitations — using Commons CSV.
- **Print / save as PDF** uses the JavaFX print pipeline described in §7a — the platform print dialog can
  target a PDF printer, so no PDF library was added to `pom.xml`.

## 9. Tests

```powershell
mvn test -Dtest=BudgetOptimizerTest                       # 17 tests, no display, no model needed
mvn test -Dtulita.budgetUiTests=true -Dtest=BudgetOptimizerUiTest   # needs a display / Xvfb
```

`BudgetOptimizerTest` (28 tests) covers every scenario in the brief: valid budget, insufficient budget, Best Value
vs Lowest Cost, missing price, optional categories, quantities, delivery + discount, invalid input,
missing specifications, determinism, duplicate alternatives, monetary precision, plus the constraint
self-check, save/export, per-line charge reconciliation and determinism, what-if option validity, the
what-if change report, the mode-invariant weighted score, INR budgets converted with the shopper's own
declared rate plus rate validation and the monotonicity of the budget guarantee, INR-denominated
catalogues, refusal rather than conversion when a currency is unsupported, and an integration run against
the real 79-product catalogue.

`BudgetOptimizerUiTest` drives the real window: it loads the catalogue, clicks the rail entry, opens the
planner, optimises, asserts the rendered result, applies a what-if option and waits for the re-optimised
basket (asserting Lowest Cost never costs more), types a delivery fee and a discount and asserts the
per-line charge labels appear, saves the plan to a temporary store and re-opens the stored detail, builds
the printable document, then asserts the infeasible and invalid-input paths.
Screenshots go to `docs/budget-optimizer*.png` when `-Dtulita.screenshots=true`.

## 10. Known limits

- The catalogue holds 79 curated products, so "one product per category" is the honest model; the brief's
  example of a laptop + mouse + keyboard + headphones needs four *catalogue* categories, and Tulita only
  offers categories that really contain products.
- Prices are historical recorded observations, not live retailer prices; a basket is a plan, not a
  checkout.
- Delivery and discounts are user estimates, not retailer data.
- Delivery/discount shares are apportioned estimates, not retailer invoices: a real shop may charge
  shipping per parcel rather than per retailer.
- Printing needs an operating-system print service; in a locked-down environment the app reports that
  rather than producing a file.
- The search is exact for the stated model, not for a model with quantity-dependent bulk pricing or
  per-item shipping rules, which the catalogue cannot support.
