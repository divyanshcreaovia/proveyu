#!/usr/bin/env python3
"""
End-to-end test of the student portal and every college role, with data-consistency checks between the two portals.
Creates its own college (does not touch demo data). Backend must run with SPRING_PROFILES_ACTIVE=dev (OTPs in responses).

  python3 scripts/e2e_portal_test.py [http://localhost:8080]

Roles: TPO (COLLEGE_ADMIN), coordinator scoped to CSE, viewer, three students (CSE eligible, ECE eligible, CSE low CGPA),
and a second college for tenancy checks.
"""
import sys
import time

sys.path.insert(0, __import__("os").path.dirname(__file__))
import smoke_test as st  # noqa: E402  (reuses call/check/register_college and the pass/fail counters)
from smoke_test import call, check  # noqa: E402

RUN = st.RUN
YEAR = time.localtime().tm_year + 1
iso = lambda t: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t))
PDF = b"%PDF-1.4\n%e2e test file\n"


def data(resp):
    return (resp[1] or {}).get("data") if isinstance(resp[1], dict) else None


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def invite(t, email, name, role, branch_ids):
    r = data(call("POST", "/api/v1/college/team/invites", {"email": email, "fullName": name, "role": role, "branchIds": branch_ids}, t))
    s, a = call("POST", "/api/v1/college/auth/accept-invite", {"token": r["devInviteToken"], "password": "Team12345"})
    check(f"{role} accepts invite", s == 200 and a["data"]["role"] == role, a)
    return a["data"]["token"]


def activate(email, roll):
    s, r = call("POST", "/api/v1/college/auth/student-signup", {"email": email, "rollNumber": roll})
    check(f"student-signup issues code for {roll}", s == 200 and r["data"].get("devOtp"), r)
    s, _ = call("POST", "/api/v1/auth/reset-password", {"email": email, "resetToken": r["data"]["devOtp"], "newPassword": "Student123"})
    check(f"{roll} sets password", s == 200)
    return login(email, "Student123")


