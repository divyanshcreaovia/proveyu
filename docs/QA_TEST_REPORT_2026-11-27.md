# ProveYu end-to-end test report (27 Nov 2026)

**Kahan test hua:** alag throwaway MySQL aur doosra backend (:8081). Asli database aur data ko nahi chheda. Pehle round mein sirf testing; uske baad bugs fix kiye gaye (section 0).

## 0. Fix ke baad ka status

**Neeche ke saare 16 bugs theek ho gaye hain** (section 1 se 4 fix se pehle ka haal batate hain). Koi database migration nahi hai. Chalu backend ko dobara build aur restart karna zaroori hai.

### Dobara test (fixed code, alag throwaway database)

| Test | Nateeja |
|---|---|
| Har bug ka check (bug gaya aur normal kaam chal raha hai) | 122/122 |
| Repo ke 14 e2e suites | Sab pass. Do AI interview suites pehli baar AI service ki limit se fail hue, dobara 113/113 aur 72/72 |
| Permission matrix (2,672 requests) | Server error 0 (pehle 16 endpoints par 500); koi galat role success nahi |
| College isolation (leak scan + 422 attacks) | Koi leak nahi; B aur C ka data nahi badla |
| Login, token, takeover | 86 pass, 1 expected (code asli owner ke e-mail par jata hai) |
| Negative aur edge cases | 165 pass; 2 flag sahi behaviour (bina applicant wali drive draft ya delete ho sakti hai) |
| Race conditions | 10 pass; 1 flag sahi behaviour (payment approve repeat par "success", credit ek hi baar) |
| Browser: login matrix aur 132 pages | 333/333 |
| Browser actions: college aur student / admin aur recruiter | 27/27 / 77/77 |
| Unit tests | Backend 246/246, frontend 43/43 |

### Behaviour jo ab alag hai

- Suspended college: student, recruiter, chat aur CXO share link band; support ticket khula.
- Recruiter search: naam ya adhoore e-mail se sirf apne college ke recruiter; naya recruiter poore e-mail se milta hai.
- Applicants wali published drive: pehle cancel, phir delete. Live drive wali company delete nahi hoti.
- Offer ka jawab, interview booking: ek saath requests mein ek hi jeetti hai, baaki ko 409.
- Refuse hota hai: beete samay ka interview, offer date se pehle joining date, khali audience wala announcement, doosre college ke student ka e-mail, kamzor password, dobara delete, restore ya withdraw.
- CSV mein formula jaisi cell ke aage `'` lagta hai.

### Chhoda gaya

- Admin recruiter ke write API par galat body bheje to 403 ke bajaye 400 milta hai (sahi body par 403). Koi asar nahi.
- Password reset aur student sign-up screen sirf 8 akshar jaanchte hain; letters aur numbers ka niyam server batata hai.
- Database mein pehle se pade galat records (jaise delete company ki live drive) saaf nahi kiye. Aisi drive par nayi application ab nahi hoti.

## 1. Nateeja (fix se pehle)

| Sawaal | Jawab |
|---|---|
| Kya koi doosre portal mein login kar sakta hai? | **Nahi.** 7 account types x 5 login pages, sab sahi; doosre portal ke URL bhi redirect |
| Kya ek college doosre college ka data dekh ya badal sakta hai? | **Nahi**, ek jagah chhodkar: recruiter search (bug 4) |
| Kya account hijack ho sakta hai (invite/signup se)? | **Nahi** |
| Kya sab kuch perfect hai? | **Nahi.** Neeche bugs hain, 3 gambhir |

## 2. Kya-kya chala

| Test | Nateeja |
|---|---|
| Har endpoint x 8 users (335 endpoints, 2,672 requests) | Kisi galat role ko success nahi mila; anonymous har jagah block |
| Repo ke 14 e2e suites | 856 checks pass (pehli fails meri test setup ki thi; theek karke dobara chalaya) |
| 3 naye colleges: data leak scan (2,466 requests) | 1 leak: recruiter search |
| College A se B aur C par attack (422 requests) | B aur C ka data bilkul nahi badla |
| Login, token, session, takeover | 82 pass, 5 flag (bug 2, 8; ek expected behaviour) |
| Negative aur edge cases | 153 pass, 15 flag (neeche) |
| Browser: login matrix, 132 pages, 3 colleges | 333/333 pass, koi console ya API error nahi |
| Rate limit | 60 galat passwords ke baad 429; nakli IP header se bypass nahi |
| Unit tests | Backend 246/246, frontend 43/43 |

## 3. Bugs

### Gambhir

| # | Bug | Kaise dohraayein | Asar | Sujhaav |
|---|---|---|---|---|
| 1 | Offer par ek saath Accept aur Decline: **sab requests success** | 8 parallel `POST /college/student/offers/{id}/respond`, 3 baar dohraya | Student ko "accepted" dikh sakta hai jabki record "declined" ho | Offer row lock karke status dobara check |
| 2 | **Suspended college** ke student aur recruiter chalte rehte hain | Admin college suspend kare; student application withdraw kare, recruiter applicants dekhe aur status badle: sab 200 | Suspend ka poora asar nahi. TPO aur CXO sahi block hote hain | Student aur recruiter side par bhi college status check |
| 3 | Offer respond mein khali ya galat body = **DECLINED** | `{}` ya `{"acept":true}` bhejo | Offer galti se decline. UI sahi body bhejta hai, API nahi rokti | `accept` field ko required karo |

