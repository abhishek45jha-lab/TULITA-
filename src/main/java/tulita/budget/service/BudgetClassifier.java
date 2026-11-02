package tulita.budget.service;

import tulita.budget.model.BudgetCategory;
import tulita.dataset.model.DatasetProduct;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Strict, evidence-based classifier for the four planning categories.
 * It uses the source title/category only (not descriptions, which often list compatibility),
 * and rejects accessories before accepting a product. Each record belongs to at most one category.
 */
public final class BudgetClassifier {
    private static final Pattern LAPTOP_WORD = Pattern.compile("laptop|notebook");
    private static final Pattern PROCESSOR = Pattern.compile("(i3|i5|i7|i9|celeron|pentium|athlon|ryzen|core m|m1|m2)");
    private static final Pattern RAM = Pattern.compile("\\b(2|4|6|8|12|16|24|32|64)\\s?gb\\b");
    private static final Pattern STORAGE = Pattern.compile("\\b(64|128|256|512)\\s?gb|\\b(1|2)\\s?tb\\b");

    private static final List<String> LAPTOP_ACCESSORIES = List.of(
            "laptop bag", "laptop backpack", "laptop sleeve", "laptop battery", "laptop charger",
            "laptop adapter", "laptop stand", "laptop table", "laptop cooler", "cooling pad",
            "laptop keyboard", "laptop mouse", "laptop ram", "laptop memory", "laptop dock",
            "laptop lock", "laptop cleaner", "laptop cleaning", "laptop webcam", "laptop hub",
            "laptop cable", "laptop screen", "laptop display", "laptop lcd", "laptop touchpad",
            "laptop hinge", "laptop bezel", "laptop palmrest", "laptop fan", "laptop skin",
            "laptop cover", "laptop case", "replacement battery", "spare battery", "screen guard",
            "keyboard protector", "notebook stand", "notebook sleeve", "notebook battery",
            "notebook charger");

    private static final List<String> KEYBOARD_ACCESSORIES = List.of(
            "piano", "musical", "midi", "synthesizer", "casio", "yamaha", "61-key", "61 key",
            "88-key", "88 key", "tone", "cover", "case", "skin", "cleaner", "cleaning", "stand",
            "holder", "tray", "desk", "lamp", "led light", "usb light", "book light", "sticker", "keycap", "key cap", "adapter",
            "cable", "otg", "bag", "protector", "guard", "mount", "drawer", "replacement",
            "compatible", "for hp", "for lenovo", "for dell", "for acer", "for asus",
            "laptop keyboard", "tablet", "printer", "toner", "mouse pad", "mouse",
            "combo", "cleaner", "cleaning", "spray");

    private static final List<String> MOUSE_ACCESSORIES = List.of(
            "mouse pad", "mousepad", "mouse-pad", "mickey", "minnie", "pencil", "keychain",
            "key chain", "case", "cover", "sticker", "toy", "table", "desk", "bag", "bungee",
            "skate", "feet", "grip", "grip tape", "tape", "skin", "not included", "printer",
            "toner", "monitor", "showpiece", "painting", "combo", "combo pack", "cable",
            "charging", "webcam", "microphone");

    private static final List<String> HEADPHONE_ACCESSORIES = List.of(
            "case", "cover", "cushion", "ear pad", "earpad", "organizer", "cable", "stand",
            "holder", "hook", "splitter", "adapter", "replacement", "pouch", "skin", "comb",
            "headband", "cleaner", "cleaning", "sanitizer", "disinfectant", "tripod", "helmet",
            "intercom", "interphone", "hearing aid", "megaphone", "speaker", "combo pack",
            "combo", "mouse", "webcam", "microphone replacement", "driver", "earphone cable");

    private BudgetClassifier() { }

    public static Optional<BudgetCategory> classify(DatasetProduct product) {
        if (product == null || product.price() == null || product.price().signum() <= 0) return Optional.empty();
        String title = SourceText.normalize(product.title());
        String category = SourceText.normalize(product.category() + " " + product.categoryPath());
        String both = title + " " + category;
        double price = product.price().doubleValue();

        if (isLaptop(title, category, both, price)) return Optional.of(BudgetCategory.LAPTOPS);
        // A bundled "keyboard + mouse" / "headset + mouse" row is not a single planning product.
        boolean keyboard = title.contains("keyboard");
        boolean mouse = title.contains("mouse");
        boolean headphone = title.contains("headphone") || title.contains("headset")
                || title.contains("earphone") || title.contains("earbud");
        if ((keyboard && mouse) || (keyboard && headphone) || (mouse && headphone)) return Optional.empty();
        if (isKeyboard(title, category, price)) return Optional.of(BudgetCategory.KEYBOARDS);
        if (isMouse(title, category, price)) return Optional.of(BudgetCategory.MICE);
        if (isHeadphone(title, price)) return Optional.of(BudgetCategory.HEADPHONES);
        return Optional.empty();
    }

    private static boolean isLaptop(String title, String category, String both, double price) {
        if (price < 8000) return false;
        if (category.contains("stationery") || category.contains("pen ")) return false;
        if (containsAny(title, LAPTOP_ACCESSORIES)) return false;
        boolean fullSpecs = PROCESSOR.matcher(title).find() && RAM.matcher(title).find() && STORAGE.matcher(title).find();
        boolean laptopWord = LAPTOP_WORD.matcher(both).find();
        boolean laptopCategory = category.contains("laptop") || category.contains("notebook");
        boolean computerCategory = category.contains("computer") || category.contains("electronic");
        // A full processor + RAM + storage set is strong evidence even when a marketplace files the row
        // under a broad "Computers" category and the model name does not repeat "laptop".
        if (!laptopWord && !laptopCategory && !(fullSpecs && computerCategory)) return false;
        // A laptop-category row still must not be an accessory masked by category noise.
        return !containsAny(title, List.of("backpack", "bag", "sleeve", "battery", "charger", "adapter",
                "stand", "table", "cooler", "dock", "lamp", "lock", "clean", "hub", "cable",
                "guard", "protector", "pen drive"));
    }

    private static boolean isKeyboard(String title, String category, double price) {
        if (price < 250 || price > 100_000) return false;
        if (!title.contains("keyboard")) return false;
        if (containsAny(title, KEYBOARD_ACCESSORIES)) return false;
        boolean usable = title.matches(".*(mechanical|wireless|wired|usb|bluetooth|gaming|backlit|key|membrane|multi-device|slim).*");
        return usable && !category.contains("musical");
    }

    private static boolean isMouse(String title, String category, double price) {
        if (price < 100 || price > 50_000) return false;
        if (!title.contains("mouse")) return false;
        if (containsAny(title, MOUSE_ACCESSORIES)) return false;
        boolean electronic = category.contains("computer") || category.contains("gaming")
                || category.contains("electronic") || category.contains("accessor")
                || category.contains("office") || category.contains("renewed")
                || category.contains("mobile") || category.contains("audio");
        return electronic;
    }

    private static boolean isHeadphone(String title, double price) {
        if (price < 150 || price > 500_000) return false;
        boolean named = title.contains("headphone") || title.contains("headset")
                || title.contains("earphone") || title.contains("earbud");
        if (!named) return false;
        return !containsAny(title, HEADPHONE_ACCESSORIES);
    }

    private static boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    /** A readable label for tests and diagnostics. */
    public static String describe(DatasetProduct product) {
        return product.title().length() > 90 ? product.title().substring(0, 90) : product.title();
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
