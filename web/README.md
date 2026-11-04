# Tulita browser edition

This is a separate, static browser preview of Tulita's curated product catalog. It does not change or replace the JavaFX desktop application in the repository root. The web edition provides catalog search, category filtering, price sorting, product comparison, source links and attribution. It does not include the desktop camera/ONNX workflow, MySQL mode, or budget optimizer.

## Deploy to Vercel

1. Import `abhishek45jha-lab/TULITA-` into Vercel (or create a separate Vercel project for this repository).
2. Select the `web-version` branch.
3. Set **Root Directory** to `web`.
4. Use the static/Other preset; no build command or output directory is required. Deploy.

The site fetches the published curated catalog and reference images from the public `main` branch at runtime. No credentials, server, or database are needed. If you later move the data, update `CATALOG_URL` and `ASSET_ROOT` in `app.js`.

A successful deployment will receive its own Vercel URL. The deployment cannot be completed from this repository change alone; a Vercel account/project authorization is needed to publish it.

## Run locally

From the repository root, serve the `web` folder with any static file server, for example:

```bash
cd web
python3 -m http.server 8000
```

Then open `http://localhost:8000`. Opening `index.html` directly may prevent the browser from fetching the remote catalog due to browser security restrictions.

## Data notes

Displayed amounts are historical source observations, not live prices. Retailer listing availability and amounts may have changed. See the [catalog attribution and provenance](../datasets/promap/ATTRIBUTION.md) in the repository.
