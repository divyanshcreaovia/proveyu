#!/usr/bin/env python3
"""
End-to-end API smoke test of the college placement flow (requires the backend running with
SPRING_PROFILES_ACTIVE=dev so verification tokens / OTPs are returned in responses).

  python3 scripts/smoke_test.py [http://localhost:8080]

Flow: register college A -> verify -> login -> branch -> students (single + import dry-run/commit)
-> company -> drive with eligibility -> preview -> publish -> student sets password (OTP) & applies
-> shortlist -> rounds -> interview (+ conflict) -> offer -> student accepts -> dashboard / reports,
plus tenancy isolation: college B cannot read college A's data, viewer cannot write.
"""
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
RUN = uuid.uuid4().hex[:6]
passed, failed = 0, 0


def call(method, path, body=None, token=None, files=None, raw=False):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if files is not None:
        boundary = "----smoke" + uuid.uuid4().hex
        parts = []
        for name, (fname, content, ctype) in files.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n'.encode() + content + b"\r\n")
        data = b"".join(parts) + f"--{boundary}--\r\n".encode()
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            content = r.read()
            return r.status, (content if raw else (json.loads(content) if content else None))
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, content


def check(name, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}  {detail}")
    return cond


def register_college(tag):
    domain = f"{tag}{RUN}.edu.in"
    email = f"tpo@{domain}"
    s, r = call("POST", "/api/v1/college/auth/register", {
        "collegeName": f"Smoke College {tag.upper()} {RUN}", "aicteCode": f"AICTE-{tag}-{RUN}", "university": "Mumbai University",
        "city": "Navi Mumbai", "state": "Maharashtra", "emailDomain": domain, "tpoName": f"TPO {tag}",
        "tpoPhone": "9" + str(int(time.time() * 1000))[-9:], "tpoEmail": email, "password": "Secret123", "acceptTerms": True})
    check(f"register college {tag}", s == 201, r)
    s, _ = call("POST", "/api/v1/auth/login", {"email": email, "password": "Secret123"})
    check("login blocked before e-mail verification (403)", s == 403)
    s, _ = call("POST", "/api/v1/auth/verify-email", {"token": r["data"]["devVerificationToken"]})
    check("verify e-mail", s == 200)
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": "Secret123"})
    check(f"login TPO {tag}", s == 200 and r["data"]["role"] == "COLLEGE_ADMIN", r)
    approve_if_pending(r["data"]["token"], email, r["data"].get("collegeApprovalStatus"))
    return r["data"]["token"], r["data"]


ADMIN_LOGIN = (os.environ.get("ADMIN_EMAIL", "admin@proveyu.local"), os.environ.get("ADMIN_PASSWORD", "Admin@1234"))


def approve_if_pending(tpo_token, tpo_email, status):
    """COLLEGE_REQUIRE_APPROVAL=true: the new college is locked until a platform admin approves it (the real flow)."""
    if status != "PENDING":
        return
    s, r = call("GET", "/api/v1/college/students", token=tpo_token)
    check("pending college cannot use the portal (403 COLLEGE_PENDING_APPROVAL)", s == 403 and r.get("errorCode") == "COLLEGE_PENDING_APPROVAL", r)
    s, r = call("POST", "/api/v1/auth/login", {"email": ADMIN_LOGIN[0], "password": ADMIN_LOGIN[1]})
    admin = r["data"]["token"] if s == 200 else None
    s, r = call("GET", "/api/v1/admin/colleges?status=PENDING", token=admin)
    college = next((c for c in (r.get("data") or []) if c.get("tpoEmail") == tpo_email), None)
    check("admin sees the college waiting for approval", college is not None, r)
    s, _ = call("POST", f"/api/v1/admin/colleges/{college['collegeId']}/decision", {"status": "APPROVED", "note": "e2e"}, admin)
    check("admin approves the college", s == 200)
    s, _ = call("GET", "/api/v1/college/students", token=tpo_token)
    check("approved college can use the portal", s == 200)


