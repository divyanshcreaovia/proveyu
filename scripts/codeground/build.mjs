// Writes Backend/src/main/resources/codeground/*.json from the topic sources here, after checking every test:
//  1. the reference solution (JavaScript) is run with the same program a student gets; its output must equal the expected output;
//  2. the starter in every language (JavaScript, Python, Java, C++) must compile and run on every test input;
//  3. the solutions in solutions/<problem>.<py|java|cpp|js> are put into that language's starter and must pass every test.
//   node scripts/codeground/build.mjs            (needs node, python3, javac, g++)
//   node scripts/codeground/build.mjs --quick    (step 1 only)
import { execFile } from 'node:child_process';
import { readdirSync, writeFileSync, readFileSync, rmSync, mkdirSync, existsSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import HIDDEN from './hidden.mjs';
import { fileURLToPath } from 'node:url';
import { inputFormat, starters, program, parseInput, returnType, withSolution, answerOf, MARK, LANGUAGES, EXT } from './lib.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../Backend/src/main/resources/codeground');
const quick = process.argv.includes('--quick');
const work = mkdtempSync(join(tmpdir(), 'codeground-'));

const norm = s => s.replace(/\r\n/g, '\n').split('\n').map(l => l.replace(/\s+$/, '')).join('\n').replace(/\n+$/, '');
const order = { EASY: 0, MEDIUM: 1, HARD: 2 };

function exec(cmd, args, opts = {}) {
  return new Promise(resolve => {
    const child = execFile(cmd, args, { timeout: opts.timeout || 20000, cwd: opts.cwd, maxBuffer: 1 << 22 }, (err, stdout, stderr) =>
      resolve({ ok: !err, out: String(stdout), err: String(stderr || (err && err.message) || '') }));
    if (opts.input !== undefined) child.stdin.end(opts.input);
  });
}

async function pool(tasks, size) {
  const results = new Array(tasks.length);
  let next = 0;
  await Promise.all(Array.from({ length: size }, async () => {
    while (next < tasks.length) { const i = next++; results[i] = await tasks[i](); }
  }));
  return results;
}

/** Compiles (if needed) and returns a function that runs the program on one input. */
async function prepare(language, code, name) {
  const dir = join(work, name + '-' + language);
  mkdirSync(dir, { recursive: true });
  if (language === 'javascript') { writeFileSync(join(dir, 'main.js'), code); return { run: input => exec(process.execPath, ['main.js'], { cwd: dir, input }) }; }
  if (language === 'python') { writeFileSync(join(dir, 'main.py'), code); return { run: input => exec('python3', ['main.py'], { cwd: dir, input }) }; }
  if (language === 'java') {
    writeFileSync(join(dir, 'Main.java'), code);
    const c = await exec('javac', ['--release', '13', '-nowarn', '-Xlint:-options', 'Main.java'], { cwd: dir, timeout: 60000 });
    if (!c.ok) return { error: c.err };
    return { run: input => exec('java', ['-Xss64m', '-cp', '.', 'Main'], { cwd: dir, input }) };
  }
  writeFileSync(join(dir, 'main.cpp'), code);
  const c = await exec('g++', ['-std=gnu++14', '-O1', '-w', '-o', 'main', 'main.cpp'], { cwd: dir, timeout: 60000 });
  if (!c.ok) return { error: c.err };
  return { run: input => exec(join(dir, 'main'), [], { cwd: dir, input }) };
}

const keys = new Set();
const topics = [];
let problems = 0, tests = 0, failures = 0;
const fail = msg => { failures++; console.log(msg); };

