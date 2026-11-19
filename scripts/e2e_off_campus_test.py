#!/usr/bin/env python3
"""
Off-campus jobs end to end: the admin posts and publishes a job, an eligible student is notified, opens the link and reports
applied -> interviewing -> offer; a student who is not eligible sees why and gets no link; the TPO sees the offer and can turn
off-campus jobs off for the college. Uses the demo college from seed_demo.py.

  python3 scripts/e2e_off_campus_test.py [http://localhost:8080]
"""
import datetime as dt
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(__file__))
import smoke_test as st  # noqa: E402
from smoke_test import call, check  # noqa: E402

A = "/api/v1/admin/off-campus/jobs"
S = "/api/v1/college/student/off-campus"
C = "/api/v1/college/off-campus"
TAG = uuid.uuid4().hex[:5]


def activate(email, password):
    """Sets the password of a seeded student who has not activated the account yet (dev profile: the OTP comes back in the response)."""
    s, r = call("POST", "/api/v1/auth/forgot-password", {"email": email})
    otp = (r or {}).get("data", {}).get("devOtp") if s == 200 else None
    if otp:
        call("POST", "/api/v1/auth/reset-password", {"email": email, "resetToken": otp, "newPassword": password})


def login(email, password):
    s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    if s != 200 and email.endswith("@demo-college.edu.in"):
        activate(email, password)
        s, r = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    check(f"login {email}", s == 200, r)
    return r["data"]["token"]


def job(**over):
    body = {
        "companyName": f"Acme {TAG}", "roleTitle": "Software Engineer", "jobType": "FULL_TIME", "workMode": "HYBRID", "location": "Pune",
        "ctcMinLpa": 6, "ctcMaxLpa": 9, "description": "Build things.", "skills": ["Java", "SQL"], "applyUrl": "https://careers.example.com/acme",
        "applyDeadline": (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=7)).isoformat().replace("+00:00", "Z"),
        "batchYears": [2027], "branchKeywords": "Computer", "minCgpa": 7, "maxBacklogs": 0, "collegeIds": [],
    }
    body.update(over)
    return body


def find(token, job_id):
    data = call("GET", S, token=token)[1]["data"]
    return data, next((j for j in data["jobs"] if j["id"] == job_id), None)


def notifications(token):
    d = call("GET", "/api/v1/notifications", token=token)[1]["data"]
    return d if isinstance(d, list) else d.get("content", d.get("rows", []))


