#!/usr/bin/env python3
"""
Three real-world-named colleges, end to end: each college gets a TPO, a coordinator, students, a company, a drive with
rounds, an interview, an offer, an announcement, chat and documents; each student uses the student portal, takes a mock
test and one AI interview per college (Groq, three running at the same time). The platform admin then checks and
manages all of it. Data must stay inside its own college.

  python3 scripts/e2e_three_colleges_test.py [http://localhost:8080]
Needs the admin account admin@proveyu.local / Admin@1234 and at least one published mock test.
"""
import os
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(__file__))
import smoke_test as st  # noqa: E402
from smoke_test import check  # noqa: E402


def call(*a, **kw):
    """smoke_test.call, waiting out the per-IP auth rate limit (60/min) instead of failing on 429."""
    for _ in range(6):
        r = st.call(*a, **kw)
        if r[0] != 429:
            return r
        time.sleep(15)
    return r

RUN = st.RUN
YEAR = time.localtime().tm_year + 1
ADMIN = ("admin@proveyu.local", "Admin@1234")
PDF = b"%PDF-1.4\n%three colleges e2e\n"
IV = "/api/v1/college/student/interview"
iso = lambda t: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t))

COLLEGES = [
    dict(key="gu", name="Galgotias University", university="Galgotias University", city="Greater Noida", state="Uttar Pradesh",
         tpo="Dr. Anil Sharma", company="Infosys", role="Systems Engineer", skills=["Java", "SQL"],
         students=[("Aman", "Verma", "CSE", 8.6), ("Riya", "Singh", "CSE", 8.1), ("Kunal", "Yadav", "IT", 7.4), ("Neha", "Tyagi", "IT", 6.2)]),
    dict(key="niet", name="Noida Institute of Engineering and Technology", university="Dr. A.P.J. Abdul Kalam Technical University",
         city="Greater Noida", state="Uttar Pradesh", tpo="Prof. Ritu Agarwal", company="HCLTech", role="Software Engineer",
         skills=["Python", "SQL"], students=[("Ishita", "Gupta", "CSE", 8.9), ("Harsh", "Chauhan", "CSE", 7.9), ("Tanvi", "Saxena", "IT", 7.2),
                                             ("Rohit", "Bhati", "IT", 5.9)]),
    dict(key="ltce", name="Lokmanya Tilak College of Engineering", university="University of Mumbai", city="Navi Mumbai", state="Maharashtra",
         tpo="Dr. Sunil Patil", company="Larsen & Toubro Infotech", role="Graduate Engineer Trainee", skills=["Java", "Spring Boot"],
         students=[("Omkar", "Jadhav", "CSE", 8.4), ("Sneha", "Pawar", "CSE", 7.7), ("Pratik", "More", "IT", 7.1), ("Aditi", "Shinde", "IT", 6.0)]),
]
JD = ("We are hiring a {role} at {company}. You will build and maintain backend services and REST APIs, write clean {s0} code, "
      "design {s1} database tables and queries, write unit tests, and work with the team in code reviews. Freshers with good "
      "fundamentals in data structures, OOP and databases are welcome.")
ANSWERS = [
    "Hello, I am a final year engineering student. I have built a placement tracker web app with a REST backend and a MySQL database, "
    "and I enjoy solving data structure problems.",
    "A REST API exposes resources over HTTP. GET reads, POST creates, PUT updates and DELETE removes. It is stateless, so every request "
    "carries what the server needs, usually a token for authentication, and responses use status codes like 200, 201, 404 and 500.",
    "An index is a sorted structure on one or more columns, usually a B-tree, so the database can find rows without a full table scan. "
    "It makes reads faster but slows down inserts and updates and uses extra space.",
    "In object oriented programming, encapsulation hides data behind methods, inheritance reuses a parent class, and polymorphism lets "
    "one interface have many implementations, for example a Shape interface with Circle and Square.",
    "I would first reproduce the bug, read the logs, add a failing unit test, then fix the code and check that all tests pass.",
    "I am interested in this role because I like backend work, and I want to grow by working on real products with an experienced team.",
]


