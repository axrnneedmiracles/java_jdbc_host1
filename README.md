# Student Record Management System (Java JDBC & SQLite)

A compact, professional Java project with a **separate frontend website** (`frontend/`) and **core Java JDBC + SQLite backend** (`backend/`), responsive on both **phone and desktop**.

## Database Schema (`students` table in SQLite)
- `roll_id` (Unique Roll ID, e.g. `CS101`)
- `name` (Student Name)
- `sub1` – `sub5` (5 Subject Marks, 0–100: Mathematics, Java Programming, DBMS, Computer Networks, Operating Systems)
- `att1` – `att5` (5 Subject Attendance %, 0–100)

## Login Credentials
- **Admin**: Username `admin` | Password `admin` (Full Add, Search, Update, Delete, CSV Import/Export)
- **Student**: Roll ID (e.g. `CS101` – `CS105`) | Password `1234` (Views ONLY their own Marks & Attendance in tabular form)
- **Deleted Student Login**: Shows `"Record not found. Kindly contact admin."`

## API Setup (No External API Key Needed)
No third-party API key is required. Your Java `ApiServer` hosts a local REST API on port `8080` and connects directly to SQLite (`backend/data/students.db`) via JDBC.

## How to Run
1. Double-click `start-backend.bat` (compiles Java and starts JDBC API on port `8080`).
2. Double-click `start-frontend.bat` (starts website on port `3000`).
3. **Desktop**: Open `http://localhost:3000`
4. **Phone (same Wi-Fi)**: Run `ipconfig` on PC, get IPv4 (e.g. `192.168.1.15`), and open `http://192.168.1.15:3000` on your phone.
