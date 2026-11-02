package tulita.budget.service;

import tulita.budget.model.BudgetCategory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Facts the source text actually states. Absent facts stay absent; nothing is guessed. */
public final class SpecProfile {
    private final String processor;
    private final Integer ramGb;
    private final Integer storageGb;
    private final String storageType;
    private final Integer generation;
    private final Double screenInches;
    private final boolean touch;
    private final boolean convertible;
    private final boolean dedicatedGraphics;
    private final boolean backlit;
    private final boolean mechanical;
    private final boolean wireless;
    private final boolean bluetooth;
    private final boolean gaming;
    private final boolean noiseCancelling;
    private final boolean overEar;
    private final boolean inEar;
    private final boolean neckband;
    private final boolean earbuds;
    private final boolean microphone;
    private final Integer batteryHours;
    private final Integer dpi;
    private final Integer buttons;
    private final boolean silent;
    private final boolean rgb;
    private final boolean combo;
    private final boolean renewed;

    private SpecProfile(Builder b) {
        this.processor = b.processor;
        this.ramGb = b.ramGb;
        this.storageGb = b.storageGb;
        this.storageType = b.storageType;
        this.generation = b.generation;
        this.screenInches = b.screenInches;
        this.touch = b.touch;
        this.convertible = b.convertible;
        this.dedicatedGraphics = b.dedicatedGraphics;
        this.backlit = b.backlit;
        this.mechanical = b.mechanical;
        this.wireless = b.wireless;
        this.bluetooth = b.bluetooth;
        this.gaming = b.gaming;
        this.noiseCancelling = b.noiseCancelling;
        this.overEar = b.overEar;
        this.inEar = b.inEar;
        this.neckband = b.neckband;
        this.earbuds = b.earbuds;
        this.microphone = b.microphone;
        this.batteryHours = b.batteryHours;
        this.dpi = b.dpi;
        this.buttons = b.buttons;
        this.silent = b.silent;
        this.rgb = b.rgb;
        this.combo = b.combo;
        this.renewed = b.renewed;
    }

    public String processor() { return processor; }
    public Integer ramGb() { return ramGb; }
    public Integer storageGb() { return storageGb; }
    public String storageType() { return storageType; }
    public Integer generation() { return generation; }
    public Double screenInches() { return screenInches; }
    public boolean touch() { return touch; }
    public boolean convertible() { return convertible; }
    public boolean dedicatedGraphics() { return dedicatedGraphics; }
    public boolean backlit() { return backlit; }
    public boolean mechanical() { return mechanical; }
    public boolean wireless() { return wireless; }
    public boolean bluetooth() { return bluetooth; }
    public boolean gaming() { return gaming; }
    public boolean noiseCancelling() { return noiseCancelling; }
    public boolean overEar() { return overEar; }
    public boolean inEar() { return inEar; }
    public boolean neckband() { return neckband; }
    public boolean earbuds() { return earbuds; }
    public boolean microphone() { return microphone; }
    public Integer batteryHours() { return batteryHours; }
    public Integer dpi() { return dpi; }
    public Integer buttons() { return buttons; }
    public boolean silent() { return silent; }
    public boolean rgb() { return rgb; }
    public boolean combo() { return combo; }
    public boolean renewed() { return renewed; }

