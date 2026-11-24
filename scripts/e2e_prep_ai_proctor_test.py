#!/usr/bin/env python3
"""
Mock test AI proctoring (camera + microphone), end to end through the API:
start a test -> AI event refused without consent -> consent -> AI event saved (DB row) -> college sees it -> flagged over the limit
(never auto-submitted) -> refused after submit.

Run against a TEST database only (it creates a test, an attempt and events). Example:
    BASE=http://localhost:8081 DB_NAME=proveyutestdb DB_USER=... DB_PASS=... python3 scripts/e2e_prep_ai_proctor_test.py
Accounts (demo seed): tpo@demo-college.edu.in / Demo@1234 and one of its students / Student@123.
DB_* are optional: without them the database checks are skipped.
"""
import json, os, random, subprocess, sys, urllib.error, urllib.request, uuid
from datetime import datetime, timezone

BASE = os.environ.get("BASE", "http://localhost:8081")
TPO = (os.environ.get("TPO_EMAIL", "tpo@demo-college.edu.in"), os.environ.get("TPO_PASSWORD", "Demo@1234"))
STUDENT = (os.environ.get("STUDENT_EMAIL", "siddharth.iyer25@demo-college.edu.in"), os.environ.get("STUDENT_PASSWORD", "Student@123"))
DB = {k: os.environ.get(f"DB_{k.upper()}") for k in ("name", "user", "pass", "host")}
JPEG = bytes([0xFF, 0xD8, 0xFF, 0xE0, 0, 16]) + b"JFIF\x00\x01" + bytes(64)
passed = failed = 0


def check(name, ok, detail=""):
    global passed, failed
    passed, failed = (passed + 1, failed) if ok else (passed, failed + 1)
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"  ({detail})" if detail and not ok else ""))


def req(method, path, token=None, body=None, raw=None, ctype=None):
    h = {}
    if token: h["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = json.dumps(body).encode(); h["Content-Type"] = "application/json"
    if raw is not None:
        data = raw; h["Content-Type"] = ctype
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read())
        except Exception: return e.code, {}


def multipart(fields, files=()):
    b = uuid.uuid4().hex
    out = b""
    for k, v in fields.items():
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode()
    for name, fname, ctype, data in files:
        out += f"--{b}\r\nContent-Disposition: form-data; name=\"{name}\"; filename=\"{fname}\"\r\nContent-Type: {ctype}\r\n\r\n".encode() + data + b"\r\n"
    return out + f"--{b}--\r\n".encode(), f"multipart/form-data; boundary={b}"


