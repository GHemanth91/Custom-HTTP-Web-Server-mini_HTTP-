package src;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Custom HTTP/1.1 Web Server — built from scratch using Java Socket Programming.
 *
 * Features:
 *  - Thread pool (ExecutorService) for concurrent client handling
 *  - URL routing engine for GET requests
 *  - POST request body parsing (key=value form data)
 *  - MIME type detection (HTML, CSS, JS, JSON, images)
 *  - Path traversal attack prevention
 *  - HTTP status codes: 200, 400, 403, 404, 405, 500
 *  - Request logging with timestamps and client IP
 *  - Graceful shutdown via JVM shutdown hook
 */
public class SimpleWebServer {

    private static final int    PORT      = 7000;
    private static final int    POOL_SIZE = 10;

    // Resolves static/ folder reliably regardless of where you run from
    private static final String STATIC_DIR = resolveStaticDir();

    private static String resolveStaticDir() {
        // 1st attempt: <current working directory>/static  (normal case)
        File candidate = new File(System.getProperty("user.dir"), "static");
        if (candidate.exists() && candidate.isDirectory()) {
            return candidate.getAbsolutePath();
        }
        // 2nd attempt: folder where the .class file lives + /static
        File classDir = new File(
            SimpleWebServer.class.getProtectionDomain()
                                 .getCodeSource()
                                 .getLocation()
                                 .getPath()
        );
        return new File(classDir.getParentFile(), "static").getAbsolutePath();
    }

