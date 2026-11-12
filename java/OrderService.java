import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Process client orders: check stock, calculate the total, reduce stock, save the order. */
public class OrderService {

    private final XmlStore store;
    private final StockService stock;

    public OrderService(XmlStore store, StockService stock) {
        this.store = store;
        this.stock = stock;
    }

    /** items looks like "1:2;3:1" meaning product 1 x 2 and product 3 x 1. */
    public Map<String, String> create(String clientId, String items) {
        if (store.findBy("clients", "id", clientId) == null) {
            throw new IllegalArgumentException("Please choose a client.");
        }
        if (items == null || items.isBlank()) {
            throw new IllegalArgumentException("Add at least one product to the order.");
        }

        // 1. read the items (the same product twice is added together)
        Map<String, Integer> needed = new LinkedHashMap<>();
        for (String part : items.split(";")) {
            String[] bits = part.split(":");
            if (bits.length != 2) throw new IllegalArgumentException("Invalid order item.");
            int quantity;
            try {
                quantity = Integer.parseInt(bits[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid quantity in the order.");
            }
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
            needed.merge(bits[0].trim(), quantity, Integer::sum);
        }

        // 2. calculate the total and make sure everything is in stock
        double total = 0;
        StringBuilder cleanItems = new StringBuilder();
        for (Map.Entry<String, Integer> line : needed.entrySet()) {
            Map<String, String> product = store.findBy("products", "id", line.getKey());
            if (product == null) throw new IllegalArgumentException("A product in the order no longer exists.");
            stock.ensureAvailable(line.getKey(), line.getValue());
            total += Double.parseDouble(product.get("price")) * line.getValue();
            if (cleanItems.length() > 0) cleanItems.append(";");
            cleanItems.append(line.getKey()).append(":").append(line.getValue());
        }

        // 3. reduce the stock and save the order
        for (Map.Entry<String, Integer> line : needed.entrySet()) {
            stock.remove(line.getKey(), line.getValue());
        }
        Map<String, String> order = new LinkedHashMap<>();
        order.put("clientId", clientId);
        order.put("date", LocalDate.now().toString());
        order.put("items", cleanItems.toString());
        order.put("total", String.format(Locale.US, "%.2f", total));
        order.put("status", "Pending");
        return store.save("orders", order);
    }
}
