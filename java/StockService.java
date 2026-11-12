import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stock / inventory rules: quantity on hand, reorder level, stock-in from suppliers. */
public class StockService {

    private final XmlStore store;

    public StockService(XmlStore store) {
        this.store = store;
    }

    private Map<String, String> rowFor(String productId) {
        Map<String, String> row = store.findBy("stock", "productId", productId);
        if (row == null) {
            Map<String, String> fresh = new LinkedHashMap<>();
            fresh.put("productId", productId);
            fresh.put("quantity", "0");
            fresh.put("reorderLevel", "5");
            row = store.save("stock", fresh);
        }
        return row;
    }

    /** Called when a new product is added: start with 0 in stock. */
    public void createRow(String productId) {
        rowFor(productId);
    }

    /** Called when a product is removed. */
    public void deleteRow(String productId) {
        Map<String, String> row = store.findBy("stock", "productId", productId);
        if (row != null) store.delete("stock", row.get("id"));
    }

    /** Goods arrive from a supplier: add them to the quantity on hand. */
    public Map<String, String> stockIn(String productId, String quantityText, String supplier) {
        if (store.findBy("products", "id", productId) == null) {
            throw new IllegalArgumentException("Please choose a product.");
        }
        int quantity = toNumber(quantityText);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (supplier == null || supplier.isBlank()) throw new IllegalArgumentException("Enter the supplier name.");

        Map<String, String> row = rowFor(productId);
        int newQuantity = Integer.parseInt(row.get("quantity")) + quantity;
        Map<String, String> change = new LinkedHashMap<>();
        change.put("id", row.get("id"));
        change.put("quantity", String.valueOf(newQuantity));
        Map<String, String> saved = store.save("stock", change);

        Map<String, String> log = new LinkedHashMap<>();
        log.put("productId", productId);
        log.put("quantity", String.valueOf(quantity));
        log.put("supplier", supplier);
        log.put("date", LocalDate.now().toString());
        store.save("stockin", log);
        return saved;
    }

    /** Throws an error if there is not enough stock. */
    public void ensureAvailable(String productId, int quantity) {
        int onHand = Integer.parseInt(rowFor(productId).get("quantity"));
        if (onHand < quantity) {
            Map<String, String> product = store.findBy("products", "id", productId);
            throw new IllegalArgumentException("Not enough stock for " + product.get("name")
                    + " (only " + onHand + " left).");
        }
    }

    /** Take items out of stock (used when an order is placed). */
    public void remove(String productId, int quantity) {
        ensureAvailable(productId, quantity);
        Map<String, String> row = rowFor(productId);
        Map<String, String> change = new LinkedHashMap<>();
        change.put("id", row.get("id"));
        change.put("quantity", String.valueOf(Integer.parseInt(row.get("quantity")) - quantity));
        store.save("stock", change);
    }

    private int toNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Quantity must be a whole number.");
        }
    }
}
