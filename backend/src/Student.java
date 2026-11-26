public class Student {
    public int id;
    public String rollId;
    public String name;
    public int sub1, sub2, sub3, sub4, sub5; // 5 Subject Marks (0-100)
    public int att1, att2, att3, att4, att5; // 5 Subject Attendance % (0-100)

    public Student(int id, String rollId, String name,
                   int sub1, int sub2, int sub3, int sub4, int sub5,
                   int att1, int att2, int att3, int att4, int att5) {
        this.id = id;
        this.rollId = rollId != null ? rollId.trim() : "";
        this.name = name != null ? name.trim() : "";
        this.sub1 = sub1; this.sub2 = sub2; this.sub3 = sub3; this.sub4 = sub4; this.sub5 = sub5;
        this.att1 = att1; this.att2 = att2; this.att3 = att3; this.att4 = att4; this.att5 = att5;
    }

    public void validate() throws AppException.InvalidInputException {
        if (rollId.isEmpty()) throw new AppException.InvalidInputException("Roll ID is required.");
        if (name.isEmpty()) throw new AppException.InvalidInputException("Student Name is required.");
        int[] marks = {sub1, sub2, sub3, sub4, sub5};
        int[] atts = {att1, att2, att3, att4, att5};
        for (int i = 0; i < 5; i++) {
            if (marks[i] < 0 || marks[i] > 100)
                throw new AppException.InvalidInputException("Subject " + (i + 1) + " marks must be between 0 and 100.");
            if (atts[i] < 0 || atts[i] > 100)
                throw new AppException.InvalidInputException("Subject " + (i + 1) + " attendance must be between 0 and 100%.");
        }
    }

    public double getPercentage() {
        return (sub1 + sub2 + sub3 + sub4 + sub5) / 5.0;
    }

    public double getAvgAttendance() {
        return (att1 + att2 + att3 + att4 + att5) / 5.0;
    }

    public String toJson() {
        return String.format(
            "{\"id\":%d,\"rollId\":\"%s\",\"name\":\"%s\"," +
            "\"sub1\":%d,\"sub2\":%d,\"sub3\":%d,\"sub4\":%d,\"sub5\":%d," +
            "\"att1\":%d,\"att2\":%d,\"att3\":%d,\"att4\":%d,\"att5\":%d," +
            "\"percentage\":%.2f,\"avgAttendance\":%.2f}",
            id, esc(rollId), esc(name),
            sub1, sub2, sub3, sub4, sub5,
            att1, att2, att3, att4, att5,
            getPercentage(), getAvgAttendance()
        );
    }

    public String toCsvRow() {
        return String.format("%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
            csvEsc(rollId), csvEsc(name),
            sub1, sub2, sub3, sub4, sub5,
            att1, att2, att3, att4, att5);
    }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String csvEsc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
