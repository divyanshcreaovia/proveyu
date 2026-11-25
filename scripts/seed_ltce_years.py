#!/usr/bin/env python3
"""
Extends the LTCE showcase college (seeded by seed_college.py) to three batches with a full story, for the CXO / college dashboards:

  Batch 2025  last season (Aug 2025 - Mar 2026), finished: ~75% placed, offers accepted / joined / declined, Dream and Super Dream
  Batch 2026  current season (from Aug 2026): completed drives with offers, one drive in interviews, upcoming drives
  Batch 2027  pre-final year: summer internships with PPOs, an upcoming internship, mock tests and AI interviews

Everything with business rules goes through the public API (students, companies, drives, rounds, results, interviews, offers and
answers, announcements, mock test attempts with proctoring, off-campus jobs, chat), so placed status, highest CTC, tiers, scores and
notifications are the application's own. Afterwards plain SQL only moves dates into the past (the API stamps "now") and adds
AI mock interviews (their scoring needs the AI service). Companies and drives stay at 30 at most.

Run against a backend started with mail OFF (blank SPRING_MAIL_PASSWORD), APP_DEV_EXPOSE_TOKENS=true and RATE_LIMIT_ENABLED=false.
Existing passwords are kept: the script remembers every LTCE password hash, signs in with a temporary password and puts the
original hashes back at the end; new students get the same password as the existing students.

    BASE=http://localhost:8080 ADMIN_EMAIL=... ADMIN_PASSWORD=... DB_NAME=... DB_USER=... DB_PASS=... python3 scripts/seed_ltce_years.py
"""
import datetime as dt
import json
import os
import random
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("BASE", "http://localhost:8080").rstrip("/")
ADMIN = (os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"])
DB = os.environ["DB_NAME"]
TEMP = "Seed@" + uuid.uuid4().hex[:10]          # only while seeding; original hashes are restored at the end
DOMAIN = "ltce.in"
REF_STUDENT = "divyansh.gupta@ltce.in"          # new students get this student's password
random.seed(2025)

# --------------------------------------------------------------------------------------------------------------- plumbing

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
        with urllib.request.urlopen(req, timeout=90) as r:
            b = r.read()
            return r.status, json.loads(b) if b else {}
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, {}


def ok(resp, what):
    s, r = resp
    if s >= 300 or (isinstance(r, dict) and r.get("success") is False):
        raise SystemExit(f"FAILED {what}: HTTP {s} {json.dumps(r)[:400]}")
    return r.get("data", r) if isinstance(r, dict) else r


def soft(resp, what):
    """Like ok() but a refusal (a business rule) is reported and skipped."""
    s, r = resp
    if s >= 300 or (isinstance(r, dict) and r.get("success") is False):
        print(f"   skipped {what}: {(r.get('message') if isinstance(r, dict) else r)}")
        return None
    return r.get("data", r) if isinstance(r, dict) else r


def sql(q, fetch=False):
    env = dict(os.environ, MYSQL_PWD=os.environ.get("DB_PASS", ""))
    args = ["mysql", "-h127.0.0.1", "-u" + os.environ.get("DB_USER", "root"), "-N", "-B", "--raw", DB]
    out = subprocess.run(args, input=q, capture_output=True, text=True, env=env)
    if out.returncode != 0: raise SystemExit("SQL failed: " + out.stderr[:500] + "\n" + q[:300])
    return [ln.split("\t") for ln in out.stdout.splitlines()] if fetch else None


def q(v):
    if v is None: return "NULL"
    if isinstance(v, (int, float)): return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def set_password(email, password):
    otp = ok(call("POST", "/api/v1/auth/forgot-password", {"email": email}), f"otp {email}").get("devOtp")
    if not otp: raise SystemExit("No devOtp: start the backend with APP_DEV_EXPOSE_TOKENS=true")
    ok(call("POST", "/api/v1/auth/reset-password", {"email": email, "resetToken": otp, "newPassword": password}), f"password {email}")


def multipart(fields):
    b = uuid.uuid4().hex
    out = b""
    for k, v in fields.items():
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
    return out + f"--{b}--\r\n".encode(), f"multipart/form-data; boundary={b}"


now = dt.datetime.now(dt.timezone.utc)
iso = lambda days: (now + dt.timedelta(days=days)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
day = lambda days: (dt.date.today() + dt.timedelta(days=days)).isoformat()
D = lambda s: dt.date.fromisoformat(s)

# --------------------------------------------------------------------------------------------------------------- data

M_FIRST = ["Aarav", "Vihaan", "Arjun", "Sai", "Ishaan", "Shaurya", "Atharv", "Ayush", "Parth", "Rudra", "Pranav", "Siddharth", "Rohit", "Amey",
           "Tushar", "Nilesh", "Hrishikesh", "Sarthak", "Kedar", "Mihir", "Ninad", "Onkar", "Piyush", "Sagar", "Sanket", "Shantanu", "Sujay",
           "Tanmay", "Vaibhav", "Viraj", "Yogesh", "Faizan", "Arnav", "Dhruv", "Kartik", "Lakshya", "Manas", "Neel", "Pratham", "Raghav",
           "Samarth", "Shivam", "Vinay", "Aniruddha", "Gaurav", "Harshal", "Jayesh", "Mandar", "Prasad", "Rushikesh"]
F_FIRST = ["Aanya", "Aadhya", "Ananya", "Diya", "Ira", "Myra", "Saanvi", "Siya", "Anvi", "Avni", "Gargi", "Janhavi", "Ketaki", "Mrunal",
           "Nupur", "Pallavi", "Radhika", "Rasika", "Sanika", "Shravani", "Urvi", "Vedika", "Yashashri", "Zoya", "Aishwarya", "Bhagyashree",
           "Chaitali", "Devika", "Esha", "Harshada", "Juhi", "Leena", "Manasi", "Rucha", "Shweta", "Trupti", "Amruta", "Dnyanada", "Kirti", "Sonal"]
LAST = ["Patil", "Deshpande", "Kulkarni", "Joshi", "Pawar", "Jadhav", "Shinde", "Gaikwad", "Chavan", "Kamble", "Sawant", "Bhosale",
        "Deshmukh", "Naik", "Shetty", "Nair", "Menon", "Iyer", "Rao", "Reddy", "Sharma", "Verma", "Gupta", "Yadav", "Mishra", "Pandey",
        "Shaikh", "Ansari", "Fernandes", "Pinto", "Thakur", "Rane", "Parab", "Mhatre", "Patkar", "Salvi", "Karande", "Waghmare", "Ghadge", "Lad",
        "Chougule", "Borse", "Khot", "Mulik", "Bapat", "Kher", "Tendulkar", "Sathe", "Hegde", "Bhide"]
SKILLS = {"COMP": ["Java", "Python", "SQL", "Data Structures", "Spring Boot", "React", "Angular", "AWS", "Docker"],
          "IT": ["Java", "Python", "SQL", "JavaScript", "React", "Node.js", "Cloud", "Linux", "Git"],
          "AIDS": ["Python", "Machine Learning", "SQL", "Pandas", "Deep Learning", "Power BI", "Statistics", "TensorFlow"],
          "EXTC": ["Embedded C", "C++", "IoT", "MATLAB", "VLSI", "Python", "Networking", "Verilog"],
          "MECH": ["AutoCAD", "SolidWorks", "CATIA", "ANSYS", "Python", "Six Sigma", "GD&T"]}
NEW_STUDENTS = {2025: {"COMP": 18, "IT": 14, "AIDS": 12, "EXTC": 10, "MECH": 10},
                2026: {"COMP": 7, "IT": 6, "AIDS": 5, "EXTC": 4, "MECH": 3},
                2027: {"COMP": 10, "IT": 8, "AIDS": 7, "EXTC": 5, "MECH": 5}}
COMPANIES = [  # name, industry, hq, mou  (21 new; 7 exist -> 28)
    ("Wipro", "IT Services", "Bengaluru", "SIGNED"), ("Cognizant", "IT Services", "Chennai", "SIGNED"),
    ("HCLTech", "IT Services", "Noida", "SIGNED"), ("Tech Mahindra", "IT Services", "Pune", "SIGNED"),
    ("Persistent Systems", "Software Products & Services", "Pune", "SIGNED"), ("ZS Associates", "Consulting & Analytics", "Pune", "IN_PROGRESS"),
    ("Morgan Stanley", "Financial Services", "Mumbai", "NONE"), ("JPMorgan Chase", "Financial Services", "Mumbai", "IN_PROGRESS"),
    ("Amazon", "E-commerce & Cloud", "Bengaluru", "NONE"), ("Mahindra & Mahindra", "Automotive", "Mumbai", "SIGNED"),
    ("Siemens", "Industrial Automation", "Mumbai", "SIGNED"), ("Fractal Analytics", "AI & Analytics", "Mumbai", "IN_PROGRESS"),
    ("Barclays", "Financial Services", "Pune", "NONE"), ("IBM", "IT Services & Consulting", "Bengaluru", "EXPIRED"),
    ("Hexaware Technologies", "IT Services", "Navi Mumbai", "SIGNED"), ("Zensar Technologies", "IT Services", "Pune", "IN_PROGRESS"),
    ("Mastek", "IT Services", "Navi Mumbai", "NONE"), ("Jio Platforms", "Telecom & Digital", "Navi Mumbai", "IN_PROGRESS"),
    ("Godrej & Boyce", "Manufacturing", "Mumbai", "NONE"), ("Tata Motors", "Automotive", "Pune", "EXPIRED"),
    ("Reliance Industries", "Energy & Petrochemicals", "Mumbai", "NONE")]
ALL = ["COMP", "IT", "AIDS", "EXTC", "MECH"]
SOFT = ["COMP", "IT", "AIDS", "EXTC"]
CS = ["COMP", "IT", "AIDS"]
# drives: key, company, title, role, ctc, branches, min cgpa, rounds, year, date, kind, selected, stipend, offer-ctc overrides
DRIVES = [
    ("tcs25", "Tata Consultancy Services", "TCS NQT Hiring 2025 - Ninja & Digital", "Assistant System Engineer", 3.6, SOFT, 6.0,
     [("TCS NQT (Aptitude + Coding)", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], 2025, "2025-08-18", "done", 10, None, {0: 7.0, 1: 7.0}),
    ("acc25", "Accenture", "Accenture Associate Software Engineer 2025", "Associate Software Engineer", 4.6, CS, 6.5,
     [("Cognitive & Technical Assessment", "APTITUDE"), ("Coding Round", "CODING"), ("Communication + Interview", "TECHNICAL")], 2025, "2025-08-28", "done", 7, None, {}),
    ("inf25", "Infosys", "Infosys Systems Engineer 2025", "Systems Engineer", 3.6, SOFT, 6.0,
     [("Online Test", "APTITUDE"), ("Interview", "TECHNICAL")], 2025, "2025-09-10", "done", 6, None, {0: 9.5}),
    ("wip25", "Wipro", "Wipro Elite NLTH 2025", "Project Engineer", 3.5, ALL, 6.0,
     [("Online Assessment", "APTITUDE"), ("Technical + HR Interview", "TECHNICAL")], 2025, "2025-09-22", "done", 6, None, {}),
    ("cts25", "Cognizant", "Cognizant GenC 2025", "Programmer Analyst Trainee", 4.0, SOFT, 6.0,
     [("GenC Assessment", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], 2025, "2025-10-08", "done", 5, None, {}),
    ("cap25", "Capgemini", "Capgemini Analyst Hiring 2025", "Analyst", 4.25, ALL, 6.0,
     [("Online Assessment", "APTITUDE"), ("Interview", "HR")], 2025, "2025-10-22", "done", 4, None, {}),
    ("lnt25", "Larsen & Toubro", "L&T Graduate Engineer Trainee 2025", "Graduate Engineer Trainee", 6.0, ["MECH", "EXTC"], 6.5,
     [("Technical Test", "APTITUDE"), ("Interview", "TECHNICAL")], 2025, "2025-11-12", "done", 4, None, {}),
    ("del25", "Deloitte", "Deloitte USI Analyst Trainee 2025", "Analyst Trainee", 7.6, CS, 7.5,
     [("Online Test", "APTITUDE"), ("Group Discussion", "GD"), ("Interview", "TECHNICAL")], 2025, "2025-12-09", "done", 3, None, {}),
    ("zs25", "ZS Associates", "ZS Business Technology Analyst 2025", "Business Technology Analyst", 9.5, CS, 7.5,
     [("Aptitude + Case Test", "APTITUDE"), ("Case Interview", "TECHNICAL"), ("HR Interview", "HR")], 2025, "2026-01-14", "done", 2, None, {}),
    ("ms25", "Morgan Stanley", "Morgan Stanley Technology Analyst 2025", "Technology Analyst", 16.0, CS, 8.0,
     [("Online Coding Test", "CODING"), ("Technical Interview 1", "TECHNICAL"), ("Technical Interview 2", "TECHNICAL"), ("HR Interview", "HR")],
     2025, "2026-02-04", "done", 2, None, {}),
    ("mm25", "Mahindra & Mahindra", "Mahindra GET (Mechanical) 2025", "Graduate Engineer Trainee", 5.5, ["MECH"], 6.0,
     [("Technical Test", "APTITUDE"), ("Interview", "TECHNICAL")], 2025, "2026-03-03", "done", 3, None, {}),
    ("wip26", "Wipro", "Wipro Elite NLTH 2026", "Project Engineer", 3.5, ALL, 6.0,
     [("Online Assessment", "APTITUDE"), ("Technical + HR Interview", "TECHNICAL")], 2026, "2026-08-20", "done", 5, None, {}),
    ("cts26", "Cognizant", "Cognizant GenC 2026", "Programmer Analyst Trainee", 4.0, SOFT, 6.0,
     [("GenC Assessment", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], 2026, "2026-09-05", "done", 4, None, {}),
    ("hcl26", "HCLTech", "HCLTech Graduate Engineer Trainee 2026", "Graduate Engineer Trainee", 4.25, SOFT, 6.0,
     [("Online Test", "APTITUDE"), ("Technical Interview", "TECHNICAL")], 2026, "2026-09-24", "done", 4, None, {}),
    ("psl26", "Persistent Systems", "Persistent Systems Software Engineer 2026", "Software Engineer", 5.0, CS, 7.0,
     [("Online Test", "APTITUDE"), ("Coding Round", "CODING"), ("Technical Interview", "TECHNICAL")], 2026, "2026-10-15", "done", 3, None, {}),
    ("jpm26", "JPMorgan Chase", "JPMorgan Chase Software Engineer 2026", "Software Engineer", 15.5, CS, 8.0,
     [("Code for Good Assessment", "CODING"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], 2026, "2026-10-29", "done", 2, None, {}),
    ("tm26", "Tech Mahindra", "Tech Mahindra Associate Software Engineer 2026", "Associate Software Engineer", 3.8, SOFT, 6.0,
     [("Online Assessment", "APTITUDE"), ("Technical Interview", "TECHNICAL"), ("HR Interview", "HR")], 2026, "2026-11-18", "ongoing", 0, None, {}),
    ("amz26", "Amazon", "Amazon SDE-1 2026", "Software Development Engineer I", 24.0, CS, 8.0,
     [("Online Coding Assessment", "CODING"), ("Technical Interview 1", "TECHNICAL"), ("Technical Interview 2", "TECHNICAL"), ("Bar Raiser", "HR")],
     2026, "2027-01-12", "open", 0, None, {}),
    ("sie27", "Siemens", "Siemens Summer Internship 2026", "Automation Engineering Intern", None, ["EXTC", "MECH", "COMP"], 7.0,
     [("Technical Test", "APTITUDE"), ("Interview", "TECHNICAL")], 2027, "2026-04-22", "intern", 4, 25000, {}),
    ("fra27", "Fractal Analytics", "Fractal Analytics Data Science Internship 2026", "Data Science Intern", None, ["AIDS", "COMP"], 7.5,
     [("Analytics Test", "APTITUDE"), ("Case Interview", "TECHNICAL")], 2027, "2026-05-12", "intern", 3, 30000, {}),
    ("bar27", "Barclays", "Barclays Summer Internship 2027", "Technology Intern", None, CS, 7.5,
     [("Online Assessment", "CODING"), ("Interview", "TECHNICAL")], 2027, "2027-01-20", "open", 0, 40000, {}),
]

ANNOUNCEMENTS = [  # date, batch filter, title, body
    ("2025-07-21", [2025], "Placement season 2025 begins", "Registrations for the 2025 placement season are open. Update your profile, resume and marksheets in the Document locker by 31 July."),
    ("2025-08-11", [2025], "TCS NQT on 18 August", "All eligible students must register for the TCS NQT on the drive page. Carry your college ID card and a pen."),
    ("2025-09-30", [2025], "Congratulations to the TCS, Accenture and Infosys selects", "28 students have offers so far. Offer letters are on your registered e-mail."),
    ("2025-11-28", [2025], "Dream drives: Deloitte and ZS", "Students placed in the Normal tier may apply if the hike is at least 25%. Check your eligibility on each drive."),
    ("2026-03-20", [2025], "Season 2025 wrap-up", "Thank you for a great season. Students who are still looking: off-campus openings are listed in the Off-campus section."),
    ("2026-04-06", [2027], "Summer internships 2026", "Siemens and Fractal Analytics internship drives are open for the third-year batch. Stipend up to Rs 30,000 a month."),
    ("2026-07-27", [2026], "Placement season 2026 begins", "Training starts on Saturday. Aptitude mock tests are mandatory: finish the first two levels before 15 August."),
    ("2026-09-12", [2026], "Mock interview drive with alumni", "Alumni from TCS, Persistent and JPMorgan will take mock interviews on 19 September. Register with your coordinator."),
    ("2026-10-20", [2026, 2027], "AI mock interviews now available", "Practise interviews with the AI interviewer in the AI interview section. One free interview every 15 days."),
    ("2026-11-10", [2026], "Amazon SDE-1 drive announced", "Amazon is hiring SDE-1 at 24 LPA. Minimum CGPA 8.0, Computer / IT / AI&DS. Registration closes on 5 January."),
]

OFF_JOBS = [  # company, role, type, mode, location, cmin, cmax, batch years, skills, posted
    ("Swiggy", "Software Development Engineer I", "FULL_TIME", "HYBRID", "Bengaluru", 18, 24, [2026], ["Java", "Data Structures"], "2026-09-02"),
    ("Razorpay", "Software Engineer - New Grad", "FULL_TIME", "ONSITE", "Bengaluru", 16, 22, [2026], ["Go", "Java", "SQL"], "2026-09-15"),
    ("Bosch", "Graduate Engineer Trainee - Mechanical", "FULL_TIME", "ONSITE", "Pune / Nashik", 5.5, 7, [2025, 2026], ["AutoCAD", "GD&T"], "2025-10-05"),
    ("Tata Elxsi", "Embedded Software Engineer", "FULL_TIME", "ONSITE", "Pune / Bengaluru", 4.5, 6, [2025, 2026], ["Embedded C", "C++"], "2025-11-12"),
    ("Mu Sigma", "Trainee Decision Scientist", "FULL_TIME", "ONSITE", "Bengaluru", 4.5, 5.5, [2025, 2026], ["SQL", "Python", "Statistics"], "2025-12-01"),
    ("PhonePe", "Software Engineer Intern", "INTERNSHIP", "ONSITE", "Bengaluru", None, None, [2027], ["Java", "Data Structures"], "2026-08-18"),
]

TECH_Q = [("Introduction", "Tell me about yourself and the project you are most proud of."),
          ("OOP", "Explain the difference between an abstract class and an interface, with an example from your project."),
          ("Data structures", "How does a HashMap work internally, and what happens on a collision?"),
          ("Databases", "What is normalization? Explain 2NF and 3NF with an example."),
          ("Problem solving", "How would you find the first non-repeating character in a string? What is the time complexity?"),
          ("Web", "What happens when you type a URL in the browser and press Enter?"),
          ("Java", "What is the difference between == and equals() in Java?"),
          ("SQL", "Write a query to find the second highest salary from an Employee table."),
          ("OS", "What is a deadlock and how can it be prevented?")]
HR_Q = [("Introduction", "Walk me through your background and why you chose engineering."),
        ("Strengths", "What are your key strengths, and how have they helped you in a team project?"),
        ("Weakness", "Tell me about a weakness you are working on."),
        ("Teamwork", "Describe a time you disagreed with a teammate and how you resolved it."),
        ("Career goals", "Where do you see yourself in five years?"),
        ("Company fit", "Why should we hire you for this role?"),
        ("Pressure", "Tell me about a deadline you nearly missed and what you did.")]
GOOD = ["I explained it step by step with an example from my final-year project, covering the main idea, a trade-off and how I tested it.",
        "I would first clarify the requirement, then describe the approach, its time and space complexity, and an edge case such as empty input.",
        "In my internship I handled this by breaking the problem into smaller parts, discussing it with my mentor and documenting the decision."]
WEAK = ["I am not fully sure, but I think it is related to how the data is stored.",
        "I have read about this but have not used it in a project yet.",
        "I would search for it and try different approaches until it works."]

# --------------------------------------------------------------------------------------------------------------- main

def main():
    C = sql(f"select college_id from college_profiles where email_domain = '{DOMAIN}'", True)[0][0]
    print("LTCE", C)
    branches = {r[1]: r[0] for r in sql(f"select id, code from college_branches where college_id = '{C}'", True)}
    accounts = sql(f"""select u.id, u.email, u.password_hash from users u where u.id in (
                         select user_id from college_members where college_id = '{C}'
                         union select user_id from college_students where college_id = '{C}' and user_id is not null)""", True)
    saved = {r[0]: r[2] for r in accounts}
    ref_hash = next(r[2] for r in accounts if r[1] == REF_STUDENT)
    print(f"remembered {len(saved)} LTCE password hashes")

    admin = login(*ADMIN) or sys.exit("admin sign-in failed")
    set_password("tpo@ltce.in", TEMP)
    t = login("tpo@ltce.in", TEMP)
    me = ok(call("GET", "/api/v1/college/me", token=t), "me")

    try:
        run(C, t, admin, me, branches)
    finally:
        # passwords: originals back, new students get the existing students' password
        print("Restoring passwords ...")
        stmts = [f"update users set password_hash = {q(h)}, reset_token = null, reset_token_expires_at = null where id = {q(i)};" for i, h in saved.items()]
        stmts.append(f"""update users u join college_students s on s.user_id = u.id and s.college_id = '{C}'
                         set u.password_hash = {q(ref_hash)}, u.reset_token = null, u.reset_token_expires_at = null
                         where u.id not in ({','.join(q(i) for i in saved)});""")
        sql("\n".join(stmts))


def run(C, t, admin, me, br):
    # ---------------------------------------------------------------- batch 2025, students
    print("Batch 2025 ...")
    soft(call("POST", "/api/v1/college/settings/batches", {"gradYear": 2025, "label": "Batch 2025", "active": True}, t), "batch 2025")
    existing = sql(f"select id, user_id, email, branch_id, batch_year, cgpa, roll_number, status from college_students where college_id = '{C}'", True)
    used_emails = {r[2] for r in existing}
    code_of = {v: k for k, v in br.items()}
    students = {}  # id -> dict
    for r in existing:
        students[r[0]] = {"id": r[0], "userId": r[1], "email": r[2], "code": code_of.get(r[3]), "year": int(r[4]), "cgpa": float(r[5] or 7),
                          "new": False}
    counters = {}
    for r in existing:
        y = int(r[4]); code = code_of.get(r[3])
        counters[(code, y)] = max(counters.get((code, y), 0), int(r[6][-3:]))
    created = []
    for year, per in NEW_STUDENTS.items():
        for code, n in per.items():
            for _ in range(n):
                female = random.random() < 0.42
                fn = random.choice(F_FIRST if female else M_FIRST); ln = random.choice(LAST)
                base = f"{fn.lower()}.{ln.lower()}"
                email, k = f"{base}@{DOMAIN}", 2
                while email in used_emails: email = f"{base}{k}@{DOMAIN}"; k += 1
                used_emails.add(email)
                counters[(code, year)] = counters.get((code, year), 0) + 1
                roll = f"{(year - 4) % 100}{code}{counters[(code, year)]:03d}"
                bump = {"COMP": .15, "IT": .1, "AIDS": .1, "EXTC": -.05, "MECH": -.15}[code]
                cg = round(min(9.7, max(5.6, random.gauss(7.45 + bump, 0.85))), 2)
                st = ok(call("POST", "/api/v1/college/students", {
                    "rollNumber": roll, "firstName": fn, "lastName": ln, "email": email, "phone": f"9{random.randint(100000000, 999999999)}",
                    "gender": "Female" if female else "Male", "branchId": br[code], "batchYear": year, "degree": "B.E.", "cgpa": cg,
                    "tenthPercent": round(random.uniform(72, 97), 1), "twelfthPercent": round(random.uniform(62, 95), 1),
                    "activeBacklogs": 1 if random.random() < 0.07 else 0, "gapYears": 1 if random.random() < 0.04 else 0,
                    "skills": random.sample(SKILLS[code], 4),
                    "linkedinUrl": f"https://www.linkedin.com/in/{base.replace('.', '-')}"}, t), f"student {email}")
                students[st["id"]] = {"id": st["id"], "email": email, "code": code, "year": year, "cgpa": cg, "new": True}
                created.append(st["id"])
    print(f"   {len(created)} new students")
    tokens = {}
    for s in students.values():
        set_password(s["email"], TEMP)
        tokens[s["id"]] = login(s["email"], TEMP)
    skill = {sid: s["cgpa"] + random.gauss(0, 0.6) for sid, s in students.items()}

    # ---------------------------------------------------------------- companies
    print("Companies ...")
    comp = {r[1]: r[0] for r in sql(f"select id, name from college_companies where college_id = '{C}'", True)}
    for name, ind, hq, mou in COMPANIES:
        if name in comp: continue
        c = ok(call("POST", "/api/v1/college/companies", {"name": name, "industry": ind, "hqLocation": hq, "mouStatus": mou,
                                                          "website": f"https://www.{name.lower().split()[0].replace('&', '')}.com"}, t), name)
        ok(call("POST", f"/api/v1/college/companies/{c['id']}/contacts", {"name": "Campus Hiring Team", "designation": "Talent Acquisition",
                                                                          "email": f"campus@{name.lower().split()[0].replace('&', '')}.com", "primaryContact": True}, t), "contact")
        comp[name] = c["id"]
    assert len(comp) <= 30, "more than 30 companies"

    # ---------------------------------------------------------------- drives
    placed = {sid for (sid,) in sql(f"select distinct student_id from college_offers where college_id = '{C}' and status in ('ACCEPTED','JOINED')", True)}
    timeline = []    # (drive id, date, kind)
    offers_made = [] # (offer id, date, year, joining)
    interviews_done = []
    for key, company, title, role, ctc, codes, minc, rounds, year, date, kind, n_sel, stipend, special in DRIVES:
        print(f"Drive {title} ...")
        upcoming = kind == "open"
        body = {"companyId": comp[company], "title": title, "roleTitle": role, "driveType": "INTERNSHIP" if stipend else "ON_CAMPUS",
                "location": "Mumbai / Pune" if not stipend else "Mumbai (Hybrid)", "mode": "OFFLINE" if not stipend else "ONLINE",
                "venue": "LTCE Seminar Hall, Koparkhairane" if not stipend else None,
                "jobDescription": f"{role} hiring for the {year} batch. " + ("Summer internship with a pre-placement interview for top interns." if stipend
                                  else "Online assessment followed by interviews. Bond: none."),
                "skills": random.sample(SKILLS[codes[0]], 3), "registrationDeadline": iso(30 if upcoming else 2), "driveDate": day(40 if upcoming else 5),
                "eligibility": {"minCgpa": minc, "maxActiveBacklogs": 0, "batchYears": [year], "branchIds": [br[c] for c in codes]}}
        if ctc: body["ctcLpa"] = ctc
        if stipend: body["stipendMonthly"] = stipend
        d = ok(call("POST", "/api/v1/college/drives", body, t), title)
        for n, typ in rounds:
            ok(call("POST", f"/api/v1/college/drives/{d['id']}/rounds", {"name": n, "roundType": typ}, t), "round")
        ok(call("POST", f"/api/v1/college/drives/{d['id']}/status", {"status": "PUBLISHED"}, t), "publish")
        timeline.append((d["id"], date, kind))
        elig = [r["studentId"] for r in ok(call("GET", f"/api/v1/college/drives/{d['id']}/eligibility?eligible=true&size=500", token=t), "eligibility")["content"]]
        dream = (ctc or 0) >= 7.5
        pool = [x for x in elig if (x not in placed or dream) and students.get(x, {}).get("year") == year]
        random.shuffle(pool)
        share = 0.35 if upcoming else (0.55 if dream else 0.75)
        applicants = pool[:max(3, int(len(pool) * share))] if pool else []
        if not applicants:
            print("   nobody eligible"); continue
        if upcoming:  # students apply themselves; nothing else happens yet
            for sid in applicants: soft(call("POST", f"/api/v1/college/student/drives/{d['id']}/apply", token=tokens[sid]), "apply")
            continue
        ok(call("POST", f"/api/v1/college/drives/{d['id']}/applications", {"studentIds": applicants}, t), "register")
        apps = ok(call("GET", f"/api/v1/college/drives/{d['id']}/applications?size=500", token=t), "applications")["content"]
        ok(call("PATCH", "/api/v1/college/applications/status", {"applicationIds": [a["id"] for a in apps], "status": "SHORTLISTED"}, t), "shortlist")
        rds = ok(call("GET", f"/api/v1/college/drives/{d['id']}/rounds", token=t), "rounds")
        alive = sorted(apps, key=lambda a: -skill.get(a["studentId"], 7))
        target = n_sel
        for k, rd in enumerate(rds):
            last = k == len(rds) - 1
            if kind == "ongoing" and k == 1:  # interviews booked for next week, the round is still running
                ok(call("POST", f"/api/v1/college/rounds/{rd['id']}/start", None, t), "start")
                for j, a in enumerate(alive[:8]):
                    start = (now + dt.timedelta(days=2 + j // 4)).replace(hour=4 + (j % 4) // 2, minute=(j % 2) * 30, second=0, microsecond=0)
                    soft(call("POST", "/api/v1/college/interviews", {"driveId": d["id"], "roundId": rd["id"], "applicationId": a["id"],
                                                                    "panelName": "Panel " + "AB"[j % 2], "startsAt": start.isoformat().replace("+00:00", "Z"),
                                                                    "endsAt": (start + dt.timedelta(minutes=25)).isoformat().replace("+00:00", "Z"),
                                                                    "room": f"Placement Cell, Room {21 + j % 2}"}, t), "interview")
                break
            keep = target if last else max(target + 1, int(len(alive) * (0.6 if k == 0 else 0.75)))
            ok(call("POST", f"/api/v1/college/rounds/{rd['id']}/start", None, t), "start")
            booked = []
            if rd.get("roundType") == "TECHNICAL" and kind == "done" and key in ("acc25", "cts26", "psl26"):
                for j, a in enumerate(alive[:6]):
                    start = (now + dt.timedelta(days=3)).replace(hour=4 + j // 2, minute=(j % 2) * 30, second=0, microsecond=0)
                    iv = soft(call("POST", "/api/v1/college/interviews", {"driveId": d["id"], "roundId": rd["id"], "applicationId": a["id"],
                                                                          "panelName": "Panel " + "AB"[j % 2], "startsAt": start.isoformat().replace("+00:00", "Z"),
                                                                          "endsAt": (start + dt.timedelta(minutes=25)).isoformat().replace("+00:00", "Z"),
                                                                          "room": f"Placement Cell, Room {21 + j % 2}"}, t), "interview")
                    if iv: booked.append((iv["id"], j))
            res = []
            for j, a in enumerate(alive):
                absent = (not last) and random.random() < 0.04
                res.append({"applicationId": a["id"], "result": "ABSENT" if absent else ("PASS" if j < keep else "FAIL"),
                            "score": round(random.uniform(62, 95) if j < keep else random.uniform(30, 60), 1)})
            ok(call("PUT", f"/api/v1/college/rounds/{rd['id']}/results", {"results": res}, t), "results")
            ok(call("POST", f"/api/v1/college/rounds/{rd['id']}/complete", None, t), "complete")
            for iv_id, j in booked:
                soft(call("PATCH", f"/api/v1/college/interviews/{iv_id}/status", {"status": "NO_SHOW" if j == 5 else "COMPLETED"}, t), "interview status")
                interviews_done.append((iv_id, d["id"], date, k))
            passed = {r["applicationId"] for r in res if r["result"] == "PASS"}
            alive = [a for a in alive if a["id"] in passed]
        if kind == "ongoing":
            continue
        selected = [a["studentId"] for a in alive]
        if kind == "intern":
            ok(call("POST", f"/api/v1/college/drives/{d['id']}/status", {"status": "COMPLETED"}, t), "complete drive")
            # top interns get a PPO in October / November 2026
            for j, sid in enumerate(selected[:2 if key == "sie27" else 1]):
                od = "2026-10-21" if key == "sie27" else "2026-11-06"
                o = soft(call("POST", "/api/v1/college/offers", {"studentId": sid, "driveId": d["id"], "companyId": comp[company],
                                                                 "roleTitle": role.replace(" Intern", "") + " (PPO)", "ctcLpa": 7.0 if key == "sie27" else 9.0,
                                                                 "offerDate": od, "joiningDate": "2027-07-05", "remarks": "Pre-placement offer after the summer internship",
                                                                 "overridePolicy": True, "overrideReason": "PPO from internship"}, t), "ppo")
                if o:
                    soft(call("POST", f"/api/v1/college/student/offers/{o['id']}/respond", {"accept": True}, tokens[sid]), "accept ppo")
                    offers_made.append((o["id"], od, year, "2027-07-05")); placed.add(sid)
            continue
        offer_day = D(date) + dt.timedelta(days=random.randint(6, 12))
        joining = "2026-07-01" if year == 2025 else "2027-07-01"
        for j, sid in enumerate(selected):
            o = soft(call("POST", "/api/v1/college/offers", {"studentId": sid, "driveId": d["id"], "companyId": comp[company],
                                                             "roleTitle": role + (" (Digital)" if special.get(j) and key == "tcs25" else
                                                                                  " (Specialist Programmer)" if special.get(j) and key == "inf25" else ""),
                                                             "ctcLpa": special.get(j, ctc), "offerDate": offer_day.isoformat(), "joiningDate": joining}, t), "offer")
            if not o: continue
            offers_made.append((o["id"], offer_day.isoformat(), year, joining))
            r = random.random()
            pending_ok = year == 2026 and D(date) >= D("2026-10-01")
            if pending_ok and r < 0.25:
                continue                                  # still deciding
            accept = r >= 0.1
            if soft(call("POST", f"/api/v1/college/student/offers/{o['id']}/respond", {"accept": accept}, tokens[sid]), "respond") and accept:
                placed.add(sid)
                if year == 2025 and random.random() < 0.85:
                    soft(call("PATCH", f"/api/v1/college/offers/{o['id']}/status", {"status": "JOINED", "remarks": "Joined on 1 July 2026"}, t), "joined")
        ok(call("POST", f"/api/v1/college/drives/{d['id']}/status", {"status": "COMPLETED"}, t), "complete drive")
    n_drives = sql(f"select count(*) from college_drives where college_id = '{C}' and deleted_at is null", True)[0][0]
    print(f"   drives now: {n_drives}")
    assert int(n_drives) <= 30, "more than 30 drives"

    # ---------------------------------------------------------------- announcements
    print("Announcements ...")
    ann = []
    for date, years, title, body in ANNOUNCEMENTS:
        a = ok(call("POST", "/api/v1/college/announcements", {"title": title, "body": body, "filter": {"batchYears": years}, "sendEmail": False}, t), title)
        ann.append((a["id"], date))

    # ---------------------------------------------------------------- mock tests with proctoring
    print("Mock test attempts ...")
    defs = {}
    for tid, dj in sql("select id, definition_json from prep_tests where published = 1 and mode = 'PRACTICE'", True):
        try:
            dd = json.loads(dj)
            defs[tid] = {qq.get("id"): qq.get("answer") for s in dd.get("sections", []) for qq in s.get("questions", [])}
        except Exception:
            pass
    attempts = []  # (attempt id, year)
    order = sorted(students.values(), key=lambda s: (s["year"], s["email"]))
    for s in order:
        if s["year"] == 2025 and random.random() < 0.5: continue
        if s["year"] != 2025 and random.random() < 0.12: continue
        tk = tokens[s["id"]]
        p = max(0.2, min(0.93, 0.38 + (s["cgpa"] - 6) * 0.13 + random.gauss(0, 0.08)))
        for step in range(3):
            tests = soft(call("GET", "/api/v1/college/student/prep/tests", token=tk), "tests") or []
            ladder = sorted([x for x in tests if x.get("order") and not x.get("locked") and x.get("mode") == "PRACTICE"], key=lambda x: x["order"])
            untried = [x for x in ladder if not x.get("attempts")]
            pick = untried[0] if untried else None
            if step == 2 and random.random() < 0.6:
                pick = next((x for x in tests if x.get("title", "").startswith("TCS NQT Pattern") and not x.get("attempts")), pick)
            if not pick: break
            v = soft(call("POST", f"/api/v1/college/student/prep/tests/{pick['id']}/start", token=tk), "start test")
            if not v: break
            aid = v["attemptId"]
            key = defs.get(pick["id"], {})
            if random.random() < 0.18:  # camera proctoring with a few flags
                if soft(call("POST", f"/api/v1/college/student/prep/attempts/{aid}/consent", None, tk), "consent"):
                    for kind_ in random.sample(["NO_FACE", "LOOKING_AWAY", "VOICE_DETECTED", "MULTIPLE_FACES"], random.randint(1, 3)):
                        raw, ct = multipart({"kind": kind_})
                        soft(call("POST", f"/api/v1/college/student/prep/attempts/{aid}/proctor/ai", token=tk, raw=raw, ctype=ct), "ai flag")
            if random.random() < 0.1:
                raw, ct = multipart({"kind": "TAB_HIDDEN"})
                soft(call("POST", f"/api/v1/college/student/prep/attempts/{aid}/proctor", token=tk, raw=raw, ctype=ct), "tab switch")
            answers = {}
            for qq in v.get("questions", []):
                if qq.get("type") != "MCQ" or random.random() > 0.92: continue
                right = key.get(qq["id"])
                n_opt = len(qq.get("options") or [1, 2, 3, 4])
                answers[qq["id"]] = {"option": right if (right is not None and random.random() < p) else random.randrange(n_opt)}
            if soft(call("POST", f"/api/v1/college/student/prep/attempts/{aid}/submit", answers, tk), "submit"):
                attempts.append((aid, s["year"]))
    print(f"   {len(attempts)} attempts")

    # ---------------------------------------------------------------- off-campus
    print("Off-campus jobs ...")
    S = "/api/v1/college/student/off-campus"
    off = []
    for co, role, jt, mode, loc, cmin, cmax, years, sk, posted in OFF_JOBS:
        body = {"companyName": co, "roleTitle": role, "jobType": jt, "workMode": mode, "location": loc, "description": f"{role} at {co}.",
                "skills": sk, "applyUrl": f"https://careers.{co.lower().replace(' ', '')}.com", "applyDeadline": iso(25), "batchYears": years, "collegeIds": []}
        if cmin: body["ctcMinLpa"] = cmin
        if cmax: body["ctcMaxLpa"] = cmax
        if jt == "INTERNSHIP": body["stipendPerMonth"] = 60000
        jid = ok(call("POST", "/api/v1/admin/off-campus/jobs", body, admin), "job")["id"]
        ok(call("POST", f"/api/v1/admin/off-campus/jobs/{jid}/status", {"status": "PUBLISHED"}, admin), "publish job")
        off.append((jid, posted))
        fits = [s for s in students.values() if s["year"] in years]
        for s in random.sample(fits, min(len(fits), random.randint(4, 9))):
            if soft(call("POST", f"{S}/{jid}/click", {}, tokens[s["id"]]), "click"):
                st = random.choices(["APPLIED", "INTERVIEWING", "OFFER", "REJECTED", "CLICKED"], [5, 2, 1, 2, 2])[0]
                if st != "CLICKED": soft(call("PUT", f"{S}/{jid}/status", {"status": st}, tokens[s["id"]]), "oc status")

    # ---------------------------------------------------------------- chat + support
    print("Chat and support ...")
    some = random.sample([s for s in students.values() if s["year"] == 2026], 3)
    for s in some:
        uid = ok(call("GET", "/api/v1/college/student/profile", token=tokens[s["id"]]), "profile")["userId"]
        soft(call("POST", "/api/v1/chat/messages", {"receiverId": uid, "message": "Please complete the first two aptitude mock tests before the Tech Mahindra drive."}, t), "chat")
        soft(call("POST", "/api/v1/chat/messages", {"receiverId": me["userId"], "message": "Sure ma'am, I will finish them this week."}, tokens[s["id"]]), "chat reply")
    soft(call("POST", "/api/v1/support/tickets", {"category": "PLACEMENT", "subject": "Offer letter not received",
                                                  "message": "I was selected in the Wipro drive but have not received the offer letter yet."}, tokens[some[0]["id"]]), "ticket")

    # ---------------------------------------------------------------- dates into the past + AI interviews (SQL)
    backdate(C, timeline, offers_made, interviews_done, ann, attempts, off, students)
    ai_interviews(C, students)


def backdate(C, timeline, offers_made, interviews_done, ann, attempts, off, students):
    print("Moving dates into the season ...")
    st = []
    for did, date, kind in timeline:
        d0 = D(date)
        if kind == "open":   # upcoming: registration still open
            st.append(f"update college_drives set drive_date = {q(date)}, registration_deadline = {q((d0 - dt.timedelta(days=7)).isoformat() + ' 18:29:00')}, "
                      f"published_at = date_sub(now(), interval 12 day), created_at = date_sub(now(), interval 15 day) where id = {q(did)};")
            continue
        created = d0 - dt.timedelta(days=24)
        st.append(f"update college_drives set drive_date = {q(date)}, registration_deadline = {q((d0 - dt.timedelta(days=4)).isoformat() + ' 18:29:00')}, "
                  f"published_at = {q(created.isoformat() + ' 05:30:00')}, created_at = {q(created.isoformat() + ' 05:00:00')}, "
                  f"updated_at = {q((d0 + dt.timedelta(days=14)).isoformat() + ' 06:00:00')} where id = {q(did)};")
        st.append(f"update college_drive_rounds set scheduled_at = date_add({q(date + ' 04:30:00')}, interval (seq - 1) * 2 day), "
                  f"created_at = {q(created.isoformat() + ' 05:00:00')}, updated_at = date_add({q(date + ' 12:30:00')}, interval (seq - 1) * 2 day) "
                  f"where drive_id = {q(did)} and status = 'COMPLETED';")
        st.append(f"update college_applications set created_at = date_sub({q(date + ' 08:00:00')}, interval 5 + floor(rand() * 14) day), "
                  f"updated_at = {q((d0 + dt.timedelta(days=7)).isoformat() + ' 09:00:00')} where drive_id = {q(did)};")
        st.append(f"""update college_round_results rr join college_drive_rounds r on r.id = rr.round_id
                      set rr.created_at = date_add(r.scheduled_at, interval 6 hour), rr.updated_at = date_add(r.scheduled_at, interval 8 hour)
                      where r.drive_id = {q(did)} and r.status = 'COMPLETED';""")
    for iv_id, did, date, k in interviews_done:
        st.append(f"""update college_interview_slots s join college_drive_rounds r on r.id = s.round_id
                      set s.starts_at = date_add(r.scheduled_at, interval timestampdiff(minute, date(s.starts_at), s.starts_at) minute),
                          s.ends_at = date_add(r.scheduled_at, interval timestampdiff(minute, date(s.starts_at), s.ends_at) minute),
                          s.created_at = date_sub(r.scheduled_at, interval 2 day), s.updated_at = date_add(r.scheduled_at, interval 1 day)
                      where s.id = {q(iv_id)};""")
    for oid, od, year, joining in offers_made:
        st.append(f"update college_offers set offer_date = {q(od)}, joining_date = {q(joining)}, created_at = {q(od + ' 07:00:00')}, "
                  f"updated_at = date_add({q(od + ' 07:00:00')}, interval 3 day) where id = {q(oid)};")
    for aid, date in ann:
        st.append(f"update college_announcements set created_at = {q(date + ' 05:30:00')}, updated_at = {q(date + ' 05:30:00')} where id = {q(aid)};")
        st.append(f"update college_announcement_recipients set created_at = {q(date + ' 05:30:00')}, "
                  f"read_at = if(rand() < 0.62, date_add({q(date + ' 06:00:00')}, interval floor(rand() * 72) hour), null) where announcement_id = {q(aid)};")
    for aid, year in attempts:
        lo, hi = {2025: ("2025-06-01", 75), 2026: ("2026-01-10", 280), 2027: ("2026-03-01", 260)}[year]
        st.append(f"""set @t = date_add({q(lo + ' 05:00:00')}, interval floor(rand() * {hi * 24}) hour);
                      update prep_attempts set started_at = @t, created_at = @t, consent_at = if(consent_at is null, null, @t),
                        deadline_at = date_add(@t, interval timestampdiff(second, started_at, deadline_at) second),
                        submitted_at = date_add(@t, interval 20 + floor(rand() * 35) minute), updated_at = date_add(@t, interval 1 hour)
                      where id = {q(aid)};
                      update prep_proctor_events set occurred_at = date_add(@t, interval 3 + seq * 4 minute), client_at = date_add(@t, interval 3 + seq * 4 minute),
                        created_at = date_add(@t, interval 3 + seq * 4 minute) where attempt_id = {q(aid)};""")
    for jid, posted in off:
        st.append(f"update off_campus_jobs set created_at = {q(posted + ' 05:00:00')}, published_at = {q(posted + ' 05:30:00')}, "
                  f"apply_deadline = date_add({q(posted + ' 18:29:00')}, interval 30 day) where id = {q(jid)};")
        st.append(f"""update off_campus_applications set clicked_at = date_add({q(posted + ' 08:00:00')}, interval floor(rand() * 240) hour),
                      applied_at = if(applied_at is null, null, date_add({q(posted + ' 10:00:00')}, interval floor(rand() * 260) hour)),
                      created_at = date_add({q(posted + ' 08:00:00')}, interval floor(rand() * 240) hour) where job_id = {q(jid)};""")
    # who was admitted when; a few left the process
    for s in students.values():
        if s.get("new"):
            st.append(f"update college_students set created_at = {q(str(s['year'] - 4) + '-08-' + str(random.randint(5, 25)).zfill(2) + ' 06:00:00')} where id = {q(s['id'])};")
    st.append(f"""update college_students set status = 'OPTED_OUT' where college_id = '{C}' and batch_year = 2025 and status = 'ACTIVE'
                  and id not in (select student_id from college_applications where college_id = '{C}' and status = 'SELECTED') order by cgpa desc limit 3;""")
    st.append(f"""update college_students set status = 'DEBARRED' where college_id = '{C}' and batch_year = 2026 and status = 'ACTIVE' and active_backlogs > 0 limit 1;""")
    sql("\n".join(st))


def ai_interviews(C, students):
    print("AI mock interviews ...")
    rows = sql(f"select s.id, s.user_id, s.batch_year, s.cgpa, b.code from college_students s join college_branches b on b.id = s.branch_id "
               f"where s.college_id = '{C}' and s.batch_year in (2026, 2027) and s.user_id is not null and s.deleted_at is null", True)
    random.shuffle(rows)
    st = []
    credits = {}
    model = "llama-3.3-70b-versatile"
    for sid, uid, year, cgpa, code in rows[:72]:
        for _ in range(random.choice([1, 1, 2])):
            itype = random.choices(["TECHNICAL", "HR", "MIXED"], [5, 2, 3])[0]
            diff = random.choices(["EASY", "MEDIUM", "HARD"], [2, 5, 2])[0]
            role = {"COMP": "Software Engineer", "IT": "Software Engineer", "AIDS": "Data Analyst", "EXTC": "Embedded Engineer",
                    "MECH": "Graduate Engineer Trainee"}[code]
            start = dt.datetime(2026, 6, 1, 4, 0) + dt.timedelta(hours=random.randint(0, 175 * 24))
            sess = str(uuid.uuid4())
            failed = random.random() < 0.04
            bank = TECH_Q if itype == "TECHNICAL" else HR_Q if itype == "HR" else [TECH_Q[0]] + random.sample(TECH_Q[1:], 3) + random.sample(HR_Q[1:], 2)
            qs = [bank[0]] + random.sample(bank[1:], 5) if itype != "MIXED" else bank[:6]
            end = start + dt.timedelta(minutes=24 + random.randint(0, 12))
            st.append(f"insert into ai_interview_sessions (id, college_id, student_id, user_id, interview_type, difficulty, role_title, status, planned_questions, "
                      f"focus_skills, credit_source, consent_at, started_at, ended_at, error_message, created_at, updated_at) values ({q(sess)}, {q(C)}, {q(sid)}, {q(uid)}, "
                      f"{q(itype)}, {q(diff)}, {q(role)}, {q('FAILED' if failed else 'COMPLETED')}, 6, {q(', '.join(random.sample(SKILLS[code], 3)))}, 'FREE', "
                      f"{q(start)}, {q(start)}, {q(end)}, {q('Scoring could not finish because the AI service was busy or offline. Try again in a few minutes.' if failed else None)}, "
                      f"{q(start)}, {q(end)});")
            credits[(sid)] = credits.get(sid, 0) + 1
            level = max(0.15, min(0.95, (float(cgpa) - 5.5) / 4.2 + random.gauss(0, 0.12) - {"EASY": -0.08, "MEDIUM": 0, "HARD": 0.1}[diff]))
            total = 0.0
            for i, (topic, text) in enumerate(qs):
                qid, aid_, eid = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
                at = start + dt.timedelta(minutes=1 + i * 4)
                st.append(f"insert into ai_interview_questions (id, session_id, seq, question_text, topic, asked_at, created_at) values "
                          f"({q(qid)}, {q(sess)}, {i + 1}, {q(text)}, {q(topic)}, {q(at)}, {q(at)});")
                good = random.random() < level
                transcript = random.choice(GOOD if good else WEAK)
                st.append(f"insert into ai_interview_answers (id, session_id, question_id, transcript, input_mode, duration_seconds, words_per_minute, answered_at) values "
                          f"({q(aid_)}, {q(sess)}, {q(qid)}, {q(transcript)}, 'TEXT', {random.randint(40, 150)}, {random.randint(95, 150)}, {q(at + dt.timedelta(minutes=3))});")
                if failed: continue
                base = 4 if good else 2
                c, r_ = max(1, min(5, base + random.choice([-1, 0, 0, 1]))), max(1, min(5, base + random.choice([0, 0, 1])))
                cm, cf = max(1, min(5, base + random.choice([-1, 0, 1]))), max(1, min(5, base + random.choice([-1, 0, 0])))
                total += (0.40 * c + 0.25 * r_ + 0.20 * cm + 0.15 * cf) / 5 * 100
                fb = ("Clear and well structured; you backed the answer with an example." if good
                      else "The answer stayed general. Explain the idea first, then give one concrete example.")
                st.append(f"insert into ai_answer_evaluations (id, answer_id, content, communication, confidence, relevance, feedback, better_answer, model, created_at) values "
                          f"({q(eid)}, {q(aid_)}, {c}, {cm}, {cf}, {r_}, {q(fb)}, {q('Start with a one-line definition, explain how it works, then a short example from your project and one trade-off.')}, "
                          f"{q(model)}, {q(at + dt.timedelta(minutes=3))});")
            if failed: continue
            overall = round(total / len(qs), 2)
            rec = "READY" if overall >= 75 else "ALMOST_READY" if overall >= 55 else "NEEDS_PRACTICE"
            verdict = {"READY": "You are interview-ready for this role.", "ALMOST_READY": "You are close; a little more practice will make a clear difference.",
                       "NEEDS_PRACTICE": "Keep practising: focus on structure and examples."}[rec]
            st.append(f"insert into ai_interview_reports (id, session_id, overall_score, recommendation, summary, strengths_json, improvements_json, model, created_at) values "
                      f"({q(str(uuid.uuid4()))}, {q(sess)}, {overall}, {q(rec)}, {q(f'You answered all {len(qs)} questions and scored {int(overall)} out of 100. {verdict}')}, "
                      f"{q(json.dumps(['Clear introduction', 'Good use of project examples'] if overall >= 60 else ['Attempted every question']))}, "
                      f"{q(json.dumps(['Give one concrete example per answer', 'State the time complexity when you describe an approach'] if overall < 80 else ['Be more concise in the opening answer']))}, "
                      f"{q(model)}, {q(end + dt.timedelta(minutes=2))});")
    for sid, n in credits.items():
        st.append(f"insert into interview_credits (student_id, college_id, free_used, paid_balance, version) values ({q(sid)}, {q(C)}, {n}, 0, 0) "
                  f"on duplicate key update free_used = free_used + {n};")
    sql("\n".join(st))
    print(f"   {sum(credits.values())} AI interviews")


if __name__ == "__main__":
    main()