def data(resp):
    return (resp[1] or {}).get("data") if isinstance(resp[1], dict) else None


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def setup_college(c):
    """TPO registers the college and runs the whole placement flow. Returns everything later steps need."""
    k = c["key"]
    print(f"\n== {c['name']}")
    domain = f"{k}{RUN}.edu.in"
    tpo_email = f"tpo@{domain}"
    s, r = call("POST", "/api/v1/college/auth/register", {
        "collegeName": f"{c['name']} {RUN}", "aicteCode": f"AICTE-{k.upper()}-{RUN}", "university": c["university"], "city": c["city"],
        "state": c["state"], "emailDomain": domain, "tpoName": c["tpo"], "tpoPhone": "9" + str(int(time.time() * 1000))[-9:],
        "tpoEmail": tpo_email, "password": "Tpo@12345", "acceptTerms": True})
    check(f"[{k}] college registered", s == 201, r)
    check(f"[{k}] login blocked until e-mail verified", login(tpo_email, "Tpo@12345") is None)
    call("POST", "/api/v1/auth/verify-email", {"token": r["data"]["devVerificationToken"]})
    s, r = call("POST", "/api/v1/auth/login", {"email": tpo_email, "password": "Tpo@12345"})
    check(f"[{k}] TPO logs in as COLLEGE_ADMIN", s == 200 and r["data"]["role"] == "COLLEGE_ADMIN", r)
    tpo = r["data"]["token"]
    me = data(call("GET", "/api/v1/college/me", token=tpo))
    college_id = me["collegeId"]

    br = {code: data(call("POST", "/api/v1/college/settings/branches", {"code": code, "name": name}, tpo))["id"]
          for code, name in [("CSE", "Computer Science & Engineering"), ("IT", "Information Technology")]}
    check(f"[{k}] 2 branches + policy saved", all(br.values()) and call("PUT", "/api/v1/college/settings/policy", {
        "policyMode": "TIER_UPGRADE", "maxApplications": 10, "maxOffers": 2, "upgradeMinHikePercent": 20, "blockAfterAccept": True}, tpo)[0] == 200)
    inv = data(call("POST", "/api/v1/college/team/invites", {"email": f"coord@{domain}", "fullName": f"Coordinator {k.upper()}",
                                                           "role": "COLLEGE_COORDINATOR", "branchIds": [br["CSE"]]}, tpo))
    s, a = call("POST", "/api/v1/college/auth/accept-invite", {"token": inv["devInviteToken"], "password": "Team@12345"})
    check(f"[{k}] coordinator (CSE) accepts invite", s == 200 and a["data"]["role"] == "COLLEGE_COORDINATOR", a)
    coord = a["data"]["token"]

    students = []
    for i, (fn, ln, code, cgpa) in enumerate(c["students"], 1):
        roll = f"{k.upper()}{YEAR % 100}{code}{i:02d}"
        s, r = call("POST", "/api/v1/college/students", {
            "rollNumber": roll, "firstName": fn, "lastName": ln, "email": f"{fn.lower()}.{ln.lower()}@{domain}", "branchId": br[code],
            "batchYear": YEAR, "degree": "B.Tech", "cgpa": cgpa, "tenthPercent": 85, "twelfthPercent": 80, "activeBacklogs": 0,
            "skills": c["skills"]}, tpo)
        students.append(dict(r["data"], first=fn, cgpa=cgpa, branch=code))
    check(f"[{k}] {len(students)} students added", len(students) == len(c["students"]) and all(x.get("id") for x in students))
    for x in students:
        s, r = call("POST", "/api/v1/college/auth/student-signup", {"email": x["email"], "rollNumber": x["rollNumber"]})
        call("POST", "/api/v1/auth/reset-password", {"email": x["email"], "resetToken": (data((s, r)) or {}).get("devOtp"), "newPassword": "Student@123"})
        x["token"] = login(x["email"], "Student@123")
    check(f"[{k}] all students activated and logged in", all(x["token"] for x in students))

    comp = data(call("POST", "/api/v1/college/companies", {"name": c["company"], "industry": "IT Services", "hqLocation": c["city"], "mouStatus": "SIGNED"}, tpo))
    call("POST", f"/api/v1/college/companies/{comp['id']}/contacts", {"name": "HR Lead", "designation": "Talent Acquisition",
                                                                    "email": f"hr@{k}{RUN}-corp.example", "primaryContact": True}, tpo)
    jd = JD.format(role=c["role"], company=c["company"], s0=c["skills"][0], s1=c["skills"][1])
    drive = data(call("POST", "/api/v1/college/drives", {
        "companyId": comp["id"], "title": f"{c['company']} {c['role']} {YEAR}", "roleTitle": c["role"], "driveType": "ON_CAMPUS", "ctcLpa": 6.5,
        "mode": "OFFLINE", "location": c["city"], "jobDescription": jd, "skills": c["skills"], "registrationDeadline": iso(time.time() + 7 * 86400),
        "eligibility": {"minCgpa": 7, "maxActiveBacklogs": 0, "batchYears": [YEAR], "branchIds": list(br.values())}}, tpo))["id"]
    r1 = data(call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "Online Aptitude", "roundType": "APTITUDE"}, tpo))["id"]
    r2 = data(call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "Technical Interview", "roundType": "TECHNICAL"}, tpo))["id"]
    check(f"[{k}] draft drive hidden from students", all(d["id"] != drive for d in data(call("GET", "/api/v1/college/student/drives", token=students[0]["token"]))))
    check(f"[{k}] drive published", call("POST", f"/api/v1/college/drives/{drive}/status", {"status": "PUBLISHED"}, tpo)[0] == 200)
    prev = data(call("GET", f"/api/v1/college/drives/{drive}/eligibility", token=tpo))
    check(f"[{k}] eligibility: 3 eligible, 1 below CGPA 7", prev["eligible"] == 3 and prev["ineligible"] == 1, prev)

    # Student portal
    for x in students:
        p = data(call("GET", "/api/v1/college/student/profile", token=x["token"]))
        x["userId"] = p["userId"]
        x["profile_ok"] = p["rollNumber"] == x["rollNumber"] and float(p["cgpa"]) == x["cgpa"] and RUN in p["collegeName"]
    check(f"[{k}] every student profile matches the college record (roll, CGPA, college)", all(x["profile_ok"] for x in students))
    low = students[-1]
    check(f"[{k}] low-CGPA student cannot apply (422)", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=low["token"])[0] == 422)
    for x in students[:3]:
        x["app"] = data(call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=x["token"]))["id"]
    check(f"[{k}] 3 eligible students applied", all(x.get("app") for x in students[:3]))
    check(f"[{k}] double apply rejected (409)", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=students[0]["token"])[0] == 409)
    apps = {a["id"]: a for a in data(call("GET", f"/api/v1/college/drives/{drive}/applications", token=tpo))["content"]}
    check(f"[{k}] TPO sees the same 3 applications", set(apps) == {x["app"] for x in students[:3]}, list(apps))

    # Rounds: student 0 and 1 pass aptitude, 2 fails; student 0 clears the interview and gets the offer
    star, second, third = students[0], students[1], students[2]
    call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [x["app"] for x in students[:3]], "status": "SHORTLISTED"}, tpo)
    call("POST", f"/api/v1/college/rounds/{r1}/start", token=tpo)
    call("PUT", f"/api/v1/college/rounds/{r1}/results", {"results": [{"applicationId": star["app"], "result": "PASS", "score": 82},
                                                                   {"applicationId": second["app"], "result": "PASS", "score": 74},
                                                                   {"applicationId": third["app"], "result": "FAIL", "score": 41}]}, tpo)
    check(f"[{k}] aptitude round completed", call("POST", f"/api/v1/college/rounds/{r1}/complete", token=tpo)[0] == 200)
    call("POST", f"/api/v1/college/rounds/{r2}/start", token=tpo)
    st_ = time.time() + 2 * 86400
    check(f"[{k}] coordinator schedules the technical interview", call("POST", "/api/v1/college/interviews", {
        "driveId": drive, "roundId": r2, "applicationId": star["app"], "panelName": "Panel A", "room": "Seminar Hall",
        "startsAt": iso(st_), "endsAt": iso(st_ + 1800)}, coord)[0] == 200)
    iv = data(call("GET", "/api/v1/college/student/interviews", token=star["token"]))
    check(f"[{k}] student sees the interview (room, round)", len(iv) == 1 and iv[0]["room"] == "Seminar Hall" and iv[0]["roundName"] == "Technical Interview", iv)
    call("PUT", f"/api/v1/college/rounds/{r2}/results", {"results": [{"applicationId": star["app"], "result": "PASS"},
                                                                   {"applicationId": second["app"], "result": "FAIL"}]}, tpo)
    call("POST", f"/api/v1/college/rounds/{r2}/complete", token=tpo)
    st_apps = {x["first"]: data(call("GET", "/api/v1/college/student/applications", token=x["token"]))[0]["status"] for x in students[:3]}
    check(f"[{k}] results reach students: SELECTED / REJECTED / REJECTED", list(st_apps.values()) == ["SELECTED", "REJECTED", "REJECTED"], st_apps)

    offer = data(call("POST", "/api/v1/college/offers", {"studentId": star["id"], "driveId": drive, "companyId": comp["id"], "roleTitle": c["role"],
                                                      "ctcLpa": 6.5}, tpo))["id"]
    call("POST", f"/api/v1/college/offers/{offer}/letter", files={"file": ("offer.pdf", PDF, "application/pdf")}, token=tpo)
    s, body = call("GET", f"/api/v1/college/student/offers/{offer}/letter", token=star["token"], raw=True)
    check(f"[{k}] student downloads the offer letter", s == 200 and body == PDF)
    check(f"[{k}] another student cannot accept it (404)", call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": True}, second["token"])[0] == 404)
    check(f"[{k}] student accepts the offer", data(call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": True}, star["token"]))["status"] == "ACCEPTED")
    p = data(call("GET", "/api/v1/college/student/profile", token=star["token"]))
    rec = data(call("GET", f"/api/v1/college/students/{star['id']}", token=tpo))["student"]
    check(f"[{k}] PLACED with 6.5 LPA in both portals", p["status"] == rec["status"] == "PLACED" and float(rec["highestCtcLpa"]) == 6.5, (p["status"], rec["status"]))

    # Announcement, chat, documents
    s, r = call("POST", "/api/v1/college/announcements", {"title": f"Pre-placement talk {RUN}", "body": f"{c['company']} talk on Friday.",
                                                        "filter": {}, "sendEmail": False}, tpo)
    check(f"[{k}] announcement to all 4 students", s == 200 and r["data"]["recipientCount"] == 4, r)
    inbox = data(call("GET", "/api/v1/college/student/announcements", token=low["token"]))["content"]
    check(f"[{k}] student inbox has it", any(a["title"] == f"Pre-placement talk {RUN}" for a in inbox))
    tpo_uid = me["userId"]
    call("POST", "/api/v1/chat/messages", {"receiverId": star["userId"], "message": f"Congrats {star['first']}!"}, tpo)
    call("POST", "/api/v1/chat/messages", {"receiverId": tpo_uid, "message": "Thank you sir"}, star["token"])
    h = data(call("GET", f"/api/v1/chat/messages/{tpo_uid}", token=star["token"]))
    msgs = [m["message"] for m in (h["content"] if isinstance(h, dict) else h)]
    check(f"[{k}] TPO <-> student chat both ways", f"Congrats {star['first']}!" in msgs and "Thank you sir" in msgs, msgs)
    s, r = call("POST", "/api/v1/college/student/documents?docType=RESUME&label=Resume", files={"file": ("cv.pdf", PDF, "application/pdf")}, token=star["token"])
    doc = r["data"]["id"]
    check(f"[{k}] coordinator verifies the resume", call("PATCH", f"/api/v1/college/students/documents/{doc}/verify", {"status": "VERIFIED"}, coord)[0] == 200)
    check(f"[{k}] student sees VERIFIED", data(call("GET", "/api/v1/college/student/documents", token=star["token"]))[0]["verificationStatus"] == "VERIFIED")

    # Reports
    kp = data(call("GET", "/api/v1/college/analytics/dashboard", token=tpo))["kpis"]
    check(f"[{k}] college dashboard: 4 students, 1 placed", kp["totalStudents"] == 4 and kp["placedStudents"] == 1, kp)
    s, body = call("GET", "/api/v1/college/students/export?format=xlsx", token=tpo, raw=True)
    check(f"[{k}] students Excel export", s == 200 and body[:2] == b"PK")
    s, body = call("GET", "/api/v1/college/reports/placement-summary?format=pdf", token=tpo, raw=True)
    check(f"[{k}] placement summary PDF", s == 200 and body[:4] == b"%PDF")
    return dict(c=c, tpo=tpo, tpo_email=tpo_email, coord=coord, college_id=college_id, drive=drive, students=students, domain=domain)


def mock_test(col):
    k, x = col["c"]["key"], col["students"][1]
    tests = data(call("GET", "/api/v1/college/student/prep/tests", token=x["token"])) or []
    t = next((t for t in tests if not t.get("locked")), None)
    if not t:
        check(f"[{k}] a mock test is open for the student", False, tests)
        return
    s, r = call("POST", f"/api/v1/college/student/prep/tests/{t['id']}/start", token=x["token"])
    aid = r["data"]["attemptId"]
    s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/submit", {}, x["token"])
    check(f"[{k}] mock test started and submitted ('{t['title']}')", s == 200 and r["data"]["maxScore"] > 0, r)
    other = col["students"][0]["token"]
    check(f"[{k}] another student cannot read the result (404)", call("GET", f"/api/v1/college/student/prep/attempts/{aid}/result", token=other)[0] == 404)


def ai_interview(col, out):
    """Runs in a thread: a MIXED interview on the drive the student applied to (Groq)."""
    x = col["students"][0]
    t = x["token"]
    res = out.setdefault(col["c"]["key"], {})
    try:
        ov = data(call("GET", IV, token=t))
        res["llm_up"] = ov["services"]["llm"]
        res["free"] = ov["credits"]["freeLeft"]
        drive = next((d for d in ov["drives"] if d["driveId"] == col["drive"]), None)
        res["drive_offered"] = drive is not None
        t0 = time.time()
        s, r = call("POST", f"{IV}/sessions", {"type": "TECHNICAL", "difficulty": "MEDIUM", "driveId": col["drive"], "consent": True}, token=t)
        res["start"] = (s, (r or {}).get("errorCode"))
        v = r["data"]
        res["plan_secs"] = round(time.time() - t0, 1)
        res["company"], res["focus"], res["planned"] = v["companyName"], v["focus"], v["plannedQuestions"]
        i = 0
        while v["status"] == "IN_PROGRESS" and v.get("current"):
            s, r = call("POST", f"{IV}/sessions/{v['id']}/answers/text", {"questionId": v["current"]["id"], "text": ANSWERS[min(i, len(ANSWERS) - 1)]}, token=t)
            if s != 200:
                res["answer_error"] = r
                break
            v, i = r["data"], i + 1
        res["answered"] = i
        sid = v["id"]
        t0 = time.time()
        while True:
            rep = data(call("GET", f"{IV}/sessions/{sid}/report", token=t))
            if rep["status"] in ("COMPLETED", "FAILED") or time.time() - t0 > 900:
                break
            time.sleep(3)
        res["report_secs"] = round(time.time() - t0)
        res["report"] = {k_: rep.get(k_) for k_ in ("status", "overallScore", "recommendation", "errorMessage")}
        res["answers"] = len(rep.get("answers") or [])
        res["sid"] = sid
    except Exception as e:  # reported by the main thread
        res["error"] = repr(e)


def main():
    print(f"Three colleges E2E against {st.BASE} (run {RUN})")
    admin = login(*ADMIN)
    if not admin:
        raise SystemExit("Admin login failed")
    before = data(call("GET", "/api/v1/admin/dashboard", token=admin))

    cols = [setup_college(c) for c in COLLEGES]

    print("\n== Data isolation between colleges")
    a, b = cols[0], cols[1]
    sid_b = b["students"][0]["id"]
    check("GU TPO cannot open a NIET student (404)", call("GET", f"/api/v1/college/students/{sid_b}", token=a["tpo"])[0] in (403, 404))
    check("GU TPO's student list has only GU students", all(a["domain"] in s_["email"] for s_ in data(call("GET", "/api/v1/college/students?size=50", token=a["tpo"]))["content"]))
    check("NIET student cannot see the GU drive", all(d["id"] != a["drive"] for d in data(call("GET", "/api/v1/college/student/drives", token=b["students"][0]["token"]))))
    check("NIET student cannot apply to the GU drive", call("POST", f"/api/v1/college/student/drives/{a['drive']}/apply", token=b["students"][1]["token"])[0] in (403, 404))
    check("LTCE TPO cannot change NIET drive status", call("POST", f"/api/v1/college/drives/{b['drive']}/status", {"status": "CANCELLED"}, cols[2]["tpo"])[0] == 404)
    check("student cannot use the admin API (403)", call("GET", "/api/v1/admin/dashboard", token=a["students"][0]["token"])[0] == 403)
    check("TPO cannot use the admin API (403)", call("GET", "/api/v1/admin/users", token=a["tpo"])[0] == 403)

    print("\n== Mock tests (one student per college)")
    for col in cols:
        mock_test(col)

    print("\n== AI interviews on Groq: one student per college, all three at the same time")
    out = {}
    threads = [threading.Thread(target=ai_interview, args=(col, out)) for col in cols]
    t0 = time.time()
    [th.start() for th in threads]
    [th.join() for th in threads]
    print(f"        all three finished in {time.time() - t0:.0f}s")
    for col in cols:
        k, r = col["c"]["key"], out.get(col["c"]["key"], {})
        if r.get("error"):
            check(f"[{k}] AI interview ran", False, r["error"])
            continue
        print(f"        [{k}] plan {r['plan_secs']}s, focus {r['focus']}, answered {r['answered']}, report in {r['report_secs']}s -> {r['report']}")
        check(f"[{k}] Groq AI up, applied drive offered for practice, free interview available", r["llm_up"] and r["drive_offered"] and r["free"] >= 1, r)
        check(f"[{k}] interview uses the drive's company and JD skills", r["company"] == col["c"]["company"]
              and any(s_.lower() in " ".join(r["focus"]).lower() for s_ in col["c"]["skills"]), (r["company"], r["focus"]))
        check(f"[{k}] all {r['planned']} questions answered, report COMPLETED with a score", r["answered"] == r["planned"] == r["answers"]
              and r["report"]["status"] == "COMPLETED" and r["report"]["overallScore"] is not None, r)
    s, _ = call("GET", f"{IV}/sessions/{out.get('gu', {}).get('sid')}", token=cols[1]["students"][0]["token"])
    check("NIET student cannot open the GU student's interview (404)", s == 404, s)

    print("\n== Platform admin")
    d = data(call("GET", "/api/v1/admin/dashboard", token=admin))
    check("admin dashboard: +3 approved colleges, +12 students", d["colleges"].get("APPROVED", 0) - before["colleges"].get("APPROVED", 0) == 3
          and d["students"] - before["students"] == 12, (before["colleges"], d["colleges"], before["students"], d["students"]))
    check("admin dashboard: +3 placed, +3 interviews", d["placedStudents"] - before["placedStudents"] == 3 and d["interviews"] - before["interviews"] == 3,
          (d["placedStudents"], before["placedStudents"], d["interviews"], before["interviews"]))
    all_cols = data(call("GET", "/api/v1/admin/colleges", token=admin))
    for col in cols:
        k = col["c"]["key"]
        row = next((x for x in all_cols if x["collegeId"] == col["college_id"]), None)
        check(f"[{k}] listed for admin as APPROVED, {col['c']['city']}", row and row["approvalStatus"] == "APPROVED" and row["city"] == col["c"]["city"], row)
        det = data(call("GET", f"/api/v1/admin/colleges/{col['college_id']}", token=admin))
        check(f"[{k}] admin college detail: 4 students, 1 placed, 1 drive, 1 offer, 1 mock attempt, 1 AI interview, team of 2",
              (det["students"], det["placedStudents"], det["drives"], det["offers"], det["mockAttempts"], det["interviews"], len(det["team"])) == (4, 1, 1, 1, 1, 1, 2),
              {x_: det[x_] for x_ in ("students", "placedStudents", "drives", "offers", "mockAttempts", "interviews")} | {"team": len(det["team"])})

    gu = cols[0]
    users = data(call("GET", f"/api/v1/admin/users?q={gu['domain']}", token=admin))
    check("admin user search by college domain: TPO + coordinator + 4 students", users["total"] == 6, users["total"])
    target = gu["students"][3]
    uid = next(u["id"] for u in users["rows"] if u["email"] == target["email"])
    row = next(u for u in users["rows"] if u["email"] == target["email"])
    check("search row shows college and roll number", RUN in row["collegeName"] and row["detail"] == target["rollNumber"], row)
    check("admin blocks a student", call("POST", f"/api/v1/admin/users/{uid}/block", token=admin)[0] == 200)
    check("blocked student's old token stops working (401)", call("GET", "/api/v1/college/student/profile", token=target["token"])[0] == 401)
    check("blocked student cannot log in", login(target["email"], "Student@123") is None)
    check("admin unblocks", call("POST", f"/api/v1/admin/users/{uid}/unblock", token=admin)[0] == 200)
    target["token"] = login(target["email"], "Student@123")
    check("student logs in again after unblock", target["token"] is not None)
    check("admin sends a password reset", call("POST", f"/api/v1/admin/users/{uid}/reset-password", token=admin)[0] == 200)
    s, r = call("POST", "/api/v1/admin/interview/credits", {"studentEmail": target["email"], "count": 2}, admin)
    check("admin gives 2 AI interview credits", s == 200 and r["data"]["paidBalance"] == 2, r)
    check("student sees 2 paid interviews", data(call("GET", IV, token=target["token"]))["credits"]["paidBalance"] == 2)

    # Settings: change, see the effect, reset
    s, r = call("PUT", "/api/v1/admin/settings", {"interview.price-rupees": "49"}, admin)
    check("admin sets interview price to 49", s == 200, r)
    check("student portal shows the new price", data(call("GET", IV, token=target["token"]))["credits"]["pricePerInterview"] == 49,
          data(call("GET", IV, token=target["token"]))["credits"])
    check("invalid setting rejected (400)", call("PUT", "/api/v1/admin/settings", {"prep.max-tab-switch-warnings": "abc"}, admin)[0] == 400)
    check("price reset to default", call("DELETE", "/api/v1/admin/settings/interview.price-rupees", token=admin)[0] == 200)

    # Broadcast to one college
    n = data(call("GET", f"/api/v1/admin/broadcast/count?audience=COLLEGE&collegeId={cols[2]['college_id']}", token=admin))
    check("broadcast audience for LTCE = 6 people", list(n.values())[0] == 6, n)
    s, r = call("POST", "/api/v1/admin/broadcast", {"audience": "COLLEGE", "collegeId": cols[2]["college_id"], "title": f"Maintenance {RUN}",
                                                  "message": "ProveYu will be down for 10 minutes on Sunday 2 AM.", "sendEmail": False}, admin)
    check("broadcast sent to 6, no e-mails", s == 200 and r["data"]["recipients"] == 6 and r["data"]["emails"] == 0, r)
    notes = data(call("GET", "/api/v1/notifications", token=cols[2]["students"][0]["token"]))
    items = notes["content"] if isinstance(notes, dict) else notes
    check("LTCE student got the notification", any(f"Maintenance {RUN}" in (x.get("message") or "") for x in items), items[:2])
    notes = data(call("GET", "/api/v1/notifications", token=cols[0]["students"][0]["token"]))
    items = notes["content"] if isinstance(notes, dict) else notes
    check("GU student did not get it", not any(f"Maintenance {RUN}" in (x.get("message") or "") for x in items))

    # Suspend / reactivate
    niet = cols[1]
    check("admin suspends NIET", call("POST", f"/api/v1/admin/colleges/{niet['college_id']}/decision", {"status": "SUSPENDED", "note": "e2e"}, admin)[0] == 200)
    check("NIET /me reports SUSPENDED (UI shows the suspended page)", data(call("GET", "/api/v1/college/me", token=niet["tpo"]))["approvalStatus"] == "SUSPENDED")
    s, r = call("GET", "/api/v1/college/students", token=niet["tpo"])
    check("NIET TPO data blocked with COLLEGE_SUSPENDED (403)", s == 403 and r.get("errorCode") == "COLLEGE_SUSPENDED", r)
    s, r = call("GET", "/api/v1/college/student/drives", token=niet["students"][0]["token"])
    print(f"        NIET student while suspended: {s} {(r or {}).get('errorCode')}")
    check("GU keeps working", call("GET", "/api/v1/college/me", token=gu["tpo"])[0] == 200)
    check("admin reactivates NIET", call("POST", f"/api/v1/admin/colleges/{niet['college_id']}/decision", {"status": "APPROVED", "note": "e2e"}, admin)[0] == 200)
    check("NIET TPO works again", call("GET", "/api/v1/college/me", token=niet["tpo"])[0] == 200)

    # New admin, exports, proctoring, audit
    s, r = call("POST", "/api/v1/admin/admins", {"email": f"ops.{RUN}@proveyu.local", "fullName": "Ops Admin", "password": "Ops@12345"}, admin)
    check("admin creates another admin (201)", s == 201 and r["data"]["email"] == f"ops.{RUN}@proveyu.local" and "password" not in str(r["data"]).lower(), r)
    ops = login(f"ops.{RUN}@proveyu.local", "Ops@12345")
    check("new admin logs in and sees the dashboard", ops and call("GET", "/api/v1/admin/dashboard", token=ops)[0] == 200)
    check("a TPO cannot create admins (403)", call("POST", "/api/v1/admin/admins", {"email": f"x{RUN}@x.com", "fullName": "X", "password": "Secret@123"}, gu["tpo"])[0] == 403)
    s, body = call("GET", "/api/v1/admin/payments/export?format=xlsx", token=admin, raw=True)
    check("payments Excel export", s == 200 and body[:2] == b"PK")
    check("proctoring list loads", call("GET", "/api/v1/admin/proctoring", token=admin)[0] == 200)
    audit = data(call("GET", "/api/v1/admin/audit?size=50", token=admin))["rows"]
    acts = {a_["action"] for a_ in audit}
    want = {"USER_BLOCKED", "USER_UNBLOCKED", "CREDITS_GRANTED", "SETTINGS_CHANGED", "BROADCAST_SENT", "COLLEGE_SUSPENDED", "COLLEGE_APPROVED", "ADMIN_CREATED", "PASSWORD_RESET_SENT"}
    check("audit log has every admin action of this run", want <= acts, sorted(want - acts))
    call("POST", f"/api/v1/admin/users/{next(a_['id'] for a_ in data(call('GET', '/api/v1/admin/admins', token=admin)) if a_['email'] == f'ops.{RUN}@proveyu.local')}/block", token=admin)

    print("\nAccounts (password):")
    for col in cols:
        print(f"  {col['c']['name']}: TPO {col['tpo_email']} (Tpo@12345), coordinator coord@{col['domain']} (Team@12345), "
              f"students e.g. {col['students'][0]['email']} (Student@123)")
    print(f"\n{st.passed} passed, {st.failed} failed")
    sys.exit(1 if st.failed else 0)


if __name__ == "__main__":
    main()
