"""End-to-end test of the mock test path (scripts/mocktests): unlock rule (first open, next opens only above 50%), the server
refusing locked tests, and every test scoring full marks with the answer key and the reference solutions. The coding problems run
in the real code runner (Judge0), so this also proves every hidden test passes within the time limit.

    python3 scripts/e2e_prep_ladder_test.py [base_url]

Uses a demo student (LADDER_STUDENT, default aditya.kulkarni3, who should have no attempts yet).
"""
import glob
import inspect
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "mocktests"))
import smoke_test  # noqa: E402
from smoke_test import call, check  # noqa: E402
import coding  # noqa: E402

STUDENT = (os.environ.get("LADDER_STUDENT", "aditya.kulkarni3@demo-college.edu.in"), "Student@123")
B = "/api/v1/college/student/prep"


def reference_code(title):
    """A standalone Python program from the problem's reference solution (the function the hidden outputs came from)."""
    p = next(p for p in coding.PROBLEMS if p["title"] == title)
    src = inspect.getsource(p["solve"])
    src = src[src.index("def _("):].replace("def _(", "def solve(", 1)
    return ("import sys, heapq\nfrom bisect import bisect_left\nfrom collections import Counter, deque\n\n"
            "def ints(s):\n    return list(map(int, s.split()))\n\n" + src + "\nsys.stdout.write(solve(sys.stdin.read()) + '\\n')\n")


def answers(defn, correct_mcq=None):
    """Server question ids are q1..qN in file order. correct_mcq: how many MCQs to get right (None = all)."""
    out, n, right = {}, 0, 0
    for s in defn["sections"]:
        for q in s["questions"]:
            n += 1
            if q["type"] == "MCQ":
                ok = correct_mcq is None or right < correct_mcq
                out[f"q{n}"] = {"option": q["answer"] if ok else (q["answer"] + 1) % len(q["options"])}
                right += ok
            else:
                out[f"q{n}"] = {"language": "python", "code": reference_code(q["title"])}
    return out


def ladder(t):
    s, r = call("GET", f"{B}/tests", token=t)
    return [x for x in r["data"] if x.get("order") is not None]


def attempt(t, test_id, ans):
    s, r = call("POST", f"{B}/tests/{test_id}/start", token=t)
    if s != 200:
        return s, r
    aid = r["data"]["attemptId"]
    # The backend allows one Submit per student every few seconds (CODE_RUN_RATE_LIMIT_SECONDS): wait and retry.
    for _ in range(6):
        s, r = call("POST", f"{B}/attempts/{aid}/submit", ans, token=t)
        if s != 429:
            return s, r
        time.sleep(6)
    return s, r


def main():
    defs = {}
    for f in sorted(glob.glob(os.path.join(HERE, "mocktests", "out", "*.json"))):
        d = json.load(open(f))
        defs[d["title"]] = d
    s, r = call("POST", "/api/v1/auth/login", {"email": STUDENT[0], "password": STUDENT[1]})
    if s != 200:  # a seeded student who never activated the account: set the password through the dev OTP reset
        f, fr = call("POST", "/api/v1/auth/forgot-password", {"email": STUDENT[0]})
        otp = (fr or {}).get("data", {}).get("devOtp") if f == 200 else None
        if otp:
            call("POST", "/api/v1/auth/reset-password", {"email": STUDENT[0], "resetToken": otp, "newPassword": STUDENT[1]})
            s, r = call("POST", "/api/v1/auth/login", {"email": STUDENT[0], "password": STUDENT[1]})
    if s != 200:
        raise SystemExit(f"login failed for {STUDENT[0]}")
    t = r["data"]["token"]

    print("== Path")
    lad = ladder(t)
    check("20 tests in the path, in order 1..20", [x["order"] for x in lad] == list(range(1, 21)), [x["order"] for x in lad])
    check("levels go easy → medium → hard", [x["level"] for x in lad] == ["EASY"] * 5 + ["MEDIUM"] * 7 + ["HARD"] * 8, [x["level"] for x in lad])
    check("test 1 is open, all others locked", not lad[0]["locked"] and all(x["locked"] for x in lad[1:]), [x["locked"] for x in lad])
    check("each locked test names the one before it", all(lad[i]["unlockAfter"] == lad[i - 1]["title"] for i in range(1, 20)))
    check("unlock rule is more than 50%", lad[0]["unlockPercent"] == 50)
    missing = [x["title"] for x in lad if x["title"] not in defs]
    check("every published test matches a built file", not missing, missing)
    s, r = call("POST", f"{B}/tests/{lad[1]['id']}/start", token=t)
    check("server refuses to start a locked test (403 TEST_LOCKED)", s == 403 and r.get("errorCode") == "TEST_LOCKED", (s, r.get("message")))

    print("== Exactly 50% does not unlock; more than 50% does")
    first = defs[lad[0]["title"]]
    n_mcq = sum(1 for sec in first["sections"] for q in sec["questions"] if q["type"] == "MCQ")
    s, r = attempt(t, lad[0]["id"], answers(first, n_mcq // 2))
    check(f"test 1 submitted with {n_mcq // 2}/{n_mcq} right = 50%", s == 200 and r["data"]["percent"] == 50, (s, r.get("data", {}).get("percent")))
    lad = ladder(t)
    check("at exactly 50% test 2 stays locked", lad[1]["locked"] and not lad[0]["passed"], (lad[1]["locked"], lad[0]["passed"]))
    s, r = attempt(t, lad[0]["id"], answers(first, n_mcq // 2 + 1))
    lad = ladder(t)
    check(f"with {n_mcq // 2 + 1}/{n_mcq} right, test 2 opens and test 3 stays locked", not lad[1]["locked"] and lad[2]["locked"] and lad[0]["passed"],
          [x["locked"] for x in lad[:3]])
    check("best score shown as a percentage", lad[0]["bestPercent"] == (n_mcq // 2 + 1) * 100 // n_mcq, lad[0]["bestPercent"])

    print("== Whole path with the answer key and reference solutions (coding runs in Judge0)")
    for i in range(1, 20):
        x = lad[i]
        d = defs[x["title"]]
        t0 = time.time()
        s, r = attempt(t, x["id"], answers(d))
        secs = time.time() - t0
        res = r.get("data") or {}
        bad = [q for q in res.get("questions", []) if q["type"] == "CODING" and q.get("testsPassed") != q.get("testsTotal")]
        check(f"{x['order']:>2}. {x['title'][:46]:46s} full marks {res.get('score')}/{res.get('maxScore')} ({secs:.0f}s)",
              s == 200 and res.get("percent") == 100 and not bad,
              (s, r.get("message"), [(q["title"], q.get("testsPassed"), q.get("testsTotal"), q.get("compileError")) for q in bad]))
        lad = ladder(t)
        if i < 19:
            check(f"    → test {i + 2} unlocked", not lad[i + 1]["locked"])
    check("whole path passed, nothing locked", all(x["passed"] and not x["locked"] for x in lad))

    print(f"\n{smoke_test.passed} passed, {smoke_test.failed} failed")
    sys.exit(1 if smoke_test.failed else 0)


if __name__ == "__main__":
    main()