    // ─────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────
    public static void main(String[] args) {

        ExecutorService threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        // Graceful shutdown — drain active threads on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            threadPool.shutdown();
            System.out.println("[Server] Done.");
        }));

        // ── Startup diagnostics — tells you exactly what the server sees ──
        File staticFolder = new File(STATIC_DIR);
        System.out.println("==============================================");
        System.out.println("  Custom HTTP Web Server  |  Port : " + PORT);
        System.out.println("  Thread Pool Size         : " + POOL_SIZE);
        System.out.println("  Working directory        : " + System.getProperty("user.dir"));
        System.out.println("  Serving files from       : " + STATIC_DIR);
        System.out.println("  static/ folder exists?   : " + staticFolder.exists());

        if (staticFolder.exists()) {
            String[] files = staticFolder.list();
            if (files != null && files.length > 0) {
                System.out.println("  Files inside static/     :");
                for (String f : files) System.out.println("      - " + f);
            } else {
                System.out.println("  WARNING: static/ folder is EMPTY — add index.html!");
            }
        } else {
            System.out.println("  ERROR: static/ folder NOT FOUND!");
            System.out.println("  FIX  : Create a 'static' folder next to this file");
            System.out.println("         and put your index.html inside it.");
        }

        System.out.println("  Visit: http://localhost:" + PORT);
        System.out.println("==============================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleRequest(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CORE REQUEST HANDLER
    // ─────────────────────────────────────────────────────────────
    private static void handleRequest(Socket clientSocket) {
        try (
            BufferedReader in  = new BufferedReader( new InputStreamReader(clientSocket.getInputStream()));
            OutputStream   out = clientSocket.getOutputStream()
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            log(requestLine + " | from " + clientSocket.getInetAddress());

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendError(out, 400, "Bad Request", "Malformed request line.");
                return;
            }

            String method = parts[0];
            String path   = parts[1];

            // Read all headers, extract Content-Length for POST
            int    contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
                }
            }

            switch (method) {
                case "GET":
                    handleGet(out, path);
                    break;
                case "POST":
                    handlePost(in, out, path, contentLength);
                    break;
                default:
                    sendError(out, 405, "Method Not Allowed",
                              "Method '" + method + "' is not supported.");
            }

        } catch (IOException e) {
            System.err.println("[Error] Handling request: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // GET HANDLER — routing + static file serving
    // ─────────────────────────────────────────────────────────────
    private static void handleGet(OutputStream out, String path) throws IOException {

        // "/" → index.html
        if (path.equals("/")) path = "/index.html";

        // Strip query string: /page?foo=bar → /page
        if (path.contains("?")) path = path.substring(0, path.indexOf("?"));

        // Strip leading slashes and build safe file path
        String relativePath = path.replaceAll("^/+", "")          // remove leading /
                                  .replace("/", File.separator);   // OS-safe separators

        File requestedFile = new File(STATIC_DIR, relativePath);
        File staticRoot    = new File(STATIC_DIR);

        log("Looking for: " + requestedFile.getAbsolutePath());

        // ── Security: block path traversal attacks (../../etc/passwd) ──
        if (!requestedFile.getCanonicalPath()
                          .startsWith(staticRoot.getCanonicalPath())) {
            log("BLOCKED path traversal attempt: " + path);
            sendError(out, 403, "Forbidden", "Access denied.");
            return;
        }

        // ── File not found ──
        if (!requestedFile.exists() || requestedFile.isDirectory()) {
            log("NOT FOUND: " + requestedFile.getAbsolutePath());
            sendError(out, 404, "Not Found",
                      "The resource '<b>" + path + "</b>' was not found on this server.");
            return;
        }

        // ── Read and serve file bytes ──
        try {
            byte[] fileBytes = Files.readAllBytes(requestedFile.toPath());
            String mimeType  = getMimeType(requestedFile.getName());

            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: "   + mimeType        + "\r\n"
                    + "Content-Length: " + fileBytes.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";

            out.write(headers.getBytes());
            out.write(fileBytes);
            out.flush();
            log("200 OK → " + path + " (" + fileBytes.length + " bytes)");

        } catch (IOException e) {
            sendError(out, 500, "Internal Server Error",
                      "Could not read file: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // POST HANDLER — parses key=value body, returns HTML response
    // ─────────────────────────────────────────────────────────────
    private static void handlePost(BufferedReader in, OutputStream out,
                                   String path, int contentLength)
            throws IOException {

        String requestBody = "";
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            in.read(bodyChars, 0, contentLength);
            requestBody = new String(bodyChars);
        }

        log("POST → " + path + " | Body: " + requestBody);

        // Parse URL-encoded form data: name=Hemanth&role=developer
        StringBuilder parsedData = new StringBuilder();
        if (!requestBody.isEmpty()) {
            for (String pair : requestBody.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    parsedData.append("<p><b>").append(kv[0])
                              .append(":</b> ").append(kv[1]).append("</p>");
                }
            }
        }

        String responseBody = "<html><body style='font-family:sans-serif;padding:20px'>"
                + "<h1>POST Received</h1>"
                + "<p>Endpoint: <code>" + path + "</code></p>"
                + "<h3>Parsed Fields:</h3>"
                + (parsedData.length() > 0 ? parsedData.toString()
                                           : "<p>No data received.</p>")
                + "<br><a href='/'>&#8592; Go Back</a>"
                + "</body></html>";

        byte[] responseBytes = responseBody.getBytes();
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + responseBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        out.write(headers.getBytes());
        out.write(responseBytes);
        out.flush();
    }

    // ─────────────────────────────────────────────────────────────
    // MIME TYPE DETECTION
    // ─────────────────────────────────────────────────────────────
    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html";
        if (fileName.endsWith(".css"))  return "text/css";
        if (fileName.endsWith(".js"))   return "application/javascript";
        if (fileName.endsWith(".json")) return "application/json";
        if (fileName.endsWith(".png"))  return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".gif"))  return "image/gif";
        if (fileName.endsWith(".svg"))  return "image/svg+xml";
        if (fileName.endsWith(".ico"))  return "image/x-icon";
        if (fileName.endsWith(".txt"))  return "text/plain";
        if (fileName.endsWith(".pdf"))  return "application/pdf";
        return "application/octet-stream"; // safe default for unknown types
    }

    // ─────────────────────────────────────────────────────────────
    // GENERIC ERROR SENDER (400, 403, 404, 405, 500)
    // ─────────────────────────────────────────────────────────────
    private static void sendError(OutputStream out, int code,
                                  String status, String message)
            throws IOException {

        String body = "<html><body style='font-family:sans-serif;padding:20px'>"
                + "<h1>" + code + " " + status + "</h1>"
                + "<p>" + message + "</p>"
                + "<hr><small>Custom HTTP Server &mdash; Hemanth Giduthuri</small>"
                + "</body></html>";

        byte[] bytes = body.getBytes();
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        out.write(headers.getBytes());
        out.write(bytes);
        out.flush();
        log(code + " " + status + " | " + message);
    }

    // ─────────────────────────────────────────────────────────────
    // LOGGER
    // ─────────────────────────────────────────────────────────────
    private static void log(String message) {
        System.out.println("[" + new java.util.Date() + "] " + message);
    }
}