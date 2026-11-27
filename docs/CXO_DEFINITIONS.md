# Leadership portal: how every number is counted

Read this with your NIRF / NAAC definitions beside you. Where your college counts differently, tell us and the metric is changed in one place
(`MetricCatalog`); the tests then check the new rule everywhere.

## Who is counted
| Term | Rule |
|---|---|
| Student | A row on the college roll that is **not deleted** (any status). A deleted student is counted nowhere, not even in tests, Code ground or documents |
| Eligible | A student whose status is not **DEBARRED** or **OPTED_OUT** |
| Placed | A student with at least one offer whose status is **ACCEPTED** or **JOINED** (any date). A student whose only offer is OFFERED, DECLINED or REVOKED is not placed |
| Unplaced | Eligible and not placed |
| Placement rate | Placed eligible students ÷ eligible students × 100. A placed student who is debarred or opted out is in "Placed students" but not in the rate |

## Offers and packages
| Term | Rule |
|---|---|
| Offer | An offer whose status is OFFERED, ACCEPTED or JOINED (declined and revoked offers are not counted) |
| Package (CTC) | The offer's CTC in lakh per annum. Offers without a package are left out of every package figure |
| Average / highest / lowest / median CTC | Over offers (not students): a student with two offers adds two values. The NIRF page instead uses **each placed student's highest accepted offer** |
| Offer acceptance rate | (ACCEPTED + JOINED) ÷ (ACCEPTED + JOINED + DECLINED); offers still open are not in either side |
| New placements | Students whose **first** accepted/joined offer date falls in the period |
| Students with several offers | Two or more offers with status OFFERED, ACCEPTED or JOINED |

## Years
| Asked about | Meaning of "year 2026" |
|---|---|
| Numbers about students (placed, offers, applications, tests, AI interviews, Code ground...) | The **batch graduating in 2026** (the student's batch year) |
| Numbers that only have a date (drives, interviews, announcements) | The **calendar year** 2026 |
| A year with no batch on record | Nothing is shown; a note says which batches exist |

## Pipeline, tests and practice
| Term | Rule |
|---|---|
| Applications | Applications of non-deleted students to drives; "selection rate" = SELECTED ÷ (SELECTED + REJECTED) |
| Placement funnel | Eligible → applied (not withdrawn) → shortlisted (shortlisted, in process or selected) → selected → placed, each counted in **students** |
| Round pass rate | PASS ÷ (PASS + FAIL); absent, hold and pending results are not in the rate |
| Mock tests | Submitted attempts only; average score = mean of score ÷ max score × 100 over attempts that have a score |
| Proctoring flags | Every flag raised, including on attempts not submitted; "flagged attempts" counts each attempt once |
| AI interviews | Sessions started by the college's students; the average score uses finished sessions that have a report |
| Code ground | Submissions as made (every run of Submit); accepted rate = ACCEPTED ÷ all submissions; active coders = different students with a submission; problems solved = a student and a problem counted once |
| Student documents | Documents in the locker, by verification status |

## Not recorded in ProveYu
Higher studies, entrepreneurship, and any placement outside the platform (the NIRF page shows "Not recorded" for these: fill them from your own records).

## How we check
`CxoIsolationIT` seeds known data in every table the metrics read and checks the exact answer; adds other colleges around it (a big one with the same branch
codes, one with odd data, one empty) and requires every number and every student list of the first college to stay identical; `e2e_cxo_final.py` compares the
15 questions and every student list with plain SQL on a two-college database.
