#!/usr/bin/env bash
# Runs every automated test of ProveYu in order and prints a pass/fail summary. See RUN_AND_TEST.md.
#
#   bash scripts/run_all_tests.sh                 # everything except the slow / one-shot suites
#   bash scripts/run_all_tests.sh --with-ladder   # also the 20-test mock test ladder (needs a student with no attempts)
#   bash scripts/run_all_tests.sh --skip-unit     # skip `mvn test`
#   bash scripts/run_all_tests.sh --check         # only check that every service is up
#   BASE_URL=http://localhost:8080 BACKEND_LOG=/path/backend.log bash scripts/run_all_tests.sh
#
# Needs the backend (dev profile, APP_DEV_EXPOSE_TOKENS=true), MySQL, Docker (code runner image) and the AI services running,
# demo data seeded (seed_demo.py, seed_student_portal.py, seed_drive_room_demo.py) and mock tests loaded. The script checks the
# ports first and stops if something is missing. The AI interview suites use Groq tokens.
set -uo pipefail
cd "$(dirname "$0")/.."

BASE_URL=${BASE_URL:-http://localhost:8080}
WITH_LADDER=0
SKIP_UNIT=0
CHECK_ONLY=0
for a in "$@"; do
  case "$a" in
    --with-ladder) WITH_LADDER=1 ;;
    --skip-unit) SKIP_UNIT=1 ;;
    --check) CHECK_ONLY=1 ;;
    *) echo "Unknown option $a"; exit 2 ;;
  esac
done

LOG_DIR=${LOG_DIR:-test-logs/$(date +%Y%m%d-%H%M%S)}
declare -a NAMES RESULTS

check_port() { ss -ltn 2>/dev/null | grep -q ":$1 " || { echo "  MISSING  $2 is not listening on port $1"; return 1; }; echo "  ok       $2 (:$1)"; }

echo "== Pre-flight"
ok=1
check_port 3306 "MySQL" || ok=0
check_port 8080 "Backend" || ok=0
check_port 11434 "Ollama (local LLM fallback)" || echo "           (AI falls back to it only when Groq fails)"
check_port 8178 "Whisper (local speech-to-text fallback)" || echo "           (needed only when Groq speech-to-text fails)"
LOCAL_AI=$(grep -E "^AI_LOCAL_MODELS_ENABLED=" Backend/.env 2>/dev/null | cut -d= -f2 | tr -d "'\" ")
if [ "${LOCAL_AI:-true}" = "false" ]; then
  echo "  info     AI_LOCAL_MODELS_ENABLED=false: Groq only, local AI services not needed"
  check_port 5002 "Piper (used by the test scripts to speak answers)" || ok=0
else
  check_port 5002 "Piper (voice)" || ok=0
fi
docker image inspect proveyu-code-runner:1 >/dev/null 2>&1 && echo "  ok       code runner image" || { echo "  MISSING  docker image proveyu-code-runner:1"; ok=0; }
curl -fs "$BASE_URL/actuator/health" | grep -q '"UP"' && echo "  ok       backend health" || { echo "  MISSING  $BASE_URL/actuator/health is not UP"; ok=0; }
if [ "$ok" = 0 ]; then echo "Start the missing services (RUN_AND_TEST.md, section 3) and run again."; exit 1; fi
if [ "$CHECK_ONLY" = 1 ]; then echo "All services are up."; exit 0; fi

mkdir -p "$LOG_DIR"

run() { # run <name> <command...>
  local name=$1; shift
  local log="$LOG_DIR/$(echo "$name" | tr ' /' '__').log"
  printf "== %-38s " "$name"
  local start=$SECONDS
  if "$@" >"$log" 2>&1; then RESULTS+=("PASS"); printf "PASS  (%ss)\n" $((SECONDS - start))
  else RESULTS+=("FAIL"); printf "FAIL  (%ss)  -> %s\n" $((SECONDS - start)) "$log"; grep -E "FAIL|Error|Traceback" "$log" | head -5 | sed 's/^/     /'; fi
  NAMES+=("$name")
}

if [ "$SKIP_UNIT" = 0 ]; then
  MVN=$(command -v mvn || ls -d ~/.m2/wrapper/dists/apache-maven-*/*/apache-maven-*/bin/mvn 2>/dev/null | head -1)
  run "Backend unit + integration" bash -c "cd Backend && '$MVN' -q test"
fi
run "Frontend build"                bash -c "cd Frontend && npx ng build --configuration development"
run "Smoke (placement flow)"        python3 scripts/smoke_test.py "$BASE_URL"
run "College + student portals"     python3 scripts/e2e_portal_test.py "$BASE_URL"
# the college mock test suite needs a second college: the one the smoke test just created
SMOKE_RUN=$(grep -o "(run [0-9a-f]*)" "$LOG_DIR/Smoke_(placement_flow).log" 2>/dev/null | head -1 | tr -d '()' | cut -d' ' -f2)
run "College mock tests"            env ${SMOKE_RUN:+OTHER_TPO=tpo@a$SMOKE_RUN.edu.in OTHER_STUDENT=asha$SMOKE_RUN@a$SMOKE_RUN.edu.in} \
  python3 scripts/e2e_college_prep_test.py "$BASE_URL"
run "Mock test + code runner"       python3 scripts/e2e_prep_test.py
# the ladder needs a student with no attempts; aditya.kulkarni3 is kept fresh for the concurrent suite
[ "$WITH_LADDER" = 1 ] && run "Mock test ladder (20 tests)" env LADDER_STUDENT="${LADDER_STUDENT:-ishaan.joshi10@demo-college.edu.in}" \
  python3 scripts/e2e_prep_ladder_test.py "$BASE_URL"
run "Concurrent students + UPI"     python3 scripts/e2e_concurrent_test.py "$BASE_URL"
run "AI interview (Groq)"           python3 scripts/e2e_interview_test.py "$BASE_URL"
sleep "${INTERVIEW_COOLDOWN:-60}"  # let Groq's per-minute token limit recover between the interview suites
run "Two AI interviews at once"     env BACKEND_LOG="${BACKEND_LOG:-}" python3 scripts/e2e_two_students_interview_test.py "$BASE_URL"
run "Three colleges, all portals"   python3 scripts/e2e_three_colleges_test.py "$BASE_URL"
RUN_ID=$(grep -o "(run [0-9a-f]*)" "$LOG_DIR/Three_colleges,_all_portals.log" 2>/dev/null | head -1 | tr -d '()' | cut -d' ' -f2)
if [ -n "$RUN_ID" ]; then run "Help & support" python3 scripts/e2e_support_test.py "$RUN_ID" "$BASE_URL"
else NAMES+=("Help & support"); RESULTS+=("SKIP"); echo "== Help & support                       SKIP  (no run id from the three-colleges test)"; fi
run "Off-campus jobs, NOC, import"  python3 scripts/e2e_off_campus_test.py "$BASE_URL"

echo
echo "== Summary (logs in $LOG_DIR)"
fails=0
for i in "${!NAMES[@]}"; do
  printf "  %-5s %s\n" "${RESULTS[$i]}" "${NAMES[$i]}"
  [ "${RESULTS[$i]}" = "FAIL" ] && fails=$((fails + 1))
done
echo "  $((${#NAMES[@]} - fails)) of ${#NAMES[@]} suites passed"
exit $(( fails > 0 ))
