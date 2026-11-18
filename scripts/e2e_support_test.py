#!/usr/bin/env python3
"""
Help & support end to end: a student and a TPO write to the ProveYu team, the admin reads, answers and closes, and the users see
the answer. Uses the accounts created by e2e_three_colleges_test.py (pass its run id).

  python3 scripts/e2e_support_test.py <run-id> [http://localhost:8080]
"""
import os
import sys
import time

RUN_ID = sys.argv.pop(1)
sys.path.insert(0, os.path.dirname(__file__))
import smoke_test as st  # noqa: E402
from smoke_test import call, check  # noqa: E402

T = "/api/v1/support/tickets"
A = "/api/v1/admin/support"


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    return r["data"]["token"] if s == 200 else None


def main():
    stu = login(f"aman.verma@gu{RUN_ID}.edu.in", "Student@123")
    other = login(f"riya.singh@gu{RUN_ID}.edu.in", "Student@123")
    tpo = login(f"tpo@niet{RUN_ID}.edu.in", "Tpo@12345")
    admin = login("admin@proveyu.local", "Admin@1234")
    check("logins", all([stu, other, tpo, admin]))
    tag = st.RUN
    open_before = call("GET", f"{A}/open-count", token=admin)[1]["data"]["open"]

    print("== Users write")
    s, r = call("POST", T, {"category": "AI_INTERVIEW", "subject": f"Interview report not loading {tag}",
                            "message": "My AI interview report keeps loading after I finished all questions."}, stu)
    check("student sends a request (201)", s == 201 and r["data"]["status"] == "OPEN" and r["data"]["portal"] == "STUDENT"
          and "Galgotias" in r["data"]["collegeName"], r)
    st_id = r["data"]["id"]
    s, r = call("POST", T, {"category": "PLACEMENT", "subject": f"Need to bulk upload offers {tag}",
                            "message": "Can we upload offer letters for many students at once?"}, tpo)
    check("TPO sends a request (201, college portal)", s == 201 and r["data"]["portal"] == "COLLEGE" and "Noida" in r["data"]["collegeName"], r)
    tpo_id = r["data"]["id"]
    check("too short message rejected (400)", call("POST", T, {"category": "OTHER", "subject": "x", "message": "short"}, stu)[0] == 400)
    check("unknown topic rejected (400)", call("POST", T, {"category": "NOPE", "subject": "x", "message": "long enough message"}, stu)[0] == 400)
    check("admin cannot use the user endpoint (403)", call("POST", T, {"category": "OTHER", "subject": "x", "message": "long enough message"}, admin)[0] == 403)
    check("anonymous cannot send (401)", call("POST", T, {"category": "OTHER", "subject": "x", "message": "long enough message"})[0] == 401)
    mine = call("GET", T, token=stu)[1]["data"]
    check("student sees own request", any(t["id"] == st_id for t in mine))
    check("another student does not see it", all(t["id"] != st_id for t in call("GET", T, token=other)[1]["data"]))
    check("student cannot open the admin inbox (403)", call("GET", A, token=stu)[0] == 403)
    check("TPO cannot answer (403)", call("POST", f"{A}/{st_id}/reply", {"reply": "x", "close": False, "sendEmail": False}, tpo)[0] == 403)

    print("== Admin inbox")
    check("open count went up by 2", call("GET", f"{A}/open-count", token=admin)[1]["data"]["open"] == open_before + 2)
    pg = call("GET", f"{A}?status=OPEN&q={tag}", token=admin)[1]["data"]
    check("inbox search finds both, with college names", pg["total"] == 2 and {t["id"] for t in pg["rows"]} == {st_id, tpo_id}, pg)
    notes = call("GET", "/api/v1/notifications", token=admin)[1]["data"]
    notes = notes["content"] if isinstance(notes, dict) else notes
    check("admin got a bell notification", any(f"Interview report not loading {tag}" in n["message"] for n in notes))

    s, r = call("POST", f"{A}/{st_id}/reply", {"reply": "Fixed now, please refresh the page. Your report is ready.", "close": False, "sendEmail": False}, admin)
    check("admin answers the student", s == 200 and r["data"]["status"] == "ANSWERED" and r["data"]["repliedBy"], r)
    t = next(t for t in call("GET", T, token=stu)[1]["data"] if t["id"] == st_id)
    check("student sees the answer", t["reply"].startswith("Fixed now") and t["status"] == "ANSWERED", t)
    notes = call("GET", "/api/v1/notifications", token=stu)[1]["data"]
    notes = notes["content"] if isinstance(notes, dict) else notes
    check("student got a notification", any("answered" in n["message"] and tag in n["message"] for n in notes))
    s, r = call("POST", f"{A}/{tpo_id}/reply", {"reply": "Not yet; it is on our list.", "close": True, "sendEmail": False}, admin)
    check("admin answers and closes the TPO's request", s == 200 and r["data"]["status"] == "CLOSED", r)
    check("TPO sees CLOSED with the answer", next(t for t in call("GET", T, token=tpo)[1]["data"] if t["id"] == tpo_id)["reply"] == "Not yet; it is on our list.")
    check("reopen", call("POST", f"{A}/{tpo_id}/status", {"status": "OPEN"}, admin)[1]["data"]["status"] == "OPEN")
    check("close again", call("POST", f"{A}/{tpo_id}/status", {"status": "CLOSED"}, admin)[1]["data"]["status"] == "CLOSED")
    check("open count back to before", call("GET", f"{A}/open-count", token=admin)[1]["data"]["open"] == open_before)
    audit = {a["action"] for a in call("GET", "/api/v1/admin/audit?size=20", token=admin)[1]["data"]["rows"]}
    check("audit log has the answers and status changes", {"SUPPORT_ANSWERED", "SUPPORT_OPEN", "SUPPORT_CLOSED"} <= audit, audit)
    check("unknown request -> 404", call("POST", f"{A}/00000000-0000-0000-0000-000000000000/status", {"status": "CLOSED"}, admin)[0] == 404)

    print(f"\n{st.passed} passed, {st.failed} failed")
    sys.exit(1 if st.failed else 0)


if __name__ == "__main__":
    main()
