#!/usr/bin/env python3
"""
Seeds one complete college through the public API, for a showcase database (LTCE by default):
college (approved by the platform admin), team, branches, policy, 60 students (all can sign in), companies, drives in every stage
(completed with offers, ongoing with rounds and interviews, open for applications, draft), offers accepted / pending, announcements,
chat, mock test attempts, a college practice test and a scheduled exam, off-campus jobs with applications, and a support ticket.

Run against a backend started with mail OFF (blank SPRING_MAIL_PASSWORD), app.dev.expose-tokens=true and the auth rate limit off,
so no e-mail reaches the student addresses and every password can be set:
    ADMIN_EMAIL=... ADMIN_PASSWORD=... TPO_PASSWORD=... STUDENT_PASSWORD=... python3 scripts/seed_college.py http://localhost:8080
Upload the 20 platform mock tests first (scripts/mocktests/load.py) so students can have attempts.
No files are uploaded (documents, offer letters, screenshots), so the database alone is complete.
"""
import datetime as dt
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")
ADMIN = (os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"])
TPO_PASS = os.environ["TPO_PASSWORD"]
TEAM_PASS = os.environ.get("TEAM_PASSWORD", TPO_PASS)
STUDENT_PASS = os.environ["STUDENT_PASSWORD"]

COLLEGE = {
    "collegeName": "Lokmanya Tilak College of Engineering", "aicteCode": "LTCE-NM-1994", "university": "University of Mumbai",
    "city": "Navi Mumbai", "state": "Maharashtra", "emailDomain": "ltce.in", "website": "https://ltce.in",
    "tpoName": "Training & Placement Officer", "tpoPhone": "9820012345", "tpoEmail": "tpo@ltce.in",
}
DOMAIN = COLLEGE["emailDomain"]
BRANCHES = [("COMP", "Computer Engineering"), ("IT", "Information Technology"), ("AIDS", "Artificial Intelligence & Data Science"),
            ("EXTC", "Electronics & Telecommunication"), ("MECH", "Mechanical Engineering")]
TEAM = [("sneha.patil", "Sneha Patil", "COLLEGE_COORDINATOR", ["COMP", "IT", "AIDS"], "Placement Coordinator (Computer, IT, AI&DS)"),
        ("rajesh.nair", "Rajesh Nair", "COLLEGE_VIEWER", [], "Head of Department, Mechanical")]
NAMES = [
    ("Divyansh", "Gupta", "M"), ("Aditi", "Kulkarni", "F"), ("Rohan", "Deshmukh", "M"), ("Sakshi", "Jadhav", "F"), ("Omkar", "Pawar", "M"),
    ("Tanvi", "Shinde", "F"), ("Yash", "Mehta", "M"), ("Pooja", "Sawant", "F"), ("Kunal", "Patil", "M"), ("Shruti", "Naik", "F"),
    ("Aniket", "More", "M"), ("Neha", "Chavan", "F"), ("Siddhesh", "Gaikwad", "M"), ("Priyanka", "Kadam", "F"), ("Harsh", "Joshi", "M"),
    ("Isha", "Bhosale", "F"), ("Pratik", "Salunkhe", "M"), ("Rutuja", "Mane", "F"), ("Atharva", "Kale", "M"), ("Mansi", "Gokhale", "F"),
    ("Vedant", "Iyer", "M"), ("Riya", "Pillai", "F"), ("Saurabh", "Mishra", "M"), ("Ankita", "Yadav", "F"), ("Nikhil", "Thakur", "M"),
    ("Snehal", "Raut", "F"), ("Akash", "Shetty", "M"), ("Kavya", "Menon", "F"), ("Tejas", "Wagh", "M"), ("Prachi", "Lokhande", "F"),
    ("Abhishek", "Singh", "M"), ("Shreya", "Kamath", "F"), ("Manish", "Tiwari", "M"), ("Gauri", "Phadke", "F"), ("Sahil", "Khan", "M"),
    ("Apurva", "Dalvi", "F"), ("Chinmay", "Apte", "M"), ("Madhura", "Ghag", "F"), ("Rahul", "Verma", "M"), ("Nikita", "Rane", "F"),
    ("Ajinkya", "Pande", "M"), ("Simran", "Kaur", "F"), ("Varun", "Nambiar", "M"), ("Payal", "Shah", "F"), ("Aditya", "Bhagat", "M"),
    ("Sayali", "Kelkar", "F"), ("Prathamesh", "Gawde", "M"), ("Diksha", "Sonawane", "F"), ("Ritesh", "Dubey", "M"), ("Komal", "Pujari", "F"),
    ("Swapnil", "Mhatre", "M"), ("Ashwini", "Tambe", "F"), ("Mayur", "Kharat", "M"), ("Bhakti", "Parab", "F"), ("Shubham", "Kokate", "M"),
    ("Vaishnavi", "Hegde", "F"), ("Darshan", "Mali", "M"), ("Pranali", "Kore", "F"), ("Soham", "Vaidya", "M"), ("Anushka", "Sathe", "F"),
]
SKILLS = {"COMP": ["Java", "Python", "SQL", "Data Structures", "Spring Boot", "React", "Angular", "AWS"],
          "IT": ["Java", "Python", "SQL", "JavaScript", "React", "Node.js", "Cloud", "Linux"],
          "AIDS": ["Python", "Machine Learning", "SQL", "Pandas", "Deep Learning", "Power BI", "Statistics"],
          "EXTC": ["Embedded C", "C++", "IoT", "MATLAB", "VLSI", "Python", "Networking"],
          "MECH": ["AutoCAD", "SolidWorks", "CATIA", "ANSYS", "Python", "Six Sigma"]}
random.seed(1994)


def call(method, path, body=None, token=None, raw=None, ctype=None):
    h = {}
    if token: h["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        data = json.dumps(body).encode(); h["Content-Type"] = "application/json"
    if raw is not None:
        data = raw; h["Content-Type"] = ctype
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            b = r.read()
            return r.status, json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, {}


def ok(resp, what):
    s, r = resp
    if s >= 300: raise SystemExit(f"Seed failed at '{what}': HTTP {s} {r}")
    return (r or {}).get("data")


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def set_password(email, password):
    otp = ok(call("POST", "/api/v1/auth/forgot-password", {"email": email}), f"otp {email}").get("devOtp")
    if not otp: raise SystemExit("No devOtp: start the backend with app.dev.expose-tokens=true")
    ok(call("POST", "/api/v1/auth/reset-password", {"email": email, "resetToken": otp, "newPassword": password}), f"password {email}")


def multipart(fields, files):
    b = uuid.uuid4().hex
    out = b""
    for k, v in fields.items():
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
    for name, fname, ctype, data in files:
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{fname}\"\r\nContent-Type: {ctype}\r\n\r\n".encode() + data + b"\r\n"
    return out + f"--{b}--\r\n".encode(), f"multipart/form-data; boundary={b}"


iso = lambda days: (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=days)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
day = lambda days: (dt.date.today() + dt.timedelta(days=days)).isoformat()


def main():
    admin = login(*ADMIN) or sys.exit("Admin login failed (ADMIN_EMAIL / ADMIN_PASSWORD)")
    if login(COLLEGE["tpoEmail"], TPO_PASS):
        sys.exit("This college is already seeded.")

    print("College, approval, settings ...")
    reg = ok(call("POST", "/api/v1/college/auth/register", {**COLLEGE, "password": TPO_PASS, "acceptTerms": True}), "register")
    ok(call("POST", "/api/v1/auth/verify-email", {"token": reg["devVerificationToken"]}), "verify")
    if reg.get("approvalStatus") == "PENDING":
        cid = next(c["collegeId"] for c in ok(call("GET", "/api/v1/admin/colleges?status=PENDING", token=admin), "pending") if c["tpoEmail"] == COLLEGE["tpoEmail"])
        ok(call("POST", f"/api/v1/admin/colleges/{cid}/decision", {"status": "APPROVED", "note": "Verified with the college"}, admin), "approve")
    t = login(COLLEGE["tpoEmail"], TPO_PASS)
    me = ok(call("GET", "/api/v1/college/me", token=t), "me")
    br = {code: ok(call("POST", "/api/v1/college/settings/branches", {"code": code, "name": name}, t), "branch")["id"] for code, name in BRANCHES}
    ok(call("PUT", "/api/v1/college/settings/policy", {"policyMode": "TIER_UPGRADE", "maxApplications": 12, "maxOffers": 2,
                                                       "upgradeMinHikePercent": 25, "blockAfterAccept": True}, t), "policy")
    team = {}
    for user, name, role, scope, desig in TEAM:
        inv = ok(call("POST", "/api/v1/college/team/invites", {"email": f"{user}@{DOMAIN}", "fullName": name, "role": role, "designation": desig,
                                                               "branchIds": [br[c] for c in scope]}, t), "invite")
        ok(call("POST", "/api/v1/college/auth/accept-invite", {"token": inv["devInviteToken"], "password": TEAM_PASS}), "accept invite")
        team[role] = login(f"{user}@{DOMAIN}", TEAM_PASS)

    print("60 students ...")
    final_year, third_year = 2026, 2027
    codes = [c for c, _ in BRANCHES]
    weights = ["COMP"] * 16 + ["IT"] * 12 + ["AIDS"] * 12 + ["EXTC"] * 10 + ["MECH"] * 10
    students = []
    counters = {}
    for i, (fn, ln, g) in enumerate(NAMES):
        code = weights[i]
        year = third_year if i % 4 == 3 else final_year  # 45 final-year and 15 third-year students, in every branch
        counters[(code, year)] = counters.get((code, year), 0) + 1
        roll = f"{(year - 4) % 100}{code}{counters[(code, year)]:03d}"
        cg = round(min(9.8, max(5.6, random.gauss(7.6, 0.9))), 2) if i else 8.42
        st = ok(call("POST", "/api/v1/college/students", {
            "rollNumber": roll, "firstName": fn, "lastName": ln, "email": f"{fn.lower()}.{ln.lower()}@{DOMAIN}",
            "phone": f"9{random.randint(100000000, 999999999)}", "gender": "Female" if g == "F" else "Male", "branchId": br[code], "batchYear": year,
            "degree": "B.E.", "cgpa": cg, "tenthPercent": round(random.uniform(72, 96), 1), "twelfthPercent": round(random.uniform(65, 94), 1),
            "activeBacklogs": 1 if i % 17 == 9 else 0, "gapYears": 0, "skills": random.sample(SKILLS[code], 4)}, t), f"student {fn}")
        st["code"] = code
        students.append(st)
    tokens = {}
    for s in students:
        set_password(s["email"], STUDENT_PASS)
        tokens[s["id"]] = login(s["email"], STUDENT_PASS)

    print("Companies ...")
    comp = {}
    for name, ind, hq, mou, tier in [("Tata Consultancy Services", "IT Services", "Mumbai", "SIGNED", "Mass"),
                                    ("Accenture", "IT Services & Consulting", "Mumbai", "SIGNED", "Mass"),
                                    ("Infosys", "IT Services", "Pune", "SIGNED", "Mass"),
                                    ("Capgemini", "IT Services", "Mumbai", "IN_PROGRESS", "Mass"),
                                    ("LTIMindtree", "IT Services", "Mumbai", "SIGNED", "Mass"),
                                    ("Deloitte", "Consulting", "Mumbai", "NONE", "Dream"),
                                    ("Larsen & Toubro", "Engineering & Construction", "Mumbai", "SIGNED", "Core")]:
        c = ok(call("POST", "/api/v1/college/companies", {"name": name, "industry": ind, "hqLocation": hq, "mouStatus": mou}, t), "company")
        ok(call("POST", f"/api/v1/college/companies/{c['id']}/contacts", {"name": "Campus Hiring Team", "designation": "Talent Acquisition",
                                                                          "primaryContact": True}, t), "contact")
        comp[name] = c["id"]

    def drive(company, title, role, ctc, codes_, minc, mode="OFFLINE", deadline=12, skills=("Java", "SQL"), jd="", rounds=(), publish=True, dtype="ON_CAMPUS",
              location="Mumbai / Pune", stipend=None):
        body = {"companyId": comp[company], "title": title, "roleTitle": role, "driveType": dtype, "location": location, "mode": mode,
                "venue": "LTCE Seminar Hall, Koparkhairane" if mode != "ONLINE" else None, "jobDescription": jd, "skills": list(skills),
                "registrationDeadline": iso(deadline), "driveDate": day(deadline + 4),
                "eligibility": {"minCgpa": minc, "maxActiveBacklogs": 0, "batchYears": [final_year], "branchIds": [br[c] for c in codes_]}}
        if ctc: body["ctcLpa"] = ctc
        if stipend: body["stipendMonthly"] = stipend
        d = ok(call("POST", "/api/v1/college/drives", body, t), title)
        for n, typ in rounds:
            ok(call("POST", f"/api/v1/college/drives/{d['id']}/rounds", {"name": n, "roundType": typ}, t), "round")
        if publish: ok(call("POST", f"/api/v1/college/drives/{d['id']}/status", {"status": "PUBLISHED"}, t), "publish")
        return d

    def eligible(d):
        return [r["studentId"] for r in ok(call("GET", f"/api/v1/college/drives/{d['id']}/eligibility?eligible=true&size=500", token=t), "eligibility")["content"]]

    def apps(d):
        return ok(call("GET", f"/api/v1/college/drives/{d['id']}/applications?size=200", token=t), "applications")["content"]

    def run_round(rid, results):
        ok(call("POST", f"/api/v1/college/rounds/{rid}/start", None, t), "start round")
        ok(call("PUT", f"/api/v1/college/rounds/{rid}/results", {"results": results}, t), "results")
        ok(call("POST", f"/api/v1/college/rounds/{rid}/complete", None, t), "complete round")

    print("Drive 1: TCS (completed, offers) ...")
    tcs = drive("Tata Consultancy Services", "TCS NQT Hiring 2026 - Ninja & Digital", "Assistant System Engineer", 3.6, ["COMP", "IT", "AIDS", "EXTC"], 6.0,
                jd="TCS National Qualifier Test based hiring.\nNinja: 3.36 LPA, Digital: 7 LPA for top performers.\nTraining at TCS Initial Learning Program.",
                rounds=[("TCS NQT (Aptitude + Coding)", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], deadline=2)
    ids = eligible(tcs)[:24]
    ok(call("POST", f"/api/v1/college/drives/{tcs['id']}/applications", {"studentIds": ids}, t), "register tcs")
    a = apps(tcs)
    ok(call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [x["id"] for x in a], "status": "SHORTLISTED"}, t), "shortlist")
    rounds = ok(call("GET", f"/api/v1/college/drives/{tcs['id']}/rounds", token=t), "rounds")
    alive = [x["id"] for x in a]
    for k, rd in enumerate(rounds):
        res = []
        for j, aid in enumerate(alive):
            passed = (j % 4 != 3) if k == 0 else (j % 5 != 4)
            res.append({"applicationId": aid, "result": "PASS" if passed else "FAIL", "score": random.randint(55, 92)})
        run_round(rd["id"], res)
        alive = [r["applicationId"] for r in res if r["result"] == "PASS"]
    by_app = {x["id"]: x for x in a}
    selected = [by_app[x]["studentId"] for x in alive]
    tcs_offers = []
    for j, sid in enumerate(selected):
        o = ok(call("POST", "/api/v1/college/offers", {"studentId": sid, "driveId": tcs["id"], "companyId": comp["Tata Consultancy Services"],
                                                       "roleTitle": "Assistant System Engineer" + (" (Digital)" if j < 2 else " (Ninja)"),
                                                       "ctcLpa": 7.0 if j < 2 else 3.6, "offerDate": day(-3), "joiningDate": day(200)}, t), "tcs offer")
        tcs_offers.append((sid, o["id"]))
    for j, (sid, oid) in enumerate(tcs_offers):
        if j % 6 != 5:  # most accept, one is still thinking
            ok(call("POST", f"/api/v1/college/student/offers/{oid}/respond", {"accept": True}, tokens[sid]), "accept")
    ok(call("POST", f"/api/v1/college/drives/{tcs['id']}/status", {"status": "COMPLETED"}, t), "complete tcs")
    placed = {sid for sid, _ in tcs_offers}

    print("Drive 2: Accenture (ongoing, interviews) ...")
    acc = drive("Accenture", "Accenture Associate Software Engineer 2026", "Associate Software Engineer", 4.6, ["COMP", "IT", "AIDS"], 6.5,
                jd="Cognitive + technical assessment, coding round and a communication assessment, then interview.",
                rounds=[("Cognitive & Technical Assessment", "APTITUDE"), ("Coding Round", "CODING"), ("Technical + HR Interview", "TECHNICAL")],
                deadline=3, skills=("Java", "Python", "SQL"))
    ids = [x for x in eligible(acc) if x not in placed][:18]
    ok(call("POST", f"/api/v1/college/drives/{acc['id']}/applications", {"studentIds": ids}, t), "register acc")
    a = apps(acc)
    ok(call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [x["id"] for x in a], "status": "SHORTLISTED"}, t), "shortlist")
    rounds = ok(call("GET", f"/api/v1/college/drives/{acc['id']}/rounds", token=t), "rounds")
    res = [{"applicationId": x["id"], "result": "FAIL" if j % 3 == 2 else "PASS", "score": random.randint(50, 90)} for j, x in enumerate(a)]
    run_round(rounds[0]["id"], res)
    alive = [r["applicationId"] for r in res if r["result"] == "PASS"]
    res = [{"applicationId": x, "result": "FAIL" if j % 4 == 3 else "PASS", "score": random.randint(60, 95)} for j, x in enumerate(alive)]
    run_round(rounds[1]["id"], res)
    alive = [r["applicationId"] for r in res if r["result"] == "PASS"]
    ok(call("POST", f"/api/v1/college/rounds/{rounds[2]['id']}/start", None, t), "start interviews")
    for j, aid in enumerate(alive[:6]):
        start = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)).replace(hour=4 + j // 3, minute=(j % 3) * 20, second=0, microsecond=0)
        ok(call("POST", "/api/v1/college/interviews", {"driveId": acc["id"], "roundId": rounds[2]["id"], "applicationId": aid,
                                                       "panelName": "Panel " + "AB"[j % 2], "startsAt": start.isoformat().replace("+00:00", "Z"),
                                                       "endsAt": (start + dt.timedelta(minutes=20)).isoformat().replace("+00:00", "Z"),
                                                       "room": "Placement Cell, Room 21" + str(j % 2 + 1)}, t), "interview")

    print("Drives 3-6: open, draft, core ...")
    inf = drive("Infosys", "Infosys Systems Engineer / Specialist Programmer", "Systems Engineer", 3.6, ["COMP", "IT", "AIDS", "EXTC"], 6.0, deadline=9,
                jd="Online test (reasoning, math, verbal, pseudo-code) followed by an interview.\nSpecialist Programmer: 9.5 LPA via HackWithInfy.",
                rounds=[("Online Test", "APTITUDE"), ("Interview", "TECHNICAL")])
    cap = drive("Capgemini", "Capgemini Analyst Hiring", "Analyst", 4.25, ["COMP", "IT", "AIDS", "EXTC", "MECH"], 6.0, deadline=14, mode="ONLINE",
                jd="Pseudo-code, English and game-based aptitude, then a technical + HR interview.", rounds=[("Online Assessment", "APTITUDE"), ("Interview", "HR")])
    drive("Deloitte", "Deloitte USI Analyst Trainee", "Analyst Trainee", 7.6, ["COMP", "IT", "AIDS"], 7.5, deadline=21,
          jd="Consulting and technology roles. Group discussion and interviews.", rounds=[("Online Test", "APTITUDE"), ("Group Discussion", "GD"), ("Interview", "TECHNICAL")],
          publish=False)
    lnt = drive("Larsen & Toubro", "L&T Graduate Engineer Trainee (Mechanical)", "Graduate Engineer Trainee", 6.0, ["MECH"], 6.5, deadline=10,
                skills=("AutoCAD", "SolidWorks"), jd="Core engineering GET role at L&T project sites.", rounds=[("Technical Test", "APTITUDE"), ("Interview", "TECHNICAL")],
                location="Mumbai / Chennai")
    drive("LTIMindtree", "LTIMindtree Summer Internship 2026", "Software Engineering Intern", None, ["COMP", "IT", "AIDS"], 7.0, deadline=18, mode="ONLINE",
          dtype="INTERNSHIP", stipend=20000, jd="8-week internship with a pre-placement interview for top interns.", rounds=[("Online Test", "APTITUDE"), ("Interview", "TECHNICAL")],
          location="Mumbai (Hybrid)")
    for d, n in [(inf, 14), (cap, 9), (lnt, 6)]:
        mine = [x for x in eligible(d) if x not in placed][:n]
        for sid in mine:
            ok(call("POST", f"/api/v1/college/student/drives/{d['id']}/apply", token=tokens[sid]), "apply")

    print("Off-campus offer, announcements, chat ...")
    extra = next(s for s in students if s["code"] == "MECH" and s["batchYear"] == final_year and s["id"] not in placed)
    o = ok(call("POST", "/api/v1/college/offers", {"studentId": extra["id"], "companyId": comp["Larsen & Toubro"], "roleTitle": "Design Engineer Intern (PPO)",
                                                   "ctcLpa": 5.5, "offerDate": day(-10), "remarks": "Pre-placement offer after summer internship",
                                                   "overridePolicy": True, "overrideReason": "PPO from internship"}, t), "ppo")
    ok(call("POST", f"/api/v1/college/student/offers/{o['id']}/respond", {"accept": True}, tokens[extra["id"]]), "accept ppo")
    for title, body in [("Placement season 2026: important instructions",
                         "Keep your resume, 10th / 12th marksheets and semester marksheets updated in the Document locker.\nCarry your college ID card for every drive."),
                        ("TCS NQT results are out", "Congratulations to everyone selected! Offer letters will be shared on your registered e-mail by TCS."),
                        ("Accenture interviews this week", "Shortlisted students: check the Interviews page for your slot. Formal dress code."),
                        ("Pre-placement training", "Aptitude and soft-skills sessions every Saturday, 10 AM - 1 PM, Seminar Hall.")]:
        ok(call("POST", "/api/v1/college/announcements", {"title": title, "body": body, "filter": {}, "sendEmail": False}, t), "announcement")
    div = students[0]
    div["userId"] = ok(call("GET", "/api/v1/college/student/profile", token=tokens[div["id"]]), "profile")["userId"]
    coord = team["COLLEGE_COORDINATOR"]
    for tok, text in [(t, "Hi Divyansh, your Accenture technical interview is on the Interviews page. All the best!"),
                      (coord, "Please update your resume in the Document locker before Friday.")]:
        ok(call("POST", "/api/v1/chat/messages", {"receiverId": div["userId"], "message": text}, tok), "chat")
    ok(call("POST", "/api/v1/chat/messages", {"receiverId": me["userId"], "message": "Thank you ma'am, I will be there on time."}, tokens[div["id"]]), "chat reply")

    print("Mock tests: attempts ...")
    for s in students[:20]:
        tk = tokens[s["id"]]
        tests = ok(call("GET", "/api/v1/college/student/prep/tests", token=tk), "tests")
        first = next((x for x in tests if x.get("order") == 1), None)
        if not first: break
        v = ok(call("POST", f"/api/v1/college/student/prep/tests/{first['id']}/start", token=tk), "start mock")
        answers = {q["id"]: {"option": random.randrange(len(q["options"]))} for q in v["questions"] if q["type"] == "MCQ" and random.random() < 0.85}
        ok(call("POST", f"/api/v1/college/student/prep/attempts/{v['attemptId']}/submit", answers, tk), "submit mock")

    print("College practice test + scheduled sessional exam ...")
    def upload_test(defn):
        body, ct = multipart({}, [("file", "test.json", "application/json", json.dumps(defn).encode())])
        return ok(call("POST", "/api/v1/college/prep/tests", token=t, raw=body, ctype=ct), "upload test")
    practice = upload_test({"title": "TCS NQT Pattern - Practice Set", "description": "Practice set in the TCS NQT pattern, prepared by the placement cell.",
                            "companyTag": "TCS", "durationMinutes": 30, "sections": [{"name": "Numerical Ability", "questions": [
        {"type": "MCQ", "text": "A shopkeeper sells an item at 20% profit. If the cost price is Rs 500, what is the selling price?", "options": ["Rs 550", "Rs 600", "Rs 620", "Rs 650"], "answer": 1},
        {"type": "MCQ", "text": "The average of 5 numbers is 24. If one number is removed the average becomes 22. Which number was removed?", "options": ["28", "30", "32", "34"], "answer": 2},
        {"type": "MCQ", "text": "Pipe A fills a tank in 6 hours and pipe B in 3 hours. Together they fill it in:", "options": ["1.5 h", "2 h", "2.5 h", "4.5 h"], "answer": 1}]},
        {"name": "Verbal Ability", "questions": [
        {"type": "MCQ", "text": "Choose the word closest in meaning to 'METICULOUS'.", "options": ["Careless", "Careful", "Quick", "Rough"], "answer": 1},
        {"type": "MCQ", "text": "Fill in the blank: She has been working here ____ 2022.", "options": ["for", "since", "from", "by"], "answer": 1}]}]})
    ok(call("PATCH", f"/api/v1/college/prep/tests/{practice['id']}/publish", {"published": True}, t), "publish practice")
    exam = upload_test({"title": "Sessional Exam 1 - Database Management Systems", "description": "Semester VII sessional, Computer / IT / AI&DS.",
                        "durationMinutes": 45, "sections": [{"name": "MCQ", "pick": 4, "questions": [
        {"type": "MCQ", "text": "Which normal form removes transitive dependency?", "options": ["1NF", "2NF", "3NF", "BCNF"], "answer": 2, "marks": 2},
        {"type": "MCQ", "text": "Which SQL clause filters groups?", "options": ["WHERE", "HAVING", "ORDER BY", "GROUP BY"], "answer": 1, "marks": 2},
        {"type": "MCQ", "text": "A primary key can contain NULL values.", "options": ["True", "False"], "answer": 1, "marks": 2},
        {"type": "MCQ", "text": "Which property of a transaction ensures all-or-nothing?", "options": ["Atomicity", "Consistency", "Isolation", "Durability"], "answer": 0, "marks": 2},
        {"type": "MCQ", "text": "Which join returns all rows of the left table?", "options": ["INNER", "LEFT OUTER", "RIGHT OUTER", "CROSS"], "answer": 1, "marks": 2}]},
        {"name": "Descriptive", "questions": [
        {"type": "TEXT", "title": "Normalization", "text": "Explain 2NF and 3NF with an example table.", "marks": 6,
         "explanation": "Partial dependency removed in 2NF; transitive dependency removed in 3NF; example with student-course-teacher."}]}]})
    start = dt.datetime.now(dt.timezone.utc).replace(hour=4, minute=30, second=0, microsecond=0) + dt.timedelta(days=5)  # 10:00 IST
    ok(call("PUT", f"/api/v1/college/prep/tests/{exam['id']}/settings", {
        "mode": "EXAM", "startsAt": start.isoformat().replace("+00:00", "Z"), "endsAt": (start + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
        "branchIds": [br["COMP"], br["IT"], br["AIDS"]], "batchYears": [final_year], "shuffleQuestions": True, "shuffleOptions": True,
        "showAnswers": False, "resultsReleased": False}, t), "exam settings")
    ok(call("PATCH", f"/api/v1/college/prep/tests/{exam['id']}/publish", {"published": True}, t), "publish exam")
    for s in students[1:9]:
        tk = tokens[s["id"]]
        v = ok(call("POST", f"/api/v1/college/student/prep/tests/{practice['id']}/start", token=tk), "start practice")
        ok(call("POST", f"/api/v1/college/student/prep/attempts/{v['attemptId']}/submit",
                {q["id"]: {"option": random.randrange(len(q["options"]))} for q in v["questions"]}, tk), "submit practice")

    print("Off-campus jobs ...")
    jobs = []
    for co, role, jt, mode, loc, cmin, cmax, url, sk in [
            ("Zoho", "Member Technical Staff", "FULL_TIME", "ONSITE", "Chennai", 6, 8, "https://careers.zohocorp.com", ["Java", "Data Structures"]),
            ("Freshworks", "Software Engineer - Freshers", "FULL_TIME", "HYBRID", "Chennai / Bengaluru", 8, 12, "https://www.freshworks.com/company/careers/", ["Java", "Python", "SQL"]),
            ("Persistent Systems", "Graduate Engineer Trainee", "FULL_TIME", "ONSITE", "Pune / Nagpur", 4.2, 6, "https://www.persistent.com/careers/", ["Java", "SQL"]),
            ("Mahindra & Mahindra", "Graduate Engineer Trainee - Mechanical", "FULL_TIME", "ONSITE", "Mumbai / Nashik", 5, 6.5, "https://www.mahindra.com/careers", ["AutoCAD", "SolidWorks"])]:
        jid = ok(call("POST", "/api/v1/admin/off-campus/jobs", {"companyName": co, "roleTitle": role, "jobType": jt, "workMode": mode, "location": loc,
                                                                "ctcMinLpa": cmin, "ctcMaxLpa": cmax, "description": f"{role} hiring for the 2026 batch.",
                                                                "skills": sk, "applyUrl": url, "applyDeadline": iso(20), "batchYears": [final_year],
                                                                "collegeIds": []}, admin), "job")["id"]
        ok(call("POST", f"/api/v1/admin/off-campus/jobs/{jid}/status", {"status": "PUBLISHED"}, admin), "publish job")
        jobs.append(jid)
    S = "/api/v1/college/student/off-campus"
    for s in students[2:8]:
        tk = tokens[s["id"]]
        if call("POST", f"{S}/{jobs[0]}/click", {}, tk)[0] == 200:
            call("PUT", f"{S}/{jobs[0]}/status", {"status": "APPLIED"}, tk)

    print("Support ticket ...")
    tk = ok(call("POST", "/api/v1/support/tickets", {"category": "PLACEMENT", "subject": "Unable to see Capgemini drive",
                                                     "message": "I am from Mechanical. Is the Capgemini drive open for us? I cannot find the Apply button."}, tokens[extra["id"]]), "ticket")
    ok(call("POST", f"/api/v1/admin/support/{tk['id']}/reply", {"reply": "Yes, Mechanical is eligible. Please refresh College > Drives; the Apply button is on the drive page.",
                                                                "close": False, "sendEmail": False}, admin), "reply")

    print(f"\nDone: {COLLEGE['collegeName']}, {len(students)} students, {len(tcs_offers) + 1} offers.")
    print(f"  TPO: {COLLEGE['tpoEmail']}   coordinator: sneha.patil@{DOMAIN}   viewer: rajesh.nair@{DOMAIN}   student: {students[0]['email']}")


if __name__ == "__main__":
    main()
