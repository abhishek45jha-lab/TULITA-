package tulita.budget.service;

import tulita.budget.model.BudgetCategory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts only facts that appear in the original source text. Every extractor is optional:
 * a missing fact lowers confidence but is never replaced by a guessed specification.
 */
public final class SpecExtractor {
    private static final Pattern DPI = Pattern.compile("(\\d{1,5})\\s*dpi");
    private static final Pattern BUTTONS = Pattern.compile("(\\d{1,2})\\s*(?:buttons?|button\\b)");
    private static final Pattern HOURS = Pattern.compile("(\\d{1,3})\\s*(?:hours|hrs|h playback|hour playback)");
    private static final Pattern SCREEN = Pattern.compile("(\\d{2}(?:\\.\\d)?)\\s*(?:\"|''|inch|inches|in\\b)");

    private SpecExtractor() { }

    public static SpecProfile extract(BudgetCategory category, String title, String specifications, String description) {
        String text = SourceText.normalize(title + " " + safe(specifications) + " " + safe(description));
        SpecProfile.Builder b = SpecProfile.builder();
        b.renewed(text.matches(".*\\b(renewed|refurbished)\\b.*"));
        switch (category) {
            case LAPTOPS -> extractLaptop(text, b);
            case KEYBOARDS -> extractKeyboard(text, b);
            case MICE -> extractMouse(text, b);
            case HEADPHONES -> extractHeadphone(text, b);
        }
        return b.build();
    }

    private static void extractLaptop(String text, SpecProfile.Builder b) {
        String processor = processor(text);
        b.processor(processor);
        Integer ram = ramGb(text);
        b.ramGb(ram);
        String storageType = storageType(text);
        Integer storage = storageGb(text);
        b.storageType(storageType).storageGb(storage);
        Matcher screen = SCREEN.matcher(text);
        if (screen.find()) b.screenInches(Double.parseDouble(screen.group(1)));
        b.generation(generation(text));
        b.touch(text.contains("touch") || text.contains("touchscreen"));
        b.convertible(text.contains("2-in-1") || text.contains("2 in 1") || text.contains("convertible"));
        b.dedicatedGraphics(text.matches(".*(nvidia|geforce|gtx|rtx|radeon rx|2gb graph|4gb graph|6gb graph).*"));
        b.backlit(text.contains("backlit") || text.contains("backlight"));
    }

    private static String processor(String text) {
        Matcher intel = Pattern.compile("\\bi([3579])\\b|core i([3579])").matcher(text);
        if (intel.find()) {
            String n = intel.group(1) == null ? intel.group(2) : intel.group(1);
            return "Core i" + n;
        }
        Matcher ryzen = Pattern.compile("ryzen\\s*([3579])").matcher(text);
        if (ryzen.find()) return "Ryzen " + ryzen.group(1);
        if (text.matches(".*\\bceleron\\b.*")) return "Celeron";
        if (text.matches(".*\\bpentium\\b.*")) return "Pentium";
        if (text.matches(".*\\bathlon\\b.*")) return "Athlon";
        if (text.matches(".*\\bm1\\b.*")) return "Apple M1";
        if (text.matches(".*\\bm2\\b.*")) return "Apple M2";
        return null;
    }

    private static Integer ramGb(String text) {
        Matcher m = Pattern.compile("(\\d{1,2})\\s*gb\\s*(?:ddr\\d?|ram|lpddr\\d?|so-dimm)").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        m = Pattern.compile("(?:ram|memory)\\s*[:\\-]?\\s*(\\d{1,2})\\s*gb").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        m = Pattern.compile("(\\d{1,2})\\s*gb\\s*(?:ram|memory)").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        return null;
    }

    private static Integer storageGb(String text) {
        Matcher m = Pattern.compile("(\\d{3,4})\\s*gb\\s*(?:ssd|hdd|emmc|pcie|nvme|solid state)").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        m = Pattern.compile("(\\d(?:\\.\\d)?)\\s*tb").matcher(text);
        if (m.find()) return Math.round(Float.parseFloat(m.group(1)) * 1024);
        m = Pattern.compile("\\b(64|128|256|512)\\s*gb\\b").matcher(text);
        if (m.find() && (text.contains("ssd") || text.contains("hdd") || text.contains("emmc") || text.contains("storage"))) {
            return Integer.valueOf(m.group(1));
        }
        return null;
    }

    private static String storageType(String text) {
        if (text.contains("ssd") || text.contains("solid state") || text.contains("nvme") || text.contains("pcie")) return "SSD";
        if (text.contains("emmc")) return "eMMC";
        if (text.contains("hdd") || text.contains("hard disk")) return "HDD";
        return null;
    }

    private static Integer generation(String text) {
        Matcher m = Pattern.compile("(?<!\\w)(1[0-4]|[7-9])(?:th|st|nd|rd)?\\s*gen\\b").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        m = Pattern.compile("(?<!\\w)(1[0-4]|[7-9])\\s*gen\\b").matcher(text);
        if (m.find()) return Integer.valueOf(m.group(1));
        return null;
    }

    private static void extractKeyboard(String text, SpecProfile.Builder b) {
        b.mechanical(text.matches(".*\\b(mechanical|मैकेनिकल)\\b.*"));
        b.wireless(text.matches(".*(wireless|bluetooth|2\\.4g).*"));
        b.bluetooth(text.contains("bluetooth"));
        b.backlit(text.matches(".*(backlit|backlight|rgb).*"));
        b.gaming(text.contains("gaming"));
        b.combo(text.contains("keyboard") && text.contains("mouse") && text.matches(".*(combo|set|सेट).*"));
    }

    private static void extractMouse(String text, SpecProfile.Builder b) {
        b.wireless(text.matches(".*(wireless|bluetooth|2\\.4g).*"));
        b.bluetooth(text.contains("bluetooth"));
        b.gaming(text.contains("gaming"));
        b.dpi(maxInt(text, DPI));
        b.buttons(maxInt(text, BUTTONS));
        b.silent(text.matches(".*(silent|quiet).*"));
        b.rgb(text.matches(".*(rgb|led|backlit).*"));
    }

    private static void extractHeadphone(String text, SpecProfile.Builder b) {
        b.wireless(text.matches(".*(wireless|bluetooth|2\\.4g).*"));
        b.bluetooth(text.contains("bluetooth"));
        b.noiseCancelling(text.matches(".*(noise cancel|noise-cancel|\\banc\\b|noise isolat|शोर रद्द).*"));
        b.overEar(text.matches(".*(over-ear|over ear|on-ear|on ear).*"));
        b.inEar(text.matches(".*(in-ear|in ear).*"));
        b.neckband(text.contains("neckband"));
        b.earbuds(text.matches(".*(earbud|tws).*"));
        b.microphone(text.matches(".*(\\bmic\\b|microphone).*"));
        b.batteryHours(maxInt(text, HOURS));
        b.gaming(text.contains("gaming"));
    }

    private static Integer maxInt(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        Integer best = null;
        while (m.find()) {
            try {
                int value = Integer.parseInt(m.group(1));
                if (best == null || value > best) best = value;
            } catch (NumberFormatException ignored) {
                // A malformed token is not a fact; leave it unknown.
            }
        }
        return best;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
