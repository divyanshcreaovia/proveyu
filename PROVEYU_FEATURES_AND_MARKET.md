# ProveYu: features, market se tulna, aur No. 1 banne ka plan

_Tareekh: 26 Nov 2026. Internal document._

**Kaise banaya:** features code ke routes, menu aur API list se liye gaye hain. Iska matlab feature **maujood** hai, yeh nahi ki har ek ki quality jaanchi gayi hai (is round mein sirf Code ground test hua hai). Competitors ki jaankari web search se hai, aur zyadatar unke **apne marketing pages** se. Maine unke products chala kar nahi dekhe.

---

## 1. Seedha jawab

- ProveYu mein placement chalane, preparation karane aur leadership ko report dene ke features **ek hi platform** mein hain. Yahi sabse badi taakat hai.
- Alag-alag features mein se zyadatar kisi na kisi competitor ke paas hain. "Sirf hamare paas hai" kisi feature ke liye saabit nahi hota.
- No. 1 banne ke liye naye features se zyada zaroori hain: **bharosemand chalna, placement ke natije badhana, aur college ka kaam aasan karna**.

---

## 2. Features, portal ke hisaab se

### 2.1 Public website
- Home, For colleges, For students, How it works, Pricing
- Pricing enquiry form (quote ke liye, fixed plan nahi)
- Portal chunne aur login ke pages

### 2.2 College portal (TPO, coordinator, viewer)
| Area | Features |
|---|---|
| Account | Registration, e-mail verify, admin approval, team invite, 3 roles, coordinator ko branch tak seemit karna |
| Dashboard | KPIs, charts, drive funnel, global search |
| Students | Add, bulk import (Excel/CSV) with history, export, status, soft delete aur restore, documents verify karna |
| Companies | Records, contacts, feedback, recruiter invite, export |
| Drives | Create, clone, status flow, eligibility rules, override, applicants, bulk status, export |
| Rounds | Multiple rounds, results type ya upload, auto aage badhna, scorecards |
| Interviews | Schedule, bulk schedule, clash check, reschedule, reminder |
| Offers | Create, letter (PDF), status, export |
| Tests | Apne mock test aur exams upload, schedule window, live monitor, proctoring log aur screenshots, extra time, marks, CSV register |
| Code ground | Student leaderboard (branch, batch filter) |
| Off-campus | Jobs, NOC approve/reject (college letterhead par PDF), export |
| Communication | Announcements (audience chunkar), chat (files ke saath) |
| Reports | 7 reports (NIRF/NAAC-style placement summary, branch, company, CTC, drive, alumni, skill-gap); xlsx/CSV/PDF; scheduled e-mail |
| Settings | Profile, logo, branches, batches, CTC tiers, placement policy, e-mail templates |
| Audit | Har badlav ka log |

### 2.3 Student portal
| Area | Features |
|---|---|
| Account | Roll number + e-mail se signup, password reset |
| Dashboard | Overview aur readiness checklist |
| Placement | Drives dekhna aur apply, applications aur rounds, withdraw, interviews, offers accept/decline, offer letter |
| Off-campus | Field ke hisaab se jobs, save, status, NOC request |
| Mock tests | Timed tests (MCQ aur coding), result, history, camera/mic/tab proctoring |
| College tests | College ke exams aur practice tests |
| Code ground | Neeche alag section |
| AI interview | Voice ya typed jawab, follow-up sawaal, report; free quota, extra ke liye UPI |
| Baaki | Announcements, placement cell se chat, document locker, profile, notifications, help & support, dark mode |

**Code ground** (naya, abhi commit nahi hua):
- Study plan: 10 topics, 74 sawaal, learning guides (4 languages)
- JavaScript, Python, Java, C++; Run (apna input), Submit, hidden tests, hints, print debugging
- Locked input code, copy-paste band, code server par save
- Problem of the day (double points), streak graph, submissions history aur filters
- Leaderboard (weekly, all time; college, branch, batch)
- Free compiler