def main():
    print(f"E2E portal test against {st.BASE} (run {RUN})")

    print("== College setup (TPO)")
    tpo, sess = st.register_college("e")
    cse = data(call("POST", "/api/v1/college/settings/branches", {"code": "CSE", "name": "Computer Science"}, tpo))["id"]
    ece = data(call("POST", "/api/v1/college/settings/branches", {"code": "ECE", "name": "Electronics"}, tpo))["id"]
    check("policy TIER_UPGRADE + block after accept", call("PUT", "/api/v1/college/settings/policy", {"policyMode": "TIER_UPGRADE", "maxApplications": 10,
          "maxOffers": 2, "upgradeMinHikePercent": 0, "blockAfterAccept": True}, tpo)[0] == 200)
    mk = lambda roll, fn, br, cgpa: data(call("POST", "/api/v1/college/students", {"rollNumber": roll, "firstName": fn, "lastName": "E2E",
                                     "email": f"{roll.lower()}@e{RUN}.edu.in", "branchId": br, "batchYear": YEAR, "cgpa": cgpa, "activeBacklogs": 0,
                                     "skills": ["Java"]}, tpo))
    s1, s2, s3 = mk(f"C{RUN}1", "Asha", cse, 8.5), mk(f"E{RUN}2", "Bala", ece, 8.0), mk(f"C{RUN}3", "Chitra", cse, 6.0)
    check("3 students created", all([s1, s2, s3]))
    company = data(call("POST", "/api/v1/college/companies", {"name": f"E2E Corp {RUN}", "industry": "IT"}, tpo))["id"]

    print("== Staff roles")
    coord = invite(tpo, f"coord{RUN}@e{RUN}.edu.in", "Coord CSE", "COLLEGE_COORDINATOR", [cse])
    viewer = invite(tpo, f"viewer{RUN}@e{RUN}.edu.in", "Viewer", "COLLEGE_VIEWER", [])
    for role, t in [("TPO", tpo), ("coordinator", coord), ("viewer", viewer)]:
        s, r = call("GET", "/api/v1/college/me", token=t)
        check(f"{role}: /college/me works", s == 200, r)
        check(f"{role}: dashboard loads", call("GET", "/api/v1/college/analytics/dashboard", token=t)[0] == 200)
        check(f"{role}: drives list loads", call("GET", "/api/v1/college/drives", token=t)[0] == 200)
        check(f"{role}: announcements list loads", call("GET", "/api/v1/college/announcements", token=t)[0] == 200)
        check(f"{role}: chat contacts load", call("GET", "/api/v1/college/chat/contacts", token=t)[0] == 200)
    s, r = call("GET", "/api/v1/college/students?size=50", token=coord)
    check("coordinator sees only CSE students (2)", s == 200 and r["data"]["totalElements"] == 2, r["data"]["totalElements"] if s == 200 else r)
    check("coordinator cannot open ECE student (404)", call("GET", f"/api/v1/college/students/{s2['id']}", token=coord)[0] == 404)
    check("coordinator can view policy", call("GET", "/api/v1/college/settings/policy", token=coord)[0] == 200)
    check("coordinator cannot change policy (403)", call("PUT", "/api/v1/college/settings/policy", {"policyMode": "OPEN", "maxApplications": 10,
          "maxOffers": 2, "upgradeMinHikePercent": 0, "blockAfterAccept": True}, coord)[0] == 403)
    check("coordinator cannot read audit log (403)", call("GET", "/api/v1/college/audit-logs", token=coord)[0] == 403)
    check("coordinator cannot invite team (403)", call("POST", "/api/v1/college/team/invites", {"email": f"x{RUN}@x.in", "fullName": "X",
          "role": "COLLEGE_VIEWER"}, coord)[0] == 403)
    check("viewer can read students", call("GET", "/api/v1/college/students", token=viewer)[0] == 200)
    check("viewer cannot add student (403)", call("POST", "/api/v1/college/students", {"rollNumber": "V1", "firstName": "V", "email": "v@x.in",
          "branchId": cse, "batchYear": YEAR}, viewer)[0] == 403)
    check("viewer cannot create drive (403)", call("POST", "/api/v1/college/drives", {"companyId": company, "title": "x", "roleTitle": "x",
          "driveType": "ON_CAMPUS"}, viewer)[0] == 403)
    check("viewer cannot announce (403)", call("POST", "/api/v1/college/announcements", {"title": "x", "body": "x", "filter": {}, "sendEmail": False}, viewer)[0] == 403)

    print("== Student sign-up / login")
    check("signup: wrong roll number rejected (404)", call("POST", "/api/v1/college/auth/student-signup", {"email": s1["email"], "rollNumber": "NOPE"})[0] == 404)
    check("signup: staff e-mail is not a student (404)", call("POST", "/api/v1/college/auth/student-signup", {"email": sess["email"], "rollNumber": "x"})[0] == 404)
    check("signup: invalid body rejected (400)", call("POST", "/api/v1/college/auth/student-signup", {"email": "bad"})[0] == 400)
    check("student cannot log in before activation", login(s1["email"], "Student123") is None)
    t1, t2, t3 = activate(s1["email"], s1["rollNumber"].lower()), activate(s2["email"], s2["rollNumber"]), activate(s3["email"], s3["rollNumber"])
    check("3 students logged in", all([t1, t2, t3]))
    check("wrong password rejected", login(s1["email"], "wrong-pass") is None)
    p1 = data(call("GET", "/api/v1/college/student/profile", token=t1))
    check("profile matches college record (roll, branch, CGPA, college)", p1["rollNumber"] == s1["rollNumber"] and p1["branch"] == "Computer Science"
          and float(p1["cgpa"]) == 8.5 and p1["collegeName"] == sess.get("collegeName", p1["collegeName"]), p1)
    check("student cannot call staff API (403)", call("GET", "/api/v1/college/students", token=t1)[0] == 403)
    check("staff cannot call student API (403)", call("GET", "/api/v1/college/student/profile", token=tpo)[0] == 403)
    check("anonymous cannot call student API (401)", call("GET", "/api/v1/college/student/profile")[0] == 401)

    print("== Drives & eligibility")
    drive_req = {"companyId": company, "title": f"E2E SDE {RUN}", "roleTitle": "SDE", "driveType": "ON_CAMPUS", "ctcLpa": 9, "mode": "ONLINE",
                 "registrationDeadline": iso(time.time() + 5 * 86400), "skills": ["Java"],
                 "eligibility": {"minCgpa": 7, "branchIds": [cse, ece], "batchYears": [YEAR]}}
    drive = data(call("POST", "/api/v1/college/drives", drive_req, tpo))["id"]
    check("draft drive hidden from students", not any(d["id"] == drive for d in data(call("GET", "/api/v1/college/student/drives", token=t1))))
    check("apply to draft rejected (409)", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=t1)[0] == 409)
    check("coordinator can publish (WRITE)", call("POST", f"/api/v1/college/drives/{drive}/status", {"status": "PUBLISHED"}, coord)[0] == 200)
    sd = {n: next(d for d in data(call("GET", "/api/v1/college/student/drives", token=t)) if d["id"] == drive) for n, t in [("s1", t1), ("s2", t2), ("s3", t3)]}
    check("S1 (CSE 8.5) eligible + canApply", sd["s1"]["eligible"] and sd["s1"]["canApply"], sd["s1"])
    check("S2 (ECE 8.0) eligible", sd["s2"]["eligible"], sd["s2"])
    check("S3 (CGPA 6.0) ineligible with CGPA reason", not sd["s3"]["eligible"] and any("CGPA" in x.upper() for x in sd["s3"]["reasons"]), sd["s3"]["reasons"])
    check("student drive shows company, CTC, tier", sd["s1"]["companyName"].startswith("E2E Corp") and float(sd["s1"]["ctcLpa"]) == 9 and sd["s1"]["tierName"], sd["s1"])
    prev = data(call("GET", f"/api/v1/college/drives/{drive}/eligibility", token=tpo))
    check("TPO eligibility preview agrees (2 eligible, 1 not)", prev["eligible"] == 2 and prev["ineligible"] == 1, prev)

    print("== Applications")
    check("S1 applies", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=t1)[0] == 200)
    check("S1 double apply rejected (409)", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=t1)[0] == 409)
    check("S3 apply rejected NOT_ELIGIBLE (422)", call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=t3)[0] == 422)
    a2 = data(call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=t2))
    check("S2 withdraws", call("POST", f"/api/v1/college/student/applications/{a2['id']}/withdraw", token=t2)[0] == 200)
    check("S1 cannot withdraw S2's application (404)", call("POST", f"/api/v1/college/student/applications/{a2['id']}/withdraw", token=t1)[0] == 404)
    apps = {a["studentId"]: a for a in data(call("GET", f"/api/v1/college/drives/{drive}/applications", token=tpo))["content"]}
    check("TPO sees S1 APPLIED and S2 WITHDRAWN", apps[s1["id"]]["status"] == "APPLIED" and apps[s2["id"]]["status"] == "WITHDRAWN", apps)
    check("viewer sees applicants too", call("GET", f"/api/v1/college/drives/{drive}/applications", token=viewer)[0] == 200)
    app1 = apps[s1["id"]]["id"]
    my = data(call("GET", "/api/v1/college/student/applications", token=t1))
    check("student's application list matches (1, APPLIED)", len(my) == 1 and my[0]["id"] == app1 and my[0]["status"] == "APPLIED", my)
    check("S1 cannot read S2's rounds (404)", call("GET", f"/api/v1/college/student/applications/{a2['id']}/rounds", token=t1)[0] == 404)

    print("== Rounds & interviews")
    call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [app1], "status": "SHORTLISTED"}, tpo)
    r1 = data(call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "Aptitude", "roundType": "APTITUDE"}, tpo))["id"]
    r2 = data(call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "HR", "roundType": "HR"}, tpo))["id"]
    rs = data(call("GET", f"/api/v1/college/student/applications/{app1}/rounds", token=t1))
    check("student sees 2 pending rounds in order", [r["name"] for r in rs] == ["Aptitude", "HR"] and all(r["status"] == "PENDING" for r in rs), rs)
    call("POST", f"/api/v1/college/rounds/{r1}/start", token=tpo)
    call("PUT", f"/api/v1/college/rounds/{r1}/results", {"results": [{"applicationId": app1, "result": "PASS", "score": 80}]}, tpo)
    check("complete round 1", call("POST", f"/api/v1/college/rounds/{r1}/complete", token=tpo)[0] == 200)
    call("POST", f"/api/v1/college/rounds/{r2}/start", token=tpo)
    rs = data(call("GET", f"/api/v1/college/student/applications/{app1}/rounds", token=t1))
    check("student sees R1 PASS, R2 ONGOING", rs[0]["result"] == "PASS" and rs[1]["status"] == "ONGOING", rs)
    my = data(call("GET", "/api/v1/college/student/applications", token=t1))[0]
    tpo_app = next(a for a in data(call("GET", f"/api/v1/college/drives/{drive}/applications", token=tpo))["content"] if a["id"] == app1)
    check("application status/round identical in both portals", my["status"] == tpo_app["status"] and my["currentRound"] == tpo_app["currentRound"], (my, tpo_app))
    start = time.time() + 3 * 86400
    check("coordinator schedules interview", call("POST", "/api/v1/college/interviews", {"driveId": drive, "roundId": r2, "applicationId": app1,
          "panelName": "Panel X", "room": "Lab 1", "meetingLink": "https://meet.example.com/x", "startsAt": iso(start), "endsAt": iso(start + 1800)}, coord)[0] == 200)
    iv = data(call("GET", "/api/v1/college/student/interviews", token=t1))
    check("student sees the interview with room, link, round", len(iv) == 1 and iv[0]["room"] == "Lab 1" and iv[0]["meetingLink"].endswith("/x")
          and iv[0]["roundName"] == "HR" and iv[0]["status"] == "SCHEDULED", iv)
    check("other students see no interview", data(call("GET", "/api/v1/college/student/interviews", token=t2)) == [])
    call("PUT", f"/api/v1/college/rounds/{r2}/results", {"results": [{"applicationId": app1, "result": "PASS"}]}, tpo)
    call("POST", f"/api/v1/college/rounds/{r2}/complete", token=tpo)
    check("student application now SELECTED", data(call("GET", "/api/v1/college/student/applications", token=t1))[0]["status"] == "SELECTED")

    print("== Announcements")
    s, r = call("POST", "/api/v1/college/announcements", {"title": "CSE only", "body": "Hello CSE", "filter": {"branchIds": [cse]}, "sendEmail": False}, tpo)
    ann = r["data"]
    check("announcement to CSE reaches 2 students", s == 200 and ann["recipientCount"] == 2, r)
    in1 = data(call("GET", "/api/v1/college/student/announcements", token=t1))["content"]
    check("S1 inbox has it, unread", len(in1) == 1 and in1[0]["title"] == "CSE only" and not in1[0]["read"], in1)
    check("S2 (ECE) inbox empty", data(call("GET", "/api/v1/college/student/announcements", token=t2))["content"] == [])
    check("profile unread count = 1", data(call("GET", "/api/v1/college/student/profile", token=t1))["unreadAnnouncements"] == 1)
    call("POST", f"/api/v1/college/student/announcements/{ann['id']}/read", token=t1)
    check("after read: unread 0", data(call("GET", "/api/v1/college/student/profile", token=t1))["unreadAnnouncements"] == 0)
    check("S2 cannot mark an announcement it never got (404)", call("POST", f"/api/v1/college/student/announcements/{ann['id']}/read", token=t2)[0] == 404)
    tl = next(a for a in data(call("GET", "/api/v1/college/announcements", token=tpo))["content"] if a["id"] == ann["id"])
    check("TPO sees readCount 1", tl["readCount"] == 1, tl)
    s, r = call("POST", "/api/v1/college/announcements", {"title": "Coord note", "body": "x", "filter": {}, "sendEmail": False}, coord)
    check("coordinator announcement limited to own branch (2)", s == 200 and r["data"]["recipientCount"] == 2, r)
    call("POST", "/api/v1/college/student/announcements/read-all", token=t3)
    check("read-all clears unread", data(call("GET", "/api/v1/college/student/profile", token=t3))["unreadAnnouncements"] == 0)

    print("== Chat")
    c1 = data(call("GET", "/api/v1/college/student/chat/contacts", token=t1))
    c2 = data(call("GET", "/api/v1/college/student/chat/contacts", token=t2))
    check("CSE student contacts: TPO + coordinator (no viewer)", sorted(c["type"] for c in c1) == ["COORDINATOR", "TPO"], c1)
    check("ECE student contacts: TPO only", [c["type"] for c in c2] == ["TPO"], c2)
    tpo_uid = next(c["userId"] for c in c1 if c["type"] == "TPO")
    coord_uid = next(c["userId"] for c in c1 if c["type"] == "COORDINATOR")
    staff_contacts = data(call("GET", "/api/v1/college/chat/contacts", token=tpo))
    check("TPO's chat contacts include S1", any(c["userId"] == p1["userId"] for c in staff_contacts))
    check("TPO -> S1 message", call("POST", "/api/v1/chat/messages", {"receiverId": p1["userId"], "message": f"hello {RUN}"}, tpo)[0] in (200, 201))
    check("S1 -> TPO reply", call("POST", "/api/v1/chat/messages", {"receiverId": tpo_uid, "message": f"thanks {RUN}"}, t1)[0] in (200, 201))
    check("S1 -> coordinator message", call("POST", "/api/v1/chat/messages", {"receiverId": coord_uid, "message": "doubt"}, t1)[0] in (200, 201))
    h = data(call("GET", f"/api/v1/chat/messages/{tpo_uid}", token=t1))
    msgs = [m["message"] for m in (h["content"] if isinstance(h, dict) else h)]
    check("S1 history has both messages", f"hello {RUN}" in msgs and f"thanks {RUN}" in msgs, msgs)
    h = data(call("GET", f"/api/v1/chat/messages/{p1['userId']}", token=tpo))
    check("TPO history matches", f"thanks {RUN}" in [m["message"] for m in (h["content"] if isinstance(h, dict) else h)])
    h = data(call("GET", f"/api/v1/chat/messages/{p1['userId']}", token=coord))
    check("coordinator received S1's message", "doubt" in [m["message"] for m in (h["content"] if isinstance(h, dict) else h)])

    print("== Document locker")
    s, r = call("POST", "/api/v1/college/student/documents?docType=RESUME&label=Main%20CV", files={"file": ("cv.pdf", PDF, "application/pdf")}, token=t1)
    doc = r["data"]
    check("upload resume with label", s == 200 and doc["label"] == "Main CV" and doc["verificationStatus"] == "PENDING", r)
    s, body = call("GET", f"/api/v1/college/student/documents/{doc['id']}/file", token=t1, raw=True)
    check("download returns the exact bytes", s == 200 and body == PDF)
    check("another student cannot download it (404)", call("GET", f"/api/v1/college/student/documents/{doc['id']}/file", token=t2, raw=True)[0] == 404)
    check("bad file type rejected (400)", call("POST", "/api/v1/college/student/documents?docType=OTHER", files={"file": ("x.exe", b"MZ", "application/octet-stream")}, token=t1)[0] == 400)
    det = data(call("GET", f"/api/v1/college/students/{s1['id']}", token=tpo))
    check("TPO sees the document in student details", any(d["id"] == doc["id"] and d["label"] == "Main CV" for d in det["documents"]), det["documents"])
    check("coordinator (CSE) can verify", call("PATCH", f"/api/v1/college/students/documents/{doc['id']}/verify", {"status": "VERIFIED"}, coord)[0] == 200)
    check("viewer cannot verify (403)", call("PATCH", f"/api/v1/college/students/documents/{doc['id']}/verify", {"status": "REJECTED"}, viewer)[0] == 403)
    d1 = data(call("GET", "/api/v1/college/student/documents", token=t1))
    check("student sees VERIFIED", d1[0]["verificationStatus"] == "VERIFIED", d1)
    check("verified document cannot be deleted by student (409)", call("DELETE", f"/api/v1/college/student/documents/{doc['id']}", token=t1)[0] == 409)
    s, r = call("POST", "/api/v1/college/student/documents?docType=CERTIFICATE", files={"file": ("c.pdf", PDF, "application/pdf")}, token=t1)
    check("pending document can be deleted", call("DELETE", f"/api/v1/college/student/documents/{r['data']['id']}", token=t1)[0] == 200)
    check("locker now has 1 document", len(data(call("GET", "/api/v1/college/student/documents", token=t1))) == 1)

    print("== Offers")
    offer = data(call("POST", "/api/v1/college/offers", {"studentId": s1["id"], "driveId": drive, "companyId": company, "roleTitle": "SDE", "ctcLpa": 9}, tpo))["id"]
    of = data(call("GET", "/api/v1/college/student/offers", token=t1))
    check("student sees OFFERED with CTC 9", len(of) == 1 and of[0]["status"] == "OFFERED" and float(of[0]["ctcLpa"]) == 9, of)
    check("offer letter missing -> 404", call("GET", f"/api/v1/college/student/offers/{offer}/letter", token=t1, raw=True)[0] == 404)
    call("POST", f"/api/v1/college/offers/{offer}/letter", files={"file": ("offer.pdf", PDF, "application/pdf")}, token=tpo)
    s, body = call("GET", f"/api/v1/college/student/offers/{offer}/letter", token=t1, raw=True)
    check("student downloads offer letter", s == 200 and body == PDF)
    check("other student cannot respond (404)", call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": True}, t2)[0] == 404)
    check("other student cannot get the letter (404)", call("GET", f"/api/v1/college/student/offers/{offer}/letter", token=t2, raw=True)[0] == 404)
    check("S1 accepts", data(call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": True}, t1))["status"] == "ACCEPTED")
    check("responding twice rejected (409)", call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": False}, t1)[0] == 409)
    p1 = data(call("GET", "/api/v1/college/student/profile", token=t1))
    check("student profile: PLACED, best offer 9", p1["status"] == "PLACED" and float(p1["highestCtcLpa"]) == 9, p1)
    stu = data(call("GET", f"/api/v1/college/students/{s1['id']}", token=tpo))["student"]
    check("college record: PLACED, highest CTC 9", stu["status"] == "PLACED" and float(stu["highestCtcLpa"]) == 9, stu)
    k = data(call("GET", "/api/v1/college/analytics/dashboard", token=tpo))["kpis"]
    check("college dashboard: 3 students, 1 placed", k["totalStudents"] == 3 and k["placedStudents"] == 1, k)
    d2 = data(call("POST", "/api/v1/college/drives", {**drive_req, "title": f"E2E Same tier {RUN}"}, tpo))["id"]
    call("POST", f"/api/v1/college/drives/{d2}/status", {"status": "PUBLISHED"}, tpo)
    sd = next(d for d in data(call("GET", "/api/v1/college/student/drives", token=t1)) if d["id"] == d2)
    check("dream policy: placed student cannot apply to same-tier drive", not sd["canApply"] and sd["reasons"], sd)
    check("unplaced S2 can still apply to it", next(d for d in data(call("GET", "/api/v1/college/student/drives", token=t2)) if d["id"] == d2)["canApply"])

    print("== Profile")
    s, r = call("PATCH", "/api/v1/college/student/profile", {"phone": "9876500099", "skills": ["Java", "SQL", "Java"], "linkedinUrl": "https://linkedin.com/in/asha",
                                                            "githubUrl": "", "cgpa": 10}, t1)
    check("profile update saves, de-duplicates skills", s == 200 and r["data"]["skills"] == ["Java", "SQL"] and r["data"]["phone"] == "9876500099", r)
    check("CGPA not changeable by student", float(r["data"]["cgpa"]) == 8.5, r["data"]["cgpa"])
    stu = data(call("GET", f"/api/v1/college/students/{s1['id']}", token=tpo))["student"]
    check("college sees updated phone + LinkedIn", stu["phone"] == "9876500099" and stu["linkedinUrl"] == "https://linkedin.com/in/asha", stu)
    check("javascript: URL rejected (400)", call("PATCH", "/api/v1/college/student/profile", {"linkedinUrl": "javascript:alert(1)"}, t1)[0] == 400)
    check("bad phone rejected (400)", call("PATCH", "/api/v1/college/student/profile", {"phone": "abc"}, t1)[0] == 400)

    print("== Notifications")
    n = data(call("GET", "/api/v1/notifications", token=t1)) or []
    check("student got in-app notifications (drive / announcement / offer)", len(n) >= 2, len(n))

    print("== Tenancy & access revocation")
    tB, _ = st.register_college("f")
    check("college B cannot read college A student (404)", call("GET", f"/api/v1/college/students/{s1['id']}", token=tB)[0] == 404)
    check("college B cannot see A's announcements", all(a["id"] != ann["id"] for a in data(call("GET", "/api/v1/college/announcements", token=tB))["content"]))
    members = data(call("GET", "/api/v1/college/team/members", token=tpo))
    cm = next(m for m in members if m["email"].startswith("coord"))
    check("TPO revokes coordinator", call("POST", f"/api/v1/college/team/members/{cm['id']}/revoke", token=tpo)[0] == 200)
    s, r = call("GET", "/api/v1/college/students", token=coord)
    check("revoked coordinator is rejected (403 ACCESS_REVOKED)", s == 403 and r.get("errorCode") == "ACCESS_REVOKED", r)
    check("revoked coordinator no longer a student chat contact",
          [c["type"] for c in data(call("GET", "/api/v1/college/student/chat/contacts", token=t1))] == ["TPO"])
    s, r = call("DELETE", f"/api/v1/college/students/{s3['id']}", token=tpo)
    check("TPO soft-deletes S3", s == 200, r)
    check("deleted student's portal shows NOT_A_COLLEGE_STUDENT (404)", call("GET", "/api/v1/college/student/profile", token=t3)[0] == 404)

    print(f"\n{st.passed} passed, {st.failed} failed")
    sys.exit(1 if st.failed else 0)


if __name__ == "__main__":
    main()
