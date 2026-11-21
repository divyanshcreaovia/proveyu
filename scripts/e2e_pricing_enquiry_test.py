#!/usr/bin/env python3
"""
Pricing page enquiries, end to end through the API (public form -> admin list -> mark contacted).

Run against a TEST database only: it creates enquiries. Example:
    BASE=http://localhost:8081 python3 scripts/e2e_pricing_enquiry_test.py
Accounts (demo seed): admin@proveyu.local / Admin@1234, tpo@demo-college.edu.in / Demo@1234, a demo student / Student@123.
"""
import json, os, random, sys, urllib.error, urllib.request

BASE = os.environ.get("BASE", "http://localhost:8081")
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@proveyu.local"), os.environ.get("ADMIN_PASSWORD", "Admin@1234"))
TPO = (os.environ.get("TPO_EMAIL", "tpo@demo-college.edu.in"), os.environ.get("TPO_PASSWORD", "Demo@1234"))
STUDENT = (os.environ.get("STUDENT_EMAIL", "siddharth.iyer25@demo-college.edu.in"), os.environ.get("STUDENT_PASSWORD", "Student@123"))
passed, failed = 0, 0


def req(method, path, body=None, token=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try: return e.code, json.loads(raw)
        except Exception: return e.code, {"raw": raw.decode(errors="replace")[:200]}


def check(name, ok, detail=""):
    global passed, failed
    if ok: passed += 1
    else: failed += 1
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  ({detail})" if detail and not ok else ""))


def login(cred):
    s, r = req("POST", "/api/v1/auth/login", {"email": cred[0], "password": cred[1]})
    return r["data"]["token"] if s == 200 else None


tag = random.randint(10000, 99999)
mail = f"tpo.pricing{tag}@example-college.in"
form = {"fullName": "Ravi Kulkarni", "email": mail, "phone": "+91 98765 43210", "organization": f"Test Institute {tag}",
        "orgType": "COLLEGE", "designation": "TPO", "city": "Pune", "studentCount": "1000_3000",
        "message": "Placement portal for 4 branches.\n<script>alert(1)</script>", "website": ""}

admin, tpo, student = login(ADMIN), login(TPO), login(STUDENT)
check("admin, TPO and student can sign in (test accounts)", bool(admin and tpo and student))

print("Public form")
s, _ = req("GET", "/api/v1/admin/pricing-enquiries/new-count", token=admin)
before = req("GET", "/api/v1/admin/pricing-enquiries/new-count", token=admin)[1]["data"]["new"]
s, r = req("POST", "/api/v1/public/pricing-enquiries", form)
check("anyone can send the form without signing in", s == 200 and r.get("success"), f"{s} {r}")
check("the answer thanks the visitor", "contact you" in (r.get("message") or ""), r.get("message"))

for name, change in [("phone is required", {"phone": ""}), ("a bad phone is refused", {"phone": "call me"}),
                     ("a bad e-mail is refused", {"email": "not-an-email"}), ("the organisation is required", {"organization": " "}),
                     ("an unknown organisation type is refused", {"orgType": "HACKER"}), ("an unknown student range is refused", {"studentCount": "9999"}),
                     ("a 3000-character message is refused", {"message": "x" * 3000})]:
    s, r = req("POST", "/api/v1/public/pricing-enquiries", {**form, "email": f"v{tag}@x.in", **change})
    check(name, s == 400, f"{s} {r.get('errorCode')}")

s, r = req("POST", "/api/v1/public/pricing-enquiries", {**form, "email": f"bot{tag}@spam.example", "website": "http://spam.example"})
check("a bot (hidden website field filled) gets a normal answer", s == 200, f"{s}")

s1, _ = req("POST", "/api/v1/public/pricing-enquiries", form)
s2, _ = req("POST", "/api/v1/public/pricing-enquiries", form)
s3, r3 = req("POST", "/api/v1/public/pricing-enquiries", form)
check("one e-mail can send 3 a day, the 4th is refused politely", (s1, s2, s3) == (200, 200, 429) and r3.get("errorCode") == "ENQUIRY_LIMIT",
      f"{s1} {s2} {s3} {r3.get('errorCode')}")

print("Who can see the list")
check("no sign-in: admin list refused", req("GET", "/api/v1/admin/pricing-enquiries")[0] in (401, 403))
check("student: admin list refused", req("GET", "/api/v1/admin/pricing-enquiries", token=student)[0] == 403)
check("TPO: admin list refused", req("GET", "/api/v1/admin/pricing-enquiries", token=tpo)[0] == 403)
check("the public path is POST only (no GET of enquiries)", req("GET", "/api/v1/public/pricing-enquiries")[0] in (401, 403, 404, 405))

print("Admin")
s, r = req("GET", f"/api/v1/admin/pricing-enquiries?q=Test%20Institute%20{tag}", token=admin)
rows = r["data"]["rows"] if s == 200 else []
check("admin sees the enquiry (search by organisation)", s == 200 and len(rows) == 3, f"{s} rows={len(rows)}")
e = rows[0] if rows else {}
check("all details are stored", e.get("fullName") == "Ravi Kulkarni" and e.get("phone") == "+91 98765 43210" and e.get("city") == "Pune"
      and e.get("designation") == "TPO" and e.get("studentCount") == "1000_3000" and e.get("orgType") == "COLLEGE", json.dumps(e)[:200])
check("e-mail is stored in lower case", e.get("email") == mail)
check("a script in the message is kept as plain text (shown escaped)", "<script>" in (e.get("message") or ""))
check("a new enquiry has status NEW", e.get("status") == "NEW")
s, r = req("GET", f"/api/v1/admin/pricing-enquiries?q=bot{tag}", token=admin)
check("the bot submission was not stored", s == 200 and r["data"]["total"] == 0)
after = req("GET", "/api/v1/admin/pricing-enquiries/new-count", token=admin)[1]["data"]["new"]
check("the New badge count went up by 3", after - before == 3, f"{before} -> {after}")
s, r = req("GET", "/api/v1/notifications", token=admin)
texts = json.dumps(r.get("data"))
check("admin got a bell notification", f"Test Institute {tag}" in texts)

s, r = req("POST", f"/api/v1/admin/pricing-enquiries/{e['id']}/status", {"status": "CONTACTED", "note": "Called, demo on Monday"}, token=admin)
u = r.get("data") or {}
check("admin marks it contacted with a note", s == 200 and u.get("status") == "CONTACTED" and u.get("adminNote") == "Called, demo on Monday"
      and bool(u.get("handledBy")), f"{s} {u}")
s, r = req("GET", f"/api/v1/admin/pricing-enquiries?status=CONTACTED&q={tag}", token=admin)
check("the Contacted filter shows it", s == 200 and any(x["id"] == e["id"] for x in r["data"]["rows"]))
s, r = req("GET", f"/api/v1/admin/pricing-enquiries?status=NEW&q={tag}", token=admin)
check("the New filter no longer shows it", s == 200 and all(x["id"] != e["id"] for x in r["data"]["rows"]))
check("the New badge count went down by 1", req("GET", "/api/v1/admin/pricing-enquiries/new-count", token=admin)[1]["data"]["new"] == after - 1)
s, r = req("POST", f"/api/v1/admin/pricing-enquiries/{e['id']}/status", {"status": "CLOSED"}, token=admin)
check("closing keeps the earlier note", s == 200 and r["data"]["status"] == "CLOSED" and r["data"]["adminNote"] == "Called, demo on Monday")
check("a TPO cannot change the status", req("POST", f"/api/v1/admin/pricing-enquiries/{e['id']}/status", {"status": "NEW"}, token=tpo)[0] == 403)
check("an unknown id gives 404", req("POST", "/api/v1/admin/pricing-enquiries/00000000-0000-0000-0000-000000000000/status",
                                     {"status": "CLOSED"}, token=admin)[0] == 404)
s, r = req("GET", "/api/v1/admin/audit?size=20", token=admin)
check("the change is in the audit log", s == 200 and "pricing enquiry" in json.dumps(r.get("data")).lower(), f"{s}")

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
