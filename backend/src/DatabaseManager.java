import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

/**
 * Dual-mode JDBC DatabaseManager:
 * - Uses PostgreSQL (Supabase / Neon) when DATABASE_URL environment variable is set.
 * - Falls back to local SQLite (data/students.db) for zero-config local execution.
 */
public class DatabaseManager {
    private static final String DB_DIR = "data";
    private static final String SQLITE_URL = "jdbc:sqlite:" + DB_DIR + "/students.db";

    private static final boolean USE_POSTGRES;
    private static final String JDBC_URL;
    private static final String DB_USER;
    private static final String DB_PASS;

    static {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
            String envUrl = System.getenv("DATABASE_URL");
            if (envUrl != null && !envUrl.isBlank()) {
                Class.forName("org.postgresql.Driver");
                USE_POSTGRES = true;
                String clean = envUrl.trim();
                if (clean.startsWith("jdbc:")) {
                    JDBC_URL = clean;
                    DB_USER = System.getenv("DB_USER");
                    DB_PASS = System.getenv("DB_PASSWORD");
                } else {
                    URI uri = new URI(clean.replaceFirst("^postgres://", "postgresql://"));
                    String userInfo = uri.getRawUserInfo();
                    String[] creds = userInfo != null ? userInfo.split(":", 2) : new String[]{"", ""};
                    DB_USER = URLDecoder.decode(creds[0], StandardCharsets.UTF_8);
                    DB_PASS = creds.length > 1 ? URLDecoder.decode(creds[1], StandardCharsets.UTF_8) : "";
                    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String query = uri.getQuery();
                    String params = (query != null && !query.isBlank()) ? query + "&" : "";
                    JDBC_URL = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath()
                        + "?" + params + "sslmode=require&prepareThreshold=0";
                }
            } else {
                Class.forName("org.sqlite.JDBC");
                USE_POSTGRES = false;
                JDBC_URL = SQLITE_URL;
                DB_USER = null;
                DB_PASS = null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Fail to initialize JDBC driver/configuration: " + e.getMessage(), e);
        }
    }

    public static boolean isPostgres() { return USE_POSTGRES; }
    public static String getEngineLabel() { return USE_POSTGRES ? "PostgreSQL (Supabase JDBC)" : "SQLite (Local JDBC)"; }
    public static String getLocationLabel() { return USE_POSTGRES ? "Supabase Cloud DB" : "backend/data/students.db"; }

    public static Connection getConnection() throws AppException.DatabaseException {
        try {
            return USE_POSTGRES
                ? DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)
                : DriverManager.getConnection(JDBC_URL);
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Database connection failed: " + e.getMessage(), e);
        }
    }

    public static void initializeDatabase() throws AppException.DatabaseException {
        String idCol = USE_POSTGRES ? "SERIAL PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
        String createStudents = """
            CREATE TABLE IF NOT EXISTS students (
                id %s,
                roll_id TEXT UNIQUE NOT NULL,
                name TEXT NOT NULL,
                sub1 INTEGER NOT NULL,
                sub2 INTEGER NOT NULL,
                sub3 INTEGER NOT NULL,
                sub4 INTEGER NOT NULL,
                sub5 INTEGER NOT NULL,
                att1 INTEGER NOT NULL,
                att2 INTEGER NOT NULL,
                att3 INTEGER NOT NULL,
                att4 INTEGER NOT NULL,
                att5 INTEGER NOT NULL
            );
            """.formatted(idCol);

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(createStudents);
            seedInitialData(conn);
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Failed to initialize database table: " + e.getMessage(), e);
        }
    }

    private static void seedInitialData(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            if (rs.next() && rs.getInt(1) == 0) {
                String sql = """
                    INSERT INTO students
                    (roll_id, name, sub1, sub2, sub3, sub4, sub5, att1, att2, att3, att4, att5)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                Object[][] samples = {
                    {"CS101", "Aarav Sharma",  88, 92, 85, 79, 91, 90, 95, 88, 82, 92},
                    {"CS102", "Priya Patel",   95, 89, 94, 90, 96, 96, 92, 95, 91, 98},
                    {"CS103", "Rohan Verma",   76, 81, 74, 85, 79, 80, 84, 78, 86, 82},
                    {"CS104", "Ananya Iyer",   91, 94, 88, 92, 87, 93, 96, 89, 94, 90},
                    {"CS105", "Kabir Nair",    68, 72, 75, 70, 74, 76, 78, 80, 75, 79}
                };
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Object[] r : samples) {
                        ps.setString(1, (String) r[0]);
                        ps.setString(2, (String) r[1]);
                        for (int i = 2; i < 12; i++) ps.setInt(i + 1, (Integer) r[i]);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }
}
