package tulita.dataset.model;
import java.util.List;
public record DatasetSnapshot(List<DatasetProduct> products, String sourceTitle, String sourceUrl,
        String license, String sha256, String firstObserved, String lastObserved, String storage) {
    public DatasetSnapshot { products=List.copyOf(products); }
    public static final String SOURCE_KEY="promptcloud_flipkart_20k";
    public static final String TITLE="Flipkart Products — PromptCloud";
    public static final String URL="https://www.kaggle.com/datasets/PromptCloudHQ/flipkart-products";
    public static final String LICENSE="CC BY-SA 4.0";
}