for (const file of readdirSync(join(here, 'topics')).sort()) {
  const t = (await import(join(here, 'topics', file))).default;
  const list = [];
  for (const p of t.problems) {
    if (keys.has(p.key)) throw new Error('duplicate problem key ' + p.key);
    keys.add(p.key);
    const answers = p.tests.map(([input]) => p.solution(...parseInput(p.args, input)));
    let ret;
    try { ret = returnType(answers); } catch (e) { throw new Error(p.key + ': ' + e.message); }
    const code = program(p.args, p.solution);
    for (const [i, [input, expected]] of p.tests.entries()) {
      const r = await exec(process.execPath, ['-e', code], { input, timeout: 5000 });
      const got = r.ok ? answerOf(r.out) : 'ERROR: ' + r.err.slice(0, 300);
      tests++;
      if (norm(got) !== norm(expected)) fail(`MISMATCH ${p.key} test ${i + 1}\n  input:    ${JSON.stringify(input)}\n  expected: ${JSON.stringify(expected)}\n  got:      ${JSON.stringify(norm(got))}`);
    }
    // hidden tests: the expected output is what the reference solution prints
    const inputs = HIDDEN[p.key];
    if (!inputs || inputs.length < 3) throw new Error(p.key + ': needs 3 hidden tests in hidden.mjs');
    if (inputs.some(i => p.tests.some(([visible]) => visible === i))) throw new Error(p.key + ': a hidden test repeats a visible one');
    const all = returnType(answers.concat(inputs.map(i => p.solution(...parseInput(p.args, i)))));
    if (all !== ret) throw new Error(p.key + ': hidden answers need type ' + all + ', the starter has ' + ret);
    const hiddenTests = [];
    for (const input of inputs) {
      const r = await exec(process.execPath, ['-e', code], { input, timeout: 5000 });
      if (!r.ok || !norm(answerOf(r.out))) fail(`HIDDEN ${p.key} ${JSON.stringify(input)}: ${r.err.slice(0, 300) || 'empty output'}`);
      hiddenTests.push({ input, output: norm(answerOf(r.out)) });
    }
    problems++;
    list.push({ key: p.key, title: p.title, difficulty: p.difficulty, statement: p.statement, inputFormat: inputFormat(p.args), hint: p.hint,
      note: p.note || null, args: p.args, returnType: ret, starters: starters(p.args, ret), tests: p.tests.map(([input, output]) => ({ input, output })), hiddenTests });
  }
  if (list.some((p, i) => i > 0 && order[p.difficulty] < order[list[i - 1].difficulty])) throw new Error(t.key + ': problems are not ordered easy to hard');
  for (const s of t.guide) {
    if (!s.tag || !s.title || !Array.isArray(s.points)) throw new Error(t.key + ': bad guide slide ' + JSON.stringify(s.title));
    if (s.code && LANGUAGES.some(l => typeof s.code[l] !== 'string')) throw new Error(t.key + ': slide "' + s.title + '" needs code in every language');
  }
  topics.push({ file, data: { key: t.key, title: t.title, summary: t.summary, guide: t.guide, problems: list } });
}
console.log(`reference: ${problems} problems, ${tests} tests, ${failures} mismatches`);

if (!quick && !failures) {
  // Every starter compiles and runs on every input; the solutions written for other languages pass.
  const jobs = [];
  const solved = [];
  for (const { data } of topics) for (const p of data.problems) for (const language of LANGUAGES) {
    jobs.push({ p, language, code: p.starters[language], name: p.key, check: false });
    const sol = join(here, 'solutions', `${p.key}.${EXT[language]}`);
    if (existsSync(sol)) {
      jobs.push({ p, language, code: withSolution(language, p.starters[language], readFileSync(sol, 'utf8')), name: p.key + '-sol', check: true });
      solved.push(`${p.key}.${EXT[language]}`);
    }
  }
  let runs = 0;
  await pool(jobs.map(j => async () => {
    const prog = await prepare(j.language, j.code, j.name);
    if (prog.error) return fail(`COMPILE ${j.name} ${j.language}\n${prog.error.slice(0, 1500)}`);
    for (const [i, c] of j.p.tests.concat(j.p.hiddenTests).entries()) {
      const r = await prog.run(c.input);
      runs++;
      if (!r.ok) fail(`CRASH ${j.name} ${j.language} test ${i + 1}: ${r.err.slice(0, 600)}`);
      else if (j.check && (!r.out.includes(MARK) || norm(answerOf(r.out)) !== norm(c.output))) fail(`WRONG ${j.name} ${j.language} test ${i + 1}\n  expected: ${JSON.stringify(c.output)}\n  got:      ${JSON.stringify(norm(r.out))}`);
    }
  }), 8);
  console.log(`starters and solutions: ${jobs.length} programs (${solved.length} solutions), ${runs} runs, ${failures} failures`);
}
rmSync(work, { recursive: true, force: true });

if (!failures) {
  rmSync(out, { recursive: true, force: true });
  mkdirSync(out, { recursive: true });
  for (const { file, data } of topics) writeFileSync(join(out, file.replace('.mjs', '.json')), JSON.stringify(data, null, 1));
  console.log('written to ' + out);
}
process.exit(failures ? 1 : 0);
