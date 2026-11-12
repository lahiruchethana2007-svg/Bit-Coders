import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monthly sales report and low-stock alerts. */
public class ReportService {

    private final XmlStore store;

    public ReportService(XmlStore store) {
        this.store = store;
    }

    /** month looks like "2026-11". */
    public Map<String, Object> monthly(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) month = YearMonth.now().toString();

        int orderCount = 0;
        double revenue = 0;
        Map<String, Integer> unitsSold = new LinkedHashMap<>();
        for (Map<String, String> order : store.readAll("orders")) {
            if (!order.get("date").startsWith(month)) continue;
            orderCount++;
            revenue += Double.parseDouble(order.get("total"));
            for (String part : order.get("items").split(";")) {
                String[] bits = part.split(":");
                unitsSold.merge(bits[0], Integer.parseInt(bits[1]), Integer::sum);
            }
        }

        List<Object> productsSold = new ArrayList<>();
        for (Map.Entry<String, Integer> line : unitsSold.entrySet()) {
            Map<String, String> product = store.findBy("products", "id", line.getKey());
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", product == null ? "(deleted product)" : product.get("name"));
            row.put("units", String.valueOf(line.getValue()));
            productsSold.add(row);
        }

        List<Object> lowStock = new ArrayList<>();
        for (Map<String, String> stockRow : store.readAll("stock")) {
            int quantity = Integer.parseInt(stockRow.get("quantity"));
            int reorderLevel = Integer.parseInt(stockRow.get("reorderLevel"));
            if (quantity <= reorderLevel) {
                Map<String, String> product = store.findBy("products", "id", stockRow.get("productId"));
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", product == null ? "(deleted product)" : product.get("name"));
                row.put("quantity", String.valueOf(quantity));
                row.put("reorderLevel", String.valueOf(reorderLevel));
                lowStock.add(row);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("month", month);
        report.put("orderCount", String.valueOf(orderCount));
        report.put("revenue", String.format(Locale.US, "%.2f", revenue));
        report.put("productsSold", productsSold);
        report.put("lowStock", lowStock);
        return report;
    }
}
