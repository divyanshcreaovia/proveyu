"""College placement team (TPO) mock tests: upload with clear errors, draft → publish, only that college's students see it, other
colleges cannot see or touch it, viewers cannot change anything, student attempt stats, download, delete, audit log.

    python3 scripts/e2e_college_prep_test.py [base_url]

Uses the demo college (seed_demo.py) and smoke-test college A (smoke_test.py). Deletes the test it creates.
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import smoke_test  # noqa: E402
from smoke_test import call, check  # noqa: E402

C = "/api/v1/college/prep/tests"
P = "/api/v1/college/student/prep"


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def upload(token, doc, name="test.json"):
    raw = doc if isinstance(doc, bytes) else json.dumps(doc).encode()
    return call("POST", C, token=token, files={"file": (name, raw, "application/json")})


def main():
    tpo = login("tpo@demo-college.edu.in", "Demo@1234")
    coord = login("coordinator@demo-college.edu.in", "Team@1234")
    viewer = login("viewer@demo-college.edu.in", "Team@1234")
    student = login("arjun.menon5@demo-college.edu.in", "Student@123")
    other_tpo = login(os.environ.get("OTHER_TPO", "tpo@a688ddb.edu.in"), "Secret123")
    other_student = login(os.environ.get("OTHER_STUDENT", "asha688ddb@a688ddb.edu.in"), "Student123")
    if not all([tpo, coord, viewer, student, other_tpo, other_student]):
        raise SystemExit("Logins failed: run seed_demo.py and smoke_test.py first")

    print("== Upload: clear errors for a broken file")
    s, r = upload(tpo, b"{ this is not json")
    check("a file that is not JSON is refused with a message", s == 400 and r.get("message"), r)
    bad = {"title": "Broken", "durationMinutes": 2, "sections": [{"name": "Quant", "questions": [
        {"type": "MCQ", "text": "2 + 2 = ?", "options": ["3", "4"], "answer": 5},
        {"type": "CODING", "title": "X", "text": "y"}]}]}
    s, r = upload(tpo, bad)
    msg = r.get("message", "")
    check("validation lists each problem (duration, answer index, missing hidden tests)",
          s == 400 and "durationMinutes" in msg and "question 1" in msg and "hidden test" in msg, msg)

    print("== Upload a real test (as a draft)")
    test = json.load(open(sorted(glob.glob(os.path.join(HERE, "mocktests", "out", "*.json")))[0]))
    test["title"] = "Demo College practice: Wipro pattern"
    test["companyTag"] = "Wipro"
    test["order"] = 1  # the path is platform-only; a college test must ignore this
    s, r = upload(tpo, test)
    check("TPO uploads a test, saved as a draft", s == 200 and r["data"]["published"] is False, r.get("message"))
    tid = r["data"]["id"]
    check("a college test never joins the unlock path (order ignored)", r["data"].get("order") is None, r["data"].get("order"))
    s, r = upload(viewer, test)
    check("a viewer cannot upload (403)", s == 403, s)
    s, r = call("GET", C, token=viewer)
    check("a viewer can see the list", s == 200 and any(t["id"] == tid for t in r["data"]))

    def student_sees(token):
        s, r = call("GET", f"{P}/tests", token=token)
        return next((t for t in r["data"] if t["id"] == tid), None)
    check("a draft is not shown to students", student_sees(student) is None)

    print("== Publish")
    s, r = call("PATCH", f"{C}/{tid}/publish", {"published": True}, token=viewer)
    check("a viewer cannot publish (403)", s == 403, s)
    s, r = call("PATCH", f"{C}/{tid}/publish", {"published": True}, token=coord)
    check("a coordinator can publish", s == 200 and r["data"]["published"], r.get("message"))
    card = student_sees(student)
    check("the college's student now sees it, open, as a standalone test", card is not None and not card["locked"] and card.get("order") is None, card)

    print("== Other colleges")
    check("a student of another college does not see it", student_sees(other_student) is None)
    s, r = call("POST", f"{P}/tests/{tid}/start", token=other_student)
    check("…and cannot start it by id (404)", s == 404, s)
    s, r = call("GET", C, token=other_tpo)
    check("another college's TPO does not see it in their list", s == 200 and all(t["id"] != tid for t in r["data"]))
    for method, path, body in (("PATCH", f"{C}/{tid}/publish", {"published": False}), ("GET", f"{C}/{tid}/definition", None), ("DELETE", f"{C}/{tid}", None)):
        s, _ = call(method, path, body, token=other_tpo, raw=method == "GET")
        check(f"another college's TPO cannot {method} it (404)", s == 404, s)

    print("== Student takes it; TPO sees stats")
    s, r = call("POST", f"{P}/tests/{tid}/start", token=student)
    aid = r["data"]["attemptId"]
    answers, n = {}, 0
    for sec in test["sections"]:
        for q in sec["questions"]:
            n += 1
            answers[f"q{n}"] = {"option": q["answer"] if n % 2 else (q["answer"] + 1) % len(q["options"])}
    s, r = call("POST", f"{P}/attempts/{aid}/submit", answers, token=student)
    pct = r["data"]["percent"]
    check("student submits and gets a score", s == 200 and pct > 0, (s, r.get("message")))
    s, r = call("GET", C, token=tpo)
    row = next(t for t in r["data"] if t["id"] == tid)
    check(f"TPO sees 1 student, 1 attempt, average {pct}%", row["students"] == 1 and row["attempts"] == 1 and row["averagePercent"] == pct, row)
    s, body = call("GET", f"{C}/{tid}/definition", token=tpo, raw=True)
    check("TPO can download the JSON to edit it", s == 200 and json.loads(body)["title"] == test["title"])

    print("== Unpublish, delete, audit")
    call("PATCH", f"{C}/{tid}/publish", {"published": False}, token=tpo)
    check("unpublished: students no longer see it", student_sees(student) is None)
    s, r = call("DELETE", f"{C}/{tid}", token=viewer)
    check("a viewer cannot delete (403)", s == 403, s)
    s, r = call("DELETE", f"{C}/{tid}", token=tpo)
    check("TPO deletes it", s == 200, r)
    s, r = call("GET", C, token=tpo)
    check("gone from the list", all(t["id"] != tid for t in r["data"]))
    s, r = call("GET", "/api/v1/college/audit-logs?size=50", token=tpo)
    items = (r.get("data") or {}).get("content", r.get("data") or [])
    actions = {i.get("action") for i in items} if isinstance(items, list) else set()
    check("upload, publish, unpublish and delete are in the audit log",
          {"MOCK_TEST_UPLOADED", "MOCK_TEST_PUBLISHED", "MOCK_TEST_UNPUBLISHED", "MOCK_TEST_DELETED"} <= actions, sorted(a for a in actions if a and "MOCK" in a))

    print(f"\n{smoke_test.passed} passed, {smoke_test.failed} failed")
    sys.exit(1 if smoke_test.failed else 0)


if __name__ == "__main__":
    main()
