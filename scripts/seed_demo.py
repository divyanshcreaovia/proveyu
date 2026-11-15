#!/usr/bin/env python3
"""
Seeds demo data through the public API (backend must run with SPRING_PROFILES_ACTIVE=dev so that
verification / invite / OTP tokens are returned in responses).

Creates: 1 demo college (auto-approved in dev), a team (TPO + coordinator + viewer), 4 branches,
50 students, 3 companies, 2 drives (one published with rounds, one draft), one recorded offer,
and a platform ADMIN account (via the local Docker MySQL container, dev only).

  python3 scripts/seed_demo.py [http://localhost:8080]

Idempotent: if the demo TPO can already log in, it exits without changes.
"""
import json
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
DOMAIN = "demo-college.edu.in"
TPO_EMAIL, TPO_PASS = f"tpo@{DOMAIN}", "Demo@1234"
COORD_EMAIL, VIEWER_EMAIL, TEAM_PASS = f"coordinator@{DOMAIN}", f"viewer@{DOMAIN}", "Team@1234"
STUDENT_PASS = "Student@123"
ADMIN_EMAIL, ADMIN_PASS = "admin@proveyu.local", "Admin@1234"
random.seed(42)


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, None


def ok(resp, what):
    s, r = resp
    if s >= 300:
        raise SystemExit(f"Seed failed at '{what}': HTTP {s} {r}")
    return r.get("data") if r else None


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def set_password(email, password):
    otp = ok(call("POST", "/api/v1/auth/forgot-password", {"email": email}), "forgot password")["devOtp"]
    ok(call("POST", "/api/v1/auth/reset-password", {"email": email, "resetToken": otp, "newPassword": password}), "reset password")


