#!/usr/bin/env python3
"""
Regenerates the two reference documents from the code, so they cannot drift from it:

  Backend/PROVEYU_API_SPECIFICATION_DOCS.md   every REST endpoint (method, path, who may call it), read from the @RestController classes
  Backend/PROVEYU_DATABASE_SCHEMA_DOCS.md     every table the code uses, with columns, read from finaluseddbschema.sql

    python3 scripts/gen_docs.py

Run it after adding endpoints or a migration (refresh finaluseddbschema.sql first). Payload details live in Swagger.
"""
import os
import re
from collections import OrderedDict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
JAVA = os.path.join(ROOT, "Backend", "src", "main", "java", "com", "proveyu")

# ---------------------------------------------------------------------------------------------------------------- API

ACCESS = {
    "CollegeAccess.READ": "College staff (TPO, coordinator, viewer)",
    "CollegeAccess.WRITE": "TPO, coordinator",
    "CollegeAccess.ADMIN": "TPO only",
    "CollegeAccess.STUDENT": "Student",
    "hasRole('ADMIN')": "Platform admin",
    "hasRole('CANDIDATE')": "Student",
    "hasRole('RECRUITER')": "Company (Drive Room)",
    "isAuthenticated()": "Any signed-in user",
}
MODULES = OrderedDict([
    ("auth", "Sign-in, e-mail verification, passwords"),
    ("college", "College portal (placement cell) and company Drive Room"),
    ("student", "Student portal, mock tests, AI interview"),
    ("offcampus", "Off-campus jobs, NOC, job import"),
    ("admin", "Platform admin portal"),
    ("support", "Help & support"),
    ("chat", "Chat (REST; live messages over the WebSocket /ws/chat)"),
    ("notification", "Notification bell"),
    ("shared", "Shared"),
])


# URL rules from SecurityConfig, used when a method has no @PreAuthorize (first match wins)
URL_RULES = [
    (None, r"^/api/v1/auth/logout$", "Any signed-in user"),
    (None, r"^/api/v1/auth/", "Public"),
    ("POST", r"^/api/v1/college/auth/(register|accept-invite|student-signup|recruiter-accept)$", "Public"),
    ("GET", r"^/api/v1/college/auth/(invite|recruiter-invite)/", "Public"),
    ("GET", r"^/api/v1/public/", "Public"),
    (None, r"^/api/v1/admin/", "Platform admin"),
    (None, r"^/api/v1/college/student/", "Student"),
    (None, r"^/api/v1/college/", "College staff"),
    (None, r"^/api/v1/recruiter/", "Company (Drive Room), platform admin"),
    (None, r".", "Any signed-in user"),
]


def url_rule(verb, path):
    for v, rx, label in URL_RULES:
        if (v is None or v == verb) and re.search(rx, path):
            return label
    return "Any signed-in user"


def access_label(expr):
    if expr is None:
        return None
    expr = expr.strip()
    if expr in ACCESS:
        return ACCESS[expr]
    m = re.match(r"hasAnyRole\((.*)\)", expr)
    if m:
        roles = [r.strip().strip("'\"") for r in m.group(1).split(",")]
        names = {"CANDIDATE": "Student", "COLLEGE_ADMIN": "TPO", "COLLEGE_COORDINATOR": "coordinator", "COLLEGE_VIEWER": "viewer",
                 "ADMIN": "Platform admin", "RECRUITER": "Company"}
        return ", ".join(names.get(r, r) for r in roles)
    return expr


def mapping_paths(args):
    """Paths from the arguments of a *Mapping annotation: "", "/x", value = "/x", {"/a", "/b"}."""
    if args is None:
        return [""]
    m = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', args)
    src = m.group(1) if m else args
    paths = re.findall(r'"([^"]*)"', src) if ("{" in src or not m) else re.findall(r'"([^"]*)"', src)
    if not m and "=" in args and not args.strip().startswith('"') and not args.strip().startswith("{"):
        return [""]
    return paths or [""]


