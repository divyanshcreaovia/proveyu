#!/usr/bin/env python3
"""Adds student-portal demo data on top of seed_demo.py: one demo student with an application in progress
(round 1 passed, interview scheduled), a pending offer, announcements, chat messages from the TPO and
coordinator, and a document in the locker. Idempotent enough to re-run (prints accounts if already done).

Usage: python3 scripts/seed_student_portal.py [http://localhost:8080]   (backend in the dev profile)
"""
import json
import sys
import time
import urllib.parse
import urllib.request
import uuid

sys.path.insert(0, __import__("os").path.dirname(__file__))
from seed_demo import BASE, TPO_EMAIL, TPO_PASS, COORD_EMAIL, TEAM_PASS, STUDENT_PASS, call, ok, login, set_password  # noqa: E402

iso = lambda hours: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + hours * 3600))


def upload(path, token, fields, name, content, ctype):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\nContent-Type: {ctype}\r\n\r\n").encode() \
        + content + f"\r\n--{boundary}--\r\n".encode()
    q = "&".join(f"{k}={urllib.parse.quote(v)}" for k, v in fields.items())
    req = urllib.request.Request(f"{BASE}{path}?{q}", data=body, method="POST",
                                 headers={"Authorization": "Bearer " + token, "Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["data"]


def main():
    t = login(TPO_EMAIL, TPO_PASS)
    if not t:
        raise SystemExit("Run scripts/seed_demo.py first.")
    coord = login(COORD_EMAIL, TEAM_PASS)

    drives = ok(call("GET", "/api/v1/college/drives?size=50", None, t), "drives")["content"]
    drive = next(d for d in drives if d["status"] in ("PUBLISHED", "ONGOING") and d["title"].startswith("TechNova"))
    apps = ok(call("GET", f"/api/v1/college/drives/{drive['id']}/applications?size=100", None, t), "applications")["content"]
    rounds = ok(call("GET", f"/api/v1/college/drives/{drive['id']}/rounds", None, t), "rounds")
    if rounds[0]["status"] == "PENDING":
        print("Moving the drive through round 1 ...")
        ok(call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [a["id"] for a in apps], "status": "SHORTLISTED"}, t), "shortlist")
        ok(call("POST", f"/api/v1/college/rounds/{rounds[0]['id']}/start", None, t), "start r1")
        ok(call("PUT", f"/api/v1/college/rounds/{rounds[0]['id']}/results",
                {"results": [{"applicationId": a["id"], "result": "PASS"} for a in apps]}, t), "r1 results")
        ok(call("POST", f"/api/v1/college/rounds/{rounds[0]['id']}/complete", None, t), "complete r1")
        apps = ok(call("GET", f"/api/v1/college/drives/{drive['id']}/applications?size=100", None, t), "applications")["content"]
    if rounds[1]["status"] == "PENDING":
        ok(call("POST", f"/api/v1/college/rounds/{rounds[1]['id']}/start", None, t), "start r2")
    r2 = rounds[1]
    # demo student: an in-process CSE applicant, so the CSE/IT coordinator is also a chat contact
    app = next((a for a in apps if a.get("branchCode") == "CSE" and a["status"] == "IN_PROCESS"),
               next((a for a in apps if a["status"] == "IN_PROCESS"), apps[0]))
    student = ok(call("GET", f"/api/v1/college/students/{app['studentId']}", None, t), "student")["student"]
    email = student["email"]
    if not login(email, STUDENT_PASS):
        set_password(email, STUDENT_PASS)
    s = login(email, STUDENT_PASS)
    me = ok(call("GET", "/api/v1/college/student/profile", None, s), "student profile")

    if not ok(call("GET", "/api/v1/college/student/documents", None, s), "docs"):
        print("Open drive the student can apply to ...")
        companies = ok(call("GET", "/api/v1/college/companies?size=10", None, t), "companies")["content"]
        nd = ok(call("POST", "/api/v1/college/drives", {
            "companyId": companies[1]["id"], "title": "Infinite Analytics Software Engineer Intern", "roleTitle": "Software Engineer Intern",
            "driveType": "INTERNSHIP", "stipendMonthly": 30000, "mode": "ONLINE", "location": "Remote", "skills": ["Python", "SQL"],
            "jobDescription": "6-month internship with a pre-placement offer for top performers.\nWork on data pipelines and APIs.",
            "registrationDeadline": iso(24 * 7), "eligibility": {"minCgpa": 6, "maxActiveBacklogs": 2}}, t), "open drive")
        ok(call("POST", f"/api/v1/college/drives/{nd['id']}/status", {"status": "PUBLISHED"}, t), "publish open drive")

        print("Scheduling interview ...")
        ok(call("POST", "/api/v1/college/interviews", {"driveId": drive["id"], "roundId": r2["id"], "applicationId": app["id"], "panelName": "Panel A",
                                                       "startsAt": iso(26), "endsAt": iso(26.5), "room": "Seminar Hall 2",
                                                       "meetingLink": "https://meet.google.com/demo-tech-round"}, t), "interview")

        print("Off-campus offer waiting for the student's reply ...")
        companies = ok(call("GET", "/api/v1/college/companies?size=10", None, t), "companies")["content"]
        ok(call("POST", "/api/v1/college/offers", {"studentId": me["studentId"], "companyId": companies[-1]["id"], "roleTitle": "Graduate Trainee (Off-campus)",
                                                   "ctcLpa": 5.2, "offerDate": time.strftime("%Y-%m-%d"), "remarks": "Demo offer",
                                                   "overridePolicy": True, "overrideReason": "Demo data"}, t), "offer")

        print("Announcements ...")
        for title, body in [("Welcome to the placement season 2026", "All final-year students: keep your resume and marksheets in the Document locker.\nThe placement cell verifies them within 2 days."),
                            (f"{drive['title']}: Technical round tomorrow", "Shortlisted students: carry your college ID card. Online candidates use the meeting link in Interviews."),
                            ("Mock interview week", "Mock technical interviews run Monday to Friday, 3-5 PM in Lab 4. Book your slot with your coordinator.")]:
            ok(call("POST", "/api/v1/college/announcements", {"title": title, "body": body, "filter": {}, "sendEmail": False}, t), "announcement")

        print("Chat ...")
        for token, text in [(t, f"Hi {me['name'].split()[0]}, congratulations on clearing the aptitude round!"),
                            (t, "Your technical interview is scheduled for tomorrow. Please check the Interviews page."),
                            (coord, "Hi, I'm your CSE coordinator. Message me here if you have any questions about the drives.")]:
            ok(call("POST", "/api/v1/chat/messages", {"receiverId": me["userId"], "message": text}, token), "chat")
        ok(call("POST", "/api/v1/chat/messages", {"receiverId": ok(call("GET", "/api/v1/college/me", None, t), "me")["userId"],
                                                  "message": "Thank you ma'am! Should I bring a printed resume?"}, s), "chat reply")

        print("Document locker ...")
        pdf = b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 144]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF"
        upload("/api/v1/college/student/documents", s, {"docType": "RESUME", "label": "Resume 2026"}, "resume.pdf", pdf, "application/pdf")
        doc = upload("/api/v1/college/student/documents", s, {"docType": "MARKSHEET_10", "label": ""}, "10th-marksheet.pdf", pdf, "application/pdf")
        ok(call("PATCH", f"/api/v1/college/students/documents/{doc['id']}/verify", {"status": "VERIFIED", "remarks": "Checked original"}, t), "verify")
    else:
        print("Student portal demo data already present.")

    print("\nDone. Try it at http://localhost:4200")
    print(f"  Student portal  /student/login : {email} / {STUDENT_PASS}   ({me['name']}, {me['rollNumber']})")
    print(f"  College portal  /college/login : {TPO_EMAIL} / {TPO_PASS}   (TPO)")
    print(f"  Coordinator     /college/login : {COORD_EMAIL} / {TEAM_PASS}")


if __name__ == "__main__":
    main()
