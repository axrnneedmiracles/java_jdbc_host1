import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Lightweight Java REST API Server bridging the separate Frontend Website with SQLite via JDBC.
 * Runs on port 8080 (0.0.0.0) so both Desktop and Mobile devices on the same network can connect.
 */
public class ApiServer {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final StudentDAO dao = new StudentDAO();
    private static final Map<String, StudentDAO.AuthResult> sessions = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        DatabaseManager.initializeDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/health", ApiServer::handleHealth);
        server.createContext("/api/login", ApiServer::handleLogin);
        server.createContext("/api/logout", ApiServer::handleLogout);
        server.createContext("/api/students", ApiServer::handleStudents);
        server.createContext("/api/export", ApiServer::handleExport);
        server.createContext("/api/import", ApiServer::handleImport);
        server.createContext("/api/db-info", ApiServer::handleDbInfo);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("====================================================");
        System.out.println(" Student Record Management JDBC API Server Started");
        System.out.println(" Local URL : http://localhost:" + PORT + "/api/health");
        System.out.println(" Admin     : admin / admin");
        System.out.println(" Student   : <Roll ID> (e.g. CS101) / 1234");
        System.out.println("====================================================");
    }

    private static void handleHealth(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        sendJson(ex, 200, "{\"status\":\"ok\",\"database\":\"" + Student.esc(DatabaseManager.getEngineLabel()) + "\"}");
    }

    private static void handleDbInfo(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        StudentDAO.AuthResult user = requireAuth(ex);
        if (user == null || !requireAdmin(ex, user)) return;
        try {
            List<Student> rows = dao.getAll();
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(rows.get(i).toJson());
            }
            arr.append("]");
            String json = String.format(
                "{\"engine\":\"%s\",\"file\":\"%s\",\"table\":\"students\",\"rowCount\":%d,\"rows\":%s}",
                Student.esc(DatabaseManager.getEngineLabel()),
                Student.esc(DatabaseManager.getLocationLabel()),
                rows.size(), arr
            );
            sendJson(ex, 200, json);
        } catch (AppException e) {
            sendError(ex, e.getStatusCode(), e.getMessage());
        }
    }

    private static void handleLogin(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        try {
            Map<String, String> body = parseJsonObject(readBody(ex));
            String username = body.getOrDefault("username", "");
            String password = body.getOrDefault("password", "");
            String role = body.getOrDefault("role", "student");

            StudentDAO.AuthResult auth = dao.authenticate(username, password, role);
            String token = UUID.randomUUID().toString();
            sessions.put(token, auth);
            String json = String.format(
                "{\"token\":\"%s\",\"username\":\"%s\",\"role\":\"%s\",\"rollId\":\"%s\"}",
                token, Student.esc(auth.username), Student.esc(auth.role), Student.esc(auth.rollId)
            );
            sendJson(ex, 200, json);
        } catch (AppException e) {
            sendError(ex, e.getStatusCode(), e.getMessage());
        }
    }

    private static void handleLogout(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        String token = extractToken(ex);
        if (token != null) sessions.remove(token);
        sendJson(ex, 200, "{\"message\":\"Logged out\"}");
    }

    private static void handleStudents(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        StudentDAO.AuthResult user = requireAuth(ex);
        if (user == null) return;

        String method = ex.getRequestMethod().toUpperCase();
        Integer id = extractId(ex.getRequestURI().getPath());

        try {
            switch (method) {
                case "GET" -> {
                    // Student can ONLY view their own record
                    if ("student".equals(user.role)) {
                        Student mine = dao.getByRollId(user.rollId);
                        if (mine == null) {
                            sendError(ex, 404, "Record not found. Kindly contact admin.");
                            return;
                        }
                        sendJson(ex, 200, "[" + mine.toJson() + "]");
                        return;
                    }
                    String q = getQueryParam(ex.getRequestURI().getRawQuery(), "q");
                    List<Student> list = (q != null && !q.isBlank()) ? dao.search(q) : dao.getAll();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(list.get(i).toJson());
                    }
                    sb.append("]");
                    sendJson(ex, 200, sb.toString());
                }
                case "POST" -> {
                    if (!requireAdmin(ex, user)) return;
                    Student created = dao.add(parseStudent(parseJsonObject(readBody(ex))));
                    sendJson(ex, 201, created.toJson());
                }
                case "PUT" -> {
                    if (!requireAdmin(ex, user)) return;
                    if (id == null) { sendError(ex, 400, "Student ID is required in URL."); return; }
                    Student updated = dao.update(id, parseStudent(parseJsonObject(readBody(ex))));
                    sendJson(ex, 200, updated.toJson());
                }
                case "DELETE" -> {
                    if (!requireAdmin(ex, user)) return;
                    if (id == null) { sendError(ex, 400, "Student ID is required in URL."); return; }
                    dao.delete(id);
                    sendJson(ex, 200, "{\"deleted\":true,\"id\":" + id + "}");
                }
                default -> sendError(ex, 405, "Method not allowed");
            }
        } catch (AppException e) {
            sendError(ex, e.getStatusCode(), e.getMessage());
        }
    }

    private static void handleExport(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        StudentDAO.AuthResult user = requireAuth(ex);
        if (user == null) return;
        try {
            List<Student> rows;
            if ("student".equals(user.role)) {
                Student mine = dao.getByRollId(user.rollId);
                if (mine == null) { sendError(ex, 404, "Record not found. Kindly contact admin."); return; }
                rows = List.of(mine);
            } else {
                rows = dao.getAll();
            }
            String csv = CsvFileHandler.exportToCsv(rows);
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"students.csv\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (AppException e) {
            sendError(ex, e.getStatusCode(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void handleImport(HttpExchange ex) throws IOException {
        if (handleCors(ex)) return;
        StudentDAO.AuthResult user = requireAuth(ex);
        if (user == null || !requireAdmin(ex, user)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendError(ex, 405, "Method not allowed");
            return;
        }
        try {
            Map<String, Object> result = CsvFileHandler.importFromCsv(readBody(ex), dao);
            List<String> errs = (List<String>) result.get("errors");
            StringBuilder errJson = new StringBuilder("[");
            for (int i = 0; i < errs.size(); i++) {
                if (i > 0) errJson.append(",");
                errJson.append("\"").append(Student.esc(errs.get(i))).append("\"");
            }
            errJson.append("]");
            String json = String.format("{\"imported\":%d,\"updated\":%d,\"errors\":%s}",
                result.get("imported"), result.get("updated"), errJson);
            sendJson(ex, 200, json);
        } catch (AppException e) {
            sendError(ex, e.getStatusCode(), e.getMessage());
        }
    }

    // --- Auth, CORS & Lightweight JSON Helpers ---

    private static boolean handleCors(HttpExchange ex) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static StudentDAO.AuthResult requireAuth(HttpExchange ex) throws IOException {
        String token = extractToken(ex);
        StudentDAO.AuthResult user = token != null ? sessions.get(token) : null;
        if (user == null) {
            sendError(ex, 401, "Unauthorized. Please log in first.");
            return null;
        }
        return user;
    }

    private static boolean requireAdmin(HttpExchange ex, StudentDAO.AuthResult user) throws IOException {
        if (!"admin".equals(user.role)) {
            sendError(ex, 403, "Forbidden: Admin privileges required for this action.");
            return false;
        }
        return true;
    }

    private static String extractToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
        return getQueryParam(ex.getRequestURI().getRawQuery(), "token");
    }

    private static Integer extractId(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 4 && !parts[3].isBlank()) {
            try { return Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String getQueryParam(String rawQuery, String key) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Student parseStudent(Map<String, String> m) throws AppException.InvalidInputException {
        try {
            return new Student(0, m.get("rollId"), m.get("name"),
                parseInt(m, "sub1"), parseInt(m, "sub2"), parseInt(m, "sub3"), parseInt(m, "sub4"), parseInt(m, "sub5"),
                parseInt(m, "att1"), parseInt(m, "att2"), parseInt(m, "att3"), parseInt(m, "att4"), parseInt(m, "att5"));
        } catch (NumberFormatException e) {
            throw new AppException.InvalidInputException("All 5 subject marks and attendance values must be valid whole numbers.");
        }
    }

    private static int parseInt(Map<String, String> m, String key) {
        return Integer.parseInt(m.getOrDefault(key, "-1").trim());
    }

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|([^,}\\s]+))").matcher(json);
        while (m.find()) {
            String val = m.group(2) != null ? m.group(2).replace("\\\"", "\"").replace("\\\\", "\\") : m.group(3);
            map.put(m.group(1), val);
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, "{\"error\":\"" + Student.esc(message) + "\"}");
    }
}
