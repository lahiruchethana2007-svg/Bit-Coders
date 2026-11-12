import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Receives every request that starts with /api/ from the web pages and
 * decides what to do:  GET = read, POST = add/update, DELETE = remove.
 */
public class ApiHandler implements HttpHandler {

    // XML files the pages may read
    private static final Set<String> READABLE =
            Set.of("products", "clients", "stock", "agents", "orders", "deliveries", "stockin", "outbox");
    // XML files the pages may add / update / remove directly
    private static final Set<String> EDITABLE = Set.of("products", "clients", "agents", "stock");

    private final XmlStore store;
    private final StockService stock;
    private final OrderService orders;
    private final DeliveryService deliveries;
    private final ReportService reports;
    private final EmailService email;

    public ApiHandler(XmlStore store) {
        this.email = new EmailService(store);
        this.store = store;
        this.stock = new StockService(store);
        this.orders = new OrderService(store, stock);
        this.deliveries = new DeliveryService(store, email);
        this.reports = new ReportService(store);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String name = exchange.getRequestURI().getPath().substring("/api/".length());
            Map<String, String> params = parseForm(exchange.getRequestURI().getRawQuery());
            if (method.equals("POST")) {
                params.putAll(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            }
            send(exchange, 200, Json.toJson(route(method, name, params)));
        } catch (IllegalArgumentException e) {
            send(exchange, 400, Json.toJson(Map.of("error", String.valueOf(e.getMessage()))));
        } catch (Exception e) {
            e.printStackTrace();
            send(exchange, 500, Json.toJson(Map.of("error", "Server error: " + e.getMessage())));
        }
    }

    private Object route(String method, String name, Map<String, String> p) {
        if (method.equals("GET")) {
            if (name.equals("report")) return reports.monthly(p.get("month"));
            if (name.equals("email-config")) return Map.of("configured",email.configured());
            if (READABLE.contains(name)) return store.readAll(name);
        }

        if (method.equals("POST")) {
            switch (name) {
                case "orders":          return orders.create(p.get("clientId"), p.get("items"));
                case "deliveries":      return deliveries.assign(p.get("orderId"), p.get("agentId"), p.get("scheduledDate"));
                case "delivery-status": return deliveries.setStatus(p.get("id"), p.get("status"));
                case "stock-in":        return stock.stockIn(p.get("productId"), p.get("quantity"), p.get("supplier"));
            }
            if (name.equals("upload-image")) return uploadImage(p);
            if (EDITABLE.contains(name)) return save(name, p);
        }

        if (method.equals("DELETE") && EDITABLE.contains(name) && !name.equals("stock")) {
            String id = p.get("id");
            store.delete(name, id);
            if (name.equals("products")) stock.deleteRow(id);
            return Map.of("deleted", String.valueOf(id));
        }

        throw new IllegalArgumentException("Unknown request: " + method + " /api/" + name);
    }

    private Map<String, String> uploadImage(Map<String, String> p) {
        String type = p.getOrDefault("type", "");
        if (!type.equals("products") && !type.equals("agents"))
            throw new IllegalArgumentException("Invalid image category.");
        String data = p.getOrDefault("image", "");
        int comma = data.indexOf(',');
        if (comma < 0) throw new IllegalArgumentException("Invalid image data.");
        String mime = data.substring(0, comma);
        String ext;
        if (mime.equals("data:image/png;base64")) ext = "png";
        else if (mime.equals("data:image/jpeg;base64")) ext = "jpg";
        else if (mime.equals("data:image/webp;base64")) ext = "webp";
        else throw new IllegalArgumentException("Only JPG, PNG and WebP images are supported.");
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(comma + 1));
            if (bytes.length > 3 * 1024 * 1024) throw new IllegalArgumentException("Image must be under 3 MB.");
            boolean valid = ext.equals("png") && bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80
                    || ext.equals("jpg") && bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                    || ext.equals("webp") && bytes.length > 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            if (!valid) throw new IllegalArgumentException("Image file content is invalid.");
            Path folder = Path.of("uploads", type);
            Files.createDirectories(folder);
            String filename = UUID.randomUUID() + "." + ext;
            Files.write(folder.resolve(filename), bytes);
            return Map.of("url", "/uploads/" + type + "/" + filename);
        } catch (IllegalArgumentException e) { throw e;
        } catch (IOException e) { throw new IllegalArgumentException("Could not save image."); }
    }

    private Map<String, String> save(String entity, Map<String, String> p) {
        boolean isNew = p.get("id") == null || p.get("id").isBlank();

        if (entity.equals("products") && p.containsKey("price")) {
            double price;
            try {
                price = Double.parseDouble(p.get("price"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Price must be a number.");
            }
            if (price < 0) throw new IllegalArgumentException("Price cannot be negative.");
        }

        Map<String, String> saved = store.save(entity, p);
        if (entity.equals("products") && isNew) stock.createRow(saved.get("id"));
        return saved;
    }

    private static Map<String, String> parseForm(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return map;
        for (String pair : text.split("&")) {
            String[] kv = pair.split("=", 2);
            map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }

    private static void send(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