def parse_controller(path):
    text = open(path, encoding="utf-8").read()
    if "@RestController" not in text:
        return []
    lines = text.split("\n")
    out = []
    pending = []          # annotations seen since the last declaration
    doc = None            # last javadoc text
    in_doc = False
    doc_buf = []
    base, cls_access, cls_name = [""], None, None
    for raw in lines:
        line = raw.strip()
        if line.startswith("/**"):
            in_doc, doc_buf = True, []
        if in_doc:
            doc_buf.append(line.strip("/* ").strip())
            if line.endswith("*/"):
                in_doc = False
                doc = " ".join(x for x in doc_buf if x and not x.startswith("@")).strip()
            continue
        if line.startswith("@"):
            pending.append(line)
            continue
        if re.search(r"\bclass\s+\w+", line) and pending:
            ann = " ".join(pending)
            rm = re.search(r"@RequestMapping\((.*?)\)\s*(?:@|$)", ann + " @")
            base = mapping_paths(rm.group(1)) if rm else [""]
            # a controller mapped at an old and a /v1/ path is listed once, at the /v1/ path
            v1 = [b for b in base if "/v1/" in b]
            base = v1[:1] if len(base) > 1 and v1 else base[:1]
            pa = re.search(r'@PreAuthorize\((.*?)\)\s*(?:@|$)', ann + " @")
            cls_access = pa.group(1).strip().strip('"') if pa else None
            cls_name = re.search(r"\bclass\s+(\w+)", line).group(1)
            pending, doc = [], None
            continue
        if pending and re.search(r"\b(public|private|protected)\b.*\(", line):
            ann = " ".join(pending)
            for verb in ("Get", "Post", "Put", "Patch", "Delete"):
                mm = re.search(r"@%sMapping(?:\((.*?)\))?\s*(?:@|$)" % verb, ann + " @")
                if mm:
                    pa = re.search(r'@PreAuthorize\((.*?)\)\s*(?:@|$)', ann + " @")
                    acc = pa.group(1).strip().strip('"') if pa else cls_access
                    name = re.search(r"(\w+)\s*\(", line.split("(")[0] + "(").group(1)
                    for b in base:
                        for p in mapping_paths(mm.group(1)):
                            full = (b.rstrip("/") + ("/" + p.lstrip("/") if p else "")) or "/"
                            out.append((verb.upper(), full, access_label(acc) or url_rule(verb.upper(), full), name, doc, cls_name))
                    break
            pending, doc = [], None
            continue
        if line and not line.startswith("//"):
            pending = [] if not line.startswith(")") else pending
    return out


def module_of(path):
    rel = os.path.relpath(path, JAVA).split(os.sep)
    return rel[0]


def gen_api():
    rows = OrderedDict((m, []) for m in MODULES)
    for dirpath, _, files in os.walk(JAVA):
        for f in sorted(files):
            if f.endswith(".java"):
                p = os.path.join(dirpath, f)
                for r in parse_controller(p):
                    rows.setdefault(module_of(p), []).append(r)
    total = sum(len(v) for v in rows.values())
    md = ["# ProveYu REST API reference", "",
          "<!-- Generated by scripts/gen_docs.py from the @RestController classes. Do not edit by hand; run the script again. -->", "",
          f"Every endpoint of the backend ({total} in total), grouped by module, with who may call it. Request and response bodies, "
          "field rules and examples are in Swagger: `http://localhost:8080/swagger-ui.html` (JSON: `/v3/api-docs`).", "",
          "## Conventions", "",
          "- Base URL: `http://localhost:8080` in development; same origin behind the reverse proxy in production.",
          "- Sign in with `POST /api/v1/auth/login`; send the returned token as `Authorization: Bearer <token>`.",
          "- Every JSON response is wrapped: `{ \"success\": true, \"message\": \"...\", \"data\": { ... } }`. Errors: "
          "`{ \"success\": false, \"status\": 404, \"code\": \"NOT_FOUND\", \"errorCode\": \"NOT_FOUND\", \"message\": \"...\", \"fieldErrors\": {...} }`.",
          "- College data is separated per college: another college's ids answer 404. Students only reach their own records.",
          "- Paged lists take `page` (from 0) and `size`. File downloads return the file itself (not wrapped).",
          "- Rate limits: 60 requests a minute per IP on sign-in routes, 120 a minute per user elsewhere.", ""]
    md += ["## Modules", ""] + [f"- [{MODULES.get(m, m)}](#{m}) ({len(v)})" for m, v in rows.items() if v] + [""]
    for m, items in rows.items():
        if not items:
            continue
        md += [f"<a id=\"{m}\"></a>", f"## {MODULES.get(m, m)}", "", f"Package `com.proveyu.{m}`.", ""]
        md += ["| Method | Path | Who | What |", "|---|---|---|---|"]
        for verb, path, acc, name, doc, cls in sorted(items, key=lambda r: (r[1], ["GET", "POST", "PUT", "PATCH", "DELETE"].index(r[0]))):
            what = (doc or re.sub(r"(?<!^)(?=[A-Z])", " ", name).lower()).replace("|", "/")
            what = what if len(what) < 160 else what[:157] + "..."
            md.append(f"| `{verb}` | `{path}` | {acc} | {what} |")
        md.append("")
    open(os.path.join(ROOT, "Backend", "PROVEYU_API_SPECIFICATION_DOCS.md"), "w", encoding="utf-8").write("\n".join(md))
    return total

