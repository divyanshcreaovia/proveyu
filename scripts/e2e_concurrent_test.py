"""Several students at the same time (no interview is actually taken):
interview counts, buying interviews by UPI and the admin approving them (including races: the same payment approved twice at once,
the same UTR claimed by two students at once), the mock test path unlocking tests 2 and 3, the server refusing test 4, and code
running in Python, Java, C++ and JavaScript for every student at once.

    python3 scripts/e2e_concurrent_test.py [base_url]

Leaves test data (purchases, credits, prep attempts); clean it up afterwards if needed.
"""
import glob
import json
import os
import random
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import smoke_test  # noqa: E402
from smoke_test import call, check  # noqa: E402

STUDENTS = ["saanvi.das13", "arjun.menon5", "krishna.sharma9", "aditya.kulkarni3"]
IV = "/api/v1/college/student/interview"
PREP = "/api/v1/college/student/prep"

CODE = {  # "Sum of digits": read N (up to 10^18), print the sum of its digits
    "python": "n = input().strip()\nprint(sum(int(c) for c in n))\n",
    "java": "import java.util.*;\npublic class Main {\n  public static void main(String[] a) {\n    String n = new Scanner(System.in).next();\n"
            "    int s = 0;\n    for (char c : n.toCharArray()) s += c - '0';\n    System.out.println(s);\n  }\n}\n",
    "cpp": "#include <bits/stdc++.h>\nusing namespace std;\nint main(){ string n; cin >> n; int s = 0; for (char c : n) s += c - '0'; cout << s << endl; }\n",
    "javascript": "const n = require('fs').readFileSync(0, 'utf8').trim();\nlet s = 0; for (const c of n) s += +c;\nconsole.log(s);\n",
}
BROKEN_JAVA = "public class Main { public static void main(String[] a) { System.out.println(1) } }\n"


def login(email):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": "Student@123"})
    assert s == 200, (email, r)
    return r["data"]["token"]


def together(fn, items):
    """Run fn(item) for all items at the same moment (a barrier releases every thread together)."""
    barrier = threading.Barrier(len(items))

    def go(x):
        barrier.wait()
        return fn(x)
    with ThreadPoolExecutor(len(items)) as ex:
        return list(ex.map(go, items))


def credits(t):
    return call("GET", IV, token=t)[1]["data"]["credits"]


def utr():
    return str(random.randint(10 ** 11, 10 ** 12 - 1))


def answers(defn, right):
    out, n, ok = {}, 0, 0
    for s in defn["sections"]:
        for q in s["questions"]:
            n += 1
            if q["type"] == "MCQ":
                good = ok < right
                out[f"q{n}"] = {"option": q["answer"] if good else (q["answer"] + 1) % len(q["options"])}
                ok += good
    return out


