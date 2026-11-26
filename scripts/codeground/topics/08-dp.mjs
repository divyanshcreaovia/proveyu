import { q } from '../lib.mjs';
import guide from '../guides/dp.mjs';

export default {
  key: 'dp', title: 'Dynamic Programming', summary: '1D and 2D tables, LIS, LCS, edit distance, knapsack',
  guide,
  problems: [
    q('climb-stairs', 'Climbing stairs', 'E', 'n:int', 'You climb 1 or 2 steps at a time. Return the number of ways to climb n steps (1 <= n <= 40).', 'It is the Fibonacci pattern.',
      n => { let a = 1, b = 1; for (let i = 2; i <= n; i++) { const c = a + b; a = b; b = c; } return b; }, [['3', '3'], ['1', '1'], ['2', '2'], ['5', '8'], ['40', '165580141']]),
    q('house-robber', 'House robber', 'M', 'money:ints', 'You cannot take from two neighbouring houses. Return the largest total you can take.', 'At each house: best without it, or best two back plus this one.',
      money => { let prev = 0, cur = 0; for (const x of money) { const next = Math.max(cur, prev + x); prev = cur; cur = next; } return cur; }, [['1 2 3 1', '4'], ['2 7 9 3 1', '12'], ['5', '5'], ['', '0'], ['2 1 1 2', '4']]),
    q('coin-change', 'Fewest coins', 'M', 'coins:ints, amount:int', 'You have unlimited coins of the given values. Return the fewest coins that make the amount, or -1.', 'dp[a] = 1 + min(dp[a - coin]).',
      (coins, amount) => { const dp = new Array(amount + 1).fill(Infinity); dp[0] = 0; for (let a = 1; a <= amount; a++) for (const c of coins) if (c <= a) dp[a] = Math.min(dp[a], dp[a - c] + 1); return dp[amount] === Infinity ? -1 : dp[amount]; },
      [['1 2 5\n11', '3'], ['2\n3', '-1'], ['1\n0', '0'], ['1 3 4\n6', '2'], ['186 419 83 408\n6249', '20']]),
    q('unique-paths', 'Unique grid paths', 'M', 'rows:int, cols:int', 'Moving only right or down, return the number of paths from the top-left to the bottom-right cell.', 'Each cell is the sum of the cell above and the cell to the left.',
      (rows, cols) => { const dp = new Array(cols).fill(1); for (let r = 1; r < rows; r++) for (let c = 1; c < cols; c++) dp[c] += dp[c - 1]; return dp[cols - 1]; }, [['3\n7', '28'], ['3\n2', '3'], ['1\n1', '1'], ['5\n5', '70'], ['10\n10', '48620']]),
    q('lis', 'Longest increasing subsequence', 'M', 'nums:ints', 'Return the length of the longest strictly increasing subsequence.', 'dp[i] = 1 + best dp[j] with nums[j] < nums[i]; or keep smallest tails and binary search.',
      nums => { const t = []; for (const x of nums) { let lo = 0, hi = t.length; while (lo < hi) { const m = (lo + hi) >> 1; if (t[m] < x) lo = m + 1; else hi = m; } t[lo] = x; } return t.length; },
      [['10 9 2 5 3 7 101 18', '4'], ['0 1 0 3 2 3', '4'], ['7 7 7', '1'], ['', '0'], ['1 2 3 4 5', '5']]),
    q('lcs', 'Longest common subsequence', 'M', 'a:str, b:str', 'Return the length of the longest subsequence common to both strings.', 'Match: diagonal + 1. Otherwise: max of up and left.',
      (a, b) => { const dp = new Array(b.length + 1).fill(0); for (let i = 1; i <= a.length; i++) { let diag = 0; for (let j = 1; j <= b.length; j++) { const up = dp[j]; dp[j] = a[i - 1] === b[j - 1] ? diag + 1 : Math.max(up, dp[j - 1]); diag = up; } } return dp[b.length]; },
      [['abcde\nace', '3'], ['abc\nabc', '3'], ['abc\ndef', '0'], ['\nabc', '0'], ['AGGTAB\nGXTXAYB', '4']]),
    q('knapsack', '0/1 knapsack', 'H', 'weights:ints, values:ints, capacity:int', 'Each item can be taken at most once. Return the largest total value with total weight within capacity.', 'dp[c] = max(dp[c], dp[c - w] + v), with c going down.',
      (weights, values, capacity) => { const dp = new Array(capacity + 1).fill(0); for (let i = 0; i < weights.length; i++) for (let c = capacity; c >= weights[i]; c--) dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]); return dp[capacity]; },
      [['1 3 4 5\n1 4 5 7\n7', '9'], ['2\n10\n1', '0'], ['1 2 3\n6 10 12\n5', '22'], ['5 5 5\n10 20 30\n10', '50'], ['4 3 2\n40 30 25\n5', '55']]),
    q('edit-distance', 'Edit distance', 'H', 'a:str, b:str', 'Return the fewest single-character insertions, deletions or replacements that turn a into b.', 'Same table as LCS, with three neighbours.',
      (a, b) => { const dp = Array.from({ length: b.length + 1 }, (_, j) => j); for (let i = 1; i <= a.length; i++) { let diag = dp[0]; dp[0] = i; for (let j = 1; j <= b.length; j++) { const up = dp[j]; dp[j] = a[i - 1] === b[j - 1] ? diag : 1 + Math.min(diag, up, dp[j - 1]); diag = up; } } return dp[b.length]; },
      [['horse\nros', '3'], ['intention\nexecution', '5'], ['\nabc', '3'], ['same\nsame', '0'], ['kitten\nsitting', '3']]),
  ],
};
