import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object performing all JDBC CRUD and role-based authentication.
 */
public class StudentDAO {
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin";
    private static final String STUDENT_DEFAULT_PASS = "1234";

    public static class AuthResult {
        public final String username;
        public final String role;
        public final String rollId;

        public AuthResult(String username, String role, String rollId) {
            this.username = username;
            this.role = role;
            this.rollId = rollId;
        }
    }

    public AuthResult authenticate(String username, String password, String role) throws AppException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AppException.InvalidInputException("Username / Roll ID and password are required.");
        }
        String cleanUser = username.trim();

        if ("admin".equalsIgnoreCase(role)) {
            if (ADMIN_USER.equals(cleanUser) && ADMIN_PASS.equals(password)) {
                return new AuthResult(ADMIN_USER, "admin", "");
            }
            throw new AppException("Invalid admin username or password.", 401);
        }

        // Student login: Roll ID must exist in database, password is "1234"
        Student student = getByRollId(cleanUser);
        if (student == null) {
            throw new AppException("Record not found. Kindly contact admin.", 404);
        }
        if (!STUDENT_DEFAULT_PASS.equals(password)) {
            throw new AppException("Invalid password for student login.", 401);
        }
        return new AuthResult(student.name, "student", student.rollId);
    }

    public List<Student> getAll() throws AppException.DatabaseException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT * FROM students ORDER BY id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Failed to fetch students: " + e.getMessage(), e);
        }
    }

    public Student getByRollId(String rollId) throws AppException.DatabaseException {
        String sql = "SELECT * FROM students WHERE LOWER(roll_id) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rollId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Failed to fetch student by Roll ID: " + e.getMessage(), e);
        }
    }

    public List<Student> search(String query) throws AppException.DatabaseException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT * FROM students WHERE LOWER(roll_id) LIKE ? OR LOWER(name) LIKE ? ORDER BY id DESC";
        String like = "%" + query.trim().toLowerCase() + "%";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Search query failed: " + e.getMessage(), e);
        }
    }

    public Student add(Student s) throws AppException {
        s.validate();
        String sql = """
            INSERT INTO students
            (roll_id, name, sub1, sub2, sub3, sub4, sub5, att1, att2, att3, att4, att5)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindStudent(ps, s);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) s.id = keys.getInt(1);
            }
            return s;
        } catch (SQLException e) {
            if (isDuplicateError(e)) {
                throw new AppException.DatabaseException("Roll ID '" + s.rollId + "' already exists.", 409);
            }
            throw new AppException.DatabaseException("Failed to add student: " + e.getMessage(), e);
        }
    }

    public Student update(int id, Student s) throws AppException {
        s.validate();
        String sql = """
            UPDATE students SET
            roll_id=?, name=?, sub1=?, sub2=?, sub3=?, sub4=?, sub5=?,
            att1=?, att2=?, att3=?, att4=?, att5=?
            WHERE id=?
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindStudent(ps, s);
            ps.setInt(13, id);
            int affected = ps.executeUpdate();
            if (affected == 0) throw new AppException.DatabaseException("Student record not found (ID: " + id + ").", 404);
            s.id = id;
            return s;
        } catch (SQLException e) {
            if (isDuplicateError(e)) {
                throw new AppException.DatabaseException("Roll ID '" + s.rollId + "' is already used by another student.", 409);
            }
            throw new AppException.DatabaseException("Failed to update student: " + e.getMessage(), e);
        }
    }

    private boolean isDuplicateError(SQLException e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("unique") || msg.contains("duplicate key") || "23505".equals(e.getSQLState());
    }

    public void delete(int id) throws AppException.DatabaseException {
        String sql = "DELETE FROM students WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int affected = ps.executeUpdate();
            if (affected == 0) throw new AppException.DatabaseException("Student record not found (ID: " + id + ").", 404);
        } catch (SQLException e) {
            throw new AppException.DatabaseException("Failed to delete student: " + e.getMessage(), e);
        }
    }

    private void bindStudent(PreparedStatement ps, Student s) throws SQLException {
        ps.setString(1, s.rollId);
        ps.setString(2, s.name);
        ps.setInt(3, s.sub1); ps.setInt(4, s.sub2); ps.setInt(5, s.sub3); ps.setInt(6, s.sub4); ps.setInt(7, s.sub5);
        ps.setInt(8, s.att1); ps.setInt(9, s.att2); ps.setInt(10, s.att3); ps.setInt(11, s.att4); ps.setInt(12, s.att5);
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        return new Student(
            rs.getInt("id"), rs.getString("roll_id"), rs.getString("name"),
            rs.getInt("sub1"), rs.getInt("sub2"), rs.getInt("sub3"), rs.getInt("sub4"), rs.getInt("sub5"),
            rs.getInt("att1"), rs.getInt("att2"), rs.getInt("att3"), rs.getInt("att4"), rs.getInt("att5")
        );
    }
}