    /** Human-readable facts for the result card. Only populated keys are shown. */
    public Map<String, String> display(BudgetCategory category) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        switch (category) {
            case LAPTOPS -> {
                put(out, "Processor", processor);
                put(out, "RAM", ramGb == null ? null : ramGb + " GB");
                if (storageGb != null) put(out, "Storage", storageGb + " GB " + (storageType == null ? "" : storageType));
                put(out, "Display", screenInches == null ? null : trim(screenInches) + "\"");
                put(out, "Generation", generation == null ? null : generation + "th gen");
                if (touch || convertible) put(out, "Display type", (touch ? "Touch" : "") + (touch && convertible ? " · " : "") + (convertible ? "2-in-1" : ""));
                if (dedicatedGraphics) put(out, "Graphics", "Dedicated GPU stated");
                if (backlit) put(out, "Keyboard", "Backlit");
            }
            case KEYBOARDS -> {
                put(out, "Type", mechanical ? "Mechanical" : "Membrane / not stated");
                put(out, "Connectivity", wireless ? (bluetooth ? "Wireless · Bluetooth" : "Wireless") : "Wired");
                if (backlit) put(out, "Backlight", "Yes");
                if (gaming) put(out, "Use", "Gaming");
                if (combo) put(out, "Bundle", "Keyboard + mouse combo");
            }
            case MICE -> {
                put(out, "Connectivity", wireless ? (bluetooth ? "Wireless · Bluetooth" : "Wireless") : "Wired");
                put(out, "Sensor", dpi == null ? null : dpi + " DPI");
                put(out, "Buttons", buttons == null ? null : buttons + " buttons");
                if (silent) put(out, "Clicks", "Silent");
                if (rgb) put(out, "Lighting", "RGB / LED");
                if (gaming) put(out, "Use", "Gaming");
            }
            case HEADPHONES -> {
                String type = overEar ? "Over-ear / on-ear" : neckband ? "Neckband" : earbuds ? "Earbuds" : inEar ? "In-ear" : null;
                put(out, "Type", type);
                put(out, "Connectivity", wireless ? (bluetooth ? "Wireless · Bluetooth" : "Wireless") : "Wired");
                if (noiseCancelling) put(out, "Noise", "Cancellation / isolation stated");
                if (microphone) put(out, "Microphone", "Yes");
                put(out, "Battery", batteryHours == null ? null : batteryHours + " hours stated");
                if (gaming) put(out, "Use", "Gaming");
            }
        }
        if (renewed) put(out, "Condition", "Renewed / refurbished stated in source");
        return Map.copyOf(out);
    }

    private static void put(Map<String, String> out, String key, String value) {
        if (value != null && !value.isBlank()) out.put(key, value);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String processor;
        private Integer ramGb;
        private Integer storageGb;
        private String storageType;
        private Integer generation;
        private Double screenInches;
        private boolean touch;
        private boolean convertible;
        private boolean dedicatedGraphics;
        private boolean backlit;
        private boolean mechanical;
        private boolean wireless;
        private boolean bluetooth;
        private boolean gaming;
        private boolean noiseCancelling;
        private boolean overEar;
        private boolean inEar;
        private boolean neckband;
        private boolean earbuds;
        private boolean microphone;
        private Integer batteryHours;
        private Integer dpi;
        private Integer buttons;
        private boolean silent;
        private boolean rgb;
        private boolean combo;
        private boolean renewed;

        public Builder processor(String v) { processor = v; return this; }
        public Builder ramGb(Integer v) { ramGb = v; return this; }
        public Builder storageGb(Integer v) { storageGb = v; return this; }
        public Builder storageType(String v) { storageType = v; return this; }
        public Builder generation(Integer v) { generation = v; return this; }
        public Builder screenInches(Double v) { screenInches = v; return this; }
        public Builder touch(boolean v) { touch = v; return this; }
        public Builder convertible(boolean v) { convertible = v; return this; }
        public Builder dedicatedGraphics(boolean v) { dedicatedGraphics = v; return this; }
        public Builder backlit(boolean v) { backlit = v; return this; }
        public Builder mechanical(boolean v) { mechanical = v; return this; }
        public Builder wireless(boolean v) { wireless = v; return this; }
        public Builder bluetooth(boolean v) { bluetooth = v; return this; }
        public Builder gaming(boolean v) { gaming = v; return this; }
        public Builder noiseCancelling(boolean v) { noiseCancelling = v; return this; }
        public Builder overEar(boolean v) { overEar = v; return this; }
        public Builder inEar(boolean v) { inEar = v; return this; }
        public Builder neckband(boolean v) { neckband = v; return this; }
        public Builder earbuds(boolean v) { earbuds = v; return this; }
        public Builder microphone(boolean v) { microphone = v; return this; }
        public Builder batteryHours(Integer v) { batteryHours = v; return this; }
        public Builder dpi(Integer v) { dpi = v; return this; }
        public Builder buttons(Integer v) { buttons = v; return this; }
        public Builder silent(boolean v) { silent = v; return this; }
        public Builder rgb(boolean v) { rgb = v; return this; }
        public Builder combo(boolean v) { combo = v; return this; }
        public Builder renewed(boolean v) { renewed = v; return this; }

        public SpecProfile build() {
            return new SpecProfile(this);
        }
    }
}
