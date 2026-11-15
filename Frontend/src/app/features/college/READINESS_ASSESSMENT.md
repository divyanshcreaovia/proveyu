# Readiness Assessment (monthly tests): pending feature

**Status:** not built yet. To be built **only when the product owner gives the go-ahead**.
**Raised:** 15 Nov 2026
**Belongs to:** College Placement Portal (`features/college` frontend, `com.proveyu.college` backend).

## What it is

Every month the college runs a proctored PROVEYU **readiness test** for its students. The TPO needs a section in the college portal to schedule these tests, see each test's results, and track how every batch and branch improves month by month. The results also feed student tiers.

## Reference design

The first version of the college UI had these screens as **mock data only** (no backend). They were removed in the rewrite but are still on the `main` branch and can be used as the visual reference:

| Old mock page (on `main`) | What it showed |
|---|---|
| `Frontend/src/app/features/college/pages/tests/` | Proctored assessment history |
| `Frontend/src/app/features/college/pages/results/` | TPO diagnostic and analytics center |
| `Frontend/src/app/features/college/pages/settings/test-config.*` | Assessment defaults and tier cutoffs |

View them with `git show main:Frontend/src/app/features/college/pages/results/results.html`.

## Planned scope

### 1. Test schedule and history
- One test per month (e.g. Test 1 Diagnostic, Test 2 Mid-term, Test 3 Pre-placement, Test 4 Final), each with title, test key, date, start time, venue/centre, duration and eligible batches.
- Per test: registered vs attended, pass rate, average score, status (scheduled / completed / published).
- Test detail: student-wise score, section-wise score, tier, attendance.
- Export to Excel and PDF; "send to training team".

### 2. Readiness / diagnostic dashboard
- Filters: batch, branch, test, tier.
- KPIs: tests conducted, submissions, pass rate (benchmark met %), average PROVEYU score, improvement vs the previous test.
- Score progression across monthly tests, with branch comparison (e.g. CS vs IT vs EXTC).
- Section-wise skill gaps (e.g. aptitude, coding, communication).
- Tier movement month over month (e.g. how many moved from Tier 3 to Tier 2).
- Remedial alerts to faculty for weak students or sections.

### 3. Settings
- Tier cutoffs: Tier 1 ≥ 70 %, Tier 2 60–70 %, Tier 3 < 60 % (editable).
- Show scores to students immediately after the test (on/off).
- Section-wise feedback in the student portal (on/off).
- Automatic reminder 24 h before the test (e-mail / in-app).

## Open questions (answer before building)

1. **Who creates the test?** The PROVEYU team centrally every month, or each college schedules its own?
2. **Where do scores come from?** Options:
   - the existing PROVEYU modules (exams, slot booking, admit card, `candidate_scores`): students book a slot and take the proctored test at a centre; or
   - the college uploads results (Excel/CSV), like round results in drives.
3. **Sections:** a fixed list for every test, or configurable per test?
4. **Placement link:** should the readiness tier be usable as a drive eligibility rule (e.g. "Tier 1 only")?
5. **Student view:** should students see their own monthly results and progress in the candidate portal (next to Campus Drives)?

## Implementation notes (for whoever builds it)

- **Backend:** add it to the existing `com.proveyu.college` module. Use a new Flyway migration (next free version; never reuse a number). Every table needs `college_id` with an index, and the tenant must come from `CollegeContext`, never from the request. Use `@PreAuthorize(CollegeAccess.READ/WRITE/ADMIN)` like the other college controllers. Reuse `TabularExporter` for Excel/CSV/PDF and `AuditService` for changes.
- **Frontend:** add pages under `features/college/readiness/` with a lazy route and a sidebar entry in `layout/college-shell.ts`. Add one service in `src/app/services/`, and models in `core/models/college.models.ts`. Use the shared UI (`pm-data-table`, `pm-stat-card`, `pm-chart`, …). Styling comes automatically from `layout/college-theme.scss` (the candidate-portal look).
- **Existing data to reuse:** `candidate_scores` + `skills` (already shown as "PROVEYU verified scores" in the student drawer and the skill-gap report), `college_tiers` (currently CTC tiers for drives; readiness tiers are score-based and should be a separate concept).
- **Tests:** extend `PlacementFlowIT` / `TenancyIsolationIT` (Testcontainers) and add Vitest specs for the new service.
