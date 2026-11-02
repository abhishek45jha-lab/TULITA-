package tulita.budget.service;

import tulita.budget.model.BudgetProduct;
import tulita.budget.model.ScoreFactor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic quality score (0–100) built only from recorded specifications and the supplied
 * source rating. The score is a planning aid, not a claim of measured performance.
 */
public final class ProductScorer {
    public record Result(int quality, List<ScoreFactor> factors) {
        public Result {
            factors = List.copyOf(factors);
        }
    }

    private ProductScorer() { }

    public static Result quality(BudgetProduct product, SpecProfile spec) {
        List<ScoreFactor> factors = new ArrayList<>();
        int points = switch (product.category()) {
            case LAPTOPS -> laptop(spec, factors, 30);
            case KEYBOARDS -> keyboard(spec, factors, 40);
            case MICE -> mouse(spec, factors, 40);
            case HEADPHONES -> headphone(spec, factors, 38);
        };
        if (product.rating() != null) {
            int bonus = product.rating().multiply(BigDecimal.valueOf(1.6))
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            bonus = Math.max(0, Math.min(8, bonus));
            factors.add(new ScoreFactor("Source rating", bonus, product.rating().stripTrailingZeros().toPlainString() + "/5"));
            points += bonus;
        } else {
            factors.add(new ScoreFactor("Source rating", 0, "Not supplied — no rating was invented"));
        }
        if (spec.renewed()) {
            factors.add(new ScoreFactor("Condition", 0, "Renewed/refurbished is stated in the source title"));
        }
        return new Result(Math.max(0, Math.min(100, points)), factors);
    }

