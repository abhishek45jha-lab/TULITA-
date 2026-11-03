# Running Tulita on other devices

Tulita is a **JavaFX desktop application**. That determines where it can run, so this page is explicit about what works, what needs a build step, and what does not work.

## 1. Quick summary
| Target | Status | What you do |
|---|---|---|
| Windows laptop/PC (64-bit) | **Supported** — self-contained build | Run `package-app.bat` once, then share the `dist\Tulita` folder |
| Linux laptop (x86-64) | **Supported** — self-contained build, verified here | `./package-app.sh`, then share `dist/Tulita` |
| macOS laptop | **Supported with the same command** | Build **on the Mac** (`./package-app.sh`); a Windows build cannot run on macOS |
| Any machine with a JDK + Maven | Supported from source | `mvn javafx:run` as in the README |
| Android phone / iPhone | **Not supported** | JavaFX 21 desktop has no mobile runtime; a phone build would be a new app |
| Chromebook / browser | **Not supported** | Deliberately a desktop app, not a website |

## 2. Build a self-contained desktop app (no JDK, no Maven needed to run it)
`jpackage` bundles a private Java runtime, the JavaFX libraries, the ONNX runtime, the curated catalog and all 237 photos.

**Windows**
```bat
package-app.bat
```
Output: `dist\Tulita\Tulita.exe` (folder is about 280 MB).

**Linux / macOS**
```bash
./package-app.sh          # needs a full JDK 17 or 21, Maven, and jpackage on PATH
```
Output: `dist/Tulita/bin/Tulita`.

Then copy the whole `dist/Tulita` folder to any machine **with the same operating system** — no installation, no Java, no internet required. Verified here: the Linux image was launched from `/tmp`, a folder with no project files, and it showed all 79 products with photos.

**Optional installers** (same OS as the build machine):
```bash
# Windows installer (needs the free WiX Toolset on PATH)
jpackage --type msi ... --win-shortcut --win-menu
# Debian/Ubuntu package
jpackage --type deb ...
# macOS disk image, built on a Mac
jpackage --type dmg ...
```

## 3. The 89 MB photo-search model
The build copies the catalog and photos but **not** the model by default, so the folder stays under 300 MB (some sharing tools cap uploads near 100 MB and Git hosts reject files over 50–100 MB).

Choose one:
1. **Let the app download it once** on the target machine — the app verifies the pinned SHA-256, then works offline. (Needs internet on first use.)
2. **Bundle it** while packaging:
   ```bash
   TULITA_INCLUDE_MODEL=1 ./package-app.sh
   ```
   ```bat
   set TULITA_INCLUDE_MODEL=1
   package-app.bat
   ```
   The app finds `model/clip-vision-int8.onnx` beside the launcher automatically, else `%USERPROFILE%\.tulita\`.

Text search, price comparison and the price chart never need the model.

## 4. Database is optional
The packaged app runs on the bundled JSON catalog. MySQL is only needed if you want to demonstrate the JDBC path: install MySQL 8, run `photo-catalog-schema.sql`, `import-photo-catalog.bat`, then create `application.properties` in the app folder with your own credentials.

## 5. What was actually tested
- Built a Linux x86-64 app image with `jpackage` and launched it **from outside the project folder**: the window opened, the sidebar showed 79 products / 161 prices / 16 categories, and the photo cards and prices rendered (`docs/packaged-app-running.png`).
- The bundled-model lookup was verified with a small runtime probe: the app resolved `dist/Tulita/model/clip-vision-int8.onnx` and the pinned SHA-256 check passed.
- Not tested: a Windows build (no Windows machine here), macOS, a phone, or an MSI/DMG installer. The scripts and commands follow the documented `jpackage` behaviour; build them once on the target OS before relying on them.

## 6. If you genuinely need it on a phone
That is a different deployment, not a packaging tweak:
- **Gluon Mobile / JavaFX Ports** for Android/iOS, or a rewrite in Kotlin Multiplatform / Flutter / React Native.
- The CLIP vision model would have to be re-exported and bundled for mobile (ONNX Runtime Mobile or TFLite), with a smaller image index.
- The MySQL connection would need a hosted backend instead of localhost credentials.

That is a new project scope; the current Tulita is an Advanced-OOP JavaFX desktop deliverable.