# ---------------------------------------------------------------------------------------------------------------- DB


PURPOSE = {
    "users": "Every account: student, college staff, company person, platform admin (role, password hash, verified, blocked)",
    "email_verification_tokens": "One-time e-mail verification tokens",
    "notifications": "Notification bell entries per user",
    "chat_messages": "One-to-one chat messages and attachments",
    "platform_settings": "Admin-editable runtime settings (interview price, UPI ID, limits, banner)",
    "support_tickets": "Help & support requests and the team's answers",
    "admin_audit_logs": "Every platform admin action",
    "college_profiles": "A college: name, codes, TPO, approval status, placement policy, off-campus policy, branding",
    "college_members": "College staff: role (TPO / coordinator / viewer) and branch scope",
    "college_invites": "Pending invites for college staff and company people",
    "college_branches": "Branches (departments) of a college",
    "college_batches": "Batches (graduation years) of a college",
    "college_tiers": "CTC tiers (normal / dream / super dream) used by the placement policy",
    "college_students": "Students of a college: academic record, status (active / placed / debarred), highest offer",
    "college_student_documents": "Student documents (resume, mark sheets, ...) and their verification",
    "college_files": "Every stored file (documents, offer letters, logos, NOC PDFs) and where it is on disk",
    "college_import_jobs": "Excel / CSV student imports (dry run and commit history)",
    "college_companies": "Companies a college works with",
    "college_company_contacts": "Contacts of those companies",
    "college_company_feedback": "Feedback and ratings of companies after drives",
    "college_drives": "Campus drives: role, CTC, eligibility, status",
    "college_drive_rounds": "Rounds of a drive (test, interview, ...)",
    "college_drive_recruiters": "Company people with Drive Room access to a drive",
    "college_eligibility_overrides": "Audited include / exclude decisions against the eligibility rules",
    "college_applications": "A student's application to a drive and its status",
    "college_round_results": "Result of each applicant in each round",
    "college_round_scorecards": "Panel scorecards from the Drive Room",
    "college_interview_slots": "Interview slots (time, room or link) with clash checks",
    "college_offers": "Offers: CTC, status (offered / accepted / joined / ...), offer letter",
    "college_announcements": "Announcements from the placement cell",
    "college_announcement_recipients": "Who received an announcement and when they read it",
    "college_email_templates": "College-specific e-mail templates",
    "college_scheduled_reports": "Reports e-mailed on a schedule",
    "college_audit_logs": "Every change made by college staff",
    "ai_interview_sessions": "AI mock interviews: student, JD, focus, status, model used",
    "ai_interview_questions": "Questions asked in an interview (including follow-ups)",
    "ai_interview_answers": "The student's answers (typed or transcribed speech)",
    "ai_answer_evaluations": "AI score and feedback per answer",
    "ai_interview_reports": "Final interview report (overall score, strengths, improvements)",
    "interview_credits": "Free and paid AI interview credits per student",
    "interview_purchases": "UPI purchases of interviews and their approval",
    "prep_tests": "Mock tests (platform ladder and college tests) as JSON",
    "prep_attempts": "Mock test attempts: answers, score, result, auto-submit reason",
    "prep_proctor_events": "Leaving-the-test events with screenshots",
    "code_ground_progress": "Code ground: problems solved and learning guides read, per student",
    "code_ground_topics": "Code ground: study plan topics added by a platform admin",
    "code_ground_problems": "Code ground: questions added by a platform admin (draft and published versions)",
    "code_ground_hidden": "Code ground: built-in questions a platform admin has hidden",
    "code_ground_schedule": "Code ground: the problem of each day",
    "code_ground_daily": "Code ground: problem-of-the-day bonus points given to a student",
    "code_ground_submissions": "Code ground: the last submissions of a student per problem, with code and verdict",
    "code_ground_drafts": "Code ground: the code a student is writing, per problem and language",
    "off_campus_jobs": "Off-campus jobs: posted by the team or imported (Careerjet), field, eligibility, status",
    "off_campus_applications": "A student's tracking of an off-campus job (saved, opened link, applied, offer)",
    "off_campus_noc_requests": "NOC requests for off-campus offers: offer letter, decision, NOC file",
    "off_campus_interests": "Fields each student wants off-campus jobs in",
    "organizations": "Organisation record behind each college (kept from the older platform; read by registration)",
    "candidate_profiles": "Older candidate profile, read by the student drawer passport",
    "domains": "Skill domains, read by the skill-gap report",
    "skills": "Skills, read by the skill-gap report",
    "candidate_scores": "Older verified scores, read by the passport and skill-gap report",
}
GROUPS = OrderedDict([
    ("Accounts, notifications, platform", ["users", "email_verification_tokens", "notifications", "chat_messages", "platform_settings",
                                            "support_tickets", "admin_audit_logs"]),
    ("College portal", None),  # every college_* table
    ("AI interview", ["ai_interview_sessions", "ai_interview_questions", "ai_interview_answers", "ai_answer_evaluations",
                      "ai_interview_reports", "interview_credits", "interview_purchases"]),
    ("Mock tests", ["prep_tests", "prep_attempts", "prep_proctor_events"]),
    ("Code ground", ["code_ground_progress", "code_ground_drafts", "code_ground_daily", "code_ground_submissions", "code_ground_topics", "code_ground_problems", "code_ground_hidden", "code_ground_schedule"]),
    ("Off-campus jobs", ["off_campus_jobs", "off_campus_applications", "off_campus_noc_requests", "off_campus_interests"]),
    ("Kept from the older platform (read only)", ["organizations", "candidate_profiles", "domains", "skills", "candidate_scores"]),
])