    /** Value-for-money percentile within the category, computed from quality per recorded rupee. */
    public static int valueScore(int quality, BigDecimal price, BigDecimal bestEfficiency) {
        if (price == null || price.signum() <= 0 || bestEfficiency == null || bestEfficiency.signum() <= 0) return 0;
        BigDecimal efficiency = BigDecimal.valueOf(quality).divide(price, 12, RoundingMode.HALF_UP);
        int score = efficiency.multiply(BigDecimal.valueOf(100))
                .divide(bestEfficiency, 0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, score));
    }

    public static BigDecimal efficiency(int quality, BigDecimal price) {
        if (price == null || price.signum() <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(quality).divide(price, 12, RoundingMode.HALF_UP);
    }

    private static int laptop(SpecProfile s, List<ScoreFactor> f, int base) {
        int p = base;
        if (s.ramGb() != null) {
            int v = s.ramGb() >= 24 ? 22 : s.ramGb() >= 12 ? 18 : s.ramGb() >= 8 ? 12 : 6;
            p += v;
            f.add(new ScoreFactor("RAM", v, s.ramGb() + " GB stated"));
        }
        if (s.storageGb() != null) {
            int v = s.storageGb() >= 2048 ? 18 : s.storageGb() >= 1024 ? 16 : s.storageGb() >= 512 ? 13 : s.storageGb() >= 256 ? 9 : 5;
            if ("SSD".equals(s.storageType())) v += 2;
            p += v;
            f.add(new ScoreFactor("Storage", v, s.storageGb() + " GB " + (s.storageType() == null ? "" : s.storageType() + " " ) + "stated"));
        } else if (s.storageType() != null) {
            p += 3;
            f.add(new ScoreFactor("Storage type", 3, s.storageType() + " stated; capacity not stated"));
        }
        if (s.processor() != null) {
            int v = switch (s.processor()) {
                case "Core i9", "Ryzen 9" -> 24;
                case "Core i7", "Ryzen 7" -> 22;
                case "Core i5", "Ryzen 5" -> 17;
                case "Core i3", "Ryzen 3" -> 11;
                case "Apple M1", "Apple M2" -> 20;
                default -> 6;
            };
            p += v;
            f.add(new ScoreFactor("Processor", v, s.processor() + " stated"));
        }
        if (s.generation() != null) {
            int v = s.generation() >= 12 ? 6 : s.generation() >= 11 ? 5 : s.generation() >= 10 ? 4 : s.generation() >= 8 ? 2 : 1;
            p += v;
            f.add(new ScoreFactor("Generation", v, s.generation() + "th gen stated"));
        }
        if (s.touch()) { p += 3; f.add(new ScoreFactor("Touchscreen", 3, "stated in source")); }
        if (s.convertible()) { p += 2; f.add(new ScoreFactor("2-in-1", 2, "convertible form stated")); }
        if (s.dedicatedGraphics()) { p += 4; f.add(new ScoreFactor("Dedicated graphics", 4, "NVIDIA/GTX/RTX/Radeon or dedicated VRAM stated")); }
        if (s.backlit()) { p += 2; f.add(new ScoreFactor("Backlit keyboard", 2, "stated in source")); }
        return p;
    }

    private static int keyboard(SpecProfile s, List<ScoreFactor> f, int base) {
        int p = base;
        if (s.mechanical()) { p += 10; f.add(new ScoreFactor("Mechanical switches", 10, "stated in source")); }
        if (s.wireless()) {
            p += 8;
            f.add(new ScoreFactor("Wireless", 8, s.bluetooth() ? "Bluetooth stated" : "Wireless stated"));
        } else {
            p += 3;
            f.add(new ScoreFactor("Wired", 3, "USB/wired stated"));
        }
        if (s.backlit()) { p += 5; f.add(new ScoreFactor("Backlight", 5, "backlit/RGB stated")); }
        if (s.gaming()) { p += 5; f.add(new ScoreFactor("Gaming", 5, "gaming keyboard stated")); }
        if (s.combo()) { p += 4; f.add(new ScoreFactor("Keyboard + mouse bundle", 4, "combo/set stated")); }
        return p;
    }

    private static int mouse(SpecProfile s, List<ScoreFactor> f, int base) {
        int p = base;
        if (s.wireless()) {
            p += 8;
            f.add(new ScoreFactor("Wireless", 8, s.bluetooth() ? "Bluetooth stated" : "Wireless stated"));
        } else {
            p += 3;
            f.add(new ScoreFactor("Wired", 3, "USB/wired stated"));
        }
        if (s.gaming()) { p += 6; f.add(new ScoreFactor("Gaming", 6, "gaming mouse stated")); }
        if (s.dpi() != null) {
            int v = s.dpi() >= 16_000 ? 8 : s.dpi() >= 8_000 ? 6 : s.dpi() >= 3_200 ? 4 : s.dpi() >= 1_600 ? 2 : 1;
            p += v;
            f.add(new ScoreFactor("Sensor resolution", v, s.dpi() + " DPI stated"));
        }
        if (s.buttons() != null && s.buttons() >= 6) {
            p += 3;
            f.add(new ScoreFactor("Buttons", 3, s.buttons() + " buttons stated"));
        }
        if (s.silent()) { p += 3; f.add(new ScoreFactor("Silent clicks", 3, "stated in source")); }
        if (s.rgb()) { p += 3; f.add(new ScoreFactor("RGB / lighting", 3, "stated in source")); }
        return p;
    }

    private static int headphone(SpecProfile s, List<ScoreFactor> f, int base) {
        int p = base;
        if (s.wireless()) {
            p += 8;
            f.add(new ScoreFactor("Wireless", 8, s.bluetooth() ? "Bluetooth stated" : "Wireless stated"));
        } else {
            p += 2;
            f.add(new ScoreFactor("Wired", 2, "wired connection stated"));
        }
        if (s.noiseCancelling()) { p += 10; f.add(new ScoreFactor("Noise cancellation / isolation", 10, "stated in source")); }
        if (s.overEar()) {
            p += 5;
            f.add(new ScoreFactor("Over-ear / on-ear", 5, "form factor stated"));
        } else if (s.neckband()) {
            p += 3;
            f.add(new ScoreFactor("Neckband", 3, "form factor stated"));
        } else if (s.earbuds() || s.inEar()) {
            p += 3;
            f.add(new ScoreFactor("In-ear / earbuds", 3, "form factor stated"));
        }
        if (s.microphone()) { p += 3; f.add(new ScoreFactor("Microphone", 3, "mic stated")); }
        if (s.batteryHours() != null) {
            int v = s.batteryHours() >= 30 ? 5 : s.batteryHours() >= 15 ? 3 : 1;
            p += v;
            f.add(new ScoreFactor("Battery", v, s.batteryHours() + " hours stated"));
        }
        if (s.gaming()) { p += 3; f.add(new ScoreFactor("Gaming", 3, "gaming headset stated")); }
        return p;
    }
}
