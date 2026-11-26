import { q } from '../lib.mjs';
import guide from '../guides/recursion.mjs';

export default {
  key: 'recursion', title: 'Recursion and Backtracking', summary: 'Factorial, towers of Hanoi, subsets, permutations, N-Queens',
  guide,
  problems: [
    q('factorial', 'Factorial', 'E', 'n:int', 'Return n! for 0 <= n <= 17.', '0! is 1.', function f(n) { return n <= 1 ? 1 : n * f(n - 1); },
      [['5', '120'], ['0', '1'], ['1', '1'], ['10', '3628800'], ['17', '355687428096000']]),
    q('power', 'Power by squaring', 'E', 'x:int, n:int', 'Return x raised to n (n >= 0) using recursion in O(log n) steps.', 'x^n = (x^(n/2))², times x when n is odd.',
      function f(x, n) { if (n === 0) return 1; const half = f(x, Math.floor(n / 2)); return (n % 2 ? half * half * x : half * half) + 0; },
      [['2\n10', '1024'], ['3\n0', '1'], ['-2\n3', '-8'], ['5\n4', '625'], ['1\n1000', '1']]),
    q('hanoi', 'Towers of Hanoi moves', 'E', 'n:int', 'Move n discs from peg A to peg C using B. Return every move as "A C" on its own line (return an array of [from, to] pairs).', 'Move n-1 to the spare, move the big disc, move n-1 back on top.',
      n => { const out = []; const go = (k, a, c, b) => { if (k === 0) return; go(k - 1, a, b, c); out.push([a, c]); go(k - 1, b, c, a); }; go(n, 'A', 'C', 'B'); return out; },
      [['2', 'A B\nA C\nB C'], ['1', 'A C'], ['3', 'A C\nA B\nC B\nA C\nB A\nB C\nA C'], ['0', ''], ['4', 'A B\nA C\nB C\nA B\nC A\nC B\nA B\nA C\nB C\nB A\nC A\nB C\nA B\nA C\nB C']]),
    q('subset-sum-count', 'Subsets that reach a sum', 'M', 'nums:ints, target:int', 'Return how many subsets (by position) add up to target. The empty subset sums to 0. At most 20 numbers.', 'For each element: take it or skip it.',
      (nums, target) => { const go = (i, s) => i === nums.length ? (s === target ? 1 : 0) : go(i + 1, s) + go(i + 1, s + nums[i]); return go(0, 0); },
      [['1 2 3\n3', '2'], ['5\n0', '1'], ['1 1 1\n2', '3'], ['2 4 6\n5', '0'], ['3 1 2 4 -1\n3', '4']]),
    q('permutations', 'All permutations', 'M', 's:str', 'The string has distinct characters (at most 6). Return all permutations in sorted order, space separated.', 'Pick each unused character, recurse, then un-pick.',
      s => { const chars = s.split('').sort(), out = [], used = []; const go = p => { if (p.length === chars.length) { out.push(p); return; } for (let i = 0; i < chars.length; i++) if (!used[i]) { used[i] = true; go(p + chars[i]); used[i] = false; } }; go(''); return out; },
      [['abc', 'abc acb bac bca cab cba'], ['a', 'a'], ['ba', 'ab ba'], ['xyz', 'xyz xzy yxz yzx zxy zyx'], ['dcba', 'abcd abdc acbd acdb adbc adcb bacd badc bcad bcda bdac bdca cabd cadb cbad cbda cdab cdba dabc dacb dbac dbca dcab dcba']]),
    q('generate-parens', 'Generate parentheses', 'M', 'n:int', 'Return all well-formed strings of n pairs of parentheses, in the order produced by trying "(" before ")".', 'Add ")" only when there are more "(" than ")".',
      n => { const out = []; const go = (p, o, c) => { if (p.length === 2 * n) { out.push(p); return; } if (o < n) go(p + '(', o + 1, c); if (c < o) go(p + ')', o, c + 1); }; go('', 0, 0); return out; },
      [['3', '((())) (()()) (())() ()(()) ()()()'], ['1', '()'], ['2', '(()) ()()'], ['4', '(((()))) ((()())) ((())()) ((()))() (()(())) (()()()) (()())() (())(()) (())()() ()((())) ()(()()) ()(())() ()()(()) ()()()()']]),
    q('n-queens', 'N-Queens count', 'H', 'n:int', 'Return the number of ways to place n queens on an n×n board so that no two attack each other (1 <= n <= 9).', 'One queen per row; track used columns and both diagonals.',
      n => { const col = new Set(), d1 = new Set(), d2 = new Set(); const go = r => { if (r === n) return 1; let total = 0; for (let c = 0; c < n; c++) { if (col.has(c) || d1.has(r - c) || d2.has(r + c)) continue; col.add(c); d1.add(r - c); d2.add(r + c); total += go(r + 1); col.delete(c); d1.delete(r - c); d2.delete(r + c); } return total; }; return go(0); },
      [['4', '2'], ['1', '1'], ['2', '0'], ['8', '92'], ['6', '4']]),
  ],
};
