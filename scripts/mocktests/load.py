"""Uploads the built mock tests (out/*.json) through the admin API and publishes them.

    python3 load.py [base_url]            # skips tests whose title already exists
    python3 load.py [base_url] --replace  # deletes and re-uploads existing ones (their attempts are deleted too)

Admin login: ADMIN_EMAIL / ADMIN_PASSWORD environment variables (defaults are the demo admin).
"""
import glob
import json
import os
import sys

REPLACE = "--replace" in sys.argv
sys.argv = [a for a in sys.argv if a != "--replace"]  # smoke_test reads the base URL from argv[1]
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import smoke_test  # noqa: E402
from smoke_test import call  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "out")


def main():
    replace = REPLACE
    s, r = call("POST", "/api/v1/auth/login", {"email": os.environ.get("ADMIN_EMAIL", "admin@proveyu.local"),
                                               "password": os.environ.get("ADMIN_PASSWORD", "Admin@1234")})
    if s != 200:
        raise SystemExit(f"Admin login failed ({s}): {r.get('message')}")
    token = r["data"]["token"]
    s, r = call("GET", "/api/v1/admin/prep/tests", token=token)
    existing = {t["title"]: t for t in r["data"]}
    for path in sorted(glob.glob(os.path.join(OUT, "*.json"))):
        with open(path, "rb") as f:
            raw = f.read()
        title = json.loads(raw)["title"]
        if title in existing:
            if not replace:
                print(f"skip     {title} (already uploaded)")
                continue
            call("DELETE", f"/api/v1/admin/prep/tests/{existing[title]['id']}", token=token)
        s, r = call("POST", "/api/v1/admin/prep/tests", token=token, files={"file": (os.path.basename(path), raw, "application/json")})
        if s != 200:
            raise SystemExit(f"Upload of {os.path.basename(path)} failed ({s}): {r.get('message')}")
        tid = r["data"]["id"]
        s, r = call("PATCH", f"/api/v1/admin/prep/tests/{tid}/publish", {"published": True}, token=token)
        if s != 200:
            raise SystemExit(f"Publishing {title} failed ({s}): {r.get('message')}")
        d = r["data"]
        print(f"loaded   {d['order']:>2}. {title}  [{d['level']}, {d['durationMinutes']} min, {d['questionCount']} questions, {d['totalMarks']} marks]")


if __name__ == "__main__":
    main()
