(() => {
  const SUBJECTS = [
    { key: 1, name: "Mathematics", short: "Math" },
    { key: 2, name: "Java Programming", short: "Java" },
    { key: 3, name: "DBMS", short: "DBMS" },
    { key: 4, name: "Computer Networks", short: "CN" },
    { key: 5, name: "Operating Systems", short: "OS" }
  ];

  const host = window.location.hostname || "localhost";
  const isLocal = !host || host === "localhost" || host === "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.");
  const defaultApi = isLocal ? `http://${host}:8080/api` : "https://java-jdbc-lqjd.onrender.com/api";

  const state = {
    apiBase: localStorage.getItem("edu_api_base") || defaultApi,
    token: sessionStorage.getItem("edu_token") || "",
    role: sessionStorage.getItem("edu_role") || "",
    username: sessionStorage.getItem("edu_username") || "",
    rollId: sessionStorage.getItem("edu_rollId") || "",
    selectedRole: "student",
    students: [],
    myRecord: null
  };

  const $ = (id) => document.getElementById(id);

  // Build 5 subject input rows inside Admin Modal
  $("subjectInputs").innerHTML = SUBJECTS.map((s) => `
    <div class="subject-row">
      <label>${s.key}. ${s.name}</label>
      <input type="number" id="fSub${s.key}" min="0" max="100" placeholder="Marks (0-100)" required />
      <input type="number" id="fAtt${s.key}" min="0" max="100" placeholder="Att % (0-100)" required />
    </div>
  `).join("");

  // API Settings
  $("apiBaseInput").value = state.apiBase;
  $("apiBaseInput").addEventListener("change", (e) => {
    const val = e.target.value.trim().replace(/\/+$/, "");
    if (val) {
      state.apiBase = val;
      localStorage.setItem("edu_api_base", val);
      showToast("API URL updated", "success");
    }
  });

  // Role Tab Switching
  document.querySelectorAll(".role-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".role-tab").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      state.selectedRole = btn.dataset.role;
      $("loginError").classList.add("hidden");
      const isAdmin = state.selectedRole === "admin";
      $("usernameLabel").textContent = isAdmin ? "Admin Username" : "Student Roll ID";
      $("username").placeholder = isAdmin ? "admin" : "e.g. CS101";
      $("demoHint").innerHTML = isAdmin
        ? "<strong>Admin Trial:</strong> Username <code>admin</code> &nbsp;|&nbsp; Password: <code>admin</code>"
        : "<strong>Student Trial:</strong> Roll ID <code>CS101</code> – <code>CS105</code> &nbsp;|&nbsp; Password: <code>1234</code>";
    });
  });

  // API Helper
  async function apiFetch(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (state.token) headers["Authorization"] = `Bearer ${state.token}`;
    if (options.body && !headers["Content-Type"]) headers["Content-Type"] = "application/json";

    let res;
    try {
      res = await fetch(`${state.apiBase}${path}`, { ...options, headers });
    } catch {
      throw new Error(`Cannot connect to Java JDBC server at ${state.apiBase}. Please run start-backend.bat first.`);
    }

    const ct = res.headers.get("content-type") || "";
    const data = ct.includes("application/json") ? await res.json() : await res.text();
    if (!res.ok) throw new Error((data && data.error) || `Request failed (${res.status})`);
    return data;
  }

  // Login
  $("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    $("loginError").classList.add("hidden");
    try {
      const data = await apiFetch("/login", {
        method: "POST",
        body: JSON.stringify({
          username: $("username").value.trim(),
          password: $("password").value,
          role: state.selectedRole
        })
      });
      state.token = data.token;
      state.role = data.role;
      state.username = data.username;
      state.rollId = data.rollId || "";
      sessionStorage.setItem("edu_token", state.token);
      sessionStorage.setItem("edu_role", state.role);
      sessionStorage.setItem("edu_username", state.username);
      sessionStorage.setItem("edu_rollId", state.rollId);
      $("loginForm").reset();
      showDashboard();
      showToast(`Signed in as ${state.role.toUpperCase()}`, "success");
    } catch (err) {
      $("loginError").textContent = err.message;
      $("loginError").classList.remove("hidden");
      showToast(err.message, "error");
    }
  });

  // Logout
  $("logoutBtn").addEventListener("click", async () => {
    try { await apiFetch("/logout", { method: "POST" }); } catch {}
    sessionStorage.clear();
    state.token = ""; state.role = ""; state.username = ""; state.rollId = ""; state.myRecord = null;
    $("dashboardView").classList.add("hidden");
    $("loginView").classList.remove("hidden");
    showToast("Logged out", "success");
  });

  // Dashboard
  async function showDashboard() {
    $("loginView").classList.add("hidden");
    $("dashboardView").classList.remove("hidden");

    const isAdmin = state.role === "admin";
    $("roleBadge").textContent = isAdmin ? "Admin" : "Student";
    $("roleBadge").className = `badge ${isAdmin ? "admin" : ""}`;
    $("userDisplay").textContent = isAdmin ? "admin" : `${state.username} (${state.rollId})`;

    $("adminSection").classList.toggle("hidden", !isAdmin);
    $("studentSection").classList.toggle("hidden", isAdmin);

    await loadStudents();
  }

  async function loadStudents(query = "") {
    try {
      const qs = query ? `?q=${encodeURIComponent(query)}` : "";
      state.students = await apiFetch(`/students${qs}`);
      if (state.role === "student") {
        state.myRecord = state.students[0] || null;
        renderStudentPortal(state.myRecord, $("studentSubjectSearch").value.trim());
      } else {
        renderAdminStats(state.students);
        renderAdminTable(state.students);
      }
    } catch (err) {
      showToast(err.message, "error");
    }
  }

  // --- STUDENT PORTAL (Tabular Marks & Attendance) ---
  function renderStudentPortal(s, filter = "") {
    if (!s) return;
    const totalMarks = s.sub1 + s.sub2 + s.sub3 + s.sub4 + s.sub5;
    $("studentBanner").innerHTML = `
      <h3>${escHtml(s.name)} — Academic &amp; Attendance Report</h3>
      <div class="my-record-grid">
        <div><span>Roll ID</span><strong>${escHtml(s.rollId)}</strong></div>
        <div><span>Total Marks</span><strong>${totalMarks} / 500</strong></div>
        <div><span>Overall Percentage</span><strong>${Number(s.percentage).toFixed(2)}%</strong></div>
        <div><span>Average Attendance</span><strong>${Number(s.avgAttendance).toFixed(1)}%</strong></div>
      </div>
    `;

    const q = filter.toLowerCase();
    const rows = SUBJECTS.filter((sub) => !q || sub.name.toLowerCase().includes(q));

    $("studentReportBody").innerHTML = rows.map((sub) => {
      const m = s[`sub${sub.key}`];
      const a = s[`att${sub.key}`];
      const grade = getGrade(m);
      const attClass = a >= 75 ? "pill-good" : "pill-bad";
      const attStatus = a >= 75 ? "Regular" : "Low Attendance";
      return `
        <tr>
          <td data-label="#">${sub.key}</td>
          <td data-label="Subject"><strong>${sub.name}</strong></td>
          <td data-label="Marks"><strong>${m}</strong> / 100</td>
          <td data-label="Grade"><span class="pill ${m >= 60 ? "pill-good" : m >= 40 ? "pill-warn" : "pill-bad"}">${grade}</span></td>
          <td data-label="Attendance">${a}%</td>
          <td data-label="Status"><span class="pill ${attClass}">${attStatus}</span></td>
        </tr>
      `;
    }).join("");

    $("studentReportFoot").innerHTML = `
      <tr>
        <td data-label="Summary" colspan="2">Overall Total / Average</td>
        <td data-label="Total Marks">${totalMarks} / 500</td>
        <td data-label="Percentage">${Number(s.percentage).toFixed(2)}%</td>
        <td data-label="Avg Attendance">${Number(s.avgAttendance).toFixed(1)}%</td>
        <td data-label="Overall Status">${s.avgAttendance >= 75 ? "Eligible" : "Shortage"}</td>
      </tr>
    `;
  }

  $("studentSubjectSearch").addEventListener("input", (e) => {
    renderStudentPortal(state.myRecord, e.target.value.trim());
  });

  // --- ADMIN PORTAL ---
  function renderAdminStats(list) {
    $("statTotal").textContent = list.length;
    const avgM = list.length ? list.reduce((acc, s) => acc + Number(s.percentage), 0) / list.length : 0;
    const avgA = list.length ? list.reduce((acc, s) => acc + Number(s.avgAttendance), 0) / list.length : 0;
    $("statAvgMarks").textContent = `${avgM.toFixed(1)}%`;
    $("statAvgAtt").textContent = `${avgA.toFixed(1)}%`;
  }

  function renderAdminTable(list) {
    $("emptyState").classList.toggle("hidden", list.length > 0);
    $("recordsBody").innerHTML = list.map((s) => `
      <tr>
        <td data-label="Roll ID"><strong>${escHtml(s.rollId)}</strong></td>
        <td data-label="Name">${escHtml(s.name)}</td>
        <td data-label="Subject Marks">
          <div class="marks-chips">
            ${SUBJECTS.map((sub) => `<span class="chip">${sub.short}: ${s[`sub${sub.key}`]}</span>`).join("")}
          </div>
        </td>
        <td data-label="Overall %">
          <span class="pill ${s.percentage >= 60 ? "pill-good" : s.percentage >= 40 ? "pill-warn" : "pill-bad"}">
            ${Number(s.percentage).toFixed(1)}%
          </span>
        </td>
        <td data-label="Avg Attendance">
          <span class="pill ${s.avgAttendance >= 75 ? "pill-good" : "pill-bad"}">
            ${Number(s.avgAttendance).toFixed(1)}%
          </span>
        </td>
        <td data-label="Actions">
          <div class="action-group">
            <button class="btn btn-secondary btn-sm" onclick="window.editStudent(${s.id})">Edit</button>
            <button class="btn btn-danger btn-sm" onclick="window.deleteStudent(${s.id}, '${escHtml(s.name)}')">Delete</button>
          </div>
        </td>
      </tr>
    `).join("");
  }

  // Admin Search
  let searchTimer;
  $("searchInput").addEventListener("input", (e) => {
    const val = e.target.value.trim();
    $("clearSearchBtn").classList.toggle("hidden", !val);
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => loadStudents(val), 200);
  });
  $("clearSearchBtn").addEventListener("click", () => {
    $("searchInput").value = "";
    $("clearSearchBtn").classList.add("hidden");
    loadStudents();
  });

  // Modal Open/Close
  function openModal(s = null) {
    $("modalTitle").textContent = s ? `Update Student (${s.rollId})` : "Add Student Record";
    $("studentId").value = s ? s.id : "";
    $("fRollId").value = s ? s.rollId : "";
    $("fName").value = s ? s.name : "";
    SUBJECTS.forEach((sub) => {
      $(`fSub${sub.key}`).value = s ? s[`sub${sub.key}`] : "";
      $(`fAtt${sub.key}`).value = s ? s[`att${sub.key}`] : "";
    });
    $("studentModal").classList.remove("hidden");
  }
  function closeModal() { $("studentModal").classList.add("hidden"); }

  $("addBtn").addEventListener("click", () => openModal());
  $("closeModalBtn").addEventListener("click", closeModal);
  $("cancelModalBtn").addEventListener("click", closeModal);

  $("studentForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    const id = $("studentId").value;
    const payload = {
      rollId: $("fRollId").value.trim(),
      name: $("fName").value.trim()
    };
    SUBJECTS.forEach((sub) => {
      payload[`sub${sub.key}`] = Number($(`fSub${sub.key}`).value);
      payload[`att${sub.key}`] = Number($(`fAtt${sub.key}`).value);
    });
    try {
      await apiFetch(id ? `/students/${id}` : "/students", {
        method: id ? "PUT" : "POST",
        body: JSON.stringify(payload)
      });
      closeModal();
      await loadStudents($("searchInput").value.trim());
      showToast(id ? "Student updated successfully" : "Student added successfully", "success");
    } catch (err) {
      showToast(err.message, "error");
    }
  });

  window.editStudent = (id) => {
    const s = state.students.find((x) => x.id === id);
    if (s) openModal(s);
  };

  window.deleteStudent = async (id, name) => {
    if (!confirm(`Delete "${name}"? They will no longer be able to log in.`)) return;
    try {
      await apiFetch(`/students/${id}`, { method: "DELETE" });
      await loadStudents($("searchInput").value.trim());
      showToast("Student record deleted", "success");
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  // View SQLite DB Modal (Admin)
  $("viewDbBtn").addEventListener("click", async () => {
    try {
      const db = await apiFetch("/db-info");
      $("dbMeta").innerHTML = `<strong>Engine:</strong> ${escHtml(db.engine)} &nbsp;|&nbsp; <strong>File:</strong> <code>${escHtml(db.file)}</code> &nbsp;|&nbsp; <strong>Table:</strong> <code>${escHtml(db.table)}</code> &nbsp;|&nbsp; <strong>Rows:</strong> ${db.rowCount}`;
      $("dbRowsBody").innerHTML = db.rows.map((r) => `
        <tr>
          <td>${r.id}</td><td><strong>${escHtml(r.rollId)}</strong></td><td>${escHtml(r.name)}</td>
          <td>${r.sub1}</td><td>${r.sub2}</td><td>${r.sub3}</td><td>${r.sub4}</td><td>${r.sub5}</td>
          <td>${r.att1}%</td><td>${r.att2}%</td><td>${r.att3}%</td><td>${r.att4}%</td><td>${r.att5}%</td>
        </tr>
      `).join("");
      $("dbModal").classList.remove("hidden");
    } catch (err) {
      showToast(err.message, "error");
    }
  });
  $("closeDbModalBtn").addEventListener("click", () => $("dbModal").classList.add("hidden"));

  // CSV Export (Admin & Student)
  async function exportCsv() {
    try {
      const csv = await apiFetch("/export");
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = state.role === "student" ? `${state.rollId}_report.csv` : "students.csv";
      a.click();
      URL.revokeObjectURL(url);
      showToast("CSV exported", "success");
    } catch (err) {
      showToast(err.message, "error");
    }
  }
  $("exportBtn").addEventListener("click", exportCsv);
  $("studentExportBtn").addEventListener("click", exportCsv);

  // CSV Import (Admin)
  $("csvFileInput").addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      const res = await apiFetch("/import", {
        method: "POST",
        headers: { "Content-Type": "text/csv" },
        body: await file.text()
      });
      await loadStudents($("searchInput").value.trim());
      const msg = `Imported ${res.imported}, updated ${res.updated}` +
        (res.errors.length ? ` (${res.errors.length} rows skipped)` : "");
      showToast(msg, res.errors.length ? "error" : "success");
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      e.target.value = "";
    }
  });

  // Helpers
  function getGrade(m) {
    if (m >= 90) return "A+";
    if (m >= 80) return "A";
    if (m >= 70) return "B";
    if (m >= 60) return "C";
    if (m >= 40) return "D";
    return "F";
  }

  function escHtml(str) {
    return String(str ?? "").replace(/[&<>"']/g, (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
    );
  }

  let toastTimer;
  function showToast(message, type = "") {
    const t = $("toast");
    t.textContent = message;
    t.className = `toast ${type}`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.add("hidden"), 3500);
  }

  if (state.token && state.role) showDashboard();
})();
