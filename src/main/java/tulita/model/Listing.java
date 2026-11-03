package tulita.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public final class Listing {
    private final RawListing data;
    public Listing(RawListing data) {
        if (data.price() == null || data.price().signum() < 0 || data.mrp() == null
                || data.mrp().compareTo(data.price()) < 0 || data.rating() == null
                || data.rating().signum() < 0 || data.rating().compareTo(new BigDecimal("5")) > 0
                || (data.delivery() != null && data.delivery().signum() < 0)) {
            throw new IllegalArgumentException("Invalid sample price, MRP, delivery or rating for listing " + data.id());
        }
        this.data = data;
    }
    public long getId() { return data.id(); }
    public long getProductId() { return data.productId(); }
    public String getStore() { return data.store(); }
    public String getTitle() { return data.title(); }
    public String getModel() { return data.model(); }
    public String getVariant() { return data.variant(); }
    public BigDecimal getPrice() { return data.price(); }
    public BigDecimal getMrp() { return data.mrp(); }
    public BigDecimal getRating() { return data.rating(); }
    public BigDecimal getDelivery() { return data.delivery(); }
    public String getEstimate() { return data.estimate(); }
    public boolean isAvailable() { return data.available(); }
    public String getUrl() { return data.url(); }
    public BigDecimal getDiscount() {
        return getMrp().signum() == 0 ? BigDecimal.ZERO : getMrp().subtract(getPrice())
            .multiply(new BigDecimal("100")).divide(getMrp(), 0, RoundingMode.HALF_UP);
    }
    public Optional<BigDecimal> finalPrice() {
        return getDelivery() == null ? Optional.empty() : Optional.of(getPrice().add(getDelivery()));
    }
}
