#!/usr/bin/env python3
"""
Checks every number the CXO portal can show against the database, for one college.

For every metric in the catalog and every dimension it allows, the CXO API (/api/v1/cxo/dashboards/run) is asked for the data and
the same number is computed here independently: raw rows are read from the tables with plain SELECTs and counted / averaged in
Python (none of the portal's SQL is reused). Read-only: nothing is written to the database.

  BASE=http://localhost:8080 DB_NAME=proveyufinaldb DB_USER=... DB_PASS=... CXO_EMAIL=... CXO_PASSWORD=... \
    python3 scripts/verify_cxo_accuracy.py
"""
import json
import os
import subprocess
import sys
import urllib.request
from collections import defaultdict
from datetime import date

BASE = os.environ.get("BASE", "http://localhost:8080")
DB = os.environ["DB_NAME"]
TOL = 0.06
ACTIVE = {"OFFERED", "ACCEPTED", "JOINED"}
PLACED = {"ACCEPTED", "JOINED"}


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read())


def rows(sql):
    env = dict(os.environ, MYSQL_PWD=os.environ.get("DB_PASS", ""))
    out = subprocess.run(["mysql", "-h127.0.0.1", "-u" + os.environ.get("DB_USER", "root"), "-B", "--raw", DB, "-e", sql],
                         capture_output=True, text=True, env=env, check=True).stdout.splitlines()
    if not out:
        return []
    head = out[0].split("\t")
    return [{k: (None if v == "NULL" else v) for k, v in zip(head, line.split("\t"))} for line in out[1:]]


def num(v):
    return None if v is None else float(v)


def month(v):
    return v[:7] if v else None


def rnd(x, d):
    return None if x is None else round(x + 1e-9, d)


