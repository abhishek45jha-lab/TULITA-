package tulita.budget.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import tulita.budget.model.BudgetRecommendation;
import tulita.budget.model.BudgetRequest;
import tulita.budget.model.OptimizationResult;
import tulita.budget.model.SelectedProduct;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the recommendation that is on screen to a CSV file.
 *
 * <p>Uses Apache Commons CSV, which the project already depends on for reading the source datasets, so no
 * new library is introduced. The export contains the same numbers the screen shows, because both are
 * produced by {@link BudgetCalculationService}.</p>
 *
 * <p>A printable PDF was not added: the project has no PDF library and adding one only for the export
 * would have meant a new dependency for a secondary feature.</p>
 */
public final class BudgetExportService {

    public Path export(OptimizationResult result, BudgetRequest request, Path target) throws IOException {
        BudgetRecommendation recommendation = result.primary();
        if (recommendation == null) {
            throw new IllegalStateException("There is no recommendation to export.");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(target);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            csv.printRecord("TULITA Budget Optimizer report");
            csv.printRecord("Generated", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            csv.printRecord("Recommendation mode", request.mode().label());
            csv.printRecord("Objective", recommendation.headline());
            csv.printRecord("Currency", request.currency());
            csv.printRecord("Preference weights", request.weights().describe());
            csv.printRecord("Search", result.algorithmNote());
            csv.printRecord();
            csv.printRecord("CATEGORY", "PRODUCT", "BRAND", "MODEL", "STORE", "QUANTITY", "UNIT PRICE",
                "LINE TOTAL", "SCORE (0-100)", "PRICE OBSERVED AT", "PRODUCT URL", "WHY IT WAS SELECTED",
                "TRADE-OFF");
            for (SelectedProduct line : recommendation.selected()) {
                csv.printRecord(line.candidate().category(), line.candidate().name(), line.candidate().brand(),
                    line.candidate().model(), line.candidate().store(), line.quantity(),
                    BudgetMoney.scale(line.candidate().unitPrice()).toPlainString(),
                    BudgetMoney.scale(line.lineTotal()).toPlainString(), line.scoreText(),
                    blankIfEmpty(line.candidate().observedAt()), blankIfEmpty(line.candidate().url()),
                    line.explanation(), line.tradeOff());
            }
            csv.printRecord();
            csv.printRecord("BUDGET SUMMARY");
            csv.printRecord("Total budget", BudgetMoney.format(recommendation.breakdown().budget(), request.currency()));
            csv.printRecord("Items subtotal",
                BudgetMoney.format(recommendation.breakdown().itemsSubtotal(), request.currency()));
            csv.printRecord("Delivery charges (your estimate)",
                BudgetMoney.format(recommendation.breakdown().deliveryCharges(), request.currency()));
            csv.printRecord("Discount (your estimate)",
                BudgetMoney.format(recommendation.breakdown().discount(), request.currency()));
            csv.printRecord("Estimated total",
                BudgetMoney.format(recommendation.breakdown().total(), request.currency()));
            csv.printRecord("Remaining budget",
                BudgetMoney.format(recommendation.breakdown().remaining(), request.currency()));
            csv.printRecord("Budget utilisation",
                BudgetMoney.percent(recommendation.breakdown().utilizationPercent()));
            csv.printRecord("Categories satisfied", String.join(" | ", recommendation.satisfiedCategories()));
            csv.printRecord();
            csv.printRecord("SPENDING BY CATEGORY");
            recommendation.breakdown().spendByCategory().forEach((category, amount) ->
                wrap(() -> csv.printRecord(category, BudgetMoney.format(amount, request.currency()))));
            csv.printRecord();
            csv.printRecord("ALTERNATIVES");
            for (var alternative : result.alternatives()) {
                csv.printRecord(alternative.label(),
                    BudgetMoney.format(alternative.recommendation().breakdown().total(), request.currency()),
                    BudgetMoney.format(alternative.recommendation().breakdown().remaining(), request.currency()),
                    String.join(" | ", alternative.differences()));
            }
            csv.printRecord();
            csv.printRecord("LIMITATIONS AND DATA NOTES");
            List<String> notes = new ArrayList<>(recommendation.limitations());
            notes.addAll(recommendation.breakdown().notes());
            for (String note : notes) {
                csv.printRecord(note);
            }
        }
        return target;
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private void wrap(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private String blankIfEmpty(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
