import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple HTTP Web Server using Java Sockets
 * Features:
 * - Handles GET and POST requests
 * - Serves static files from /static folder
 * - Logs requests to MySQL (DBLogger)
 * - Multithreaded using ExecutorService
 */
public class SimpleWebServer {

    // Server runs on port 7000
    private static final int PORT = 7000;

    // Maximum number of concurrent threads (requests handled at once)
    private static final int POOL_SIZE = 10;

    // Path to static folder (resolved dynamically)
    private static final String STATIC_DIR = resolveStaticDir();

    /**
     * Finds the correct static directory location
     * Works regardless of where you run the server from
     */
    private static String resolveStaticDir() {
        // First try: current working directory + /static
        File candidate = new File(System.getProperty("user.dir"), "static");
        if (candidate.exists()) return candidate.getAbsolutePath();

        // Second try: location of compiled class file + /static
        File classDir = new File(
                SimpleWebServer.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .getPath()
        );

        return new File(classDir.getParentFile(), "static").getAbsolutePath();
    }

    /**
     * MAIN METHOD - Server starts here
     */
    public static void main(String[] args) {

        // Initialize database (create table if not exists)
        DBLogger.init();

        // Thread pool for handling multiple clients
        ExecutorService threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        System.out.println("Server started at http://localhost:" + PORT);
        System.out.println("Serving from: " + STATIC_DIR);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Infinite loop to accept client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Each request handled in a separate thread
                threadPool.execute(() -> handleRequest(clientSocket));
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Handles incoming HTTP request
     */
    private static void handleRequest(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()
        ) {

            // Read first line of HTTP request (e.g., GET /index.html HTTP/1.1)
            String requestLine = in.readLine();
            if (requestLine == null) return;

            System.out.println("Request: " + requestLine);

            // Split request line
            String[] parts = requestLine.split(" ");
            String method = parts[0]; // GET / POST
            String path = parts[1];   // requested URL path
            String ip = clientSocket.getInetAddress().toString();

            // Read headers (important for POST body length)
            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            // Route request based on HTTP method
            if (method.equals("GET")) {
                handleGet(out, path, ip);
            } else if (method.equals("POST")) {
                handlePost(in, out, path, contentLength, ip);
            } else {
                sendError(out, 405, "Method Not Allowed", ip, path);
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Handles GET requests (serves static files)
     */
    private static void handleGet(OutputStream out, String path, String ip) throws IOException {

        // Decode URL (handles spaces like %20)
        path = URLDecoder.decode(path, "UTF-8");

        // Custom route: /logs → show DB logs page
        if (path.equals("/logs")) {
            handleLogs(out);
            return;
        }

        // Default route → index.html
        if (path.equals("/")) path = "/index.html";

        // Remove query parameters (?name=value)
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        // Remove leading slash
        String relativePath = path.replaceAll("^/+", "");

        // Create file reference
        File file = new File(STATIC_DIR, relativePath);

        System.out.println("Looking for file: " + file.getAbsolutePath());

        // Security: prevent accessing files outside static folder
        if (!file.getCanonicalPath().startsWith(new File(STATIC_DIR).getCanonicalPath())) {
            sendError(out, 403, "Forbidden", ip, path);
            return;
        }

        // If file not found
        if (!file.exists() || file.isDirectory()) {
            sendError(out, 404, "Not Found", ip, path);
            return;
        }

        // Read file content
        byte[] data = Files.readAllBytes(file.toPath());

        // Send HTTP response
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + getMimeType(file.getName()) + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(header.getBytes());
        out.write(data);

        // Log request to database
        DBLogger.log("GET", path, 200, ip);
    }

    /**
     * Handles POST requests (form submission)
     */
    private static void handlePost(BufferedReader in, OutputStream out,
                                  String path, int len, String ip) throws IOException {

        // Read request body
        char[] body = new char[len];
        in.read(body);
        String requestBody = new String(body);

        // Simple HTML response
        String response = "<html><body><h1>POST Received</h1><p>"
                + requestBody + "</p></body></html>";

        byte[] data = response.getBytes();

        // Send response
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n";

        out.write(header.getBytes());
        out.write(data);

        // Log POST request
        DBLogger.log("POST", path, 200, ip);
    }

    /**
     * Sends error response (404, 403, etc.)
     */
    private static void sendError(OutputStream out, int code,
                                 String message, String ip, String path) throws IOException {

        String body = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
        byte[] data = body.getBytes();

        String header = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n";

        out.write(header.getBytes());
        out.write(data);

        // Log error
        DBLogger.log("ERROR", path, code, ip);
    }

    /**
     * Returns MIME type based on file extension
     */
    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "application/javascript";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    /**
     * Handles /logs route → shows logs from database
     */
    private static void handleLogs(OutputStream out) throws IOException {

        // Get HTML table from DB
        String html = DBLogger.getLogsHTML();

        byte[] data = html.getBytes();

        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n";

        out.write(header.getBytes());
        out.write(data);
    }
}