def ai(attempt, token, kind, metadata=None, snapshot=None):
    fields = {"kind": kind, "clientAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}
    if metadata: fields["metadata"] = json.dumps(metadata)
    body, ct = multipart(fields, [("snapshot", "c.jpg", "image/jpeg", snapshot)] if snapshot else [])
    return req("POST", f"/api/v1/college/student/prep/attempts/{attempt}/proctor/ai", token, raw=body, ctype=ct)


def sql(q):
    if not all(DB[k] for k in ("name", "user", "pass")): return None
    env = dict(os.environ, MYSQL_PWD=DB["pass"])
    r = subprocess.run(["mysql", "-h", DB["host"] or "127.0.0.1", "-u", DB["user"], "-N", "-B", DB["name"], "-e", q], env=env,
                       capture_output=True, text=True, timeout=30)
    return r.stdout.strip()


def login(cred):
    s, r = req("POST", "/api/v1/auth/login", body={"email": cred[0], "password": cred[1]})
    return r["data"]["token"] if s == 200 else None


tpo, student = login(TPO), login(STUDENT)
check("TPO and student can sign in (test accounts)", bool(tpo and student))
if not (tpo and student): sys.exit(1)

print("Set-up")
title = f"AI proctor e2e {random.randint(1000, 9999)}"
test_json = json.dumps({"title": title, "durationMinutes": 30, "sections": [{"name": "Aptitude", "questions": [
    {"type": "MCQ", "text": "2 + 2 = ?", "options": ["3", "4", "5", "6"], "answer": 1}]}]}).encode()
body, ct = multipart({}, [("file", "t.json", "application/json", test_json)])
s, r = req("POST", "/api/v1/college/prep/tests", tpo, raw=body, ctype=ct)
check("TPO uploads a test", s == 200, f"{s} {r.get('message')}")
test_id = r["data"]["id"]
req("PATCH", f"/api/v1/college/prep/tests/{test_id}/publish", tpo, body={"published": True})
cards = req("GET", "/api/v1/college/student/prep/tests", student)[1]["data"]
card = next((c for c in cards if c["id"] == test_id), None)
check("student's test card says AI proctoring is on", bool(card and card.get("aiProctoring")))
s, r = req("POST", f"/api/v1/college/student/prep/tests/{test_id}/start", student)
attempt = r["data"]["attemptId"]
check("student starts the test", s == 200 and r["data"].get("aiProctoring") is True and r["data"].get("consentAt") is None)
base = f"/api/v1/college/student/prep/attempts/{attempt}"

print("Consent")
s, r = ai(attempt, student, "NO_FACE")
check("AI event without consent is refused (403 CONSENT_REQUIRED)", s == 403 and r.get("errorCode") == "CONSENT_REQUIRED", f"{s} {r.get('errorCode')}")
s1, r1 = req("POST", base + "/consent", student)
s2, r2 = req("POST", base + "/consent", student)
check("consent is recorded and idempotent", s1 == s2 == 200 and r1["data"]["consentAt"] == r2["data"]["consentAt"])
check("attempt view shows the consent", req("GET", base, student)[1]["data"].get("consentAt") is not None)

print("Events")
s, r = ai(attempt, student, "MULTIPLE_FACES", {"faces": 2}, JPEG)
d = r.get("data") or {}
check("AI event with snapshot is saved", s == 200 and d.get("recorded") and d["event"]["hasScreenshot"] and d["aiWarnings"] == 1, f"{s} {r}")
ev = d.get("event", {}).get("id")
row = sql(f"select kind, metadata, screenshot_path is not null from prep_proctor_events where id = '{ev}'")
if row is not None:
    check("DB row: kind, metadata and snapshot path", row.split("\t") == ["MULTIPLE_FACES", '{"faces": 2}', "1"], row)
    check("DB: ai_warning_count = 1", sql(f"select ai_warning_count from prep_attempts where id = '{attempt}'") == "1")
s, r = ai(attempt, student, "MULTIPLE_FACES", {"faces": 2})
check("same kind within 10 s is a no-op", s == 200 and r["data"]["recorded"] is False and r["data"]["aiWarnings"] == 1)
check("invalid kind is refused", ai(attempt, student, "TAB_HIDDEN")[1].get("errorCode") == "INVALID_KIND")
check("snapshot over 200 KB is refused", ai(attempt, student, "NO_FACE", snapshot=JPEG + bytes(205 * 1024))[0] == 413)

print("College")
s, r = req("GET", f"/api/v1/college/prep/attempts/{attempt}/proctor", tpo)
evs = (r.get("data") or {}).get("events", [])
check("college log shows the AI event with metadata", s == 200 and any(e["kind"] == "MULTIPLE_FACES" and e.get("ai") and e.get("metadata") for e in evs))
s, r = req("GET", f"/api/v1/college/prep/attempts?flagged=true", tpo)
rowc = next((a for a in r.get("data", []) if a["attemptId"] == attempt), None)
check("attempt is in the college's flagged list with its AI count", bool(rowc) and rowc.get("aiWarnings") == 1 and rowc.get("tabSwitches") == 0)

print("Limit")
for k in ("NO_FACE", "LOOKING_AWAY", "CAMERA_BLOCKED", "VOICE_DETECTED"):
    ai(attempt, student, k)
if sql("select 1") is not None:
    sql(f"update prep_proctor_events set occurred_at = occurred_at - interval 1 minute where attempt_id = '{attempt}'")
    s, r = ai(attempt, student, "NO_FACE")
    d = r.get("data") or {}
    check("over the limit (5): flagged, not submitted", d.get("aiWarnings") == 6 and d.get("flagged") is True and d.get("autoSubmitted") is False, str(d)[:200])
    check("DB: attempt still open", sql(f"select submitted_at is null from prep_attempts where id = '{attempt}'") == "1")
log = req("GET", base + "/proctor", student)[1]["data"]
check("student's own log shows the AI events", log.get("aiWarnings", 0) >= 5 and sum(1 for e in log["events"] if e.get("ai")) >= 5)

print("After submit")
req("POST", base + "/submit", student, body={})
s, r = ai(attempt, student, "LOOKING_AWAY")
check("AI event after submit is refused (409)", s == 409 and r.get("errorCode") == "ATTEMPT_SUBMITTED", f"{s} {r.get('errorCode')}")

req("DELETE", f"/api/v1/college/prep/tests/{test_id}", tpo)  # tidy up the test (its attempt goes with it)
print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
