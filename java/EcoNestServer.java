import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/** Starts the web server. Run it from the econest folder. */
public class EcoNestServer {

    private static final int PORT = 8080;
    // only these folders can be opened by the browser (the xml folder stays private)
    private static final Set<String> PUBLIC_FOLDERS = Set.of("html", "js", "css", "uploads");
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=UTF-8",
            "js", "text/javascript; charset=UTF-8",
            "css", "text/css; charset=UTF-8",
            "png", "image/png", "jpg", "image/jpeg", "webp", "image/webp");

    public static void main(String[] args) throws Exception {
        XmlStore store = new XmlStore("xml");

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/", new ApiHandler(store));
        server.createContext("/", EcoNestServer::serveFile);
        server.start();

        System.out.println("EcoNest is running: http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop.");
    }

    /** Sends an HTML, JS or CSS file to the browser. */
    private static void serveFile(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) requested = "/html/index.html";

        Path file = Paths.get("." + requested).normalize();
        boolean allowed = file.getNameCount() >= 2
                && PUBLIC_FOLDERS.contains(file.getName(0).toString())
                && Files.isRegularFile(file);

        if (!allowed) {
            byte[] message = "Page not found".getBytes();
            exchange.sendResponseHeaders(404, message.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(message);
            }
            return;
        }

        String name = file.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1);
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().add("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