def main():
    admin = call("POST", "/api/v1/auth/login", {"email": "admin@proveyu.local", "password": "Admin@1234"})[1]["data"]["token"]
    tokens = together(lambda e: login(f"{e}@demo-college.edu.in"), STUDENTS)
    users = dict(zip(STUDENTS, tokens))
    print(f"== {len(users)} students logged in at the same time")

    print("== Interview count")
    before = {u: credits(t) for u, t in users.items()}
    for u, c in before.items():
        check(f"{u}: 1 interview available (free), UPI buying on", c["freeLeft"] + c["paidBalance"] == 1 and c["paymentsReady"], c)

    print("== All buy 2 interviews by UPI at the same time")
    orders = together(lambda u: (u, call("POST", f"{IV}/purchases", {"quantity": 2}, token=users[u])), STUDENTS)
    ok = all(s == 200 and r["data"]["amountRupees"] == 40 and "am=40.00" in r["data"]["upiUri"] for _, (s, r) in orders)
    check("4 payment requests created together, each Rs 40.00 in the UPI link", ok, orders)
    refs = [r["data"]["reference"] for _, (s, r) in orders]
    check("every request has its own reference", len(set(refs)) == 4, refs)
    pid = {u: r["data"]["purchaseId"] for u, (s, r) in orders}
    utrs = {u: utr() for u in STUDENTS}
    subs = together(lambda u: call("POST", f"{IV}/purchases/{pid[u]}/utr", {"utr": utrs[u]}, token=users[u]), STUDENTS)
    check("all 4 UTRs submitted together, each waiting for verification", all(s == 200 and r["data"]["status"] == "SUBMITTED" for s, r in subs), subs)
    check("counts unchanged before the admin approves", all(credits(t)["paidBalance"] == before[u]["paidBalance"] for u, t in users.items()))

    print("== Same UTR claimed by two students at the same moment")
    extra = {u: call("POST", f"{IV}/purchases", {"quantity": 1}, token=users[u])[1]["data"]["purchaseId"] for u in STUDENTS[:2]}
    shared = utr()
    race = together(lambda u: call("POST", f"{IV}/purchases/{extra[u]}/utr", {"utr": shared}, token=users[u]), STUDENTS[:2])
    codes = sorted(s for s, _ in race)
    check("exactly one succeeds, the other gets UTR_ALREADY_USED", codes == [200, 409] and any(r.get("errorCode") == "UTR_ALREADY_USED" for _, r in race), race)

    print("== Admin approves all at the same time (+ the same payment approved twice at once)")
    targets = [pid[u] for u in STUDENTS] + [pid[STUDENTS[0]]]
    appr = together(lambda p: call("POST", f"/api/v1/admin/interview/purchases/{p}/approve", token=admin), targets)
    check("all 5 approve calls answered OK", all(s == 200 and r["data"]["status"] == "PAID" for s, r in appr), [s for s, _ in appr])
    after = {u: credits(t) for u, t in users.items()}
    for u in STUDENTS:
        a = after[u]
        check(f"{u}: exactly 2 added (not 4 for the double approve) → {a['freeLeft'] + a['paidBalance']} available",
              a["paidBalance"] == before[u]["paidBalance"] + 2 and a["freeLeft"] + a["paidBalance"] == 3, (before[u], a))
    mine = call("GET", IV, token=users[STUDENTS[0]])[1]["data"]["purchases"]
    check("student sees the payment as approved", any(p["id"] == pid[STUDENTS[0]] and p["status"] == "PAID" for p in mine))

    print("== Limit: at most 3 payments waiting per student")
    t3 = users[STUDENTS[2]]
    statuses = []
    for i in range(4):
        s, r = call("POST", f"{IV}/purchases", {"quantity": 1 + i}, token=t3)
        if s != 200:
            statuses.append((s, r.get("errorCode")))
            break
        s2, _ = call("POST", f"{IV}/purchases/{r['data']['purchaseId']}/utr", {"utr": utr()}, token=t3)
        statuses.append(s2)
    check("3 can wait; the 4th is refused (TOO_MANY_PENDING)", statuses[:3] == [200, 200, 200] and statuses[3] == (409, "TOO_MANY_PENDING"), statuses)

    print("== Mock test path: all 4 unlock tests 2 and 3 at the same time")
    defs = {json.load(open(f))["title"]: json.load(open(f)) for f in glob.glob(os.path.join(HERE, "mocktests", "out", "*.json"))}

    def path(u):
        t = users[u]
        lad = [x for x in call("GET", f"{PREP}/tests", token=t)[1]["data"] if x.get("order")]
        log = {"start": [x["locked"] for x in lad[:4]]}
        for i in range(2):
            d = defs[lad[i]["title"]]
            n = sum(1 for s in d["sections"] for q in s["questions"] if q["type"] == "MCQ")
            s, r = call("POST", f"{PREP}/tests/{lad[i]['id']}/start", token=t)
            aid = r["data"]["attemptId"]
            s, r = call("POST", f"{PREP}/attempts/{aid}/submit", answers(d, n * 3 // 4), token=t)
            log[f"t{i + 1}"] = r["data"]["percent"]
        lad = [x for x in call("GET", f"{PREP}/tests", token=t)[1]["data"] if x.get("order")]
        log["end"] = [x["locked"] for x in lad[:4]]
        s, r = call("POST", f"{PREP}/tests/{lad[3]['id']}/start", token=t)
        log["t4"] = (s, r.get("errorCode"))
        return log
    logs = together(path, STUDENTS)
    for u, lg in zip(STUDENTS, logs):
        check(f"{u}: start [open, locked, locked, locked] → scored {lg['t1']}% and {lg['t2']}% → [open, open, open, locked]; test 4 refused",
              lg["start"] == [False, True, True, True] and lg["end"] == [False, False, False, True] and lg["t4"] == (403, "TEST_LOCKED"), lg)

    print("== Code: 4 students × 4 languages at the same time (16 runs) + a compile error")
    # A temporary standalone test (the sample template, which has a "Sum of digits" problem), deleted at the end.
    tpl = json.load(open(os.path.join(HERE, "..", "Frontend", "public", "samples", "mock-test-template.json")))
    tpl["title"] = "Concurrent test (temporary)"
    s, r = call("POST", "/api/v1/admin/prep/tests", token=admin, files={"file": ("t.json", json.dumps(tpl).encode(), "application/json")})
    std = r["data"]
    call("PATCH", f"/api/v1/admin/prep/tests/{std['id']}/publish", {"published": True}, token=admin)
    att = {}
    for u in STUDENTS:
        v = call("POST", f"{PREP}/tests/{std['id']}/start", token=users[u])[1]["data"]
        q = next(q for q in v["questions"] if q["type"] == "CODING" and "digit" in (q.get("title") or "").lower())
        att[u] = (v["attemptId"], q["id"])
    jobs = [(u, lang) for u in STUDENTS for lang in CODE]
    t0 = time.time()

    def run(job):
        u, lang = job
        aid, qid = att[u]
        return job, call("POST", f"{PREP}/attempts/{aid}/run", {"questionId": qid, "language": lang, "code": CODE[lang]}, token=users[u])
    res = together(run, jobs)
    secs = time.time() - t0
    for (u, lang), (s, r) in res:
        d = r.get("data") or {}
        cases = d.get("cases") or []
        if not (s == 200 and not d.get("compileError") and cases and all(c["passed"] for c in cases)):
            check(f"{u} {lang} compiles and passes the samples", False, (s, r.get("message"), d.get("compileError"), cases[:1]))
    passed = sum(1 for _, (s, r) in res if s == 200 and not (r.get("data") or {}).get("compileError") and all(c["passed"] for c in r["data"]["cases"]))
    check(f"all 16 runs compile and pass the samples ({passed}/16, {secs:.0f}s for all together)", passed == 16)
    s, r = call("POST", f"{PREP}/attempts/{att[STUDENTS[0]][0]}/run", {"questionId": att[STUDENTS[0]][1], "language": "java", "code": BROKEN_JAVA},
                token=users[STUDENTS[0]])
    check("a Java syntax error is reported as a compile error", s == 200 and bool((r.get("data") or {}).get("compileError")), r.get("data"))
    for u in STUDENTS:
        call("POST", f"{PREP}/attempts/{att[u][0]}/submit", {}, token=users[u])
    check("temporary test removed", call("DELETE", f"/api/v1/admin/prep/tests/{std['id']}", token=admin)[0] == 200)

    print(f"\n{smoke_test.passed} passed, {smoke_test.failed} failed")
    sys.exit(1 if smoke_test.failed else 0)


if __name__ == "__main__":
    main()
