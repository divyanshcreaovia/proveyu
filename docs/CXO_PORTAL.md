# Leadership (CXO) portal: how a question becomes numbers

A CXO types a question; the AI (or, without an AI key, a keyword planner) only **picks** metrics from a fixed catalog.
All SQL is written once in `MetricCatalog`; filters are bind parameters; the college always comes from the signed-in account.

## Years (what the dashboard may show)

The years named in the question are read by `ScopeEnforcer` (code, not the model) and applied to every plan.

| Question | What is shown |
|---|---|
| no year ("placement overview") | the plan as it is (all batches; trends = last 12 months) |
| one year ("2025", "placement in 2026") | that year only. Student numbers (placed, offers, applications, tests...) = the **batch graduating that year**; numbers with only a date (drives, interviews, announcements) = that **calendar year**. Numbers tied to neither (the recruiter list) are left out |
| a year the college has no batch for ("2024") | **nothing**: KPIs show "–", charts "No data". A note names the batches on record |
| several years ("2024 vs 2026", "2023 to 2025", "since 2024") | one chart per metric with exactly those years side by side; a year with no batch is not drawn (the note says so) |
| "all years", "year on year", "batch wise" | every batch on record side by side |
| "2026 vs last year" | 2025 and 2026 side by side |

A KPI for a batch shows the change against the previous batch (when it exists); a calendar-year KPI against the previous year.

## Metrics

Placement and students, offers and CTC (average, highest, lowest, bands, companies that hired), drives, applications and funnel,
selection rounds, interviews, recruiters, mock tests, AI interviews, off-campus, announcements, proctoring, average CGPA, and
**Code ground** (`code_submissions`, `code_accept_rate`, `code_active_coders`, `code_problems_solved`).
Splits: branch, batch, year, month, company, status, type, tier, package band, test, level, stage, round, language, gender.

## Tests

- `ScopeEnforcerTest`: how years, ranges, "since", "all years" and "last year" are read; every topic with one, several and all years.
- `CxoYearScopeIT`: real MySQL with known data: one year, a missing year, several years, calendar years, another college never leaks,
  and every metric × split × filter runs without a database error.
- `DashboardPlannerTest`, `CxoNoticesTest`: planner, titles, notes.

## Beyond dashboards

| Feature | Where | How it works |
|---|---|---|
| **Board report** | Export button on a dashboard (Overview or Saved) | The dashboard on screen with fresh numbers and the AI takeaways, as a PDF or PowerPoint. Lists of student names are left out. `POST /api/v1/cxo/dashboards/export?format=pdf|pptx` |
| **Drill-down** | Click a bar, a slice or a number tile | The students behind it (name, roll no., branch, batch, CGPA, status, placed), up to 200, with a CSV download. Only for numbers counted over students. `POST /api/v1/cxo/drill` |
| **What changed** | `/cxo/changes` | The last 7 / 30 / 90 days against the same length before it: students placed, offers, applications, drives, interviews, packages, tests, code practice, flags; plus the highlights and the companies with offers in the period |
| **Forecast** | `/cxo/forecast` | A batch's placements projected to the end of the year from the pace of the last three 30-day periods: likely, slower and faster. Shows where last year's batch stood at the same date. Marked low confidence when few students were placed recently |
| **Share link** | Share button on a saved dashboard | A read-only link (7 to 180 days) that needs no sign-in. Only a hash of the secret is stored; the link is shown once; it can be switched off; the numbers are live; no student names; never indexed or cached |
| **NIRF / NAAC figures** | `/cxo/benchmark` | Placement outcomes for a batch and the two before it: batch size, placed, % placed, median / average / highest / lowest package (each placed student's best accepted offer), offers, companies, students with several offers, by branch; as a PDF or PowerPoint. Higher studies and entrepreneurship are marked "Not recorded" |
| **How is this calculated** | the **i** button on every widget | The metric's definition and the filters in force (batches, branch, period, split) |

## Regression set

`src/test/resources/cxo/questions.txt` has 120+ questions (English and Hinglish) with what each must produce: which years, which metrics.
`CxoYearScopeIT.theQuestionSetKeepsWorking` plans and runs every one against known data. To cover a new kind of question, add a line and run it.

## Final check across two colleges

`scripts/e2e_cxo_final.py` (test database only) builds the 15 Ask AI questions for the CXO of each of two colleges and compares every widget with SQL
written in the script, compares every drill-down list with the students the database says are behind that number or bar (name, roll no., branch, batch,
CGPA, status, placed), and then tries to reach across: the other college's dashboards, links, labels, injected filters, exports, benchmark, forecast
and changes. It exits non-zero on any mismatch or leak. The second college can be a copy of the first with prefixed names and different numbers.
