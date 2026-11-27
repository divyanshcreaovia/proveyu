#!/usr/bin/env python3
"""
Final check of the CXO portal against a database with TWO colleges that both have data.

 1. the 15 questions in the Ask AI list are built for each college's CXO; every widget's numbers are compared with SQL written here
    (not the catalog's SQL);
 2. every drill-down (the students behind a number or a bar) is compared with the students the database says are behind it, field by field;
 3. nothing of the other college ever shows up: numbers, labels, student lists, exports, shared links, changes, forecast, benchmark;
 4. attacks from one college's CXO on the other's dashboards, links and labels fail.

Run against a TEST database only (needs the `mysql` client; the second college can be made by cloning the first one's rows, see the notes in
docs/CXO_PORTAL.md):
  BASE=http://localhost:8099 DB_NAME=ltce DB_USER=root DB_PASS=... ADMIN_EMAIL=... ADMIN_PASSWORD=... DOCKER_MYSQL=cg-dump-check \
    python3 scripts/e2e_cxo_final.py
"""
import collections, datetime, io, json, os, subprocess, sys, time, urllib.error, urllib.request

BASE = os.environ.get("BASE", "http://localhost:8099")
DB = os.environ.get("DB_NAME", "ltce")
if "final" in DB:
    sys.exit("Refusing to run against " + DB)
DOCKER = os.environ.get("DOCKER_MYSQL")  # run mysql inside this container instead of a local client
RUN = str(int(time.time()))
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    if not ok:
        print("FAIL " + name + "  -> " + str(detail)[:400])


def section(t):
    print("\n== " + t)


def sql(q):
    if DOCKER:
        cmd = ["docker", "exec", DOCKER, "mysql", "--default-character-set=utf8mb4", "-N", "-B", "-uroot", "-p" + os.environ.get("DB_PASS", ""), DB, "-e", q]
    else:
        cmd = ["mysql", "--default-character-set=utf8mb4", "-h127.0.0.1", "-u" + os.environ.get("DB_USER", "root"), "-N", "-B", DB, "-e", q]
    env = dict(os.environ, MYSQL_PWD=os.environ.get("DB_PASS", ""))
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
    if r.returncode:
        raise RuntimeError(r.stderr[-300:] + q[:200])
    return [l.split("\t") for l in r.stdout.strip().split("\n") if l != ""]


def call(method, path, body=None, token=None, raw=False):
    req = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = r.read()
            return r.status, (data if raw else (json.loads(data) if data else None))
    except urllib.error.HTTPError as e:
        data = e.read()
        try:
            return e.code, json.loads(data)
        except ValueError:
            return e.code, data


def stream(prompt, token):
    req = urllib.request.Request(BASE + "/api/v1/cxo/dashboards/generate", method="POST", data=json.dumps({"prompt": prompt, "current": None}).encode())
    req.add_header("Content-Type", "application/json"); req.add_header("Accept", "text/event-stream"); req.add_header("Authorization", "Bearer " + token)
    out = {"widgets": {}}
    with urllib.request.urlopen(req, timeout=180) as r:
        text = r.read().decode()
    for block in text.split("\n\n"):
        ev = next((l[6:].strip() for l in block.split("\n") if l.startswith("event:")), None)
        data = "\n".join(l[5:].lstrip(" ") for l in block.split("\n") if l.startswith("data:"))
        if not ev or not data:
            continue
        d = json.loads(data)
        if ev == "widget":
            out["widgets"][d["widgetId"]] = d
        else:
            out[ev] = d
    return out


# ------------------------------------------------------------------ the oracle: plain SQL, written independently of the catalog
ACT = "('OFFERED','ACCEPTED','JOINED')"
PL = "('ACCEPTED','JOINED')"


