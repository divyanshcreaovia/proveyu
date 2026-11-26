# Code ground

Code ground is the students' coding practice area (`/student/code-ground`): a study plan of topics, each with a learning guide and
practice problems that are checked on the server, a leaderboard, and a free compiler. The platform admin manages topics, problems and
the problem of the day from **Admin → Code ground**; each college's placement cell sees its students' ranking under **College → Code ground**.

## What a student gets

| Area | What it does |
|---|---|
| Study plan | 10 topics (Basics, Arrays, Strings, Hashing, Stack and Queue, Recursion, Searching and Sorting, Dynamic Programming, Maths and Bits, Lists, Trees and Graphs), 74 problems from easy to hard, a learning guide per topic |
| Learning guide | 8 short lessons per topic: why it matters, real-world use, core idea, syntax, patterns, complexity, common mistakes, going further. Code examples in JavaScript, Python, Java and C++ |
| Problem workspace | Statement, one example, language picker (JavaScript, Python, Java, C++), editor, hint, Reset code, **Run** (one input: the example or the student's own, nothing is scored) and **Submit** (all visible tests, then hidden tests) |
| Help while coding | What the code prints is shown separately from the answer; compile and runtime errors; the input / print code under `solve()` is folded and locked; code is saved on the server (any computer) |
| Rules in the editor | Copy, cut, paste and drag-and-drop are switched off; every submission is saved with its code and time (a notice says so) |
| Problem of the day | Same problem for every student; passing it that day gives its points again (Easy 10, Medium 20, Hard 40), once a day |
| Activity | One-year calendar, current and longest streak (a day counts when a new problem or the problem of the day is solved) |
| My submissions | Every submission with filters (day, result, language, difficulty, name, "not solved yet") and a summary; the last 20 per problem are kept |
| Leaderboard | This week (from Monday 00:00 India time) or all time; whole college, own branch or own batch; top 3, top 10 and the student's own rank. Points: Easy 10, Medium 20, Hard 40, guide 5, plus problem-of-the-day bonuses, each counted once. Equal points: whoever got there first is ahead |
| Free compiler | A plain compiler tab (nothing scored) |

A problem is solved only when the **server** has run the code on every visible and hidden test and all matched; the browser cannot
mark anything solved. Hidden tests show only pass or fail (never their input, answer or error).

## Where the content lives

* **Built-in problems and guides are files, not database rows:** `Backend/src/main/resources/codeground/*.json` (10 files). They are
  packed into the jar and read when the backend starts, so a new server needs no import.
* They are **generated** from `scripts/codeground/` (do not edit the JSON by hand):
  `topics/*.mjs` (problems, hints, visible tests, JavaScript reference solution), `hidden.mjs` (three hidden inputs per problem),
  `guides/*.mjs` (lessons), `solutions/` (Python, Java and C++ solutions used only for testing), `lib.mjs` (builds the starter
  program of every language: an empty `solve()` plus the code that reads the input and prints the answer).
* `node scripts/codeground/build.mjs` (needs node, python3, javac, g++) runs every reference solution against its tests, compiles and
  runs every starter in all four languages on every test, runs the typed solutions, and only then writes the JSON.
  `--quick` checks only the JavaScript reference solutions.
* Starters for problems added by an admin are made by `CodeGroundStarters.java`, which must give the same text as `lib.mjs`
  (`CodeGroundAdminIT` compares them for every built-in problem).
* Judge versions the starters must run on: Node 12, Python 3.8, Java 13, GCC 9 (C++14).

## Admin portal (platform ADMIN)

* **Study plan and questions:** add a topic (with an optional learning guide as JSON), add a question with a form, or upload a JSON file
  (the portal has a sample download; the same sample is `Backend/src/main/resources/codeground-sample/sample.json`).
  Built-in problems can be hidden or shown, not edited.
* A saved question is a **draft**. **Publish** runs the admin's reference solution on the judge against every visible and hidden test
  and also builds and runs the starters of the other three languages; it refuses on any mismatch and says which test failed. Students
  see only published versions. A published problem that students have worked on cannot be deleted, only unpublished (points stay).
* **Problem of the day:** choose a published problem for any coming day. A day left on automatic gets one picked the first time anyone asks,
  and keeps it (adding problems never changes today's problem). Changing today's problem after some students earned the bonus is allowed;
  they keep it.
* Every change is written to the admin audit log.

## Data (migrations V45 to V49)

| Table | Holds |
|---|---|
| `code_ground_progress` | Per student: problems solved or tried, guides read |
| `code_ground_drafts` | The code a student is writing, per problem and language |
| `code_ground_submissions` | Every Submit: code, result, tests passed (last 20 per problem and student) |
| `code_ground_daily` | Problem-of-the-day bonuses given |
| `code_ground_topics`, `code_ground_problems`, `code_ground_hidden` | Admin-added topics and problems (draft and published copies); built-in problems the admin hid |
| `code_ground_schedule` | The problem of each day |

Drafts and submissions hold students' code. Leaderboard points are worked out from these rows (nothing extra is stored), and a problem
that is hidden or unpublished later still counts for points already earned.

## Settings and capacity (Judge0)

Code runs on Judge0 CE (see `README.md`). Settings that matter for a class using Code ground:

| Setting | Default | Suggested for a class | Why |
|---|---|---|---|
| `JUDGE0_MAX_PARALLEL` | 4 | 10 (if the Judge0 VM has the CPUs) | runs the backend sends to Judge0 at once |
| `CODE_QUEUE_WAIT_MS` | 20000 | 90000 | how long a run waits for a free slot before "judge busy" (shared with mock tests) |
| `JUDGE0_POLL_TIMEOUT_MS` | 60000 | 60000 | how long the backend waits for a result |
| `CODE_RUN_RATE_LIMIT_SECONDS` | 5 | 5 | gap between two Runs, or two Submits, of one student |
| `code.ground.leaderboard-cache-seconds` | 20 | 20 | the board is rebuilt at most this often per college, and at once when one of its students earns points |

Measured on one test machine with a 10-CPU Judge0 VM (short program, one student account, bursts of a few minutes, not a long run):

* Judge0 handles about 10 jobs a second for Python and JavaScript and 3.5 for Java and C++. A Submit is 8 jobs (5 visible, 3 hidden); Run is 1.
* Default settings: 20 students pressing Submit together all get their result (p95 31 s); with 40, about 60 % do and the rest see "judge busy".
* `JUDGE0_MAX_PARALLEL=10` with a 90 s wait: 60 of 60 got a correct result (the last after 88 s). 60 Runs together finish in 14 s.
* Pages (overview, problem, leaderboard, submissions) answered 200 users at once with p95 under 0.3 s.

To go further: Judge0 workers (`COUNT` in `judge0.conf`), or compile once per submission instead of once per test.

## Tests

* `Backend`: `CodeGroundIT` (content, run, submit, hidden tests, daily, submissions, leaderboard), `CodeGroundAdminIT` (starters, upload,
  publish, hide, schedule), `SecurityIsolationIT` (three colleges: role walls, data isolation, chat, sign-in).
* `node scripts/codeground/e2e.mjs` against a running backend (test database!) with a student login: sends every reference solution
  through the API to the real judge, plus the Python, Java and C++ solutions, wrong, broken and endless programs.

## Known limits

* Copy-paste is blocked in the browser only; developer tools, extensions or a phone can get around it. There is no plagiarism check
  yet and no typing or tab-switch tracking.
* The free compiler and the mock-test editor keep their own simple behaviour.
* The "sign-in stops after a password change" list and the rate limits are in memory per backend instance.
