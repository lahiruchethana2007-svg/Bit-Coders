import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assign delivery agents to orders, track delivery status and send the email notifications. */
public class DeliveryService {

    private final XmlStore store;
    private final EmailService email;

    public DeliveryService(XmlStore store, EmailService email) {
        this.store = store;
        this.email = email;
    }

    /** Give an order to a delivery agent. The agent is emailed. */
    public Map<String, String> assign(String orderId, String agentId, String date) {
        Map<String, String> order = store.findBy("orders", "id", orderId);
        Map<String, String> agent = store.findBy("agents", "id", agentId);
        if (order == null) throw new IllegalArgumentException("Please choose an order.");
        if (agent == null) throw new IllegalArgumentException("Please choose a delivery agent.");
        if (store.findBy("deliveries", "orderId", orderId) != null) {
            throw new IllegalArgumentException("This order already has a delivery agent.");
        }
        if (date == null || date.isBlank()) date = LocalDate.now().toString();

        Map<String, String> delivery = new LinkedHashMap<>();
        delivery.put("orderId", orderId);
        delivery.put("agentId", agentId);
        delivery.put("scheduledDate", date);
        delivery.put("status", "Assigned");
        Map<String, String> saved = store.save("deliveries", delivery);
        setOrderStatus(orderId, "Assigned");

        Map<String, String> client = store.findBy("clients", "id", order.get("clientId"));
        email.send(agent.get("email"),
                "New delivery assigned: order #" + orderId,
                "Hello " + agent.get("name") + ",\n\n"
                + "You have been assigned order #" + orderId + " for " + date + ".\n"
                + "Client: " + (client == null ? "-" : client.get("name")) + "\n"
                + "Phone: " + (client == null ? "-" : client.get("phone")) + "\n"
                + "Address: " + (client == null ? "-" : client.get("address")) + "\n\n"
                + "EcoNest");
        return saved;
    }

    /** Change the status to Assigned, Dispatched or Delivered. Dispatched emails the client. */
    public Map<String, String> setStatus(String id, String status) {
        if (!List.of("Assigned", "Dispatched", "Delivered").contains(status)) {
            throw new IllegalArgumentException("Unknown status.");
        }
        Map<String, String> delivery = store.findBy("deliveries", "id", id);
        if (delivery == null) throw new IllegalArgumentException("Delivery not found.");

        Map<String, String> change = new LinkedHashMap<>();
        change.put("id", id);
        change.put("status", status);
        Map<String, String> saved = store.save("deliveries", change);
        setOrderStatus(delivery.get("orderId"), status);

        if (status.equals("Dispatched") && !"Dispatched".equals(delivery.get("status"))) {
            Map<String, String> order = store.findBy("orders", "id", delivery.get("orderId"));
            Map<String, String> client = order == null ? null : store.findBy("clients", "id", order.get("clientId"));
            Map<String, String> agent = store.findBy("agents", "id", delivery.get("agentId"));
            if (client != null) {
                email.send(client.get("email"),
                        "Your EcoNest order #" + order.get("id") + " has been dispatched",
                        "Hello " + client.get("name") + ",\n\n"
                        + "Your order #" + order.get("id") + " (total Rs. " + order.get("total") + ") is on its way.\n"
                        + (agent == null ? "" : "Delivery agent: " + agent.get("name") + " (" + agent.get("phone") + ")\n")
                        + "\nThank you for choosing EcoNest.");
            }
        }
        return saved;
    }

    private void setOrderStatus(String orderId, String status) {
        Map<String, String> change = new LinkedHashMap<>();
        change.put("id", orderId);
        change.put("status", status);
        store.save("orders", change);
    }
}
