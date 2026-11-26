import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Handles CSV file import and export with Java I/O and structured exception handling.
 */
public class CsvFileHandler {
    private static final String HEADER = "rollId,name,sub1,sub2,sub3,sub4,sub5,att1,att2,att3,att4,att5";
    private static final Path BACKUP_PATH = Paths.get("data", "students_export.csv");

    public static String exportToCsv(List<Student> students) throws AppException.FileHandlingException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");
        for (Student s : students) {
            sb.append(s.toCsvRow()).append("\n");
        }
        String csv = sb.toString();

        try (BufferedWriter writer = Files.newBufferedWriter(BACKUP_PATH, StandardCharsets.UTF_8)) {
            writer.write(csv);
        } catch (IOException e) {
            throw new AppException.FileHandlingException("Failed to write CSV export file: " + e.getMessage(), e);
        }
        return csv;
    }

    public static Map<String, Object> importFromCsv(String csvContent, StudentDAO dao) throws AppException {
        if (csvContent == null || csvContent.isBlank()) {
            throw new AppException.FileHandlingException("Uploaded CSV file is empty.");
        }

        int imported = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                if (lineNo == 1 && line.toLowerCase().contains("rollid")) continue;

                try {
                    String[] c = parseCsvLine(line);
                    if (c.length < 12) {
                        throw new AppException.InvalidInputException(
                            "Expected 12 columns: rollId,name,sub1..sub5,att1..att5");
                    }
                    Student s = new Student(0, c[0], c[1],
                        Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4]),
                        Integer.parseInt(c[5]), Integer.parseInt(c[6]),
                        Integer.parseInt(c[7]), Integer.parseInt(c[8]), Integer.parseInt(c[9]),
                        Integer.parseInt(c[10]), Integer.parseInt(c[11]));

                    Student existing = dao.getByRollId(s.rollId);
                    if (existing != null) {
                        dao.update(existing.id, s);
                        updated++;
                    } else {
                        dao.add(s);
                        imported++;
                    }
                } catch (NumberFormatException e) {
                    errors.add("Line " + lineNo + ": Marks and attendance must be whole numbers.");
                } catch (AppException e) {
                    errors.add("Line " + lineNo + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new AppException.FileHandlingException("Error reading CSV data: " + e.getMessage(), e);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("imported", imported);
        summary.put("updated", updated);
        summary.put("errors", errors);
        return summary;
    }

    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (ch == ',' && !inQuotes) { fields.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }
        fields.add(cur.toString().trim());
        return fields.toArray(new String[0]);
    }
}