def main():
    print(f"Off-campus E2E against {st.BASE}")
    admin = login("admin@proveyu.local", "Admin@1234")
    tpo = login("tpo@demo-college.edu.in", "Demo@1234")
    good = login("siddharth.iyer25@demo-college.edu.in", "Student@123")   # CSE 2027, CGPA 7.2, no backlogs
    blocked = login("aarav.sharma1@demo-college.edu.in", "Student@123")   # CSE 2027, CGPA 8.37, 2 backlogs
    policy = call("GET", "/api/v1/college/settings/policy", token=tpo)[1]["data"]
    original = policy.get("offCampusPolicy", "ALLOW")
    check("policy has offCampusPolicy", original in ("ALLOW", "BLOCK_PLACED", "DISABLED"), policy)
    call("PUT", "/api/v1/college/settings/policy", {**policy, "offCampusPolicy": "ALLOW"}, tpo)

    try:
        print("== Admin posts")
        check("student cannot use the admin API (403)", call("GET", A, token=good)[0] == 403)
        s, r = call("POST", A, job(applyUrl="javascript:alert(1)"), admin)
        check("non-http link rejected (400)", s == 400, r)
        s, r = call("POST", A, job(ctcMinLpa=10, ctcMaxLpa=5), admin)
        check("min CTC above max rejected (400)", s == 400, r)
        s, r = call("POST", A, job(), admin)
        check("job saved as draft", s == 201 and r["data"]["status"] == "DRAFT", r)
        jid = r["data"]["id"]
        check("draft hidden from students", find(good, jid)[1] is None)

        before = len(notifications(good))
        s, r = call("POST", f"{A}/{jid}/status", {"status": "PUBLISHED"}, admin)
        check("publish", s == 200 and r["data"]["status"] == "PUBLISHED" and r["data"]["publishedAt"], r)
        notes = notifications(good)
        check("eligible student notified", len(notes) > before and any(f"Acme {TAG}" in n["message"] for n in notes), notes[:2])
        check("ineligible student not notified", not any(f"Acme {TAG}" in n["message"] for n in notifications(blocked)))

        print("== Student who is not eligible")
        _, j = find(blocked, jid)
        check("sees the job with the reason and no link", j and not j["canApply"] and j["applyUrl"] is None
              and any("backlog" in x for x in j["reasons"]), j)
        check("cannot open the link (403)", call("POST", f"{S}/{jid}/click", {}, blocked)[0] == 403)
        check("cannot mark as applied (403)", call("PUT", f"{S}/{jid}/status", {"status": "APPLIED"}, blocked)[0] == 403)

        print("== Eligible student")
        _, j = find(good, jid)
        check("can apply and gets the link", j and j["canApply"] and j["applyUrl"] == "https://careers.example.com/acme" and j["eligible"], j)
        check("interviewing before applied is refused (400)", call("PUT", f"{S}/{jid}/status", {"status": "INTERVIEWING"}, good)[0] == 400)
        s, r = call("POST", f"{S}/{jid}/save", {}, good)
        check("save", s == 200 and r["data"]["saved"] is True, r)
        s, r = call("POST", f"{S}/{jid}/click", {}, good)
        check("open link returns the URL", s == 200 and r["data"]["url"] == "https://careers.example.com/acme", r)
        check("status is CLICKED", find(good, jid)[1]["status"] == "CLICKED")
        for st_ in ("APPLIED", "INTERVIEWING"):
            s, r = call("PUT", f"{S}/{jid}/status", {"status": st_}, good)
            check(f"mark {st_}", s == 200 and r["data"]["status"] == st_, r)
        tpo_before = len(notifications(tpo))
        s, r = call("PUT", f"{S}/{jid}/status", {"status": "OFFER", "offerCtcLpa": 8.5, "note": "Joining in July"}, good)
        check("mark OFFER with CTC", s == 200 and r["data"]["status"] == "OFFER" and float(r["data"]["offerCtcLpa"]) == 8.5, r)
        check("still saved", find(good, jid)[1]["saved"] is True)
        check("TPO notified of the offer", len(notifications(tpo)) > tpo_before)

        print("== Placement cell and admin")
        s, r = call("GET", C, token=tpo)
        row = next((x for x in r["data"]["rows"] if x["jobId"] == jid), None)
        check("TPO sees the offer", s == 200 and row and row["status"] == "OFFER" and float(row["offerCtcLpa"]) == 8.5 and row["note"] == "Joining in July", row)
        check("TPO totals count it", r["data"]["offers"] >= 1 and r["data"]["applied"] >= 1, r["data"])
        check("TPO status filter works", all(x["status"] == "OFFER" for x in call("GET", f"{C}?status=OFFER", token=tpo)[1]["data"]["rows"]))
        s, body = call("GET", f"{C}/export?format=xlsx", token=tpo, raw=True)
        check("TPO export (xlsx)", s == 200 and body[:2] == b"PK")
        check("student cannot use the college API (403)", call("GET", C, token=good)[0] == 403)
        r = call("GET", f"{A}?q=Acme%20{TAG}", token=admin)[1]["data"]["rows"][0]
        check("admin counts", r["counts"]["clicked"] == 1 and r["counts"]["applied"] == 1 and r["counts"]["offers"] == 1 and r["counts"]["saved"] == 1, r["counts"])
        apps = call("GET", f"{A}/{jid}/applicants", token=admin)[1]["data"]
        check("admin sees the applicant", len(apps) == 1 and apps[0]["status"] == "OFFER" and "Siddharth" in apps[0]["studentName"], apps)

        print("== NOC")
        pdf = b"%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
        s, r = call("POST", f"{S}/{jid}/noc?note=Joining%201%20July", token=blocked, files={"offerLetter": ("offer.pdf", pdf, "application/pdf")})
        check("student without an offer cannot ask for a NOC", s in (403, 409), r)
        s, r = call("POST", f"{S}/{jid}/noc?note=Joining%201%20July", token=good, files={"offerLetter": ("offer.pdf", pdf, "application/pdf")})
        check("student asks for a NOC with the offer letter", s == 201 and r["data"]["status"] == "PENDING", r)
        noc_id = r["data"]["id"]
        s, r = call("POST", f"{S}/{jid}/noc", token=good, files={"offerLetter": ("offer.pdf", pdf, "application/pdf")})
        check("second open request refused (409)", s == 409, r)
        check("student sees the request on the job", find(good, jid)[1]["noc"]["status"] == "PENDING")
        s, r = call("GET", f"{C}/nocs?status=PENDING", token=tpo)
        check("TPO sees the pending request", s == 200 and any(n["id"] == noc_id and n["studentNote"] == "Joining 1 July" for n in r["data"]), r)
        check("pending count on the overview", call("GET", C, token=tpo)[1]["data"]["pendingNocs"] >= 1)
        s, body = call("GET", f"{C}/nocs/{noc_id}/offer", token=tpo, raw=True)
        check("TPO opens the offer letter", s == 200 and body[:4] == b"%PDF")
        check("reject without a reason refused (400)", call("POST", f"{C}/nocs/{noc_id}/decision?status=REJECTED", token=tpo, files={})[0] == 400)
        s, r = call("POST", f"{C}/nocs/{noc_id}/decision?status=APPROVED&note=Finish%20semester%20exams%20first", token=tpo, files={})
        check("TPO approves; NOC generated", s == 200 and r["data"]["status"] == "APPROVED" and r["data"]["hasNoc"] and r["data"]["nocGenerated"], r)
        check("approving a full-time NOC records the placement", r["data"]["placementRecorded"] is True, r["data"])
        placed = call("GET", "/api/v1/college/students?q=siddharth.iyer25", token=tpo)[1]["data"]["content"][0]
        check("student is now PLACED with the off-campus CTC as highest", placed["status"] == "PLACED" and float(placed["highestCtcLpa"]) >= 8.5, placed)
        offer = next((o for o in call("GET", f"/api/v1/college/offers?size=100", token=tpo)[1]["data"]["content"] if o["companyName"] == f"Acme {TAG}"), None)
        check("an ACCEPTED off-campus offer is in the college's offers", offer and offer["status"] == "ACCEPTED" and float(offer["ctcLpa"]) == 8.5, offer)
        check("already decided (409)", call("POST", f"{C}/nocs/{noc_id}/decision?status=REJECTED&note=x", token=tpo, files={})[0] == 409)
        s, body = call("GET", f"{C}/nocs/{noc_id}/noc", token=tpo, raw=True)
        check("TPO downloads the NOC PDF", s == 200 and body[:4] == b"%PDF")
        s, body = call("GET", f"{S}/noc/{noc_id}/noc", token=good, raw=True)
        check("student downloads the NOC PDF", s == 200 and body[:4] == b"%PDF")
        check("other student cannot download it (404)", call("GET", f"{S}/noc/{noc_id}/noc", token=blocked, raw=True)[0] == 404)
        n = find(good, jid)[1]["noc"]
        check("student sees APPROVED with the remark", n["status"] == "APPROVED" and n["hasNoc"] and n["decisionNote"] == "Finish semester exams first", n)
        check("student notified of the NOC", any("NOC" in x["message"] for x in notifications(good)))

        print("== Import")
        s, r = call("GET", "/api/v1/admin/off-campus/import", token=admin)
        check("import status readable", s == 200 and "configured" in r["data"], r)
        s, r = call("POST", "/api/v1/admin/off-campus/import/run", {}, admin)
        check("manual import runs (or explains the missing key)", s == 200 and (r["data"]["configured"] or "CAREERJET_API_KEY" in (r["data"]["error"] or "")), r)
        check("imported list filter works", call("GET", f"{A}?source=CAREERJET&field=DATA", token=admin)[0] == 200)
        check("student cannot run the import (403)", call("POST", "/api/v1/admin/off-campus/import/run", {}, good)[0] == 403)

        print("== Fields and interests")
        s, r = call("POST", A, job(roleTitle="Data Analyst", collegeIds=[]), admin)
        check("field detected from the role", s == 201 and r["data"]["field"] == "DATA", r)
        jdata = r["data"]["id"]
        s, r = call("POST", A, job(roleTitle="Graduate Trainee", field="MECHANICAL"), admin)
        check("field chosen by the admin", s == 201 and r["data"]["field"] == "MECHANICAL", r)
        jmech = r["data"]["id"]
        before_int = len(notifications(good))
        s, r = call("PUT", f"{S}/interests", {"fields": ["DATA", "SOFTWARE", "DATA"]}, good)
        check("student saves interests (deduplicated)", s == 200 and r["data"] == ["DATA", "SOFTWARE"], r)
        check("interests come back with the jobs", call("GET", S, token=good)[1]["data"]["interests"] == ["DATA", "SOFTWARE"])
        for x in (jdata, jmech):
            call("POST", f"{A}/{x}/status", {"status": "PUBLISHED"}, admin)
        notes = [n["message"] for n in notifications(good)][: len(notifications(good)) - before_int]
        check("notified about the data job, not the mechanical one",
              any("Data Analyst" in n for n in notes) and not any("Graduate Trainee" in n for n in notes), notes)
        _, jj = find(good, jmech)
        check("mechanical job still visible to the student (All tab)", jj is not None and jj["field"] == "MECHANICAL", jj)
        check("admin field filter", all(j["field"] == "DATA" for j in call("GET", f"{A}?field=DATA", token=admin)[1]["data"]["rows"]))
        call("PUT", f"{S}/interests", {"fields": []}, good)
        for x in (jdata, jmech):
            call("POST", f"{A}/{x}/status", {"status": "CLOSED"}, admin)

        print("== Other college only")
        other = str(uuid.uuid4())
        s, r = call("POST", A, job(roleTitle="Analyst", collegeIds=[other]), admin)
        jid2 = r["data"]["id"]
        call("POST", f"{A}/{jid2}/status", {"status": "PUBLISHED"}, admin)
        check("job for another college is hidden", find(good, jid2)[1] is None)
        check("and cannot be opened (404)", call("POST", f"{S}/{jid2}/click", {}, good)[0] == 404)
        s, r = call("POST", f"{A}/{jid2}/duplicate", {}, admin)
        check("duplicate makes a draft copy", s == 200 and r["data"]["status"] == "DRAFT" and r["data"]["roleTitle"] == "Analyst (copy)", r)

        print("== College turns it off")
        call("PUT", "/api/v1/college/settings/policy", {**policy, "offCampusPolicy": "DISABLED"}, tpo)
        data, _ = find(good, jid)
        check("student sees DISABLED and no jobs", data["policy"] == "DISABLED" and data["jobs"] == [], data)
        check("student actions refused (403)", call("POST", f"{S}/{jid}/save", {}, good)[0] == 403)
        call("PUT", "/api/v1/college/settings/policy", {**policy, "offCampusPolicy": "ALLOW"}, tpo)

        print("== Admin closes")
        call("POST", f"{A}/{jid}/status", {"status": "CLOSED"}, admin)
        _, j = find(good, jid)
        check("closed job stays in the student's list, no link", j and not j["canApply"] and j["applyUrl"] is None and j["status"] == "OFFER", j)
        check("closed job cannot be opened (409)", call("POST", f"{S}/{jid}/click", {}, good)[0] == 409)
        for x in (jid2, r["data"]["id"]):
            call("POST", f"{A}/{x}/status", {"status": "CLOSED"}, admin)
        audit = {a["action"] for a in call("GET", "/api/v1/admin/audit?size=30", token=admin)[1]["data"]["rows"]}
        check("audit log has create / publish / close", {"OFF_CAMPUS_JOB_CREATED", "OFF_CAMPUS_JOB_PUBLISHED", "OFF_CAMPUS_JOB_CLOSED"} <= audit, audit)
    finally:
        call("PUT", "/api/v1/college/settings/policy", {**policy, "offCampusPolicy": original}, tpo)

    print(f"\n{st.passed} passed, {st.failed} failed")
    sys.exit(1 if st.failed else 0)


if __name__ == "__main__":
    main()
