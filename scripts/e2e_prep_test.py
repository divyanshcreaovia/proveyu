#!/usr/bin/env python3
"""
End-to-end test of mock tests: admin uploads + publishes, a student starts, runs code (all 4 languages),
autosaves, submits, and the server-side score is checked. Needs the backend (dev) and the code-runner image.

  python3 scripts/e2e_prep_test.py [admin_email admin_password student_email student_password]
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import smoke_test as st  # noqa: E402
from smoke_test import call, check  # noqa: E402

ADMIN = (sys.argv[1], sys.argv[2]) if len(sys.argv) > 2 else ("admin@proveyu.local", "Admin@1234")
STUDENT = (sys.argv[3], sys.argv[4]) if len(sys.argv) > 4 else ("aarav.sharma1@demo-college.edu.in", "Student@123")
SAMPLE = os.path.join(os.path.dirname(__file__), "mock-tests", "placement-mock-1.json")

SUM_DIGITS = {
    "python": "n = input().strip()\nprint(sum(int(c) for c in n))\n",
    "java": "import java.util.*;\npublic class Main { public static void main(String[] a) { String n = new Scanner(System.in).next(); int s = 0; for (char c : n.toCharArray()) s += c - '0'; System.out.println(s); } }\n",
    "cpp": "#include <bits/stdc++.h>\nusing namespace std;\nint main(){ string n; cin >> n; int s = 0; for(char c : n) s += c - '0'; cout << s << endl; }\n",
    "javascript": "const n = require('fs').readFileSync(0, 'utf8').trim();\nconsole.log([...n].reduce((s, c) => s + Number(c), 0));\n",
}
SECOND_LARGEST_JAVA = """import java.util.*;
public class Main { public static void main(String[] x) { Scanner sc = new Scanner(System.in); int n = sc.nextInt();
  TreeSet<Long> s = new TreeSet<>(); for (int i = 0; i < n; i++) s.add(sc.nextLong());
  s.pollLast(); System.out.println(s.isEmpty() ? -1 : s.last()); } }
