import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Solve a big problem with a smaller copy of itself', [
    'Many problems are naturally self-similar: a folder holds folders, a tree holds subtrees, a list of choices holds smaller lists of choices.',
    'Recursion is the base of backtracking, divide and conquer (merge sort, quick sort), tree and graph search and dynamic programming.',
    'Interviewers use it to see whether you can trust a smaller answer and build on it.',
  ]),
  slide('Real-world use', 'Recursion in real software', [
    'File explorers and "du" walk folders inside folders to compute sizes.',
    'Browsers walk the HTML tree (the DOM) recursively to draw a page; JSON parsers read nested objects the same way.',
    'Compilers evaluate expressions like (2 + 3) × (4 − 1) by recursing on each bracket.',
    'Puzzle solvers (Sudoku, N-Queens) and route planners try a choice, recurse, and undo it: backtracking.',
  ]),
  slide('Core idea', 'Base case + smaller step', [
    'Base case: the smallest input you can answer directly (n = 0, an empty list, a leaf).',
    'Recursive step: shrink the input, call yourself, and combine the result.',
    'Trust the call: assume solve(n − 1) is right and only think about one level.',
    'Each call waits on the call stack until its child returns; that is how the answer comes back up.',
  ]),
  slide('Syntax', 'Factorial in your language', [
    'Every recursive function has the same shape: stop, or call yourself on something smaller.',
  ], {
    javascript: `function fact(n) {
  if (n <= 1) return 1;        // base case
  return n * fact(n - 1);      // smaller step
}`,
    python: `def fact(n):
    if n <= 1:                 # base case
        return 1
    return n * fact(n - 1)     # smaller step`,
    java: `static long fact(int n) {
    if (n <= 1) return 1;          // base case
    return n * fact(n - 1);        // smaller step
}`,
    cpp: `long long fact(int n) {
    if (n <= 1) return 1;          // base case
    return n * fact(n - 1);        // smaller step
}`,
  }),
  slide('Pattern', 'Backtracking: choose, explore, undo', [
    'To list all subsets, at each element decide "take it" or "skip it", recurse, then undo the choice.',
    'The same template gives permutations, combinations, N-Queens and word search.',
    'Copy the current path when you save an answer; it keeps changing afterwards.',
  ], {
    javascript: `function subsets(a, i, path, out) {
  if (i === a.length) { out.push([...path]); return; }
  path.push(a[i]); subsets(a, i + 1, path, out);   // take
  path.pop();      subsets(a, i + 1, path, out);   // skip
}`,
    python: `def subsets(a, i, path, out):
    if i == len(a):
        out.append(path[:])
        return
    path.append(a[i]); subsets(a, i + 1, path, out)   # take
    path.pop();        subsets(a, i + 1, path, out)   # skip`,
    java: `static void subsets(int[] a, int i, List<Integer> path, List<List<Integer>> out) {
    if (i == a.length) { out.add(new ArrayList<>(path)); return; }
    path.add(a[i]); subsets(a, i + 1, path, out);            // take
    path.remove(path.size() - 1); subsets(a, i + 1, path, out); // skip
}`,
    cpp: `void subsets(vector<int>& a, int i, vector<int>& path, vector<vector<int>>& out) {
    if (i == (int)a.size()) { out.push_back(path); return; }
    path.push_back(a[i]); subsets(a, i + 1, path, out);   // take
    path.pop_back();      subsets(a, i + 1, path, out);   // skip
}`,
  }),
  slide('Complexity', 'Count the calls', [
    'Cost = number of calls × work per call. fact(n) makes n calls: O(n).',
    'Two calls per level (subsets, naive Fibonacci) give about 2ⁿ calls: fine for n ≤ 20, hopeless for n = 50.',
    'Memory: the call stack is as deep as the longest chain of calls, O(depth).',
    'Repeated sub-calls with the same arguments? Store the answers (memoisation): that is dynamic programming.',
  ]),
  slide('Common mistakes', 'What breaks recursive code', [
    'No base case, or a step that does not shrink the input: infinite recursion and a stack overflow.',
    'Forgetting to return the recursive result (writing solve(n − 1) instead of return solve(n − 1)).',
    'Sharing one list between branches without undoing the change, or saving it without a copy.',
    'Very deep recursion (10⁵ levels) can overflow the stack: Python stops at about 1000 by default. Use a loop or an explicit stack.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Draw the recursion tree for a small input; it shows the cost and the repeated work at a glance.',
    'Prune early in backtracking: stop a branch as soon as it cannot lead to an answer (sum already too big).',
    'Divide and conquer: split into halves, solve each, merge (merge sort, fast power, counting inversions). The master theorem gives the cost.',
    'Next step: Searching and Sorting, where divide and conquer pays off.',
  ]),
];
