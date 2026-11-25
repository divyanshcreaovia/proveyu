#!/usr/bin/env python3
"""
End-to-end test of the CXO portal (college leadership AI dashboards).

  admin creates a CXO account -> CXO signs in -> profile + catalog -> live build stream (status, plan, widget..., insight, done)
  -> numbers match the database -> follow-up refine -> run / save / list / pin / update / delete
  -> security: wrong portal APIs, injected filters, unknown metrics, other college's dashboard, revoke ends access at once.

Run against a TEST database only:
  BASE=http://localhost:8090 DB_NAME=proveyutestdb DB_USER=... DB_PASS=... ADMIN_EMAIL=... ADMIN_PASSWORD=... \
    python3 scripts/e2e_cxo_test.py
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://localhost:8090")
DB = os.environ.get("DB_NAME", "proveyutestdb")
if "final" in DB:
    sys.exit("Refusing to run against " + DB + ": use a test database")
RUN = str(int(time.time()))
results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"  -> {str(detail)[:300]}"))


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw


def stream(prompt, token, current=None):
    """POST the generate endpoint and return the list of (event, data) in arrival order."""
    req = urllib.request.Request(BASE + "/api/v1/cxo/dashboards/generate", method="POST",
                                 data=json.dumps({"prompt": prompt, "current": current}).encode())
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "text/event-stream")
    req.add_header("Authorization", "Bearer " + token)
    events, name, data = [], None, []
    with urllib.request.urlopen(req, timeout=120) as r:
        for raw in r:
            line = raw.decode().rstrip("\r\n")
            if line.startswith("event:"):
                name = line[6:].strip()
            elif line.startswith("data:"):
                data.append(line[5:].lstrip(" ") if line[5:6] == " " else line[5:])
            elif line == "" and data:
                events.append((name or "message", json.loads("\n".join(data))))
                name, data = None, []
    return events


def sql(q):
    env = dict(os.environ, MYSQL_PWD=os.environ.get("DB_PASS", ""))
    out = subprocess.run(["mysql", "-h127.0.0.1", "-u" + os.environ.get("DB_USER", "root"), "-N", "-B", DB, "-e", q],
                         capture_output=True, text=True, env=env, check=True).stdout.strip()
    return [row.split("\t") for row in out.splitlines()] if out else []


def main():
    # ---------------------------------------------------------------- admin creates a CXO for the college with the most data
    s, r = call("POST", "/api/v1/auth/login", {"email": os.environ["ADMIN_EMAIL"], "password": os.environ["ADMIN_PASSWORD"]})
    check("admin signs in", s == 200 and r["data"]["role"] == "ADMIN", r)
    admin = r["data"]["token"]
    college = sql("""select p.college_id, p.college_name from college_profiles p
                     where p.approval_status = 'APPROVED'
                     order by (select count(*) from college_offers o where o.college_id = p.college_id) desc,
                              (select count(*) from college_students s where s.college_id = p.college_id) desc limit 1""")[0]
    other = sql(f"select college_id from college_profiles where approval_status = 'APPROVED' and college_id <> '{college[0]}' limit 1")[0][0]
    email = f"cxo.{RUN}@example.test"
    s, r = call("POST", "/api/v1/admin/cxo-users", {"collegeId": college[0], "fullName": "Dr Test Principal", "email": email,
                                                    "designation": "Principal", "password": "Lead2026x!"}, admin)
    check("admin creates CXO account", s == 201 and r["data"]["collegeId"] == college[0], r)
    member = r["data"]["memberId"]
    s, r = call("POST", "/api/v1/admin/cxo-users", {"collegeId": college[0], "fullName": "Weak", "email": f"w{RUN}@example.test",
                                                    "password": "abcdefgh"}, admin)
    check("weak password rejected", s == 400, r)
    s, r = call("GET", "/api/v1/admin/cxo-users", None, admin)
    check("admin lists CXO accounts", s == 200 and any(x["memberId"] == member for x in r["data"]), r)
    s, r = call("GET", "/api/v1/admin/users?role=CXO", None, admin)
    check("CXO role searchable in admin users", s == 200 and any(x["email"] == email for x in r["data"]["rows"]), r)

    # ---------------------------------------------------------------- CXO signs in
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": "Lead2026x!"})
    check("CXO signs in with role CXO and college", s == 200 and r["data"]["role"] == "CXO" and r["data"]["collegeId"] == college[0], r)
    tok = r["data"]["token"]
    s, r = call("GET", "/api/v1/cxo/me", None, tok)
    check("profile", s == 200 and r["data"]["collegeName"] == college[1] and r["data"]["designation"] == "Principal", r)
    s, r = call("GET", "/api/v1/cxo/catalog", None, tok)
    cat = r["data"]
    check("catalog lists metrics, branches, batches, suggestions", s == 200 and len(cat["metrics"]) >= 15 and cat["suggestions"], r)

    # ---------------------------------------------------------------- live build
    t0 = time.time()
    ev = stream("How is our placement season going? Show placement rate by branch", tok)
    names = [e[0] for e in ev]
    check("stream order: status.. plan, widget.., insight, done", names[0] == "status" and "plan" in names and names[-1] == "done"
          and names.index("plan") < names.index("widget") and names.index("insight") < names.index("done"), names)
    plan = next(d for n, d in ev if n == "plan")
    widgets = [d for n, d in ev if n == "widget"]
    check("one widget event per planned widget", len(widgets) == len(plan["widgets"]) and len(plan["widgets"]) >= 5, (len(widgets), len(plan["widgets"])))
    check("KPI tiles first", plan["widgets"][0]["type"] == "KPI", plan["widgets"][0])
    check("insight has a headline", any(n == "insight" and d["headline"] for n, d in ev))
    print(f"     built {len(widgets)} widgets in {time.time() - t0:.1f}s, source={ev[-1][1]['source']}")

    # numbers equal the database
    by_metric = {w["metric"] + ":" + w["dimension"]: w for w in plan["widgets"]}
    data = {d["widgetId"]: d for d in widgets}
    c = college[0]
    db_students = int(sql(f"select count(*) from college_students where college_id='{c}' and deleted_at is null")[0][0])
    db_placed = int(sql(f"""select count(*) from college_students s join (select distinct student_id from college_offers where college_id='{c}'
                            and status in ('ACCEPTED','JOINED')) p on p.student_id=s.id where s.college_id='{c}' and s.deleted_at is null""")[0][0])
    # the model picks the tiles, so the student count is checked on a fixed spec (the plan's own tiles are checked below)
    fixed = {"title": "fixed", "widgets": [{"id": "w1", "title": "Students", "type": "KPI", "metric": "students_total", "dimension": "NONE", "size": "sm"}]}
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": fixed}, tok)
    got = next((d["value"] for d in (r.get("data") or []) if d["widgetId"] == "w1"), None)
    check("Students KPI = database", s == 200 and got == db_students, (got, db_students))
    w = by_metric.get("placed_students:NONE")
    check("Placed KPI = database", w and data[w["id"]]["value"] == db_placed, (w and data[w["id"]], db_placed))
    w = by_metric.get("placement_rate:BRANCH")
    if w:
        rows = sql(f"""select coalesce(b.code,'Unknown'), round(100*count(p.student_id)/nullif(count(*),0),1) from college_students s
                       left join college_branches b on b.id=s.branch_id left join (select distinct student_id from college_offers where college_id='{c}'
                       and status in ('ACCEPTED','JOINED')) p on p.student_id=s.id where s.college_id='{c}' and s.deleted_at is null
                       and s.status not in ('DEBARRED','OPTED_OUT') group by 1""")
        expect = {k: float(v) for k, v in rows}
        got = dict(zip(data[w["id"]]["labels"], data[w["id"]]["values"]))
        check("placement rate by branch = database", all(abs(got[k] - expect[k]) < 0.06 for k in expect) and set(got) == set(expect), (got, expect))

    # other topics build without errors
    for q in ["Show package distribution, average and highest CTC by company", "How are students doing in mock tests and AI interviews?",
              "Monthly trend of offers and drives", "Show the placement funnel and applications by status"]:
        ev2 = stream(q, tok)
        ws = [d for n, d in ev2 if n == "widget"]
        check(f"builds: {q[:48]}", ev2[-1][0] == "done" and len(ws) >= 4, [n for n, _ in ev2][-3:])

    # two builds at once for the same user: the second gets an error event (never an HTTP error that would sign the user out)
    import threading
    first = []
    th = threading.Thread(target=lambda: first.extend(stream("Show the full placement overview", tok)))
    th.start()
    time.sleep(0.05)
    second = stream("Show drives by status", tok)
    th.join()
    check("parallel build of the same user -> error event, first build completes",
          second and second[0][0] == "error" and first and first[-1][0] == "done", (second[:1], first[-1:]))
    check("immediate next build after done is accepted", stream("Show offers by company", tok)[-1][0] == "done")

    # follow-up keeps the dashboard and applies the batch filter
    batch = cat["batches"][-1] if cat["batches"] else None
    if batch:
        ev3 = stream(f"only batch {batch}", tok, current=plan)
        spec3 = ev3[-1][1]["spec"]
        check("follow-up applies batch filter to every widget", all(w.get("batchYear") == batch for w in spec3["widgets"]
                                                                    if w["metric"] not in ("drives", "interviews", "companies")), spec3["widgets"][:2])

    # ---------------------------------------------------------------- run / save / list / pin / update / delete
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": plan}, tok)
    check("run returns fresh data for every widget", s == 200 and len(r["data"]) == len(plan["widgets"]), r)
    s, r = call("POST", "/api/v1/cxo/dashboards", {"title": "Season overview", "prompt": "placement season", "spec": plan}, tok)
    check("save", s == 201 and r["data"]["id"], r)
    did = r["data"]["id"]
    s, r = call("GET", "/api/v1/cxo/dashboards", None, tok)
    check("list contains it", s == 200 and any(x["id"] == did for x in r["data"]), r)
    s, r = call("POST", f"/api/v1/cxo/dashboards/{did}/pin", {"pinned": True}, tok)
    s, r = call("GET", f"/api/v1/cxo/dashboards/{did}", None, tok)
    check("get + pinned", s == 200 and r["data"]["pinned"] and r["data"]["spec"]["widgets"], r)
    s, r = call("PUT", f"/api/v1/cxo/dashboards/{did}", {"title": "Renamed", "spec": plan}, tok)
    check("update", s == 200 and r["data"]["title"] == "Renamed", r)

    # ---------------------------------------------------------------- security
    evil = {"title": "x", "widgets": [
        {"title": "a", "type": "KPI", "metric": "students_total", "dimension": "NONE", "branchCode": "X' OR '1'='1", "size": "sm"},
        {"title": "b", "type": "BAR", "metric": "students_total; drop table users", "dimension": "BRANCH", "size": "md"},
        {"title": "c", "type": "LINE", "metric": "offers", "dimension": "COMPANY", "size": "md"}]}
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": evil}, tok)
    check("injected branch ignored, unknown metric dropped, chart type fixed",
          s == 200 and len(r["data"]) == 2 and r["data"][0]["value"] == db_students, r)
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": {"title": "x", "widgets": [
        {"title": "c", "type": "BAR", "metric": "offers", "dimension": "STUDENT_NAME", "size": "md"}]}}, tok)
    check("unknown dimension (e.g. student names) rejected", s == 400, r)
    check("users table still there", int(sql("select count(*) from users")[0][0]) > 0)
    s, _ = call("GET", "/api/v1/college/students", None, tok)
    check("CXO cannot call the college staff API", s == 403, s)
    s, _ = call("GET", "/api/v1/admin/cxo-users", None, tok)
    check("CXO cannot call the admin API", s == 403, s)
    s, _ = call("GET", "/api/v1/cxo/me", None, admin)
    check("admin cannot call the CXO API", s == 403, s)
    s, _ = call("GET", "/api/v1/cxo/me")
    check("no token -> 401", s == 401, s)
    # a second CXO of another college cannot read the first one's dashboard
    email2 = f"cxo2.{RUN}@example.test"
    call("POST", "/api/v1/admin/cxo-users", {"collegeId": other, "fullName": "Other Director", "email": email2, "password": "Lead2026x!"}, admin)
    s, r = call("POST", "/api/v1/auth/login", {"email": email2, "password": "Lead2026x!"})
    tok2 = r["data"]["token"]
    s, _ = call("GET", f"/api/v1/cxo/dashboards/{did}", None, tok2)
    check("other college's CXO cannot open the dashboard", s == 404, s)
    s, r = call("POST", "/api/v1/cxo/dashboards/run", {"spec": fixed}, tok2)
    other_students = int(sql(f"select count(*) from college_students where college_id='{other}' and deleted_at is null")[0][0])
    got = next((d["value"] for d in (r.get("data") or []) if d["widgetId"] == "w1"), None)
    check("same spec, other CXO -> only their own college's numbers", s == 200 and got == other_students != db_students, (got, other_students))

    s, _ = call("DELETE", f"/api/v1/cxo/dashboards/{did}", None, tok)
    check("delete", s == 200, s)

    # ---------------------------------------------------------------- revoke ends access at once
    s, _ = call("POST", f"/api/v1/admin/cxo-users/{member}/revoke", None, admin)
    check("admin revokes", s == 200, s)
    s, _ = call("GET", "/api/v1/cxo/me", None, tok)
    check("revoked CXO's open session stops working", s in (401, 403), s)
    s, _ = call("POST", "/api/v1/auth/login", {"email": email, "password": "Lead2026x!"})
    check("revoked CXO cannot sign in", s in (401, 403), s)
    s, _ = call("POST", f"/api/v1/admin/cxo-users/{member}/restore", None, admin)
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": "Lead2026x!"})
    check("restored CXO signs in again", s == 200, r)
    logged = int(sql(f"select count(*) from cxo_prompt_log where user_id=(select id from users where email='{email}')")[0][0])
    check("every prompt is in the audit log", logged >= 6, logged)

    passed = sum(1 for _, ok in results if ok)
    print(f"\n{passed}/{len(results)} checks passed")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
