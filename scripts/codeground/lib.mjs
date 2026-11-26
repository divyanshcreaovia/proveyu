// Builds the starter programs of a Code ground problem in JavaScript, Python, Java and C++ from one input description, so every
// language reads stdin and prints the answer the same way. Harnesses must run on what Judge0 runs:
// Node 12 (no ?. / ??), Python 3.8, Java 13 (no records / text blocks / switch expressions), GCC 9 in C++14 mode.

export const LANGUAGES = ['javascript', 'python', 'java', 'cpp'];

const TEXT = {
  int: 'an integer', str: 'a string (may contain spaces)', ints: 'integers separated by spaces (may be empty)', words: 'words separated by spaces',
  rows: 'a count k, then k lines of integers', tree: 'a binary tree in level order, "null" for a missing child',
};

export function parseArgs(spec) {
  return spec.split(',').map(s => s.trim()).filter(Boolean).map(s => {
    const [name, type] = s.split(':');
    if (!(type in TEXT)) throw new Error('bad type ' + type);
    return { name, type };
  });
}

export function inputFormat(args) {
  return args.map(a => `${a.name}: ${TEXT[a.type]}`);
}

/** Parses one test input exactly like the harnesses do (used to call the reference solution in-process). */
export function parseInput(args, input) {
  const lines = input.replace(/\r/g, '').split('\n');
  let at = 0;
  const line = () => lines[at++] || '';
  const int = () => Number(line());
  const ints = () => line().trim().split(/\s+/).filter(Boolean).map(Number);
  const words = () => line().trim().split(/\s+/).filter(Boolean);
  const read = { int, str: line, ints, words, rows: () => Array.from({ length: int() }, ints), tree: () => words().map(w => (w === 'null' ? null : Number(w))) };
  return args.map(a => read[a.type]());
}

/** The answer type, from the reference solution's answers on every test: int, long, double, bool, string, or a 1D / 2D array of int or string. */
export function returnType(values) {
  const kinds = new Set();
  const nums = [], inner = [];
  let depth = 0;
  for (const v of values) {
    if (Array.isArray(v)) {
      kinds.add('array');
      for (const x of v) {
        if (Array.isArray(x)) { depth = 2; inner.push(...x); } else inner.push(x);
      }
    } else kinds.add(typeof v);
    if (typeof v === 'number') nums.push(v);
  }
  if (kinds.size !== 1) throw new Error('answers have mixed types: ' + Array.from(kinds).join(', '));
  const kind = Array.from(kinds)[0];
  const numType = list => list.some(x => !Number.isInteger(x)) ? 'double' : list.some(x => Math.abs(x) > 2147483647) ? 'long' : 'int';
  if (kind === 'boolean') return 'bool';
  if (kind === 'string') return 'string';
  if (kind === 'number') return numType(nums);
  if (kind !== 'array') throw new Error('unsupported answer type ' + kind);
  const elem = inner.some(x => typeof x === 'string') ? 'string' : 'int';
  if (elem === 'int' && numType(inner.filter(x => typeof x === 'number')) !== 'int') throw new Error('arrays of long/double are not supported');
  return elem + (depth === 2 ? '[][]' : '[]');
}

const FOOTER = 'Input is read and the answer is printed for you: no need to change below';

/** Printed by every harness just before the answer, so what the student prints while debugging is not taken as the answer. */
export const MARK = '##PROVEYU-ANSWER##';

/** The answer part of a program's output (what follows the last marker). */
export function answerOf(stdout) {
  const s = stdout.replace(/\r\n/g, '\n');
  const at = s.lastIndexOf(MARK + '\n');
  return at < 0 ? s : s.slice(at + MARK.length + 1);
}

// ---------------------------------------------------------------- JavaScript
function js(args) {
  const need = new Set(args.map(a => a.type));
  const R = {
    int: 'const readInt = () => Number(readLine());',
    ints: 'const readInts = () => readLine().trim().split(/\\s+/).filter(Boolean).map(Number);',
    words: 'const readWords = () => readLine().trim().split(/\\s+/).filter(Boolean);',
    rows: 'const readRows = () => Array.from({ length: readInt() }, readInts);',
    tree: "const readTree = () => readWords().map(w => (w === 'null' ? null : Number(w)));",
  };
  if (need.has('rows')) { need.add('int'); need.add('ints'); }
  if (need.has('tree')) need.add('words');
  const call = { int: 'readInt()', str: 'readLine()', ints: 'readInts()', words: 'readWords()', rows: 'readRows()', tree: 'readTree()' };
  return [
    "const lines = require('fs').readFileSync(0, 'utf8').replace(/\\r/g, '').split('\\n');",
    'let at = 0;',
    "const readLine = () => lines[at++] || '';",
    ...['int', 'ints', 'words', 'rows', 'tree'].filter(k => need.has(k)).map(k => R[k]),
    ...args.map(a => `const ${a.name} = ${call[a.type]};`),
    `const answer = solve(${args.map(a => a.name).join(', ')});`,
    `console.log('\\n${MARK}');`,
    "console.log(Array.isArray(answer) ? (answer.some(Array.isArray) ? answer.map(r => r.join(' ')).join('\\n') : answer.join(' ')) : String(answer));",
  ].join('\n');
}

