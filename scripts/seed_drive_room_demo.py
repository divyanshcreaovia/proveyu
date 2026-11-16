#!/usr/bin/env python3
"""
Prepares the "Infinite Analytics Software Engineer Intern" demo drive so the whole hiring process can be run by hand:
applicants (still APPLIED), a resume for each, three PENDING rounds, two company people with Drive Room access, and
student logins. Safe to re-run: finished steps are skipped. Needs seed_demo.py + seed_student_portal.py first.

  python3 scripts/seed_drive_room_demo.py [http://localhost:8080]
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
from seed_demo import TPO_EMAIL, TPO_PASS, STUDENT_PASS, call, ok, login, set_password  # noqa: E402
from smoke_test import call as mcall  # multipart-capable  # noqa: E402

DRIVE_TITLE = "Infinite Analytics Software Engineer Intern"
COMPANY = "Infinite Analytics"
RECRUITER_PASS = "Recruit@123"
PEOPLE = [("hr@infinite-analytics-demo.com", "Karan Mehta", "Engineering Manager"),
          ("panel@infinite-analytics-demo.com", "Sneha Iyer", "Senior Data Engineer (panel)")]
APPLICANTS = 24


def pdf_resume(name, roll, branch, cgpa, skills):
    """A small, valid one-page PDF resume (no external libraries)."""
    lines = [f"{name}", f"Roll number: {roll}", f"Branch: {branch}", f"CGPA: {cgpa}", f"Skills: {', '.join(skills)}",
             "Projects: Campus placement tracker (Angular, Spring Boot); Sales dashboard (Python, SQL)",
             "Objective: Software engineering internship in data platforms."]
    esc = lambda s: s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    text = "BT /F1 18 Tf 50 780 Td (" + esc(lines[0]) + ") Tj /F1 11 Tf " + "".join(f"0 -22 Td ({esc(l)}) Tj " for l in lines[1:]) + "ET"
    objs = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            f"<< /Length {len(text)} >>\nstream\n{text}\nendstream", "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out, offsets = "%PDF-1.4\n", []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out.encode("latin-1")))
        out += f"{i} 0 obj\n{o}\nendobj\n"
    xref = len(out.encode("latin-1"))
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n" + "".join(f"{o:010d} 00000 n \n" for o in offsets)
    out += f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n"
    return out.encode("latin-1", "replace")


def main():
    t = login(TPO_EMAIL, TPO_PASS)
    if not t:
        raise SystemExit("Run scripts/seed_demo.py first.")
    drive = next((d for d in ok(call("GET", "/api/v1/college/drives?size=100", None, t), "drives")["content"] if d["title"] == DRIVE_TITLE), None)
    if not drive:
        raise SystemExit(f'Drive "{DRIVE_TITLE}" not found. Run scripts/seed_student_portal.py first.')
    did = drive["id"]
    company = next(c for c in ok(call("GET", "/api/v1/college/companies?size=50", None, t), "companies")["content"] if c["name"] == COMPANY)

    print("Drive details ...")
    full = ok(call("GET", f"/api/v1/college/drives/{did}", None, t), "drive")
    if full["companyId"] != company["id"] or not full.get("driveDate"):
        body = {k: full.get(k) for k in ("title", "roleTitle", "driveType", "ctcLpa", "stipendMonthly", "ctcBreakup", "bondMonths", "bondDetails",
                                         "jobDescription", "skills", "registrationDeadline", "eligibility")}
        body.update({"companyId": company["id"], "mode": "HYBRID", "location": "Bengaluru / Remote", "venue": "Online assessment, interviews in Seminar Hall 2",
                     "driveDate": time.strftime("%Y-%m-%d", time.gmtime(time.time() + 4 * 86400)),
                     "jobDescription": "6-month Software Engineer internship in the Data Platform team.\n"
                                       "- Build data pipelines and REST APIs (Python, SQL, Spark)\n"
                                       "- Work with senior engineers on production features\n"
                                       "- Pre-placement offer for top performers",
                     "skills": ["Python", "SQL", "Data Structures"]})
        ok(call("PUT", f"/api/v1/college/drives/{did}", body, t), "update drive")

    print("Rounds ...")
    rounds = ok(call("GET", f"/api/v1/college/drives/{did}/rounds", None, t), "rounds")
    if not rounds:
        for name, typ in [("Online Assessment", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")]:
            ok(call("POST", f"/api/v1/college/drives/{did}/rounds", {"name": name, "roundType": typ}, t), "round")

    print("Applicants ...")
    apps = ok(call("GET", f"/api/v1/college/drives/{did}/applications?size=200", None, t), "applications")["content"]
    if len(apps) < APPLICANTS:
        eligible = ok(call("GET", f"/api/v1/college/drives/{did}/eligibility?eligible=true&size=500", None, t), "eligibility")["content"]
        have = {a["studentId"] for a in apps}
        add = [r["studentId"] for r in eligible if r["studentId"] not in have and not r.get("applicationStatus")][:APPLICANTS - len(apps)]
        if add:
            ok(call("POST", f"/api/v1/college/drives/{did}/applications", {"studentIds": add}, t), "register applicants")
        apps = ok(call("GET", f"/api/v1/college/drives/{did}/applications?size=200", None, t), "applications")["content"]

    print("Resumes ...")
    added = 0
    for a in apps:
        detail = ok(call("GET", f"/api/v1/college/students/{a['studentId']}", None, t), "student")
        if any(d["docType"] == "RESUME" for d in detail["documents"]):
            continue
        s = detail["student"]
        pdf = pdf_resume(s["fullName"], s["rollNumber"], s.get("branchName") or "-", s.get("cgpa"), s.get("skills") or [])
        st, r = mcall("POST", f"/api/v1/college/students/{a['studentId']}/documents?docType=RESUME",
                      files={"file": (f"{s['rollNumber']}-resume.pdf", pdf, "application/pdf")}, token=t)
        if st >= 300:
            raise SystemExit(f"resume upload failed: {st} {r}")
        added += 1
    print(f"  {added} resume(s) added")

    print("Company Drive Room access ...")
    existing = {x["email"]: x for x in ok(call("GET", f"/api/v1/college/drives/{did}/recruiters", None, t), "access")}
    for email, name, desig in PEOPLE:
        if email in existing and existing[email]["status"] == "ACTIVE":
            continue
        inv = ok(call("POST", f"/api/v1/college/drives/{did}/recruiters", {"email": email, "fullName": name, "designation": desig, "shareDetails": True}, t), "invite")
        ok(call("POST", "/api/v1/college/auth/recruiter-accept", {"token": inv["devInviteToken"], "fullName": name, "password": RECRUITER_PASS}), "accept")

    print("Student logins ...")
    students = []
    for a in apps[:4]:
        email = ok(call("GET", f"/api/v1/college/students/{a['studentId']}", None, t), "student")["student"]["email"]
        if not login(email, STUDENT_PASS):
            set_password(email, STUDENT_PASS)
        students.append((a["studentName"], email))

    print(f"\nReady: {DRIVE_TITLE} ({COMPANY}) with {len(apps)} applicants, 3 rounds not started.")
    print(f"  TPO            /college/login : {TPO_EMAIL} / {TPO_PASS}")
    for email, name, desig in PEOPLE:
        print(f"  {desig[:14]:<14} /company/login : {email} / {RECRUITER_PASS}   ({name})")
    for name, email in students:
        print(f"  Student        /student/login : {email} / {STUDENT_PASS}   ({name})")


if __name__ == "__main__":
    main()