### 2.4 Company Drive Room (recruiter)
Invite se login, apne drives, applicants (filters, resume, ZIP), rounds chalana, results aur scorecards, offers aur letters, drive report.

### 2.5 Leadership (CXO) portal
AI Studio (sawaal likh kar dashboard banana), dashboards save, edit, pin, aur AI summary.

### 2.6 Platform admin
Dashboard, colleges approve/suspend, users (block, password reset), admins, CXO accounts, UPI payments verify, off-campus jobs (manual aur roz ka auto import), mock tests upload/publish, Code ground (sawaal, topics, JSON upload, daily sawaal), proctoring review, support tickets, pricing enquiries, broadcast, settings, audit log, code-runner health.

---

## 3. Market se tulna

| Category | Udaharan | Woh kya batate hain |
|---|---|---|
| Placement management | InCruiter, Wishire, RB Tech, PrepInsta Optimus | Drives, shortlisting, offer tracking, analytics; Wishire: readiness score aur NAAC/NIRF report; RB Tech: mobile apps |
| Assessment aur proctoring | iamneo (NeoPAT), CodeTantra, HackerRank | Adaptive tests, AI proctoring, screen/camera recording, browser lockdown |
| AI interview aur resume | InterviewEra, AbhyaX, HireEdge, PrepFree | Voice mock interview, **ATS resume builder**, JD match, batch-wise readiness |
| Opportunities aur contests | Unstop, Internshala | Hackathons, quizzes, internships, certificates, mobile app |

| Feature | ProveYu | Market mein |
|---|---|---|
| Drive, eligibility, rounds, offers | Hai | Aam (zaroori cheez) |
| Company ka apna Drive Room | Hai | Recruiter portal kuch vendors dete hain |
| Mock tests, coding, proctoring | Hai | Aam; bade players ka proctoring zyada gehra |
| Coding practice, leaderboard, streak | Hai | Practice platforms par aam |
| AI mock interview | Hai | Bahut bheed |
| Off-campus jobs + NOC workflow | Hai | NOC wala hissa mujhe kisi source mein nahi dikha |
| AI se leadership dashboard | Hai | Mujhe kisi source mein nahi dikha |
| NIRF/NAAC-style report | Hai (summary) | Kuch vendors dete hain |
| Resume builder / ATS check | **Nahi** | Kaafi vendors dete hain |
| Monthly readiness score | **Nahi** (planned: `READINESS_ASSESSMENT.md`) | Wishire, iamneo, AbhyaX |
| Mobile app / PWA | **Nahi** | RB Tech, Internshala |
| SMS / WhatsApp alerts | **Nahi** | Verify nahi kiya |
| Code plagiarism check | **Nahi** | Bade assessment tools mein aam maana jata hai |
| Hackathons / inter-college contests | **Nahi** | Unstop |
| Alumni mentorship | **Nahi** (sirf alumni report) | Kuch campus ERPs |

"Mujhe nahi dikha" ka matlab yeh nahi ki kisi ke paas nahi hai. Demo lekar confirm karo.

---

## 4. Abhi turant theek karne wali baatein

1. **Commit aur backup.** Code ground ka poora kaam sirf ek machine par hai.
2. **College portal mein ek galat line:** student drawer "resume builder" ka zikr karta hai, jo maujood nahi (`students/student-drawer.ts`).
3. **Purane version naam:** mock test editor "Java 21, Node 20" dikhata hai, Judge0 par Java 13 aur Node 12 hain. Node 12 aur Java 13 bahut purane hain, Judge0 images update karna sochiye.
4. **Judge0:** SSH tunnel ki jagah internal network. Load test mein 40 ek saath Submit par 40% ke aas-paas ko "busy" mila (default settings).
5. **Docs purane hain:** README abhi `V44` aur 58 tables batata hai, aur uski portal list mein CXO portal nahi hai.

---

## 5. No. 1 banne ka plan