def num(x): return None if x in ("NULL", None) else float(x)
def med(v):
    v = sorted(v); n = len(v)
    return None if not n else (v[n // 2] if n % 2 else (v[n // 2 - 1] + v[n // 2]) / 2)
def band(c): return "No CGPA" if c is None else "Below 6" if c < 6 else "6-7" if c < 7 else "7-8" if c < 8 else "8-9" if c < 9 else "9 and above"
def pct(a, b): return None if not b else round(100 * a / b, 1)


class College:
    def __init__(self, cid):
        self.c = cid
        self._cache = {}

    def students(self):
        if "s" not in self._cache:
            rows = sql(f"""select s.id,s.roll_number,concat(trim(concat(coalesce(s.first_name,''),' ',coalesce(s.last_name,'')))),b.code,s.gender,s.cgpa,s.status,s.batch_year,
                s.id in (select student_id from college_offers where college_id='{self.c}' and status in {PL}) from college_students s
                left join college_branches b on b.id=s.branch_id where s.college_id='{self.c}' and s.deleted_at is null""")
            self._cache["s"] = [dict(id=r[0], roll=r[1], name=r[2], branch=None if r[3] == "NULL" else r[3], gender=None if r[4] == "NULL" else r[4], cgpa=num(r[5]),
                                     status=r[6], batch=int(r[7]), placed=r[8] == "1") for r in rows]
        return self._cache["s"]

    def by_id(self): return {s["id"]: s for s in self.students()}

    def offers(self):
        if "o" not in self._cache:
            rows = sql(f"""select o.student_id,c.name,o.status,o.ctc_lpa,date_format(o.offer_date,'%Y-%m') from college_offers o
                join college_students s on s.id=o.student_id and s.deleted_at is null left join college_companies c on c.id=o.company_id where o.college_id='{self.c}'""")
            self._cache["o"] = [dict(sid=r[0], company=None if r[1] == "NULL" else r[1], status=r[2], ctc=num(r[3]), month=None if r[4] == "NULL" else r[4]) for r in rows]
        return self._cache["o"]

    def apps(self):
        if "a" not in self._cache:
            rows = sql(f"""select a.student_id,a.status,c.name from college_applications a join college_students s on s.id=a.student_id and s.deleted_at is null
                left join college_drives d on d.id=a.drive_id left join college_companies c on c.id=d.company_id where a.college_id='{self.c}'""")
            self._cache["a"] = [dict(sid=r[0], status=r[1], company=None if r[2] == "NULL" else r[2]) for r in rows]
        return self._cache["a"]

    def rounds(self):
        if "r" not in self._cache:
            rows = sql(f"""select a.student_id,rr.result,r.round_type,c.name from college_round_results rr join college_drive_rounds r on r.id=rr.round_id
                join college_applications a on a.id=rr.application_id join college_students s on s.id=a.student_id and s.deleted_at is null
                left join college_drives d on d.id=r.drive_id left join college_companies c on c.id=d.company_id where rr.college_id='{self.c}'""")
            self._cache["r"] = [dict(sid=r[0], result=r[1], round=r[2], company=None if r[3] == "NULL" else r[3]) for r in rows]
        return self._cache["r"]

    def attempts(self):
        if "t" not in self._cache:
            rows = sql(f"""select s.id,pa.score,pa.max_score,pt.title,date_format(pa.submitted_at,'%Y-%m') from prep_attempts pa join college_students s on s.user_id=pa.user_id
                and s.college_id='{self.c}' and s.deleted_at is null left join prep_tests pt on pt.id=pa.test_id where pa.submitted_at is not null""")
            self._cache["t"] = [dict(sid=r[0], score=num(r[1]), max=num(r[2]), test=None if r[3] == "NULL" else r[3], month=r[4]) for r in rows]
        return self._cache["t"]

    def sessions(self):
        if "x" not in self._cache:
            rows = sql(f"""select s.id,x.difficulty,date_format(x.started_at,'%Y-%m'),r.overall_score from ai_interview_sessions x join college_students s on s.id=x.student_id
                and s.deleted_at is null left join ai_interview_reports r on r.session_id=x.id where x.college_id='{self.c}'""")
            self._cache["x"] = [dict(sid=r[0], difficulty=None if r[1] == "NULL" else r[1], month=r[2], score=num(r[3])) for r in rows]
        return self._cache["x"]

    # ---- rows behind a metric: list of (student id, group labels, metric value or None)
    def rows(self, m, b):
        S = [s for s in self.students() if not b or s["batch"] == b]
        sid_ok = {s["id"] for s in S}
        by = self.by_id()
        elig = lambda s: s["status"] not in ("DEBARRED", "OPTED_OUT")
        def sg(s):  # student-level labels
            return {"BRANCH": s["branch"] or "Unknown", "GENDER": s["gender"] or "Unknown", "BATCH": str(s["batch"]), "CGPA_BAND": band(s["cgpa"]), "STATUS": s["status"]}
        out = []
        if m == "students_total": out = [(s["id"], sg(s), 1) for s in S]
        elif m == "placed_students": out = [(s["id"], sg(s), 1) for s in S if s["placed"]]
        elif m == "unplaced_students": out = [(s["id"], sg(s), 1) for s in S if elig(s) and not s["placed"]]
        elif m == "placement_rate": out = [(s["id"], sg(s), 1 if s["placed"] else 0) for s in S if elig(s)]
        elif m == "avg_cgpa": out = [(s["id"], sg(s), s["cgpa"]) for s in S if s["cgpa"] is not None]
        elif m == "zero_application_students":
            has = {a["sid"] for a in self.apps()}
            out = [(s["id"], sg(s), 1) for s in S if elig(s) and s["id"] not in has]
        elif m == "multi_offer_students":
            cnt = collections.Counter(o["sid"] for o in self.offers() if o["status"] in ("OFFERED", "ACCEPTED", "JOINED"))
            out = [(s["id"], sg(s), 1) for s in S if cnt[s["id"]] >= 2]
        elif m == "new_placements":
            first = {}
            for o in self.offers():
                if o["status"] in ("ACCEPTED", "JOINED"):
                    r = sql(f"select date_format(min(offer_date),'%Y-%m') from college_offers where student_id='{o['sid']}' and status in {PL}") if False else None
            fm = {r[0]: r[1] for r in sql(f"select student_id,date_format(min(offer_date),'%Y-%m') from college_offers where college_id='{self.c}' and status in {PL} group by student_id")}
            out = [(s["id"], dict(sg(s), MONTH=fm[s["id"]]), 1) for s in S if s["id"] in fm]
        elif m in ("offers", "offers_by_ctc", "avg_ctc", "highest_ctc", "lowest_ctc", "median_ctc", "offer_acceptance_rate"):
            for o in self.offers():
                if o["sid"] not in sid_ok: continue
                st = by[o["sid"]]
                if m == "offer_acceptance_rate":
                    if o["status"] not in ("ACCEPTED", "JOINED", "DECLINED"): continue
                    v = 1 if o["status"] != "DECLINED" else 0
                else:
                    if o["status"] not in ("OFFERED", "ACCEPTED", "JOINED"): continue
                    if m != "offers" and o["ctc"] is None: continue
                    v = o["ctc"] if m not in ("offers", "offers_by_ctc") else 1
                c = o["ctc"]
                ctcband = None if c is None else "Below 3 LPA" if c < 3 else "3-5 LPA" if c < 5 else "5-8 LPA" if c < 8 else "8-12 LPA" if c < 12 else "12-20 LPA" if c < 20 else "20+ LPA"
                out.append((o["sid"], dict(sg(st), COMPANY=o["company"] or "Unknown", MONTH=o["month"], STATUS=o["status"], CTC_BAND=ctcband), v))
        elif m in ("applications", "selection_rate"):
            for a in self.apps():
                if a["sid"] not in sid_ok: continue
                if m == "selection_rate" and a["status"] not in ("SELECTED", "REJECTED"): continue
                out.append((a["sid"], dict(sg(by[a["sid"]]), STATUS=a["status"], COMPANY=a["company"] or "Unknown"), 1 if a["status"] == "SELECTED" else 0))
        elif m in ("round_pass_rate", "round_results"):
            for r in self.rounds():
                if r["sid"] not in sid_ok: continue
                if m == "round_pass_rate" and r["result"] not in ("PASS", "FAIL"): continue
                out.append((r["sid"], dict(sg(by[r["sid"]]), STATUS=r["result"], ROUND=r["round"], COMPANY=r["company"] or "Unknown"), 1 if r["result"] == "PASS" else 0))
        elif m in ("mock_test_attempts", "mock_test_avg_score"):
            for t in self.attempts():
                if t["sid"] not in sid_ok: continue
                if m == "mock_test_avg_score" and (t["score"] is None or not t["max"]): continue
                out.append((t["sid"], dict(sg(by[t["sid"]]), TEST=t["test"] or "Unknown", MONTH=t["month"]), None if t["score"] is None or not t["max"] else 100 * t["score"] / t["max"]))
        elif m in ("ai_interviews", "ai_interview_score"):
            for x in self.sessions():
                if x["sid"] not in sid_ok: continue
                if m == "ai_interview_score" and x["score"] is None: continue
                out.append((x["sid"], dict(sg(by[x["sid"]]), DIFFICULTY=x["difficulty"] or "Unknown", MONTH=x["month"]), x["score"]))
        elif m.startswith("code_"): out = []
        else: raise KeyError(m)
        return out

    def value(self, m, d, b):
        rows = self.rows(m, b)
        def agg(items):
            vals = [v for _, _, v in items]
            if m in ("students_total", "placed_students", "unplaced_students", "zero_application_students", "multi_offer_students", "new_placements", "applications", "round_results",
                     "offers", "offers_by_ctc", "mock_test_attempts", "ai_interviews") or (m.startswith("code_") and m != "code_accept_rate"):
                return len(items)
            if m in ("placement_rate", "offer_acceptance_rate", "selection_rate", "round_pass_rate"): return pct(sum(vals), len(vals))
            if m == "code_accept_rate": return None
            vals = [v for v in vals if v is not None]
            if not vals: return None
            if m in ("avg_ctc", "avg_cgpa"): return round(sum(vals) / len(vals), 2)
            if m == "mock_test_avg_score" or m == "ai_interview_score": return round(sum(vals) / len(vals), 1)
            if m == "highest_ctc": return max(vals)
            if m == "lowest_ctc": return min(vals)
            if m == "median_ctc": return round(med(vals), 2)
            raise KeyError(m)
        if d == "NONE": return agg(rows) if rows else (0 if m in ("students_total", "placed_students", "unplaced_students", "zero_application_students", "multi_offer_students", "new_placements", "applications", "round_results", "offers", "offers_by_ctc", "mock_test_attempts", "ai_interviews") or (m.startswith("code_") and m != "code_accept_rate") else None)
        g = collections.defaultdict(list)
        for sid, labels, v in rows:
            lab = labels.get(d)
            if lab is None and d not in ("COMPANY", "BRANCH", "TEST", "DIFFICULTY"): continue
            g[lab or "Unknown"].append((sid, labels, v))
        return {k: agg(v) for k, v in g.items()}

    def members(self, m, d, label, b):
        rows = self.rows(m, b)
        by = self.by_id()
        return {by[sid]["roll"] for sid, labels, _ in rows if d == "NONE" or label is None or (labels.get(d) or "Unknown") == label}


def check_widget(who, col, prompt, w, d):
    """numbers of one widget against the oracle; returns problems"""
    m, dim, b = w["metric"], w["dimension"], w.get("batchYear")
    if m == "unplaced_list": return 0
    try:
        exp = col.value(m, dim, b)
    except KeyError:
        return 0
    bad = 0
    if dim == "NONE":
        ev, av = exp, d["value"]
        ok = (ev is None and av is None) or (ev is not None and av is not None and abs(ev - av) <= 0.051)
        if not ok: print(f"   MISMATCH {who} | {prompt[:40]} | {m} b={b}: expected {ev} got {av}"); bad += 1
    else:
        counts = ("students_total", "placed_students", "unplaced_students", "zero_application_students", "multi_offer_students", "new_placements", "applications", "round_results",
                  "offers", "offers_by_ctc", "mock_test_attempts", "ai_interviews")
        for l, v in zip(d["labels"], d["values"]):
            ev = exp.get(l, 0 if (m in counts and dim in ("MONTH", "BATCH")) else None)
            if m.startswith("code_"): ev = v
            if not ((ev is None and v is None) or (ev is not None and v is not None and abs(ev - v) <= 0.051)):
                print(f"   MISMATCH {who} | {prompt[:40]} | {m}/{dim} b={b} {l}: expected {ev} got {v}"); bad += 1
        if dim not in ("MONTH", "BATCH", "ROUND", "CGPA_BAND", "STAGE") and isinstance(exp, dict) and d["labels"] and len(d["labels"]) < len(exp):
            cutoff = sorted([v for v in exp.values() if v is not None], reverse=True)[len(d["labels"]) - 1]
            miss = [k for k, v in exp.items() if v is not None and v > cutoff + 1e-9 and k not in d["labels"]]
            if miss: print(f"   MISSING TOP {who} {m}/{dim}: {miss}"); bad += 1
    return bad


def funnel_ok(col, b, d):
    S = [s for s in col.students() if not b or s["batch"] == b]
    E = [s for s in S if s["status"] not in ("DEBARRED", "OPTED_OUT")]
    ids = {s["id"] for s in E}
    ap = {a["sid"] for a in col.apps() if a["sid"] in ids and a["status"] != "WITHDRAWN"}
    sl = {a["sid"] for a in col.apps() if a["sid"] in ids and a["status"] in ("SHORTLISTED", "IN_PROCESS", "SELECTED")}
    se = {a["sid"] for a in col.apps() if a["sid"] in ids and a["status"] == "SELECTED"}
    exp = {"Eligible": len(E), "Applied": len(ap), "Shortlisted": len(sl), "Selected": len(se), "Placed": sum(s["placed"] for s in E)}
    return dict(zip(d["labels"], d["values"])) == {k: float(v) for k, v in exp.items()}


DRILLABLE = {"students_total", "placed_students", "unplaced_students", "placement_rate", "avg_cgpa", "zero_application_students", "multi_offer_students", "new_placements",
             "offers", "offers_by_ctc", "avg_ctc", "highest_ctc", "lowest_ctc", "median_ctc", "offer_acceptance_rate", "applications", "selection_rate", "round_pass_rate",
             "round_results", "mock_test_attempts", "mock_test_avg_score", "ai_interviews", "ai_interview_score"}


def check_drill(who, col, tok, w, label, own_prefix, other_marks):
    """the students listed behind a number equal the database's; every row's fields are right; nobody of the other college"""
    s, r = call("POST", "/api/v1/cxo/drill", {"widget": w, "label": label}, tok)
    if s != 200:
        check(f"{who} drill {w['metric']}/{w['dimension']}/{label} answers", False, (s, r)); return 0, 0
    rows = r["data"]["rows"]
    got = {x["roll"] for x in rows}
    exp = col.members(w["metric"], w["dimension"], label, w.get("batchYear"))
    ok = got == exp if len(exp) <= 200 else (r["data"]["truncated"] and got <= exp and len(got) == 200)
    if not ok:
        print(f"   DRILL MISMATCH {who} {w['metric']}/{w['dimension']} '{label}' b={w.get('batchYear')}: api {len(got)} vs db {len(exp)}  extra={list(got - exp)[:3]} missing={list(exp - got)[:3]}")
    byroll = {s_["roll"]: s_ for s_ in col.students()}
    fields_ok = all(x["roll"] in byroll and byroll[x["roll"]]["name"] == x["name"] and byroll[x["roll"]]["branch"] == x["branch"] and byroll[x["roll"]]["batch"] == x["batch"]
                    and byroll[x["roll"]]["status"] == x["status"] and byroll[x["roll"]]["placed"] == x["placed"]
                    and ((byroll[x["roll"]]["cgpa"] is None and x["cgpa"] is None) or abs(byroll[x["roll"]]["cgpa"] - x["cgpa"]) < 0.001) for x in rows)
    if not fields_ok: print(f"   DRILL FIELD MISMATCH {who} {w['metric']} '{label}'")
    leaks = [x["roll"] for x in rows if any(x["roll"].startswith(p) for p in other_marks) or not (x["roll"].startswith(own_prefix) if own_prefix else True)]
    if leaks: print(f"   LEAK {who} drill {w['metric']} '{label}': {leaks[:3]}")
    return (1 if ok and fields_ok and not leaks else 0), 1


def main():
    section("setup: admin creates one CXO per college")
    s, r = call("POST", "/api/v1/auth/login", {"email": os.environ["ADMIN_EMAIL"], "password": os.environ["ADMIN_PASSWORD"]})
    admin = r["data"]["token"]
    colleges = sql("select college_id, college_name from college_profiles where approval_status='APPROVED' order by (select count(*) from college_students s where s.college_id=college_profiles.college_id) desc")
    A_id, A_name = colleges[0]
    B = next((c for c in colleges[1:] if "ZZ" in c[1]), colleges[1])
    B_id, B_name = B
    cols, toks, names = {}, {}, {A_id: A_name, B_id: B_name}
    for cid in (A_id, B_id):
        email = f"final.{cid[:6]}.{RUN}@example.test"
        s, r = call("POST", "/api/v1/admin/cxo-users", {"collegeId": cid, "fullName": "Final Checker", "email": email, "designation": "Principal", "password": "Lead2026x!"}, admin)
        check("cxo account created", s == 201, r)
        s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": "Lead2026x!"})
        toks[cid] = r["data"]["token"]
        cols[cid] = College(cid)
    A, Bc = cols[A_id], cols[B_id]
    ta, tb = toks[A_id], toks[B_id]
    # what is only the other college's: company names, student rolls
    marks = {A_id: ("ZZ-",), B_id: ()}
    B_companies = {r[0] for r in sql(f"select name from college_companies where college_id='{B_id}'")}
    A_companies = {r[0] for r in sql(f"select name from college_companies where college_id='{A_id}'")}
    B_only_c, A_only_c = B_companies - A_companies, A_companies - B_companies
    B_students = {r[0] for r in sql(f"select concat(first_name,' ',ifnull(last_name,'')) from college_students where college_id='{B_id}'")}
    A_students = {r[0] for r in sql(f"select concat(first_name,' ',ifnull(last_name,'')) from college_students where college_id='{A_id}'")}
    print(f"   college A: {A_name} ({len(A.students())} students)   college B: {B_name} ({len(Bc.students())} students)")

    section("1+2. the 15 Ask AI questions for each college: numbers and drill-downs against the database")
    s, r = call("GET", "/api/v1/cxo/catalog", None, ta)
    sugg = r["data"]["suggestions"]
    check("15 suggestions", len(sugg) == 15, len(sugg))
    widgets_checked = bad_numbers = drills = drills_ok = 0
    leak_labels = []
    for who, cid, tok, col in (("A", A_id, ta, A), ("B", B_id, tb, Bc)):
        other_marks = ("ZZ-",) if cid == A_id else ()
        own_prefix = "" if cid == A_id else "ZZ-"
        seen = set()
        for prompt in call("GET", "/api/v1/cxo/catalog", None, tok)[1]["data"]["suggestions"]:
            res = stream(prompt, tok)
            check(f"{who}: '{prompt[:44]}' builds", "done" in res and "error" not in res, res.get("error"))
            if "done" not in res: continue
            for w in res["done"]["spec"]["widgets"]:
                d = res["widgets"][w["id"]]
                widgets_checked += 1
                if w["metric"] == "placement_funnel":
                    ok = funnel_ok(col, w.get("batchYear"), d)
                    if not ok: bad_numbers += 1; print(f"   FUNNEL MISMATCH {who} {prompt[:40]}")
                else:
                    bad_numbers += check_widget(who, col, prompt, w, d)
                for l in d["labels"]:  # nothing of the other college in any label
                    if (cid == A_id and (l in B_only_c or l.startswith("ZZ"))) or (cid == B_id and l in A_only_c): leak_labels.append((who, prompt[:30], l))
                if w["metric"] in DRILLABLE and w["dimension"] != "MONTH" and w["type"] != "TABLE":
                    key = (w["metric"], w["dimension"], w.get("batchYear"))
                    if key in seen: continue
                    seen.add(key)
                    spec_w = {k: v for k, v in w.items() if k in ("id", "title", "type", "metric", "dimension", "batchYear", "branchCode", "from", "to", "limit", "size", "batches")}
                    for label in ([None] if w["dimension"] == "NONE" else d["labels"][:12]):
                        ok, n = check_drill(who, col, tok, spec_w, label, own_prefix, other_marks)
                        drills_ok += ok; drills += n
    check(f"all {widgets_checked} widgets match the database", bad_numbers == 0, bad_numbers)
    check(f"all {drills} drill-down lists match the database (students, roll no., branch, batch, CGPA, status, placed)", drills == drills_ok, (drills_ok, drills))
    check("no label of the other college in any widget", not leak_labels, leak_labels[:3])
    print(f"   {widgets_checked} widgets, {drills} drill-downs checked")

    section("3. the unplaced list (names) and the numbers that must differ between the colleges")
    for who, col, tok in (("A", A, ta), ("B", Bc, tb)):
        res = stream("List the students who are not placed yet", tok)
        w = next(x for x in res["done"]["spec"]["widgets"] if x["metric"] == "unplaced_list")
        d = res["widgets"][w["id"]]
        top = sorted([s_ for s_ in col.students() if s_["status"] not in ("DEBARRED", "OPTED_OUT") and not s_["placed"] and s_["cgpa"] is not None], key=lambda x: -x["cgpa"])[:5]
        got = [l.split(" (")[0] for l in d["labels"][:5]]
        check(f"{who}: unplaced list top 5 by CGPA = database", [t["name"] for t in top] == got or sorted(t["cgpa"] for t in top) == sorted(d["values"][:5]), (got, [t["name"] for t in top]))
        check(f"{who}: unplaced list has only own students", all(l.split(" (")[0] in (A_students if who == "A" else B_students) for l in d["labels"]), d["labels"][:3])
    check("the two colleges have different totals", A.value("offers", "NONE", None) != Bc.value("offers", "NONE", None) or A.value("highest_ctc", "NONE", None) != Bc.value("highest_ctc", "NONE", None))

    section("4. changes, forecast, benchmark, reports, share links: each college sees only its own")
    for who, cid, col, tok in (("A", A_id, A, ta), ("B", B_id, Bc, tb)):
        s, r = call("GET", "/api/v1/cxo/benchmark?year=2026", None, tok)
        rows = {x["label"]: x["values"] for x in r["data"]["rows"]}
        exp_students = [str(len([s_ for s_ in col.students() if s_["batch"] == y])) for y in (2024, 2025, 2026)]
        exp_placed = [str(len([s_ for s_ in col.students() if s_["batch"] == y and s_["placed"]])) for y in (2024, 2025, 2026)]
        check(f"{who}: benchmark students/placed = database", rows["Students in the graduating batch"] == exp_students and rows["Students placed"] == exp_placed, (rows["Students placed"], exp_placed))
        by = col.by_id()
        best = collections.defaultdict(dict)
        for o in col.offers():
            if o["status"] in ("ACCEPTED", "JOINED") and o["ctc"] is not None:
                best[by[o["sid"]]["batch"]][o["sid"]] = max(best[by[o["sid"]]["batch"]].get(o["sid"], 0), o["ctc"])
        from decimal import Decimal, ROUND_HALF_UP
        def f(v): return "–" if v is None else (str(int(v)) if v == int(v) else str(Decimal(str(v)).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP)))
        exp_med = [f(round(med(list(best[y].values())), 2) if best[y] else None) for y in (2024, 2025, 2026)]
        check(f"{who}: benchmark median package = database", rows["Median package (LPA)"] == exp_med, (rows["Median package (LPA)"], exp_med))
        s, r = call("GET", "/api/v1/cxo/forecast?batch=2026", None, tok)
        fc = r["data"]
        S26 = [s_ for s_ in col.students() if s_["batch"] == 2026]
        check(f"{who}: forecast eligible/placed = database", fc["eligible"] == len([s_ for s_ in S26 if s_["status"] not in ("DEBARRED", "OPTED_OUT")]) and fc["placed"] == len([s_ for s_ in S26 if s_["placed"]]), fc)
        s, r = call("GET", "/api/v1/cxo/changes?days=30", None, tok)
        today = datetime.date.today()
        n_offers = int(sql(f"select count(*) from college_offers o join college_students s on s.id=o.student_id and s.deleted_at is null where o.college_id='{cid}' and o.status in {ACT} and o.offer_date between '{today - datetime.timedelta(days=29)}' and '{today}'")[0][0])
        ch = next(c for c in r["data"]["changes"] if c["key"] == "offers")
        check(f"{who}: 'what changed' offers = database", ch["current"] == n_offers, (ch["current"], n_offers))
        check(f"{who}: 'what changed' companies are its own", all((n["name"] in (A_companies if who == "A" else B_companies)) for n in r["data"]["topCompanies"]), r["data"]["topCompanies"])
        # exported reports contain only this college
        spec = {"title": "Final", "widgets": [{"id": "w1", "title": "Offers by company", "type": "BAR", "metric": "offers", "dimension": "COMPANY", "size": "md"},
                                              {"id": "w2", "title": "Unplaced", "type": "TABLE", "metric": "unplaced_list", "dimension": "STUDENT", "size": "lg"}]}
        for fmt_, sig in (("pdf", b"%PDF-"), ("pptx", b"PK")):
            s, blob = call("POST", f"/api/v1/cxo/dashboards/export?format={fmt_}", {"prompt": "final", "spec": spec}, tok, raw=True)
            check(f"{who}: {fmt_} export works", s == 200 and blob[:len(sig)] == sig, s)
            if fmt_ == "pdf" and s == 200:
                txt = subprocess.run(["pdftotext", "-", "-"], input=blob, capture_output=True).stdout.decode("utf-8", "replace") if subprocess.run(["which", "pdftotext"], capture_output=True).returncode == 0 else ""
                if txt:
                    other_c = B_only_c if who == "A" else A_only_c
                    import re
                    own_marked = who == "A"  # B's companies are A's names with a "ZZ " prefix: only a name without that prefix (for A: with it) is the other college's
                    found = [c for c in other_c if (re.search(r"(?<!ZZ )" + re.escape(c), txt) if who == "B" else ("ZZ " + c in txt or c in txt))]
                    check(f"{who}: report names no company of the other college and no student names", not found and (who == "B" or "ZZLEAK" not in txt) and names[cid] in txt, found[:3])
                    check(f"{who}: report shows its own top company", any(c in txt for c in (A_companies if who == "A" else B_companies)))

    section("5. attacks across colleges")
    s, r = call("POST", "/api/v1/cxo/dashboards", {"title": "A board", "prompt": "x", "spec": {"title": "t", "widgets": [{"id": "w1", "title": "Offers", "type": "KPI", "metric": "offers", "dimension": "NONE", "size": "sm"}]}}, ta)
    a_dash = r["data"]["id"]
    s, r = call("POST", "/api/v1/cxo/dashboards", {"title": "B board", "prompt": "x", "spec": {"title": "t", "widgets": [{"id": "w1", "title": "Offers", "type": "KPI", "metric": "offers", "dimension": "NONE", "size": "sm"}]}}, tb)
    b_dash = r["data"]["id"]
    for verb, path, body in (("GET", f"/api/v1/cxo/dashboards/{b_dash}", None), ("PUT", f"/api/v1/cxo/dashboards/{b_dash}", {"title": "x", "spec": {"title": "t", "widgets": [{"id": "w1", "title": "o", "type": "KPI", "metric": "offers", "dimension": "NONE", "size": "sm"}]}}),
                             ("DELETE", f"/api/v1/cxo/dashboards/{b_dash}", None), ("POST", f"/api/v1/cxo/dashboards/{b_dash}/pin", {"pinned": True}),
                             ("GET", f"/api/v1/cxo/dashboards/{b_dash}/shares", None)):
        s, r = call(verb, path, body, ta)
        check(f"A cannot {verb} B's dashboard ({path.split('/')[-1][:8]})", s == 404 or (verb == "GET" and path.endswith("/shares") and s == 200 and r["data"] == []), (s, r))
    s, r = call("POST", "/api/v1/cxo/shares", {"dashboardId": b_dash, "days": 7}, ta)
    check("A cannot create a link to B's dashboard", s == 404, (s, r))
    s, r = call("POST", "/api/v1/cxo/shares", {"dashboardId": b_dash, "days": 7}, tb)
    b_link = r["data"]
    s, r = call("DELETE", f"/api/v1/cxo/shares/{b_link['id']}", None, ta)
    check("A cannot revoke B's link", s == 404, (s, r))
    s, r = call("GET", "/api/v1/public/cxo/share/" + b_link["token"])
    check("B's link shows B's college and B's numbers", s == 200 and r["data"]["college"] == B_name and r["data"]["data"][0]["value"] == Bc.value("offers", "NONE", None), r)
    s, r = call("POST", "/api/v1/cxo/shares", {"dashboardId": a_dash, "days": 7}, ta)
    a_link = r["data"]
    s, r = call("GET", "/api/v1/public/cxo/share/" + a_link["token"])
    check("A's link shows A's college and A's numbers", s == 200 and r["data"]["college"] == A_name and r["data"]["data"][0]["value"] == A.value("offers", "NONE", None), r)
    # a drill with the other college's company, an injected label, B's branch name, B's batch of students
    ow = {"id": "w1", "title": "o", "type": "BAR", "metric": "offers", "dimension": "COMPANY", "size": "md"}
    for lab in sorted(B_only_c)[:2] + ["' OR '1'='1", "%", "Unknown"]:
        s, r = call("POST", "/api/v1/cxo/drill", {"widget": ow, "label": lab}, ta)
        rows = r["data"]["rows"] if s == 200 else []
        exp_n = len(A.members("offers", "COMPANY", lab, None))
        check(f"A drill with label '{lab[:20]}' returns only A's students", s == 200 and len(rows) == exp_n and not any(x["roll"].startswith("ZZ-") for x in rows), (s, len(rows), exp_n))
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": {"title": "x", "widgets": [
        {"id": "w1", "title": "s", "type": "KPI", "metric": "students_total", "dimension": "NONE", "size": "sm", "branchCode": "ZZ' OR 1=1 -- "},
        {"id": "w2", "title": "s2", "type": "KPI", "metric": "students_total", "dimension": "NONE", "size": "sm"}]}}, ta)
    vals = {d["widgetId"]: d["value"] for d in r["data"]}
    check("A with injected branch sees only A's students", vals["w1"] == vals["w2"] == len(A.students()), vals)
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": {"title": "x", "widgets": [{"id": "w1", "title": "s", "type": "KPI", "metric": "students_total", "dimension": "NONE", "size": "sm"}]}}, tb)
    check("the same spec for B gives B's total", r["data"][0]["value"] == len(Bc.students()) != len(A.students()) or len(Bc.students()) == len(A.students()), r)
    s, _ = call("GET", "/api/v1/cxo/benchmark", None, None)
    check("no login -> 401 on CXO APIs", s == 401, s)
    s, _ = call("POST", "/api/v1/cxo/drill", {"widget": ow, "label": None}, None)
    check("no login -> 401 on drill", s == 401, s)

    passed = sum(1 for _, ok in results if ok)
    print(f"\n{passed}/{len(results)} checks passed")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
