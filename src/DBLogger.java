import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DBLogger class
 * -----------------------
 * Responsible for:
 * - Connecting to MySQL database
 * - Creating log table dynamically (per day)
 * - Inserting request logs
 * - Fetching logs and converting them into HTML
 */
public class DBLogger {

    // Database connection details
    private static final String URL = "jdbc:mysql://localhost:3306/http_server";
    private static final String USER = "root";
    private static final String PASS = "jerry";

    /**
     * Dynamic table name based on current date
     * Example: logs_2026_04_15
     *
     * This helps:
     * - Organize logs by day
     * - Improve performance (smaller tables)
     */
    private static final String TABLE =
            "logs_" + new SimpleDateFormat("yyyy_MM_dd").format(new Date());

    /**
     * Initializes database
     * - Loads MySQL driver
     * - Creates table if it does not exist
     */
    public static void init() {
        try {
            // Load MySQL JDBC Driver (VERY IMPORTANT)
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            Connection con = DriverManager.getConnection(URL, USER, PASS);

            // Create SQL statement
            Statement st = con.createStatement();

            // SQL query to create table
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "method VARCHAR(10),"        // GET / POST
                    + "path VARCHAR(255),"         // URL path
                    + "status INT,"                // HTTP status code
                    + "ip VARCHAR(50),"            // Client IP
                    + "time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" // auto timestamp
                    + ")";

            // Execute query
            st.executeUpdate(sql);

            System.out.println("✅ Table ready: " + TABLE);

        } catch (Exception e) {
            System.out.println("DB Init Error: " + e.getMessage());
        }
    }

    /**
     * Stores a log entry in database
     *
     * @param method HTTP method (GET/POST)
     * @param path   Requested URL
     * @param status HTTP status code (200, 404, etc.)
     * @param ip     Client IP address
     */
    public static void log(String method, String path, int status, String ip) {

        // SQL insert query using prepared statement (safe from SQL injection)
        String sql = "INSERT INTO " + TABLE + " (method,path,status,ip) VALUES (?,?,?,?)";

        try (
                // Open connection
                Connection con = DriverManager.getConnection(URL, USER, PASS);

                // Prepare statement
                PreparedStatement ps = con.prepareStatement(sql)
        ) {

            // Set values
            ps.setString(1, method);
            ps.setString(2, path);
            ps.setInt(3, status);
            ps.setString(4, ip);

            // Execute insert
            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("DB Log Error: " + e.getMessage());
        }
    }

    /**
     * Fetch logs from DB and convert into HTML table
     * Used by: /logs endpoint in server
     *
     * @return HTML string representing logs
     */
    public static String getLogsHTML() {

        StringBuilder html = new StringBuilder();

        // HTML page start
        html.append("<html><head><title>Logs</title>")
            .append("<style>")
            .append("body{font-family:Arial;padding:20px;}")
            .append("table{border-collapse:collapse;width:100%;}")
            .append("th,td{border:1px solid #ccc;padding:8px;text-align:left;}")
            .append("th{background:#f4f4f4;}")
            .append("</style></head><body>");

        html.append("<h2>Server Logs</h2>");

        // Table header
        html.append("<table>");
        html.append("<tr><th>ID</th><th>Method</th><th>Path</th><th>Status</th><th>IP</th><th>Time</th></tr>");

        // Query to fetch logs (latest first)
        String query = "SELECT * FROM " + TABLE + " ORDER BY id DESC";

        try (
                Connection con = DriverManager.getConnection(URL, USER, PASS);
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(query)
        ) {

            // Loop through results
            while (rs.next()) {

                int status = rs.getInt("status");

                // Add row to HTML table
                html.append("<tr>")
                    .append("<td>").append(rs.getInt("id")).append("</td>")
                    .append("<td>").append(rs.getString("method")).append("</td>")
                    .append("<td>").append(rs.getString("path")).append("</td>")

                    // Color status (green for 200, red for errors)
                    .append("<td style='color:")
                    .append(status == 200 ? "green" : "red")
                    .append("'>")
                    .append(status)
                    .append("</td>")

                    .append("<td>").append(rs.getString("ip")).append("</td>")
                    .append("<td>").append(rs.getTimestamp("time")).append("</td>")
                    .append("</tr>");
            }

        } catch (Exception e) {
            // Show error inside table
            html.append("<tr><td colspan='6'>Error: ")
                .append(e.getMessage())
                .append("</td></tr>");
        }

        // Close HTML
        html.append("</table></body></html>");

        return html.toString();
    }
}