"""


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    check(f"login {email}", s == 200, r)
    return r["data"]["token"]


def main():
    print(f"Mock test E2E against {st.BASE}")
    admin, stu = login(*ADMIN), login(*STUDENT)

    print("== Admin upload")
    bad = json.dumps({"title": "", "durationMinutes": 1, "sections": [{"name": "A", "questions": [{"type": "MCQ", "text": "x", "options": ["a"], "answer": 3}]}]}).encode()
    s, r = call("POST", "/api/v1/admin/prep/tests", files={"file": ("bad.json", bad, "application/json")}, token=admin)
    check("invalid file rejected with reasons (400)", s == 400 and "title is required" in r.get("message", ""), r)
    check("student cannot use admin API (403)", call("GET", "/api/v1/admin/prep/tests", token=stu)[0] == 403)
    with open(SAMPLE, "rb") as f:
        s, r = call("POST", "/api/v1/admin/prep/tests", files={"file": ("placement-mock-1.json", f.read(), "application/json")}, token=admin)
    t = r["data"]
    check("sample test uploaded as draft (17 MCQ + 2 coding, 37 marks)", s == 200 and t["questionCount"] == 19 and t["codingCount"] == 2
          and t["totalMarks"] == 37 and not t["published"], r)
    tid = t["id"]
    check("draft hidden from students", all(x["id"] != tid for x in call("GET", "/api/v1/college/student/prep/tests", token=stu)[1]["data"]))
    check("publish", call("PATCH", f"/api/v1/admin/prep/tests/{tid}/publish", {"published": True}, admin)[0] == 200)
    card = next(x for x in call("GET", "/api/v1/college/student/prep/tests", token=stu)[1]["data"] if x["id"] == tid)
    check("student sees the card with sections", len(card["sections"]) == 5 and card["durationMinutes"] == 45, card)

    print("== Attempt")
    s, r = call("POST", f"/api/v1/college/student/prep/tests/{tid}/start", token=stu)
    a = r["data"]
    aid = a["attemptId"]
    raw = json.dumps(a)
    check("attempt started with 19 questions and a 45-min deadline", s == 200 and len(a["questions"]) == 19, r)
    check("no answers or hidden tests sent to the browser", '"answer"' not in raw and '"tests"' not in raw and "999999999999999999" not in raw)
    s, r2 = call("POST", f"/api/v1/college/student/prep/tests/{tid}/start", token=stu)
    check("starting again resumes the same attempt", r2["data"]["attemptId"] == aid)

    mcq = [q for q in a["questions"] if q["type"] == "MCQ"]
    coding = [q for q in a["questions"] if q["type"] == "CODING"]
    with open(SAMPLE) as f:
        definition = json.load(f)
    correct = [q["answer"] for sec in definition["sections"] for q in sec["questions"] if q["type"] == "MCQ"]

    print("== Run code (samples) in all 4 languages")
    for lang, code in SUM_DIGITS.items():
        t0 = time.time()
        s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/run", {"questionId": coding[0]["id"], "language": lang, "code": code}, stu)
        cases = (r.get("data") or {}).get("cases", [])
        check(f"{lang}: samples pass ({time.time() - t0:.1f}s)", s == 200 and len(cases) == 2 and all(c["passed"] for c in cases), r)
    s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/run", {"questionId": coding[0]["id"], "language": "cpp", "code": "int main( {"}, stu)
    check("compile error is reported", s == 200 and r["data"]["compileError"], r)
    s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/run", {"questionId": coding[0]["id"], "language": "python", "code": "while True: pass"}, stu)
    check("infinite loop stopped with time limit", s == 200 and all(c["timedOut"] for c in r["data"]["cases"]), r)
    s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/run", {"questionId": coding[0]["id"], "language": "python",
                                                                          "code": "import urllib.request\nprint(urllib.request.urlopen('http://example.com', timeout=2).status)"}, stu)
    check("sandbox has no network", s == 200 and not any(c["passed"] for c in r["data"]["cases"]) and r["data"]["cases"][0]["error"], r)

    print("== Autosave + submit")
    answers = {q["id"]: {"option": correct[i] if i < 12 else (correct[i] + 1) % len(q["options"])} for i, q in enumerate(mcq)}  # 12 right, 5 wrong
    check("autosave", call("PUT", f"/api/v1/college/student/prep/attempts/{aid}/answers", answers, stu)[0] == 200)
    resumed = call("GET", f"/api/v1/college/student/prep/attempts/{aid}", token=stu)[1]["data"]
    check("autosaved answers come back on resume", len(resumed["answers"]) == 17)
    final = {coding[0]["id"]: {"language": "python", "code": SUM_DIGITS["python"]},
             coding[1]["id"]: {"language": "java", "code": SECOND_LARGEST_JAVA}}
    s, r = call("POST", f"/api/v1/college/student/prep/attempts/{aid}/submit", final, stu)
    res = r["data"]
    check("score = 12 MCQ + 10 + 10 = 32 / 37", s == 200 and float(res["score"]) == 32 and res["maxScore"] == 37 and res["percent"] == 86, res and {k: res[k] for k in ("score", "maxScore", "percent")})
    cq = [q for q in res["questions"] if q["type"] == "CODING"]
    check("coding: all hidden tests passed", all(q["testsPassed"] == q["testsTotal"] == 4 for q in cq), cq)
    check("result has 5 section scores and explanations", len(res["sections"]) == 5 and all(q.get("explanation") for q in res["questions"]))
    check("cannot change answers after submit (409)", call("PUT", f"/api/v1/college/student/prep/attempts/{aid}/answers", answers, stu)[0] == 409)
    card = next(x for x in call("GET", "/api/v1/college/student/prep/tests", token=stu)[1]["data"] if x["id"] == tid)
    check("card shows best score and attempt count", float(card["bestScore"]) == 32 and card["attempts"] >= 1 and card["lastAttemptId"] == aid, card)
    other = login("vivaan.khan2@demo-college.edu.in", "Student@123")
    check("another student cannot read this result (404)", call("GET", f"/api/v1/college/student/prep/attempts/{aid}/result", token=other)[0] == 404)
    check("cleanup: test copy deleted", call("DELETE", f"/api/v1/admin/prep/tests/{tid}", token=admin)[0] == 200)
    print(f"\n{st.passed} passed, {st.failed} failed")
    sys.exit(1 if st.failed else 0)


if __name__ == "__main__":
    main()
