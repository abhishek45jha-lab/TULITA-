package tulita.budget;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the budget tests without Maven/Surefire (the sandbox toolchain has no Maven distribution).
 * It invokes every {@code @Test} method reflectively and reports a non-zero exit on failure.
 * A normal Maven build discovers the same methods through JUnit 5 Surefire.
 */
public final class BudgetTestMain {
    public static void main(String[] args) throws Exception {
        List<Class<?>> classes = List.of(BudgetOptimizerTest.class, BudgetClassifierTest.class, BudgetCsvExporterTest.class, BudgetDataLoaderTest.class);
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> type : classes) {
            Object instance = type.getDeclaredConstructor().newInstance();
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Test.class)) continue;
                String name = type.getSimpleName() + "." + method.getName();
                try {
                    method.setAccessible(true);
                    method.invoke(instance);
                    passed++;
                    System.out.println("PASS " + name);
                } catch (Throwable failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    failures.add(name + " -> " + cause);
                    System.out.println("FAIL " + name + " -> " + cause);
                    cause.printStackTrace(System.out);
                }
            }
        }
        System.out.println("\nBudget tests: " + passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) throw new AssertionError("Budget test failures: " + failures);
    }
}
