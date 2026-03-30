import java.io.*;
import java.net.*;

public class SimpleWebServer {
    public static void main(String[] args) {
        int port = 7000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started at http://localhost:" + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleRequest(clientSocket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()) {
            // Read request (first line is enough)
            String requestLine = in.readLine();
            System.out.println(requestLine);

            // Skip remaining headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
            }

            // 👉 Read index.html file
            File file = new File("index.html");

            if (!file.exists()) {
                String response = "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Type: text/html\r\n\r\n" +
                        "<h1>404 File Not Found</h1>";

                out.write(response.getBytes());
                out.flush();
                return;
            }

            BufferedReader fileReader = new BufferedReader(new FileReader(file));
            StringBuilder content = new StringBuilder();

            while ((line = fileReader.readLine()) != null) {
                content.append(line).append("\n");
            }

            // HTTP response
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html\r\n\r\n" +
                    content.toString();

            out.write(response.getBytes());
            out.flush();

            clientSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}