### Phase 1 (0–3 mahine): bharosa
| Kaam | Kyun |
|---|---|
| Production setup: monitoring, backup, restore drill, error alerts | Placement ke din system girna matlab college gaya |
| Load test poore flows par (exam 500+ students) | Abhi sirf Code ground ka load test hua hai |
| Security audit, 2FA staff ke liye | Student data aur offer letters |
| **DPDP taiyari** | Rules November 2025 mein notify hue; mukhya zimmedariyan May 2027 se. Consent, data delete, breach process. Camera/mic data par khaas dhyan. Vakil se confirm karo |
| 3–5 pilot colleges, har hafte feedback | Asli zaroorat yahin pata chalegi |

### Phase 2 (3–9 mahine): natije
| Kaam | Kyun |
|---|---|
| **PROVEYU readiness score** (aptitude, coding, communication, interview) | Sabse bada gap. Aapke paas data pehle se hai (tests, code ground, AI interview); ise ek score mein jodo, TPO ko trend dikhao, drives mein eligibility banao |
| **Resume builder + ATS check** | Har student ko chahiye |
| Aptitude aur verbal practice bank, company-wise sets (TCS, Infosys, Wipro) | Placement tests ka bada hissa |
| Mobile (pehle PWA), push, e-mail ke saath WhatsApp | Student phone par hai |
| Weak students ke liye remedial plan | "Report" se "sudhaar" tak |

### Phase 3 (9–18 mahine): network
| Kaam | Kyun |
|---|---|
| Company marketplace: ek company kai colleges mein drive | Yeh asli moat hai. Jitne college, utni companies, aur ulta |
| Verified skill profile jo company dekh sake | Student platform chhodna nahi chahega |
| Inter-college contests aur hackathons | Muft prachar |
| Alumni mentorship aur referrals | Placement ke mauke badhte hain |
| ERP/SSO integration, public API | Bade institutions ke liye zaroori |
| Plagiarism aur contest mode | Ranking par company tabhi bharosa karegi |

### Naapne ke number
- College: kitne drive platform par chale, TPO ke kitne ghante bache
- Student: weekly active %, readiness score mein sudhaar, interview-se-offer %
- Business: pilot se paid, renewal %, har college par companies

### Kya mat karo
- 20 naye features ek saath. Phase 1 pura kiye bina aage mat badho.
- LMS ya poora college ERP banana. Placement mein sabse achha banna zyada kaam aayega.
- Public site par woh likhna jo abhi bana nahi.

---

## 6. Sources (vendor claims; independently verify nahi)
- [PlacementPilot comparison](https://placementpilot.ai/blog/best-placement-management-software-india)
- [InCruiter campus placement](https://incruiter.com/solutions/campus-placement)
- [Wishire for colleges](https://wishire.in/for-colleges)
- [RB Tech placement software](https://www.rbtechservices.in/placement-management-software)
- [PrepInsta Optimus press coverage](https://indiaeducationdiary.in/prepinsta-transforms-over-100-colleges-with-its-placement-automation-software-optimus-digitising-3-5-lakh-assessments/)
- [iamneo for colleges](https://iamneo.ai/ai-assessment-platform-for-colleges/)
- [CodeTantra review](https://greatify.ai/blog-posts/codetantra-online-exam-platform-review)
- [Coding assessment tools comparison (HackerRank)](https://www.hackerrank.com/writing/best-coding-assessment-tools)
- [InterviewEra](https://interviewera.com/), [AbhyaX](https://edu.abhyax.co.in/), [HireEdge](https://www.hireedge.in/), [PrepFree](https://www.prepfree.in/)
- [Unstop campus hiring](https://unstop.com/blog/top-campus-hiring-platforms)
- [DPDP Rules notification (Bar and Bench)](https://www.barandbench.com/view-point/meity-notifies-final-digital-personal-data-protection-rules-2025), [timeline (India Briefing)](https://www.india-briefing.com/news/dpdp-rules-2025-india-data-protection-law-compliance-40769.html)