def gen_db():
    sql = open(os.path.join(ROOT, "finaluseddbschema.sql"), encoding="utf-8").read()
    header = re.search(r"Flyway version (\d+)", sql)
    tables = OrderedDict()
    for m in re.finditer(r"CREATE TABLE `(\w+)` \((.*?)\n\) ENGINE", sql, flags=re.S):
        cols, keys = [], []
        for ln in m.group(2).split("\n"):
            ln = ln.strip().rstrip(",")
            c = re.match(r"`(\w+)` (.+)", ln)
            if c:
                rest = c.group(2)
                typ = rest.split(" ")[0]
                cols.append((c.group(1), typ, "NOT NULL" not in rest, re.search(r"DEFAULT ('[^']*'|\S+)", rest)))
            elif ln.startswith(("PRIMARY", "UNIQUE", "CONSTRAINT")):
                keys.append(ln.replace("`", ""))
        tables[m.group(1)] = (cols, keys)
    md = ["# ProveYu database schema", "",
          "<!-- Generated by scripts/gen_docs.py from finaluseddbschema.sql. Do not edit by hand; run the script again. -->", "",
          f"MySQL 8, database `proveyu_college`, utf8mb4. {len(tables)} tables the application uses"
          + (f", at Flyway version {header.group(1)}" if header else "") + ". The exact DDL is in "
          "[finaluseddbschema.sql](../finaluseddbschema.sql); migrations are in `Backend/src/main/resources/db/migration` "
          "(Flyway runs them when the backend starts; `ddl-auto: none`).", "",
          "Ids are UUIDs stored as `CHAR(36)`. Times are `DATETIME` written by the application. Every `college_*` row carries "
          "`college_id`, and every query filters by it.", ""]
    groups = OrderedDict()
    for g, names in GROUPS.items():
        groups[g] = [t for t in tables if t.startswith("college_")] if names is None else [t for t in names if t in tables]
    seen = {t for v in groups.values() for t in v}
    rest = [t for t in tables if t not in seen]
    if rest:
        groups["Other"] = rest
    md += ["## Tables", ""]
    for g, names in groups.items():
        md += [f"**{g}**", ""] + [f"- [`{t}`](#{t}): {PURPOSE.get(t, '')}" for t in names] + [""]
    for g, names in groups.items():
        md += [f"## {g}", ""]
        for t in names:
            cols, keys = tables[t]
            md += [f"<a id=\"{t}\"></a>", f"### `{t}`", "", PURPOSE.get(t, ""), "", "| Column | Type | Null | Default |", "|---|---|---|---|"]
            for name, typ, nullable, default in cols:
                md.append(f"| `{name}` | `{typ}` | {'yes' if nullable else ''} | {('`' + default.group(1) + '`') if default else ''} |")
            if keys:
                md += ["", "Keys: " + "; ".join(f"`{k}`" for k in keys)]
            md.append("")
    open(os.path.join(ROOT, "Backend", "PROVEYU_DATABASE_SCHEMA_DOCS.md"), "w", encoding="utf-8").write("\n".join(md))
    return len(tables)


if __name__ == "__main__":
    print(f"API reference: {gen_api()} endpoints")
    print(f"Database schema: {gen_db()} tables")
