# Running Tulita in VS Code

Everything below is what this repository was actually verified with: **JDK 21** (JDK 17 or newer works) and
**Maven 3.9**. You do not need to install JavaFX separately — Maven downloads it.

## 1. Install the prerequisites

| Tool | Check it works | Notes |
|---|---|---|
| JDK 17+ (21 recommended) | `java -version` | Any distribution: Temurin, Oracle, Microsoft OpenJDK |
| Maven 3.8+ | `mvn -v` | Must print a Java version, not an error |
| VS Code | — | plus the extension below |

Install the **Extension Pack for Java** (`vscjava.vscode-java-pack`). VS Code will prompt you because the
repo ships `.vscode/extensions.json`.

## 2. Open the project

`File → Open Folder…` → pick the folder that contains **`pom.xml`** (i.e. the unzipped `tulita` folder,
not its parent).

The Java extension will read `pom.xml`, download JavaFX/Jackson/ONNX/Commons-CSV into `~/.m2`, and show
`tulita` under **Java Projects**. Wait for the progress bar to finish before running anything.

## 3. Run the app (recommended: the task)

Press **`Ctrl+Shift+B`** (macOS: `Cmd+Shift+B`) → **`Tulita: run app (catalogue mode)`**.

That runs `mvn javafx:run` with `TULITA_MODE=catalog` and `TULITA_DATA_SOURCE=csv`, so it uses the bundled
79-product catalogue — **no database and no model required**. Then in the left rail click
**◎ Budget planner**.

Equivalent terminal commands (**Terminal → New Terminal**):

```bash
# Linux / macOS
TULITA_MODE=catalog TULITA_DATA_SOURCE=csv mvn javafx:run

# Windows PowerShell
$env:TULITA_MODE="catalog"; $env:TULITA_DATA_SOURCE="csv"; mvn javafx:run
```

## 4. Run / debug with the Run button (F5)

`Run and Debug` panel → choose **`Tulita (catalogue mode)`** → **F5**. Breakpoints in any `tulita.*`
class work, including the budget optimizer.

Why there is a launcher class: Java refuses to start a class that extends
`javafx.application.Application` when the JavaFX jars are on the class path (which is what VS Code does),
and prints *"JavaFX runtime components are missing"*. `tulita.Launcher` is a plain `main` that calls
`TulitaApp.main(...)`, so Run/Debug works. `mvn javafx:run` keeps using `tulita.TulitaApp`.

If VS Code ever says the main class is not found: **Java Projects → tulita → right-click →
"Clean Java Language Server Workspace" → Restart and delete**, then let Maven finish reloading.

## 5. Run the tests

- **Testing** panel (flask icon) → run any class, e.g. `BudgetOptimizerTest` — or
- Terminal: `mvn test -Dtest=BudgetOptimizerTest` (28 tests, no display needed) — or
- **`Ctrl+Shift+P` → Tasks: Run Task → `Tulita: run all tests`** (`mvn clean test`).

The GUI tests are opt-in and **must** run from a terminal, because they need a display:

```bash
# Linux with a desktop session
mvn test -Dtulita.budgetUiTests=true -Dtest=BudgetOptimizerUiTest

# headless Linux (CI, container, WSL)
sudo apt install -y xvfb
xvfb-run -a -s '-screen 0 1600x1200x24' mvn test \
  -DargLine='-Xmx512m -Djava.awt.headless=false' \
  -Dtulita.budgetUiTests=true -Dtest=BudgetOptimizerUiTest
```

## 6. Optional: photo search (87 MB ONNX model)

Photo/camera matching needs a model that is **not** in the repo:

- Windows: `setup-photo-search.bat`, or
- manual: put `clip-vision-int8.onnx` in `~/.tulita/`, or
- from a fresh copy: set `TULITA_INCLUDE_MODEL=1` before running `setup-photo-search.bat`.

## 7. Optional: MySQL mode

`TULITA_DATA_SOURCE=mysql` needs a running MySQL 8 server. Copy `application.properties.example` to
`application.properties`, fill in your own credentials (never commit it), then run
`import-photo-catalog.bat` / `.sh` once. Without MySQL, stay on `TULITA_DATA_SOURCE=csv`.

## 8. Useful environment variables

| Variable | Purpose |
|---|---|
| `TULITA_MODE` | `catalog` (default, bundled data), `dataset`, `multistore`, or legacy demo UI |
| `TULITA_DATA_SOURCE` | `csv` (default) or `mysql` |
| `TULITA_BUDGET_STORE` | Where saved budget plans go (default `~/.tulita/budget-recommendations.json`) |

## 9. Exact commands, copy-paste

```bash
git clone <your-repo>  # or unzip Tulita-Complete-Source-INR-Budget-Optimizer.zip
cd tulita
mvn -q clean test                                        # 133 tests: 122 executed, 0 failures
TULITA_MODE=catalog TULITA_DATA_SOURCE=csv mvn javafx:run # start the app
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| *"JavaFX runtime components are missing"* | You ran `tulita.TulitaApp` directly. Use the task / `mvn javafx:run`, or run `tulita.Launcher` |
| *"mvn: command not found"* | Install Maven, or use the VS Code **Java Projects** dependency resolution and the F5 config |
| No categories in the planner | The catalogue folder wasn't found — keep `datasets/promap/` inside the project and run with `TULITA_DATA_SOURCE=csv` |
| Tests killed / OOM on a small machine | Run the GUI and MySQL test classes separately; they fork their own JVM |
| GTK / `libgtk-3-0` errors on Linux | `sudo apt install -y libgtk-3-0 libxtst6 libgl1` |
