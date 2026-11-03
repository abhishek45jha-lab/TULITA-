package tulita;

/**
 * Plain entry point for IDEs (VS Code, IntelliJ, Eclipse) and for running the packaged app.
 *
 * <p>Java refuses to start a class that extends {@code javafx.application.Application} when the JavaFX
 * modules are on the <em>class path</em> instead of the module path ("JavaFX runtime components are
 * missing"). Editors put Maven dependencies on the class path, so pressing Run/Debug on
 * {@link TulitaApp} directly would fail. This launcher does not extend {@code Application}, so the check
 * never triggers and the same wiring works everywhere.</p>
 *
 * <p>Build tools that manage the JavaFX module path themselves (the {@code javafx-maven-plugin} used by
 * {@code mvn javafx:run}, and {@code package-app.sh}) keep using {@link TulitaApp} — nothing changes for
 * them.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        TulitaApp.main(args);
    }
}