### Medium

| # | Bug | Detail |
|---|---|---|
| 4 | Recruiter search sab colleges ke recruiters dikhata hai | TPO/coordinator `GET /college/companies/recruiters?q=co` se doosre colleges ke recruiters ke naam aur e-mail dekh sakte hain. Link karne par nayi drive access nahi milti. Tay karo ki yeh jaan-boojhkar hai ya nahi |
| 5 | Interview slot duplicate | Ek saath 5 requests par same student, same time ke 5 slots ban gaye (database mein confirm) |
| 6 | Drive aur company delete par rok nahi | Applicants wali published drive DRAFT ho jaati hai ya delete; student ko application "APPLIED" dikhta rehta hai. Company delete karne par uski published drive chalu rehti hai aur nayi application bhi leti hai |
| 7 | CSV export mein formula injection | Student ka naam `=HYPERLINK(...)` ho to CSV mein waisa hi jaata hai; Excel mein formula chal sakta hai. Sirf students CSV jaancha |
| 8 | Kamzor password kuch jagah chal jaata hai | `12345678` / `aaaaaaaa`: password reset aur `/auth/register` accept karte hain; baaki jagah reject |

### Chhote

| # | Bug |
|---|---|
| 9 | Exam extra time API khali body `{}` par 500 |
| 10 | File upload endpoints JSON content-type par 500 dete hain (415 hona chahiye) |
| 11 | Ek student ka offer 6 baar ek saath banane par 1 bana, 5 ko 500 (database deadlock) |
| 12 | Validation chhoot: e-mail `a@b`, 1000 skills, joining date offer date se pehle, beete samay ka interview, 0 logon ko announcement |
| 13 | 100,000 akshar ka chat message: galat error ("conflicts with existing data") |
| 14 | Delete, restore aur withdraw dobara karne par bhi "success" |
| 15 | College doosre college ke student ke e-mail se apna student record bana sakta hai. Us student par koi asar nahi dikha |
| 16 | Kuch write endpoints par viewer ko permission error (403) se pehle validation error (400) milta hai. Sahi data par 403 hi milta hai |

## 4. Test cases (positive, negative, edge)

| Area | Positive | Negative / edge | Fail |
|---|---|---|---|
| Login aur session | Sahi login, case alag e-mail, logout, reset | Galat password, SQL text, 1 lakh akshar password, forged/alg-none token, logout ke baad token, block, revoke, role ghatana, OTP 5 galat, code dobara | 8 |
| College register, team | Register, verify, invite, accept | Maujood e-mail, TPO khud ko revoke/demote, doosre college ki branch | - |
| Students | Add, delete, restore, import template, export | Duplicate roll/e-mail, galat CGPA/batch/DOB/phone, hindi/emoji/script naam, khali/binary/exe import, galat paging aur sort | 7, 12, 14 |
| Companies, drives | Create, publish, clone | Duplicate naam, negative CTC, beeti deadline, galat status jump, delete rules | 6 |
| Applications, rounds | Apply, withdraw, re-apply | 8 baar ek saath apply, ineligible, start se pehle result | 14 |
| Interviews, offers | Schedule, offer, respond | Ulta samay, beeta samay, double booking, ek saath jawab, policy limit | 1, 3, 5, 11, 12 |
| Documents, files | Hindi naam wali PDF | exe, html, svg, nakli PDF, 11 MB, 60 MB, path traversal | - |
| Chat, announcements | Bhejna, padhna | Khali, bahut bada, khud ko, student se student, doosre college ko | 12, 13 |
| Reports | 7 reports x CSV, XLSX, PDF | Galat type/format, galat schedule | - |
| Mock tests, coding, proctoring | 20 test ladder, 4 languages, ek saath run | Locked test, time khatam, tab switch limit | 9 |
| AI interview, UPI | Interview, report, purchase, approve | Dobara UTR, galat quantity, 8 baar approve (credit sirf ek baar), ek free credit se 5 start | - |
| Off-campus, NOC, support, pricing, CXO | Poore flows | Doosre student ki file, chhota message, doosre college ka dashboard | - |
| Admin | Saare pages, block, broadcast, settings | Khud ko block, maujood e-mail se CXO | - |

## 5. Jo test nahi hua

- Asli e-mail delivery (SMTP band tha) aur asli UPI payment.
- Aapka Judge0 server (docker code runner use hua), Careerjet job import (key khali), local AI fallback.
- Bade load par test (sirf chhote parallel requests).
- Browser forms ki client-side validation; negative cases API par chale.
- Is round mein phone layout aur dark mode.
- Har endpoint ka positive aur negative dono; permission check sab par hua, feature cases upar ki table tak.

AI interview aur CXO tests ne aapki Groq key use ki (thoda quota kharch hua).