function starterJs(args) {
  return `function solve(${args.map(a => a.name).join(', ')}) {\n  // Write your code here and return the answer\n  \n}\n\n// ----- ${FOOTER} -----\n${js(args)}\n`;
}

/** The reference program: the solution function (renamed to solve) with the JavaScript harness. */
export function program(args, fn) {
  const src = fn.toString();
  return `const solve = ${src.startsWith('function') || src.startsWith('(') || /^\w+ =>/.test(src) ? src : 'function ' + src};\n${js(args)}\n`;
}

// ---------------------------------------------------------------- Python
function starterPy(args) {
  const call = { int: '_int()', str: '_line()', ints: '_ints()', words: '_words()', rows: '_rows()', tree: '_tree()' };
  return `def solve(${args.map(a => a.name).join(', ')}):
    # Write your code here and return the answer
    pass


# ----- ${FOOTER} -----
import sys
_lines = sys.stdin.read().replace('\\r', '').split('\\n')
_at = 0


def _line():
    global _at
    s = _lines[_at] if _at < len(_lines) else ''
    _at += 1
    return s


def _int():
    s = _line().strip()
    return int(s) if s else 0


def _ints():
    return [int(x) for x in _line().split()]


def _words():
    return _line().split()


def _rows():
    return [_ints() for _ in range(_int())]


def _tree():
    return [None if w == 'null' else int(w) for w in _words()]


def _fmt(v):
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, float):
        return str(int(v)) if v == int(v) and abs(v) < 1e15 else repr(v)
    return str(v)


def _show(v):
    if isinstance(v, (list, tuple)):
        if any(isinstance(x, (list, tuple)) for x in v):
            return '\\n'.join(' '.join(_fmt(x) for x in row) for row in v)
        return ' '.join(_fmt(x) for x in v)
    return _fmt(v)


${args.map(a => `${a.name} = ${call[a.type]}`).join('\n')}
_answer = solve(${args.map(a => a.name).join(', ')})
print('\\n${MARK}')
print(_show(_answer))
`;
}

// ---------------------------------------------------------------- Java
const JAVA_ARG = { int: 'int', str: 'String', ints: 'int[]', words: 'String[]', rows: 'int[][]', tree: 'Integer[]' };
const JAVA_RET = { int: 'int', long: 'long', double: 'double', bool: 'boolean', string: 'String', 'int[]': 'int[]', 'string[]': 'String[]', 'int[][]': 'int[][]', 'string[][]': 'String[][]' };
const JAVA_ZERO = { int: '0', long: '0', double: '0', bool: 'false', string: '""', 'int[]': 'new int[0]', 'string[]': 'new String[0]', 'int[][]': 'new int[0][]', 'string[][]': 'new String[0][]' };

function starterJava(args, ret) {
  const call = { int: 'readInt()', str: 'readLine()', ints: 'readInts()', words: 'readWords()', rows: 'readRows()', tree: 'readTree()' };
  return `import java.util.*;
import java.io.*;

public class Main {

    static ${JAVA_RET[ret]} solve(${args.map(a => `${JAVA_ARG[a.type]} ${a.name}`).join(', ')}) {
        // Write your code here and return the answer
        return ${JAVA_ZERO[ret]};
    }

    // ----- ${FOOTER} -----
    static List<String> lines = new ArrayList<>();
    static int at = 0;

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (String l = in.readLine(); l != null; l = in.readLine()) lines.add(l);
${args.map(a => `        ${JAVA_ARG[a.type]} ${a.name} = ${call[a.type]};`).join('\n')}
        ${JAVA_RET[ret]} output_ = solve(${args.map(a => a.name).join(', ')});
        System.out.println("\\n${MARK}");
        System.out.println(show(output_));
    }

    static String readLine() { return at < lines.size() ? lines.get(at++) : ""; }
    static int readInt() { String s = readLine().trim(); return s.isEmpty() ? 0 : Integer.parseInt(s); }
    static String[] readWords() { String s = readLine().trim(); return s.isEmpty() ? new String[0] : s.split("\\\\s+"); }
    static int[] readInts() { String[] w = readWords(); int[] a = new int[w.length]; for (int i = 0; i < w.length; i++) a[i] = Integer.parseInt(w[i]); return a; }
    static int[][] readRows() { int k = readInt(); int[][] r = new int[k][]; for (int i = 0; i < k; i++) r[i] = readInts(); return r; }
    static Integer[] readTree() { String[] w = readWords(); Integer[] t = new Integer[w.length]; for (int i = 0; i < w.length; i++) t[i] = w[i].equals("null") ? null : Integer.valueOf(w[i]); return t; }

    static String show(int v) { return String.valueOf(v); }
    static String show(long v) { return String.valueOf(v); }
    static String show(boolean v) { return String.valueOf(v); }
    static String show(String v) { return String.valueOf(v); }
    static String show(double v) { return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString(); }
    static String show(int[] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? " " : "").append(v[i]); return b.toString(); }
    static String show(String[] v) { return String.join(" ", v); }
    static String show(int[][] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? "\\n" : "").append(show(v[i])); return b.toString(); }
    static String show(String[][] v) { StringBuilder b = new StringBuilder(); for (int i = 0; i < v.length; i++) b.append(i > 0 ? "\\n" : "").append(show(v[i])); return b.toString(); }
}
`;
}