def main():
    me = call("POST", "/api/v1/auth/login", {"email": os.environ["CXO_EMAIL"], "password": os.environ["CXO_PASSWORD"]})["data"]
    tok = me["token"]
    C = me["collegeId"]
    print(f"College: {me['collegeName']} ({C})\n")
    catalog = call("GET", "/api/v1/cxo/catalog", token=tok)["data"]

    # ---------------- raw data (plain SELECTs only)
    branches = {r["id"]: r["code"] for r in rows(f"select id, code from college_branches where college_id='{C}'")}
    students = rows(f"select id, user_id, branch_id, batch_year, status, first_name, last_name, roll_number, cgpa from college_students "
                    f"where college_id='{C}' and deleted_at is null")
    st = {s["id"]: s for s in students}
    by_user = {s["user_id"]: s for s in students if s["user_id"]}
    companies = {r["id"]: r for r in rows(f"select id, name, mou_status, deleted_at from college_companies where college_id='{C}'")}
    tiers = {r["tier_rank"]: r["name"] for r in rows(f"select tier_rank, name from college_tiers where college_id='{C}'")}
    offers = [o for o in rows(f"select student_id, company_id, ctc_lpa, tier_rank, offer_date, status from college_offers where college_id='{C}'")
              if o["student_id"] in st]
    drives = rows(f"select id, company_id, ctc_lpa, drive_type, drive_date, status from college_drives where college_id='{C}' and deleted_at is null")
    all_drives = {r["id"]: r for r in rows(f"select id, company_id from college_drives where college_id='{C}'")}
    apps = [a for a in rows(f"select id, student_id, drive_id, status, created_at from college_applications where college_id='{C}'") if a["student_id"] in st]
    app_by_id = {a["id"]: a for a in apps}
    rounds = {r["id"]: r for r in rows(f"select id, drive_id, seq, round_type, scheduled_at from college_drive_rounds where college_id='{C}'")}
    results = [x for x in rows(f"select round_id, application_id, result from college_round_results where college_id='{C}'")
               if x["application_id"] in app_by_id and x["round_id"] in rounds]
    anns = {a["id"]: a for a in rows(f"select id, title, created_at from college_announcements where college_id='{C}'")}
    recips = [r for r in rows(f"select announcement_id, student_id, read_at from college_announcement_recipients where college_id='{C}'")
              if r["student_id"] in st and r["announcement_id"] in anns]
    ocjobs = {j["id"]: j for j in rows("select id, company_name, role_title from off_campus_jobs")}
    slots = rows(f"select drive_id, status, starts_at from college_interview_slots where college_id='{C}'")
    tests = {r["id"]: r["title"] for r in rows("select id, title from prep_tests")}
    attempts = [a for a in rows("select user_id, test_id, score, max_score, submitted_at from prep_attempts where submitted_at is not null")
                if a["user_id"] in by_user]
    sessions = [x for x in rows(f"select id, student_id, interview_type, difficulty, status, started_at from ai_interview_sessions where college_id='{C}'")
                if x["student_id"] in st]
    reports = {r["session_id"]: num(r["overall_score"]) for r in rows("select session_id, overall_score from ai_interview_reports")}
    all_attempts = {a["id"]: a for a in rows("select id, user_id, test_id from prep_attempts") if a["user_id"] in by_user}
    events = [e for e in rows("select attempt_id, kind, occurred_at from prep_proctor_events") if e["attempt_id"] in all_attempts]
    offcampus = [o for o in rows(f"select student_id, job_id, status, applied_at from off_campus_applications where college_id='{C}' and applied_at is not null")
                 if o["student_id"] in st]

    def br(s):
        return branches.get(s["branch_id"]) or "Unknown"

    eligible = [s for s in students if s["status"] not in ("DEBARRED", "OPTED_OUT")]
    placed_ids = {o["student_id"] for o in offers if o["status"] in PLACED}
    active = [o for o in offers if o["status"] in ACTIVE]
    today = date.today()
    window = []  # last 12 months, as the portal shows a month chart
    y, m = today.year, today.month
    for _ in range(12):
        window.insert(0, f"{y:04d}-{m:02d}")
        m -= 1
        if m == 0:
            y, m = y - 1, 12

    def comp(cid):
        return (companies.get(cid) or {}).get("name") or "Unknown"

    def drive_comp(did):
        return comp((all_drives.get(did) or {}).get("company_id"))

    def tier(o):
        return tiers.get(o["tier_rank"]) or "Unknown"

    def band(c):
        return "Below 3 LPA" if c < 3 else "3-5 LPA" if c < 5 else "5-8 LPA" if c < 8 else "8-12 LPA" if c < 12 else "12-20 LPA" if c < 20 else "20+ LPA"

    # (items, label function per dimension, month function, aggregate) per metric
    def count(xs):
        return float(len(xs))

    def avg_of(f, d):
        return lambda xs: rnd(sum(f(x) for x in xs) / len(xs), d) if xs else None

    def rate(xs, ok):
        return rnd(100.0 * sum(1 for x in xs if ok(x)) / len(xs), 1) if xs else None

    sdims = {"BRANCH": br, "BATCH": lambda s: s["batch_year"] or "Unknown"}
    odims = {"COMPANY": lambda o: comp(o["company_id"]), "BRANCH": lambda o: br(st[o["student_id"]]),
             "BATCH": lambda o: st[o["student_id"]]["batch_year"] or "Unknown", "TIER": tier, "STATUS": lambda o: o["status"]}
    adims = {"STATUS": lambda a: a["status"], "COMPANY": lambda a: drive_comp(a["drive_id"]), "BRANCH": lambda a: br(st[a["student_id"]]),
             "BATCH": lambda a: st[a["student_id"]]["batch_year"] or "Unknown"}
    tdims = {"TEST": lambda a: tests.get(a["test_id"]) or "Unknown", "BRANCH": lambda a: br(by_user[a["user_id"]]),
             "BATCH": lambda a: by_user[a["user_id"]]["batch_year"] or "Unknown"}
    xdims = {"TYPE": lambda x: x["interview_type"], "DIFFICULTY": lambda x: x["difficulty"], "BRANCH": lambda x: br(st[x["student_id"]]),
             "BATCH": lambda x: st[x["student_id"]]["batch_year"] or "Unknown"}
    with_ctc = [o for o in active if o["ctc_lpa"] is not None]
    scored = [a for a in attempts if a["score"] is not None and num(a["max_score"]) and num(a["max_score"]) > 0]
    reported = [x for x in sessions if x["id"] in reports and reports[x["id"]] is not None]
    decided = [a for a in apps if a["status"] in ("SELECTED", "REJECTED")]

    def name(s):
        n = f"{s['first_name'] or ''} {s['last_name'] or ''}".strip()
        return f"{n} ({s['roll_number'] or '-'}) · {branches.get(s['branch_id']) or '-'} · {s['batch_year'] or '-'}"

    def rnd_student(x):
        return st[app_by_id[x["application_id"]]["student_id"]]

    def job(o):
        j = ocjobs.get(o["job_id"])
        return f"{j['role_title']} · {j['company_name']}" if j and j["role_title"] is not None and j["company_name"] is not None else "Unknown"

    rdims = {"ROUND": lambda x: rounds[x["round_id"]]["round_type"], "COMPANY": lambda x: drive_comp(rounds[x["round_id"]]["drive_id"]),
             "BRANCH": lambda x: br(rnd_student(x)), "BATCH": lambda x: rnd_student(x)["batch_year"] or "Unknown"}
    rmonth = lambda x: month(rounds[x["round_id"]]["scheduled_at"])
    pdims = {"TYPE": lambda e: e["kind"], "TEST": lambda e: tests.get(all_attempts[e["attempt_id"]]["test_id"]) or "Unknown",
             "BRANCH": lambda e: br(by_user[all_attempts[e["attempt_id"]]["user_id"]]),
             "BATCH": lambda e: by_user[all_attempts[e["attempt_id"]]["user_id"]]["batch_year"] or "Unknown"}
    andims = {"ANNOUNCEMENT": lambda r: anns[r["announcement_id"]]["title"], "BRANCH": lambda r: br(st[r["student_id"]]),
              "BATCH": lambda r: st[r["student_id"]]["batch_year"] or "Unknown"}

    M = {
        "offer_acceptance_rate": ([o for o in offers if o["status"] in ("ACCEPTED", "JOINED", "DECLINED")],
                                  {k: v for k, v in odims.items() if k != "STATUS"}, lambda o: month(o["offer_date"]),
                                  lambda xs: rate(xs, lambda o: o["status"] in PLACED)),
        "unplaced_list": ([s for s in eligible if s["id"] not in placed_ids], {"STUDENT": name}, None,
                          lambda xs: max((num(x["cgpa"]) for x in xs if x["cgpa"] is not None), default=None)),
        "round_results": (results, {**rdims, "STATUS": lambda x: x["result"]}, rmonth, count),
        "round_pass_rate": ([x for x in results if x["result"] in ("PASS", "FAIL")], rdims, rmonth, lambda xs: rate(xs, lambda x: x["result"] == "PASS")),
        "announcements": (list(anns.values()), {}, lambda a: month(a["created_at"]), count),
        "announcement_read_rate": (recips, andims, lambda r: month(anns[r["announcement_id"]]["created_at"]),
                                   lambda xs: rate(xs, lambda r: r["read_at"] is not None)),
        "proctoring_flags": (events, pdims, lambda e: month(e["occurred_at"]), count),
        "flagged_attempts": (events, {k: v for k, v in pdims.items() if k != "TYPE"}, lambda e: month(e["occurred_at"]),
                             lambda xs: float(len({e["attempt_id"] for e in xs}))),
        "students_total": (students, {**sdims, "STATUS": lambda s: s["status"]}, None, count),
        "placed_students": ([s for s in students if s["id"] in placed_ids], sdims, None, count),
        "placement_rate": (eligible, sdims, None, lambda xs: rate(xs, lambda s: s["id"] in placed_ids)),
        "unplaced_students": ([s for s in eligible if s["id"] not in placed_ids], sdims, None, count),
        "offers": (active, odims, lambda o: month(o["offer_date"]), count),
        "avg_ctc": (with_ctc, {k: v for k, v in odims.items() if k != "STATUS"}, lambda o: month(o["offer_date"]), avg_of(lambda o: num(o["ctc_lpa"]), 2)),
        "highest_ctc": (with_ctc, {k: v for k, v in odims.items() if k != "STATUS"}, lambda o: month(o["offer_date"]),
                        lambda xs: max(num(o["ctc_lpa"]) for o in xs) if xs else None),
        "offers_by_ctc": (with_ctc, {"CTC_BAND": lambda o: band(num(o["ctc_lpa"]))}, lambda o: month(o["offer_date"]), count),
        "drives": (drives, {"STATUS": lambda d: d["status"], "TYPE": lambda d: d["drive_type"], "COMPANY": lambda d: comp(d["company_id"])},
                   lambda d: month(d["drive_date"]), count),
        "avg_drive_ctc": ([d for d in drives if d["ctc_lpa"] is not None], {"TYPE": lambda d: d["drive_type"], "COMPANY": lambda d: comp(d["company_id"])},
                          lambda d: month(d["drive_date"]), avg_of(lambda d: num(d["ctc_lpa"]), 2)),
        "applications": (apps, adims, lambda a: month(a["created_at"]), count),
        "selection_rate": (decided, {k: v for k, v in adims.items() if k != "STATUS"}, lambda a: month(a["created_at"]),
                           lambda xs: rate(xs, lambda a: a["status"] == "SELECTED")),
        "interviews": (slots, {"STATUS": lambda i: i["status"], "COMPANY": lambda i: drive_comp(i["drive_id"])}, lambda i: month(i["starts_at"]), count),
        "companies": ([c for c in companies.values() if c["deleted_at"] is None], {"STATUS": lambda c: c["mou_status"]}, None, count),
        "mock_test_attempts": (attempts, tdims, lambda a: month(a["submitted_at"]), count),
        "mock_test_avg_score": (scored, tdims, lambda a: month(a["submitted_at"]), avg_of(lambda a: 100 * num(a["score"]) / num(a["max_score"]), 1)),
        "ai_interviews": (sessions, {**xdims, "STATUS": lambda x: x["status"]}, lambda x: month(x["started_at"]), count),
        "ai_interview_score": (reported, xdims, lambda x: month(x["started_at"]), avg_of(lambda x: reports[x["id"]], 1)),
        "offcampus_applications": (offcampus, {"STATUS": lambda o: o["status"], "BRANCH": lambda o: br(st[o["student_id"]]),
                                               "BATCH": lambda o: st[o["student_id"]]["batch_year"] or "Unknown",
                                               "COMPANY": lambda o: (ocjobs.get(o["job_id"]) or {}).get("company_name") or "Unknown", "JOB": job},
                                   lambda o: month(o["applied_at"]), count),
    }

    units = {m["key"]: m["unit"] for m in catalog["metrics"]}

    def expected(metric, dim):
        if metric == "placement_funnel":
            ids = {s["id"] for s in eligible}
            applied = {a["student_id"] for a in apps if a["status"] != "WITHDRAWN" and a["student_id"] in ids}
            short = {a["student_id"] for a in apps if a["status"] in ("SHORTLISTED", "IN_PROCESS", "SELECTED") and a["student_id"] in ids}
            sel = {a["student_id"] for a in apps if a["status"] == "SELECTED" and a["student_id"] in ids}
            return {"Eligible": float(len(ids)), "Applied": float(len(applied)), "Shortlisted": float(len(short)),
                    "Selected": float(len(sel)), "Placed": float(len(placed_ids & ids))}
        items, dims, mfun, agg = M[metric]
        if dim == "NONE":
            return agg(items)
        groups = defaultdict(list)
        if dim == "MONTH":
            for x in items:
                k = mfun(x)
                if k in window:
                    groups[k].append(x)
            return {k: (agg(groups[k]) if groups[k] else (0.0 if units[metric] == "COUNT" else None)) for k in window}
        for x in items:
            groups[str(dims[dim](x))].append(x)
        out = {k: agg(v) for k, v in groups.items()}
        if dim == "BATCH" and units[metric] == "COUNT":  # every batch of the college is listed, 0 when it has nothing yet
            for b in {str(x["batch_year"]) for x in students if x["batch_year"]}:
                out.setdefault(b, 0.0)
        return out

    # ---------------- ask the portal, compare
    jobs = [(m["key"], d) for m in catalog["metrics"] for d in m["dimensions"]]
    ok = bad = 0
    report = []
    for i in range(0, len(jobs), 12):
        chunk = jobs[i:i + 12]
        for kind in ("KPI", "CHART"):
            part = [(k, d) for k, d in chunk if (d == "NONE") == (kind == "KPI")]
            if not part:
                continue
            spec = {"title": "verify", "widgets": [{"title": f"{k}:{d}", "type": "KPI" if d == "NONE" else "TABLE" if d == "STUDENT" else "BAR", "metric": k, "dimension": d,
                                                    "limit": 50 if d == "STUDENT" else 20, "size": "md"} for k, d in part]}
            got = call("POST", "/api/v1/cxo/dashboards/run", {"spec": spec}, tok)["data"]
            for (k, d), w in zip(part, got):
                exp = expected(k, d)
                if d == "NONE":
                    g = w["value"]
                    good = (g is None and (exp is None or exp == 0)) or (g is not None and exp is not None and abs(g - exp) <= TOL)
                    shown = f"{g}"
                else:
                    g = dict(zip(w["labels"], w["values"]))
                    exp = {k2: v for k2, v in exp.items()}
                    if d == "STUDENT" and len(exp) > 50:  # the list shows the 50 highest CGPAs (ties by name); every row must be right
                        top = sorted(exp.items(), key=lambda kv: (-(kv[1] if kv[1] is not None else -1), kv[0]))[:50]
                        cut = top[-1][1]
                        exp = {k2: v for k2, v in exp.items() if k2 in g or (v is not None and cut is not None and v > cut)}
                    good = set(g) == set(exp) and all((g[x] is None and exp[x] is None) or
                                                      (g[x] is not None and exp[x] is not None and abs(g[x] - exp[x]) <= TOL) for x in exp)
                    shown = f"{len(g)} groups"
                ok += good
                bad += not good
                report.append((good, k, d, shown, exp if not good else None, g if not good else None))
                print(f"{'OK  ' if good else 'DIFF'} {k:24s} {d:11s} {shown}")
                if not good:
                    print(f"       portal:   {g}\n       database: {exp}")
    # ---------------- year on year: a KPI filtered to the latest batch must carry the previous batch's number
    batches = sorted({int(x["batch_year"]) for x in students if x["batch_year"]})
    if len(batches) >= 2:
        latest, prev = batches[-1], batches[-2]
        if prev == latest - 1:
            for m in catalog["metrics"]:
                if "NONE" not in m["dimensions"] or "BATCH" not in m["dimensions"]:
                    continue
                spec = {"title": "yoy", "widgets": [{"title": m["key"], "type": "KPI", "metric": m["key"], "dimension": "NONE", "batchYear": latest, "size": "sm"}]}
                w = call("POST", "/api/v1/cxo/dashboards/run", {"spec": spec}, tok)["data"][0]
                exp = expected(m["key"], "BATCH").get(str(prev))
                if exp is None and m["unit"] == "COUNT":
                    exp = 0.0
                g = w.get("previous")
                good = w.get("previousLabel") == f"batch {prev}" and ((g is None and exp is None) or (g is not None and exp is not None and abs(g - exp) <= TOL))
                ok += good
                bad += not good
                print(f"{'OK  ' if good else 'DIFF'} {m['key']:24s} vs batch {prev}  portal={g} database={exp}")
    print(f"\n{ok}/{ok + bad} metric x dimension checks match the database")
    sys.exit(0 if bad == 0 else 1)


if __name__ == "__main__":
    main()
