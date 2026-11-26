import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Stop solving the same thing twice', [
    'Dynamic programming (DP) turns exponential brute force into polynomial time by remembering answers to smaller problems.',
    'It is the topic that separates candidates in product-company rounds: knapsack, LIS, edit distance and coin change are classics.',
    'Once you see the pattern ("best way to reach state X"), many hard-looking questions become a table to fill.',
  ]),
  slide('Real-world use', 'DP in products you use', [
    'Spell check and "did you mean" use edit distance between words; git diff uses longest common subsequence.',
    'Maps and ride apps compute shortest routes with DP-style relaxation (Bellman-Ford, Floyd-Warshall).',
    'Budget planners and cargo loading are knapsack problems: the most value within a weight or cost limit.',
    'Text justification in word processors and speech recognition (Viterbi) are DP too.',
  ]),
  slide('Core idea', 'State, choice, base case', [
    'State: what you need to know to describe a sub-problem, e.g. dp[i] = best answer for the first i items.',
    'Transition: how dp[i] is built from smaller states, e.g. dp[i] = max(dp[i − 1], dp[i − 2] + a[i]).',
    'Base case: the answer for the smallest states (dp[0], dp[1]).',
    'Beginner route: write the recursion first, add memoisation, then turn it into a loop (tabulation).',
  ]),
  slide('Syntax', 'Climbing stairs: memo and table', [
    'Ways to climb n stairs taking 1 or 2 steps: ways(n) = ways(n − 1) + ways(n − 2).',
  ], {
    javascript: `const dp = new Array(n + 1).fill(0);
dp[0] = 1; dp[1] = 1;
for (let i = 2; i <= n; i++) dp[i] = dp[i - 1] + dp[i - 2];
return dp[n];`,
    python: `from functools import lru_cache

@lru_cache(maxsize=None)
def ways(n):
    return 1 if n <= 1 else ways(n - 1) + ways(n - 2)`,
    java: `long[] dp = new long[n + 2];
dp[0] = 1; dp[1] = 1;
for (int i = 2; i <= n; i++) dp[i] = dp[i - 1] + dp[i - 2];
return dp[n];`,
    cpp: `vector<long long> dp(n + 2, 0);
dp[0] = 1; dp[1] = 1;
for (int i = 2; i <= n; i++) dp[i] = dp[i - 1] + dp[i - 2];
return dp[n];`,
  }),
  slide('Pattern', '1-D DP: take it or leave it', [
    'House robber: at each house either skip it (dp[i − 1]) or rob it and skip the previous (dp[i − 2] + a[i]).',
    'Coin change: dp[x] = fewest coins for amount x = 1 + min over coins c of dp[x − c].',
    'Only the last two values are needed? Keep two variables instead of an array: O(1) memory.',
  ], {
    javascript: `let prev = 0, cur = 0;
for (const x of houses) [prev, cur] = [cur, Math.max(cur, prev + x)];
return cur;`,
    python: `prev, cur = 0, 0
for x in houses:
    prev, cur = cur, max(cur, prev + x)
return cur`,
    java: `int prev = 0, cur = 0;
for (int x : houses) {
    int next = Math.max(cur, prev + x);
    prev = cur; cur = next;
}
return cur;`,
    cpp: `int prev = 0, cur = 0;
for (int x : houses) {
    int next = max(cur, prev + x);
    prev = cur; cur = next;
}
return cur;`,
  }),
  slide('Pattern', '2-D DP: two strings or items × capacity', [
    'Longest common subsequence: dp[i][j] for the first i characters of a and the first j of b. Equal characters extend the diagonal; otherwise take the better of dropping one.',
    '0/1 knapsack: dp[w] = best value within weight w. Loop the weight backwards so each item is used once.',
  ], {
    javascript: `const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
for (let i = 1; i <= n; i++)
  for (let j = 1; j <= m; j++)
    dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);`,
    python: `dp = [[0] * (m + 1) for _ in range(n + 1)]
for i in range(1, n + 1):
    for j in range(1, m + 1):
        dp[i][j] = dp[i-1][j-1] + 1 if a[i-1] == b[j-1] else max(dp[i-1][j], dp[i][j-1])`,
    java: `int[][] dp = new int[n + 1][m + 1];
for (int i = 1; i <= n; i++)
    for (int j = 1; j <= m; j++)
        dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1) ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);`,
    cpp: `vector<vector<int>> dp(n + 1, vector<int>(m + 1, 0));
for (int i = 1; i <= n; i++)
    for (int j = 1; j <= m; j++)
        dp[i][j] = a[i - 1] == b[j - 1] ? dp[i - 1][j - 1] + 1 : max(dp[i - 1][j], dp[i][j - 1]);`,
  }),
  slide('Complexity', 'States × choices', [
    'Time = number of states × work per state. LCS has n·m states and O(1) work: O(n·m).',
    'Memory can often drop one dimension: keep only the previous row.',
    'Spot DP: "count the ways", "minimum / maximum cost", "is it possible", with choices that overlap.',
  ]),
  slide('Common mistakes', 'What breaks DP code', [
    'A state that misses information (e.g. ignoring whether the last item was taken).',
    'Wrong base case: dp[0] = 1 for counting ways, but 0 for "minimum coins".',
    'Wrong loop order: a state is read before it is filled. In 0/1 knapsack a forward loop reuses an item.',
    'Unreachable states left as 0 instead of Infinity / −1 in "minimum" problems.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Explain in this order: state, transition, base case, answer, complexity. Interviewers grade exactly that.',
    'LIS in O(n log n) with patience sorting (binary search over tails).',
    'Other families: interval DP (matrix chain, burst balloons), DP on trees, bitmask DP over subsets, digit DP.',
    'Rebuild the actual choice (not just the value) by walking back through the table.',
    'Next step: Maths and Bits.',
  ]),
];