// ---------------------------------------------------------------- C++
const CPP_ARG = { int: 'int', str: 'string', ints: 'vector<int>', words: 'vector<string>', rows: 'vector<vector<int>>', tree: 'vector<int>' };
const CPP_RET = { int: 'int', long: 'long long', double: 'double', bool: 'bool', string: 'string', 'int[]': 'vector<int>', 'string[]': 'vector<string>', 'int[][]': 'vector<vector<int>>', 'string[][]': 'vector<vector<string>>' };
const CPP_ZERO = { int: '0', long: '0', double: '0', bool: 'false', string: '""', 'int[]': '{}', 'string[]': '{}', 'int[][]': '{}', 'string[][]': '{}' };

function starterCpp(args, ret) {
  const call = { int: 'readInt()', str: 'readLine()', ints: 'readInts()', words: 'readWords()', rows: 'readRows()', tree: 'readTree()' };
  const tree = args.some(a => a.type === 'tree');
  return `#include <bits/stdc++.h>
using namespace std;
${tree ? '\nconst int NONE = INT_MIN; // a missing node in the tree\n' : ''}
${CPP_RET[ret]} solve(${args.map(a => `${CPP_ARG[a.type]} ${a.name}`).join(', ')}) {
    // Write your code here and return the answer
    return ${CPP_ZERO[ret]};
}

// ----- ${FOOTER} -----
vector<string> LINES;
size_t AT = 0;
string readLine() { return AT < LINES.size() ? LINES[AT++] : string(); }
int readInt() { stringstream s(readLine()); int v = 0; s >> v; return v; }
vector<string> readWords() { stringstream s(readLine()); vector<string> v; string w; while (s >> w) v.push_back(w); return v; }
vector<int> readInts() { vector<int> v; for (const string& w : readWords()) v.push_back(stoi(w)); return v; }
vector<vector<int>> readRows() { int k = readInt(); vector<vector<int>> r; for (int i = 0; i < k; i++) r.push_back(readInts()); return r; }
vector<int> readTree() { vector<int> t; for (const string& w : readWords()) t.push_back(w == "null" ? INT_MIN : stoi(w)); return t; }

string show(int v) { return to_string(v); }
string show(long long v) { return to_string(v); }
string show(bool v) { return v ? "true" : "false"; }
string show(const string& v) { return v; }
string show(double v) { ostringstream o; o << setprecision(15) << v; return o.str(); }
template <class T> string show(const vector<T>& v) {
    string out;
    for (size_t i = 0; i < v.size(); i++) out += (i ? " " : "") + show(v[i]);
    return out;
}
template <class T> string show(const vector<vector<T>>& v) {
    string out;
    for (size_t i = 0; i < v.size(); i++) out += (i ? "\\n" : "") + show(v[i]);
    return out;
}

int main() {
    for (string l; getline(cin, l);) { if (!l.empty() && l.back() == '\\r') l.pop_back(); LINES.push_back(l); }
${args.map(a => `    ${CPP_ARG[a.type]} ${a.name} = ${call[a.type]};`).join('\n')}
    ${CPP_RET[ret]} output_ = solve(${args.map(a => a.name).join(', ')});
    fflush(stdout); // what you printed with printf comes first
    cout << "\\n${MARK}\\n" << show(output_) << endl;
    return 0;
}
`;
}

export const EXT = { javascript: 'js', python: 'py', java: 'java', cpp: 'cpp' };

export function starters(args, ret) {
  return { javascript: starterJs(args), python: starterPy(args), java: starterJava(args, ret), cpp: starterCpp(args, ret) };
}

/** Puts a solution in place of the starter's empty solve(). */
export function withSolution(language, starter, solution) {
  const lines = starter.split('\n');
  const i = lines.findIndex(l => l.includes('Write your code here'));
  const end = language === 'python' ? i + 1 : language === 'javascript' ? i + 2 : i + 2;
  const indent = language === 'java' ? '    ' : '';
  const body = solution.replace(/\s+$/, '').split('\n').map(l => (l ? indent + l : l));
  return [...lines.slice(0, i - 1), ...body, ...lines.slice(end + 1)].join('\n');
}

/** One problem. tests: [stdin, expected output]; the first one is the example shown with the statement. */
export function q(key, title, difficulty, args, statement, hint, solution, tests, note) {
  return { key, title, difficulty: { E: 'EASY', M: 'MEDIUM', H: 'HARD' }[difficulty], args: parseArgs(args), statement, hint, solution, tests, note };
}

/**
 * One slide of a learning guide. tag: what kind of slide (Why it matters, Real-world use, Core idea, Syntax, Pattern, Complexity,
 * Common mistakes, Going further). code: one snippet per language { javascript, python, java, cpp } (optional).
 */
export const slide = (tag, title, points, code) => ({ tag, title, points, code: code || null });
