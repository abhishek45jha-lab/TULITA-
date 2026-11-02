package tulita.budget.model;

import java.util.Objects;

/** One transparent reason behind a quality score. Points are only awarded from recorded source text. */
public record ScoreFactor(String label, int points, String evidence) {
    public ScoreFactor {
        Objects.requireNonNull(label, "label");
        evidence = evidence == null ? "" : evidence;
    }

    public String describe() {
        if (evidence.isBlank()) return points == 0 ? label : label + " (+" + points + ")";
        return (points == 0 ? label : label + " (+" + points + ")") + " — " + evidence;
    }
}
