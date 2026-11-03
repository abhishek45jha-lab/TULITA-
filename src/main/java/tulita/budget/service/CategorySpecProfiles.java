package tulita.budget.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Category-aware specification profiles.
 *
 * <p>The catalogue stores specifications as the retailer's own key/value pairs, and the keys differ
 * between categories (a blender publishes Watts and Capacity, a laptop publishes Processor Speed and
 * Hard Drive Capacity). This class maps those real keys onto a small numeric metric set per category
 * so that products inside one category can be compared on the same scale.</p>
 *
 * <p>Only keys that are actually present are read. No metric is ever imputed: a product that
 * publishes no matching key simply has no specification data, which the scoring service reports
 * instead of rewarding.</p>
 */
public final class CategorySpecProfiles {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private CategorySpecProfiles() {
    }

    /** One comparable metric: which retailer keys feed it, in which unit, and which direction wins. */
    public record SpecMetric(String label, String unit, boolean higherIsBetter, List<String> keys,
                             Map<String, Double> unitFactors) {

        public SpecMetric {
            keys = List.copyOf(keys);
            unitFactors = Map.copyOf(unitFactors);
        }

        /** Reads the first number in the value and converts it to the metric's base unit. */
        public OptionalDouble read(String value) {
            if (value == null) {
                return OptionalDouble.empty();
            }
            Matcher matcher = NUMBER.matcher(value);
            if (!matcher.find()) {
                return OptionalDouble.empty();
            }
            double number;
            try {
                number = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
            String lower = value.toLowerCase(Locale.ROOT);
            double factor = 1.0;
            String matchedUnit = "";
            for (Map.Entry<String, Double> unit : unitFactors.entrySet()) {
                if (lower.contains(unit.getKey()) && unit.getKey().length() > matchedUnit.length()) {
                    matchedUnit = unit.getKey();
                    factor = unit.getValue();
                }
            }
            return OptionalDouble.of(number * factor);
        }

        public boolean matches(String key) {
            String lower = key.toLowerCase(Locale.ROOT);
            for (String candidate : keys) {
                if (lower.equals(candidate) || lower.contains(candidate)) {
                    return true;
                }
            }
            return false;
        }

        public String displayLabel() {
            return unit.isBlank() ? label : label + " (" + unit + ")";
        }
    }

    public record Profile(String category, List<SpecMetric> metrics) {

        public Profile {
            metrics = List.copyOf(metrics);
        }

        public List<String> labels() {
            List<String> out = new ArrayList<>();
            for (SpecMetric metric : metrics) {
                out.add(metric.displayLabel());
            }
            return out;
        }

        public SpecMetric byLabel(String label) {
            for (SpecMetric metric : metrics) {
                if (metric.displayLabel().equals(label) || metric.label().equals(label)) {
                    return metric;
                }
            }
            return null;
        }

        /** Metric label -> value, for the keys that were really present in this product's specs. */
        public Map<String, Double> extract(String specificationsJson) {
            Map<String, Double> out = new LinkedHashMap<>();
            if (specificationsJson == null || specificationsJson.isBlank()) {
                return out;
            }
            JsonNode root;
            try {
                root = MAPPER.readTree(specificationsJson);
            } catch (Exception ignored) {
                return out;
            }
            if (!root.isArray()) {
                return out;
            }
            for (JsonNode node : root) {
                String key = node.path("key").asText("");
                String value = node.path("value").asText("");
                if (key.isBlank() || value.isBlank()) {
                    continue;
                }
                for (SpecMetric metric : metrics) {
                    if (metric.matches(key) && !out.containsKey(metric.displayLabel())) {
                        metric.read(value).ifPresent(v -> out.put(metric.displayLabel(), v));
                    }
                }
            }
            return out;
        }
    }

    private static SpecMetric storage() {
        return new SpecMetric("Storage", "GB", true,
            List.of("hard drive capacity", "storage capacity", "hdd capacity", "memory capacity", "storage"),
            Map.of("tb", 1024.0, "gb", 1.0, "mb", 0.001));
    }

    private static SpecMetric memory() {
        return new SpecMetric("Memory", "GB", true,
            List.of("ram size", "installed ram", "system memory", "memory capacity", "ram"),
            Map.of("tb", 1024.0, "gb", 1.0, "mb", 0.001));
    }

    private static SpecMetric processor() {
        return new SpecMetric("Processor speed", "GHz", true,
            List.of("processor speed", "cpu speed", "clock speed"),
            Map.of("ghz", 1.0, "mhz", 0.001));
    }

    private static SpecMetric screen() {
        return new SpecMetric("Screen size", "in", true,
            List.of("screen size", "display size", "screen"),
            Map.of("inch", 1.0, "in", 1.0, "\"", 1.0));
    }

    private static SpecMetric capacity() {
        return new SpecMetric("Capacity", "oz", true,
            List.of("capacity", "volume", "jar capacity"),
            Map.of("fl oz", 1.0, "oz", 1.0, "qt", 32.0, "gal", 128.0, "l", 33.814, "ml", 0.033814));
    }

    private static SpecMetric power() {
        return new SpecMetric("Power", "W", true,
            List.of("watts", "wattage", "power"),
            Map.of("kw", 1000.0, "w", 1.0));
    }

    private static SpecMetric speeds() {
        return new SpecMetric("Speeds", "levels", true,
            List.of("number of speeds", "speeds", "speed settings"),
            Map.of());
    }

    private static SpecMetric pieces() {
        return new SpecMetric("Pieces", "count", true,
            List.of("number of pieces", "piece count", "count", "pack count"),
            Map.of());
    }

    private static SpecMetric weight() {
        return new SpecMetric("Weight", "lb", true,
            List.of("item weight", "assembled product weight", "package weight", "item package weight", "weight"),
            Map.of("lb", 1.0, "pound", 1.0, "kg", 2.20462, "g", 0.00220462, "oz", 0.0625));
    }

    private static SpecMetric warranty() {
        return new SpecMetric("Warranty", "months", true,
            List.of("warranty"),
            Map.of("year", 12.0, "yr", 12.0, "month", 1.0, "day", 1.0 / 30.0));
    }

    /** The metrics that make sense for a category, drawn from what this catalogue really records. */
    public static Profile forCategory(String category) {
        String name = category == null ? "" : category.toLowerCase(Locale.ROOT);
        List<SpecMetric> metrics = new ArrayList<>();
        if (name.contains("computer") || name.contains("laptop")) {
            metrics.addAll(List.of(storage(), memory(), processor(), screen(), weight(), warranty()));
        } else if (name.contains("electronic") || name.contains("phone")) {
            metrics.addAll(List.of(memory(), storage(), screen(), weight(), warranty()));
        } else if (name.contains("kitchen") || name.contains("appliance")) {
            metrics.addAll(List.of(capacity(), power(), speeds(), weight(), warranty()));
        } else if (name.contains("health")) {
            metrics.addAll(List.of(pieces(), capacity(), weight(), warranty()));
        } else if (name.contains("household") || name.contains("craft")) {
            metrics.addAll(List.of(pieces(), capacity(), weight(), warranty()));
        } else if (name.contains("toy") || name.contains("game")) {
            metrics.addAll(List.of(pieces(), weight(), warranty()));
        } else if (name.contains("pet")) {
            metrics.addAll(List.of(weight(), capacity(), pieces(), warranty()));
        } else if (name.contains("bag") || name.contains("travel") || name.contains("cloth")
            || name.contains("gear")) {
            metrics.addAll(List.of(capacity(), weight(), pieces(), warranty()));
        } else if (name.contains("garden") || name.contains("outdoor")) {
            metrics.addAll(List.of(power(), weight(), capacity(), warranty()));
        } else {
            metrics.addAll(List.of(pieces(), capacity(), power(), weight(), warranty()));
        }
        return new Profile(category, metrics);
    }
}
