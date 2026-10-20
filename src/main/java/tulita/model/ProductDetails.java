package tulita.model;

import java.util.Objects;

/** Immutable, encapsulated canonical product. Variant is mandatory for safe grouping. */
public final class ProductDetails {
    private final long id;
    private final String name, brand, model, category, variant, art;
    public ProductDetails(long id, String name, String brand, String model, String category, String variant, String art) {
        this.id = id;
        this.name = Objects.requireNonNull(name);
        this.brand = Objects.requireNonNull(brand);
        this.model = Objects.requireNonNull(model);
        this.category = Objects.requireNonNull(category);
        this.variant = Objects.requireNonNull(variant);
        this.art = Objects.requireNonNull(art);
    }
    public long getId() { return id; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getCategory() { return category; }
    public String getVariant() { return variant; }
    public String getArt() { return art; }
}
