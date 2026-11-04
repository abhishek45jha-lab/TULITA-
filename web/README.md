# Tulita browser edition

This standalone browser version is kept on the `web-version` branch; the JavaFX desktop application on `main` is not replaced or modified. It now includes:

- Catalog discovery, product search, category filtering, price sorting, and recorded-offer comparisons.
- A browser budget planner with category selection, four objectives, per-store delivery estimates, a discount assumption, and optional shopper-entered INR conversion.
- Local image upload and a best-effort visual-lookalike search. The selected query image is processed in the browser and is not uploaded; this uses coarse color-histogram matching, not the desktop CLIP/ONNX model.

## Deploy to Vercel

Import `abhishek45jha-lab/TULITA-` into Vercel, select the `web-version` branch, set **Root Directory** to `web`, choose the **Other** static preset, and deploy without a build command. Existing Vercel deployments should redeploy after these commits.

The site reads the public curated catalog and reference photos from the repository's `main` branch. It needs no database, credentials, or server. The branch does not configure Vercel account settings or create a live URL by itself.

## Run locally

Serve the `web` directory with a static server, for example:

```bash
cd web
python3 -m http.server 8000
```

Open `http://localhost:8000`. Opening the HTML file directly may block remote catalog loading due to browser security rules.

## Data and limitations

Amounts are historical observations, not live prices. The browser budget planner is a simplified companion, not a port of the desktop Java Pareto optimizer. It selects one offer per selected category and reports when its search-state cap is reached. The image preview compares color histograms only; it is not proof of product identity. See [catalog provenance and attribution](../datasets/promap/ATTRIBUTION.md).
