// End-to-end check of Code ground against a running backend and the real code judge: every problem's reference solution is sent
// through the API (as a student's JavaScript program) and must pass all tests; the Python, Java and C++ solutions in solutions/ must
// pass too (in the judge's own language versions), and wrong and broken programs must not, in every language. Use a test database.
//   BASE=http://localhost:8090 STUDENT_EMAIL=... STUDENT_PASSWORD=... node scripts/codeground/e2e.mjs
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { program, withSolution, LANGUAGES, EXT } from './lib.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE || 'http://localhost:8090';
let token = null, passed = 0, failed = 0;
const check = (name, ok, detail) => { ok ? passed++ : failed++; if (!ok) console.log('FAIL', name, detail ? JSON.stringify(detail).slice(0, 500) : ''); };
async function api(method, path, body) {
  for (let i = 0; ; i++) {
    const r = await fetch(BASE + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined });
    const j = await r.json().catch(() => ({}));
    if ((r.status === 429 || r.status === 503) && i < 8) { await new Promise(s => setTimeout(s, 3000)); continue; }
    return { status: r.status, data: j.data, message: j.message };
  }
}

const login = await api('POST', '/api/v1/auth/login', { email: process.env.STUDENT_EMAIL, password: process.env.STUDENT_PASSWORD });
if (login.status !== 200) throw new Error('student sign-in failed: ' + login.message);
token = login.data.token;
const C = '/api/v1/college/student/code-ground';
const topics = [];
const built = {};
for (const f of readdirSync(join(here, 'topics')).sort()) {
  topics.push((await import(join(here, 'topics', f))).default);
  for (const p of JSON.parse(readFileSync(join(here, '../../Backend/src/main/resources/codeground', f.replace('.mjs', '.json')), 'utf8')).problems) built[p.key] = p;
}

const first = (await api('GET', C)).data;
check('overview offers the four languages', first.languages.map(l => l.key).join() === LANGUAGES.join(), first.languages);
check('overview lists every topic in order', first.topics.map(t => t.key).join() === topics.map(t => t.key).join(), first.topics.map(t => t.key));

const t0 = Date.now();
for (const t of topics) {
  const guide = (await api('GET', `${C}/topics/${t.key}/guide`)).data;
  check(`${t.key} guide`, guide && guide.slides.length === t.guide.length);
  check(`${t.key} guide complete`, (await api('POST', `${C}/topics/${t.key}/guide/complete`)).status === 200);
  for (const p of t.problems) {
    const view = (await api('GET', `${C}/problems/${p.key}`)).data;
    check(`${p.key} view`, view && JSON.stringify(view.starters) === JSON.stringify(built[p.key].starters) && view.example.input === p.tests[0][0]);
    const r = await api('POST', `${C}/problems/${p.key}/submit`, { language: 'javascript', code: program(p.args, p.solution) });
    const bad = (r.data?.cases || []).filter(c => !c.passed).slice(0, 1);
    check(`${p.key} reference solution passes all ${p.tests.length} tests`, r.status === 200 && r.data.allPassed && r.data.solved, { status: r.status, message: r.message, compile: r.data?.compileError, bad });
  }
  process.stdout.write(`  ${t.key}: done (${Math.round((Date.now() - t0) / 1000)} s)\n`);
}

// the solutions written in Python, Java and C++ pass on the judge
let others = 0;
for (const t of topics) for (const p of t.problems) for (const language of LANGUAGES.filter(l => l !== 'javascript')) {
  const file = join(here, 'solutions', `${p.key}.${EXT[language]}`);
  if (!existsSync(file)) continue;
  others++;
  const code = withSolution(language, built[p.key].starters[language], readFileSync(file, 'utf8'));
  const r = await api('POST', `${C}/problems/${p.key}/submit`, { language, code });
  const bad = (r.data?.cases || []).filter(c => !c.passed).slice(0, 1);
  check(`${p.key} ${language} solution passes`, r.status === 200 && r.data.allPassed, { status: r.status, message: r.message, compile: r.data?.compileError, bad });
}
console.log(`  ${others} Python / Java / C++ solutions checked (${Math.round((Date.now() - t0) / 1000)} s)`);

// in every language: the untouched starter, a wrong answer, a syntax error and an endless loop must not pass
const p = topics[1].problems[0];
const starterOf = l => built[p.key].starters[l];
const broken = {
  javascript: { wrong: program(p.args, function f() { return -999; }), syntax: 'function solve( {', loop: 'while (true) {}' },
  python: { wrong: withSolution('python', starterOf('python'), 'def solve(nums):\n    return -999'), syntax: 'def solve(:\n', loop: 'while True:\n    pass\n' },
  java: { wrong: withSolution('java', starterOf('java'), 'static int solve(int[] nums) {\n    return -999;\n}'), syntax: 'public class Main { void x( }',
    loop: 'public class Main { public static void main(String[] a) { while (true) {} } }' },
  cpp: { wrong: withSolution('cpp', starterOf('cpp'), 'int solve(vector<int> nums) {\n    return -999;\n}'), syntax: 'int main( {', loop: 'int main() { while (true) {} }' },
};
for (const language of LANGUAGES) {
  const b = broken[language];
  const compiled = language === 'java' || language === 'cpp';
  const cases = [['untouched starter', starterOf(language), r => r.cases.every(x => !x.passed)],
    ['wrong answer', b.wrong, r => r.cases.every(x => !x.passed && x.actual.trim() === '-999')],
    ['syntax error', b.syntax, r => compiled ? !!r.compileError : r.cases.every(x => !x.passed && x.error)],
    ['endless loop', b.loop, r => r.cases.every(x => !x.passed && x.timedOut)]];
  for (const [name, code, ok] of cases) {
    const r = await api('POST', `${C}/problems/${p.key}/submit`, { language, code });
    check(`${language} ${name} is not a pass`, r.status === 200 && !r.data.allPassed && (r.data.compileError || r.data.cases.length === p.tests.length) && ok(r.data),
      r.data?.compileError || r.data?.cases?.[0] || r.message);
  }
}
const unknown = await api('POST', `${C}/problems/${p.key}/submit`, { language: 'ruby', code: 'puts 1' });
check('an unknown language is refused', unknown.status === 400, unknown);

const after = (await api('GET', C)).data;
check('every topic is fully done', after.topics.every(t => t.done === t.total && t.guideRead), after.topics.map(t => `${t.key} ${t.done}/${t.total}`));
console.log(`\n${passed}/${passed + failed} checks passed in ${Math.round((Date.now() - t0) / 1000)} s`);
process.exit(failed ? 1 : 0);