def main():
    print(f"Smoke test against {BASE} (run {RUN})")
    print("== Auth & onboarding")
    s, r = call("POST", "/api/v1/auth/register", {"email": f"x{RUN}@x.com", "password": "Secret123", "fullName": "X", "role": "ADMIN"})
    check("public register cannot create ADMIN", s == 400, r)
    tA, sessA = register_college("a")
    check("college approval status returned", sessA.get("collegeApprovalStatus") in ("APPROVED", "PENDING"))
    s, r = call("GET", "/api/v1/college/me", token=tA)
    check("GET /college/me", s == 200 and r["data"]["role"] == "COLLEGE_ADMIN", r)

    print("== Settings")
    s, r = call("POST", "/api/v1/college/settings/branches", {"code": "CSE", "name": "Computer Science"}, tA)
    check("add branch CSE", s == 200, r)
    cse = r["data"]["id"]
    s, r = call("POST", "/api/v1/college/settings/branches", {"code": "ECE", "name": "Electronics"}, tA)
    ece = r["data"]["id"]
    s, r = call("GET", "/api/v1/college/settings/tiers", token=tA)
    check("default tiers Normal/Dream/Super Dream", s == 200 and len(r["data"]) == 3, r)
    s, r = call("PUT", "/api/v1/college/settings/policy", {"policyMode": "TIER_UPGRADE", "maxApplications": 10, "maxOffers": 2,
                                                            "upgradeMinHikePercent": 0, "blockAfterAccept": True}, tA)
    check("save placement policy", s == 200, r)

    print("== Students")
    year = time.localtime().tm_year + 1
    stu = {"rollNumber": f"R{RUN}01", "firstName": "Asha", "lastName": "Patil", "email": f"asha{RUN}@a{RUN}.edu.in", "phone": "9000000001",
           "branchId": cse, "batchYear": year, "cgpa": 8.2, "tenthPercent": 90, "twelfthPercent": 88, "activeBacklogs": 0, "gapYears": 0,
           "skills": ["Java", "SQL"]}
    s, r = call("POST", "/api/v1/college/students", stu, tA)
    check("create student", s == 201, r)
    asha = r["data"]
    s, r = call("POST", "/api/v1/college/students", stu, tA)
    check("duplicate roll rejected (409)", s == 409, r)
    s, r = call("POST", "/api/v1/college/students", {**stu, "rollNumber": "X", "email": "bad"}, tA)
    check("validation error has fieldErrors", s == 400 and "email" in (r.get("fieldErrors") or {}), r)

    csv = ("roll_number,first_name,last_name,email,phone,branch_code,batch_year,cgpa,tenth_percent,twelfth_percent,active_backlogs,skills\n"
           f"R{RUN}02,Bala,K,bala{RUN}@x.in,9000000002,CSE,{year},6.5,70,72,1,Python\n"
           f"R{RUN}03,Chitra,M,chitra{RUN}@x.in,9000000003,ECE,{year},9.1,95,93,0,Java;React\n"
           f"R{RUN}03,Dup,Row,dup{RUN}@x.in,9000000004,ECE,{year},7,70,70,0,\n"
           f"R{RUN}04,Bad,Row,not-an-email,,XYZ,{year},11,70,70,0,\n").encode()
    s, r = call("POST", "/api/v1/college/students/import?dryRun=true", files={"file": ("students.csv", csv, "text/csv")}, token=tA)
    d = r.get("data") or {}
    check("import dry-run validates (2 valid, 1 duplicate, 1 error row)", s == 200 and d.get("validRows") == 2 and d.get("duplicateRows") == 1
          and d.get("errorRows") == 1 and d.get("created") == 0, r)
    s, r = call("POST", "/api/v1/college/students/import?dryRun=false", files={"file": ("students.csv", csv, "text/csv")}, token=tA)
    check("import commit creates 2", s == 200 and r["data"]["created"] == 2, r)
    s, r = call("GET", "/api/v1/college/students?size=5&sort=cgpa&dir=desc&branchId=" + ece, token=tA)
    check("server-side filter/sort/paginate", s == 200 and r["data"]["totalElements"] == 1 and r["data"]["content"][0]["firstName"] == "Chitra", r)
    s, body = call("GET", "/api/v1/college/students/export?format=xlsx", token=tA, raw=True)
    check("export students xlsx", s == 200 and body[:2] == b"PK")
    s, body = call("GET", "/api/v1/college/students/export?format=pdf", token=tA, raw=True)
    check("export students pdf", s == 200 and body[:4] == b"%PDF")

    print("== Company & drive")
    s, r = call("POST", "/api/v1/college/companies", {"name": f"Acme {RUN}", "industry": "IT", "mouStatus": "SIGNED"}, tA)
    check("create company", s == 201, r)
    company = r["data"]["id"]
    s, r = call("POST", f"/api/v1/college/companies/{company}/contacts", {"name": "HR Lead", "email": "hr@acme.com", "primaryContact": True}, tA)
    check("add company contact", s == 200, r)
    drive_req = {"companyId": company, "title": f"Acme SDE {RUN}", "roleTitle": "Software Engineer", "driveType": "ON_CAMPUS", "ctcLpa": 9,
                 "mode": "OFFLINE", "venue": "Seminar Hall", "registrationDeadline": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + 7 * 86400)),
                 "skills": ["Java"], "eligibility": {"minCgpa": 7, "maxActiveBacklogs": 0, "branchIds": [cse, ece], "batchYears": [year]}}
    s, r = call("POST", "/api/v1/college/drives", drive_req, tA)
    check("create drive (DRAFT)", s == 201 and r["data"]["status"] == "DRAFT" and r["data"]["tierName"] == "Dream", r)
    drive = r["data"]["id"]
    s, r = call("GET", f"/api/v1/college/drives/{drive}/eligibility", token=tA)
    check("eligibility preview: 2 eligible / 1 ineligible with reasons", s == 200 and r["data"]["eligible"] == 2 and r["data"]["ineligible"] == 1
          and any(row["reasons"] for row in r["data"]["content"] if not row["eligible"]), r)
    s, r = call("POST", f"/api/v1/college/drives/{drive}/status", {"status": "PUBLISHED"}, tA)
    check("publish drive", s == 200 and r["data"]["status"] == "PUBLISHED", r)
    s, r = call("POST", f"/api/v1/college/drives/{drive}/clone", token=tA)
    check("clone drive", s == 200 and r["data"]["status"] == "DRAFT", r)
    clone = r["data"]["id"]

    print("== Student self-service")
    s, r = call("POST", "/api/v1/auth/forgot-password", {"email": asha["email"]})
    otp = (r.get("data") or {}).get("devOtp")
    check("forgot-password (dev exposes OTP only under dev flag)", s == 200 and otp, r)
    s, r = call("POST", "/api/v1/auth/reset-password", {"email": asha["email"], "resetToken": otp, "newPassword": "Student123"})
    check("student sets password via OTP", s == 200, r)
    s, r = call("POST", "/api/v1/auth/login", {"email": asha["email"], "password": "Student123"})
    check("student login", s == 200 and r["data"]["role"] == "CANDIDATE", r)
    tS = r["data"]["token"]
    s, r = call("GET", "/api/v1/college/student/drives", token=tS)
    check("student sees eligible drive", s == 200 and any(d["id"] == drive and d["canApply"] for d in r["data"]), r)
    s, r = call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=tS)
    check("student applies", s == 200, r)
    s, r = call("POST", f"/api/v1/college/student/drives/{drive}/apply", token=tS)
    check("double apply rejected", s == 409, r)
    s, r = call("GET", "/api/v1/college/students", token=tS)
    check("student cannot call staff API (403)", s == 403, r)

    print("== Applications, rounds, interviews")
    s, r = call("GET", f"/api/v1/college/drives/{drive}/applications", token=tA)
    app = r["data"]["content"][0]
    check("TPO sees application", s == 200 and app["status"] == "APPLIED", r)
    s, r = call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [app["id"]], "status": "SHORTLISTED"}, tA)
    check("bulk shortlist", s == 200 and r["data"]["updated"] == 1, r)
    s, r1 = call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "Aptitude", "roundType": "APTITUDE"}, tA)
    s, r2 = call("POST", f"/api/v1/college/drives/{drive}/rounds", {"name": "HR", "roundType": "HR"}, tA)
    check("add 2 rounds", r1["data"]["seq"] == 1 and r2["data"]["seq"] == 2)
    s, r = call("POST", f"/api/v1/college/rounds/{r2['data']['id']}/start", token=tA)
    check("cannot start round 2 before round 1", s == 409, r)
    s, r = call("POST", f"/api/v1/college/rounds/{r1['data']['id']}/start", token=tA)
    check("start round 1", s == 200, r)
    s, r = call("POST", f"/api/v1/college/rounds/{r1['data']['id']}/complete", token=tA)
    check("complete blocked while results pending", s == 409, r)
    s, r = call("PUT", f"/api/v1/college/rounds/{r1['data']['id']}/results",
                {"results": [{"applicationId": app["id"], "result": "PASS", "score": 78}]}, tA)
    check("record result", s == 200, r)
    s, r = call("POST", f"/api/v1/college/rounds/{r1['data']['id']}/complete", token=tA)
    check("complete round 1 (auto-advance)", s == 200, r)
    s, r = call("POST", f"/api/v1/college/rounds/{r2['data']['id']}/start", token=tA)
    check("start round 2", s == 200, r)
    start = time.time() + 2 * 86400
    iso = lambda t: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t))
    slot = {"driveId": drive, "roundId": r2["data"]["id"], "applicationId": app["id"], "panelName": "Panel A", "room": "R-101",
            "startsAt": iso(start), "endsAt": iso(start + 1800)}
    s, r = call("POST", "/api/v1/college/interviews", slot, tA)
    check("schedule interview", s == 200, r)
    s, r = call("POST", "/api/v1/college/interviews", {**slot, "startsAt": iso(start + 600), "endsAt": iso(start + 2400)}, tA)
    check("conflict detected (409 + conflicts list)", s == 409 and isinstance(r.get("data"), list) and len(r["data"]) >= 1, r)
    s, r = call("PUT", f"/api/v1/college/rounds/{r2['data']['id']}/results", {"results": [{"applicationId": app["id"], "result": "PASS"}]}, tA)
    s, r = call("POST", f"/api/v1/college/rounds/{r2['data']['id']}/complete", token=tA)
    check("complete final round -> SELECTED", s == 200, r)

    print("== Offer & placement")
    s, r = call("POST", "/api/v1/college/offers", {"studentId": asha["id"], "driveId": drive, "companyId": company, "roleTitle": "Software Engineer",
                                                 "ctcLpa": 9}, tA)
    check("record offer", s == 201 and r["data"]["tierName"] == "Dream", r)
    offer = r["data"]["id"]
    s, r = call("POST", f"/api/v1/college/student/offers/{offer}/respond", {"accept": True}, tS)
    check("student accepts offer", s == 200 and r["data"]["status"] == "ACCEPTED", r)
    s, r = call("GET", f"/api/v1/college/students/{asha['id']}", token=tA)
    check("student auto-marked PLACED", s == 200 and r["data"]["student"]["status"] == "PLACED", r)
    s, r = call("GET", f"/api/v1/college/drives/{clone}/eligibility", token=tA)
    row = [x for x in r["data"]["content"] if x["studentId"] == asha["id"]][0]
    check("dream policy: placed student ineligible for another same-tier drive", not row["eligible"] and row["reasons"], row)
    pdf = b"%PDF-1.4\n%smoke\n"
    s, r = call("POST", f"/api/v1/college/offers/{offer}/letter", files={"file": ("offer.pdf", pdf, "application/pdf")}, token=tA)
    check("upload offer letter (pdf)", s == 200 and r["data"]["offerLetterFileId"], r)
    s, r = call("POST", f"/api/v1/college/offers/{offer}/letter", files={"file": ("offer.pdf", b"MZ fake exe", "application/pdf")}, token=tA)
    check("reject file with wrong signature", s == 400, r)

    print("== Dashboard, reports, audit, search")
    s, r = call("GET", "/api/v1/college/analytics/dashboard", token=tA)
    k = (r.get("data") or {}).get("kpis", {})
    check("dashboard KPIs updated (placed=1)", s == 200 and k.get("placedStudents") == 1 and k.get("totalStudents") == 3, k)
    for t in ["placement-summary", "branch", "company", "ctc", "drive", "alumni", "skill-gap"]:
        s, body = call("GET", f"/api/v1/college/reports/{t}?format=xlsx", token=tA, raw=True)
        check(f"report {t} xlsx", s == 200 and body[:2] == b"PK")
    s, body = call("GET", "/api/v1/college/reports/placement-summary?format=pdf", token=tA, raw=True)
    check("placement summary pdf", s == 200 and body[:4] == b"%PDF")
    s, r = call("GET", "/api/v1/college/audit-logs?size=100", token=tA)
    actions = {e["action"] for e in r["data"]["content"]}
    check("audit log has import/offer/policy entries", {"STUDENTS_IMPORTED", "OFFER_CREATED", "SETTINGS_POLICY"} <= actions, actions)
    s, r = call("GET", "/api/v1/college/search?q=Asha", token=tA)
    check("global search", s == 200 and len(r["data"]["students"]) == 1, r)
    s, r = call("POST", "/api/v1/college/announcements", {"title": "Prep session", "body": "Tomorrow 10am", "filter": {"batchYears": [year]}, "sendEmail": False}, tA)
    check("announcement to filtered group", s == 200 and r["data"]["recipientCount"] == 3, r)

    print("== Team & roles")
    s, r = call("POST", "/api/v1/college/team/invites", {"email": f"viewer{RUN}@a{RUN}.edu.in", "fullName": "Viewer", "role": "COLLEGE_VIEWER"}, tA)
    check("invite viewer", s == 200 and r["data"]["devInviteToken"], r)
    s, r = call("POST", "/api/v1/college/auth/accept-invite", {"token": r["data"]["devInviteToken"], "password": "Viewer123"})
    check("accept invite -> session", s == 200 and r["data"]["role"] == "COLLEGE_VIEWER", r)
    tV = r["data"]["token"]
    s, r = call("GET", "/api/v1/college/students", token=tV)
    check("viewer can read", s == 200)
    s, r = call("POST", "/api/v1/college/companies", {"name": "Nope"}, tV)
    check("viewer cannot write (403)", s == 403, r)

    print("== Tenancy isolation")
    tB, _ = register_college("b")
    s, r = call("GET", f"/api/v1/college/students/{asha['id']}", token=tB)
    check("college B cannot read college A student (404)", s == 404, r)
    s, r = call("GET", "/api/v1/college/students?collegeId=" + sessA["collegeId"], token=tB)
    check("client-sent collegeId is ignored", s == 200 and r["data"]["totalElements"] == 0, r)
    s, r = call("GET", f"/api/v1/college/drives/{drive}/eligibility", token=tB)
    check("college B cannot read college A drive", s == 404, r)
    s, r = call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [app["id"]], "status": "REJECTED"}, tB)
    check("college B cannot modify college A application", s == 404, r)
    s, r = call("GET", "/api/v1/college/students")
    check("unauthenticated -> 401", s == 401, r)

    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