def main():
    if login(TPO_EMAIL, TPO_PASS):
        print("Demo data already present. Log in as", TPO_EMAIL, "/", TPO_PASS)
        return

    print("Registering demo college ...")
    reg = ok(call("POST", "/api/v1/college/auth/register", {
        "collegeName": "Proveyu Demo Institute of Technology", "aicteCode": "AICTE-DEMO-0001", "university": "University of Mumbai",
        "city": "Navi Mumbai", "state": "Maharashtra", "emailDomain": DOMAIN, "website": "https://demo-college.edu.in",
        "tpoName": "Dr. Meera Kulkarni", "tpoPhone": "9876500001", "tpoEmail": TPO_EMAIL, "password": TPO_PASS, "acceptTerms": True}), "register")
    ok(call("POST", "/api/v1/auth/verify-email", {"token": reg["devVerificationToken"]}), "verify e-mail")
    if reg["approvalStatus"] != "APPROVED":
        print("NOTE: college is PENDING approval (COLLEGE_REQUIRE_APPROVAL=true). Approve it as admin, then re-run is not needed.")
    t = login(TPO_EMAIL, TPO_PASS)

    print("Branches, batches, policy ...")
    branches = {}
    for code, name in [("CSE", "Computer Science & Engineering"), ("IT", "Information Technology"), ("ECE", "Electronics & Communication"), ("MECH", "Mechanical Engineering")]:
        branches[code] = ok(call("POST", "/api/v1/college/settings/branches", {"code": code, "name": name}, t), "branch")["id"]
    ok(call("PUT", "/api/v1/college/settings/policy", {"policyMode": "TIER_UPGRADE", "maxApplications": 15, "maxOffers": 2,
                                                       "upgradeMinHikePercent": 20, "blockAfterAccept": True}, t), "policy")

    print("Team ...")
    for email, name, role, scope in [(COORD_EMAIL, "Rahul Deshmukh", "COLLEGE_COORDINATOR", [branches["CSE"], branches["IT"]]),
                                     (VIEWER_EMAIL, "Priya Nair", "COLLEGE_VIEWER", [])]:
        inv = ok(call("POST", "/api/v1/college/team/invites", {"email": email, "fullName": name, "role": role,
                                                               "designation": "Placement " + role.split("_")[1].title(), "branchIds": scope}, t), "invite")
        ok(call("POST", "/api/v1/college/auth/accept-invite", {"token": inv["devInviteToken"], "password": TEAM_PASS}), "accept invite")

    print("50 students ...")
    first = ["Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Ayaan", "Krishna", "Ishaan", "Ananya", "Diya", "Saanvi",
             "Aadhya", "Pari", "Anika", "Navya", "Myra", "Sara", "Ira", "Kabir", "Rohan", "Neha", "Pooja", "Siddharth"]
    last = ["Sharma", "Patil", "Iyer", "Reddy", "Gupta", "Khan", "Joshi", "Menon", "Das", "Nair", "Kulkarni", "Singh", "Rao", "Shah"]
    skills_pool = ["Java", "Python", "SQL", "React", "Angular", "Spring Boot", "C++", "Data Structures", "AWS", "Machine Learning", "AutoCAD", "Embedded C"]
    year = time.localtime().tm_year + 1
    students = []
    codes = list(branches)
    for i in range(50):
        code = codes[i % 4]
        fn, ln = first[i % len(first)], last[(i * 5 + i // 3) % len(last)]
        backlogs = 0 if i % 9 else 2
        st = ok(call("POST", "/api/v1/college/students", {
            "rollNumber": f"{year % 100}{code}{i + 1:03d}", "firstName": fn, "lastName": ln, "email": f"{fn.lower()}.{ln.lower()}{i + 1}@{DOMAIN}",
            "phone": f"98{76540000 + i:08d}", "gender": "Female" if i % 2 else "Male", "branchId": branches[code],
            "batchYear": year if i < 40 else year - 1, "degree": "B.Tech", "cgpa": round(random.uniform(6.0, 9.7), 2),
            "tenthPercent": round(random.uniform(65, 97), 1), "twelfthPercent": round(random.uniform(60, 95), 1),
            "activeBacklogs": backlogs, "gapYears": 1 if i % 13 == 0 else 0,
            "skills": random.sample(skills_pool, 3)}, t), f"student {i}")
        students.append(st)
    for s in students[:3]:
        set_password(s["email"], STUDENT_PASS)

    print("Companies ...")
    companies = []
    for name, ind, mou in [("TechNova Solutions", "IT Services", "SIGNED"), ("Infinite Analytics", "Data & AI", "IN_PROGRESS"), ("Bharat Motors", "Automotive", "NONE")]:
        c = ok(call("POST", "/api/v1/college/companies", {"name": name, "industry": ind, "hqLocation": "Pune", "mouStatus": mou}, t), "company")
        ok(call("POST", f"/api/v1/college/companies/{c['id']}/contacts", {"name": "HR Manager", "designation": "Talent Acquisition",
                                                                          "email": "hr@" + name.split()[0].lower() + ".com", "primaryContact": True}, t), "contact")
        companies.append(c)

    print("Drives ...")
    iso = lambda days: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(time.time() + days * 86400))
    d1 = ok(call("POST", "/api/v1/college/drives", {
        "companyId": companies[0]["id"], "title": "TechNova Graduate Engineer Drive", "roleTitle": "Graduate Engineer Trainee", "driveType": "ON_CAMPUS",
        "ctcLpa": 6.5, "location": "Pune / Bengaluru", "bondMonths": 12, "mode": "OFFLINE", "venue": "Main Auditorium",
        "jobDescription": "Full-stack development on enterprise products.", "skills": ["Java", "SQL"], "registrationDeadline": iso(10),
        "driveDate": time.strftime("%Y-%m-%d", time.gmtime(time.time() + 14 * 86400)),
        "eligibility": {"minCgpa": 6.5, "maxActiveBacklogs": 0, "batchYears": [year], "branchIds": [branches["CSE"], branches["IT"], branches["ECE"]]}}, t), "drive 1")
    for n, typ in [("Online Aptitude", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")]:
        ok(call("POST", f"/api/v1/college/drives/{d1['id']}/rounds", {"name": n, "roundType": typ}, t), "round")
    ok(call("POST", f"/api/v1/college/drives/{d1['id']}/status", {"status": "PUBLISHED"}, t), "publish")
    preview = ok(call("GET", f"/api/v1/college/drives/{d1['id']}/eligibility?eligible=true&size=500", None, t), "eligibility")
    eligible_ids = [r["studentId"] for r in preview["content"]][:20]
    ok(call("POST", f"/api/v1/college/drives/{d1['id']}/applications", {"studentIds": eligible_ids}, t), "register applicants")

    ok(call("POST", "/api/v1/college/drives", {
        "companyId": companies[1]["id"], "title": "Infinite Analytics Data Scientist (Dream)", "roleTitle": "Associate Data Scientist",
        "driveType": "ON_CAMPUS", "ctcLpa": 12, "mode": "HYBRID", "skills": ["Python", "Machine Learning"], "registrationDeadline": iso(20),
        "eligibility": {"minCgpa": 8, "maxActiveBacklogs": 0, "requiredSkills": []}}, t), "drive 2")

    print("Offer from a previous-batch off-campus placement ...")
    alumni = [s for s in students if s["batchYear"] == year - 1][0]
    ok(call("POST", "/api/v1/college/offers", {"studentId": alumni["id"], "companyId": companies[2]["id"], "roleTitle": "Design Engineer",
                                               "ctcLpa": 4.8, "offerDate": time.strftime("%Y-%m-%d")}, t), "offer")

    print("Platform admin (dev only, via Docker MySQL) ...")
    s, _ = call("POST", "/api/v1/auth/register", {"email": ADMIN_EMAIL, "password": ADMIN_PASS, "fullName": "Platform Admin", "role": "CANDIDATE"})
    try:
        subprocess.run(["docker", "exec", "proveyu-mysql", "mysql", "-uroot", "-pproveyu_dev_root", "PROVEYU", "-e",
                        f"UPDATE users SET role='ADMIN', email_verified_at=NOW() WHERE email='{ADMIN_EMAIL}'"], check=True, capture_output=True)
        admin_note = f"{ADMIN_EMAIL} / {ADMIN_PASS}"
    except Exception as e:
        admin_note = f"(not created: {e})"

    print("\nDone. Demo accounts:")
    print(f"  TPO (college admin) : {TPO_EMAIL} / {TPO_PASS}")
    print(f"  Coordinator (CSE,IT): {COORD_EMAIL} / {TEAM_PASS}")
    print(f"  Viewer              : {VIEWER_EMAIL} / {TEAM_PASS}")
    for s in students[:3]:
        print(f"  Student             : {s['email']} / {STUDENT_PASS}")
    print(f"  Platform admin      : {admin_note}")


if __name__ == "__main__":
    main()
