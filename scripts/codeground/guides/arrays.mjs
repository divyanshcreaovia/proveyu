import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'The most common input in coding rounds', [
    'Around half of placement coding questions give you an array. Strings, matrices and DP tables are arrays too.',
    'Elements sit next to each other in memory, so reading a[i] is instant (O(1)) and scanning is fast.',
    'A few patterns (prefix sums, sliding window, two pointers, Kadane) turn slow O(n²) answers into O(n).',
  ]),
  slide('Real-world use', 'Where arrays work for you', [
    'Stock apps: daily prices are an array; "best time to buy and sell" and "biggest gain over a period" are array scans (Kadane).',
    'Monitoring dashboards: the moving average of CPU use or sensor readings over the last k minutes is a sliding window.',
    'Analytics: total sales between two dates in O(1) after one prefix-sum pass.',
    'Search results from two sorted lists are merged with two pointers; images are arrays of pixels.',
  ]),
  slide('Core idea', 'Index, length, one pass', [
    'Positions run from 0 to length − 1. Reading past the end is the most common crash.',
    'Most questions need one pass: keep the best value, a count or a running sum as you go.',
    'Ask: do I need a new array, or can I change this one in place?',
  ]),
  slide('Syntax', 'Create, loop, find the largest', [
    'Each language has a "for each" loop for reading and an index loop when you need the position.',
  ], {
    javascript: `const nums = [3, 9, 2];
let best = nums[0];
for (const x of nums) best = Math.max(best, x);
console.log(nums.length, best);   // 3 9`,
    python: `nums = [3, 9, 2]
best = nums[0]
for x in nums:
    best = max(best, x)
print(len(nums), best)   # 3 9`,
    java: `int[] nums = {3, 9, 2};
int best = nums[0];
for (int x : nums) best = Math.max(best, x);
System.out.println(nums.length + " " + best);   // 3 9`,
    cpp: `vector<int> nums = {3, 9, 2};
int best = nums[0];
for (int x : nums) best = max(best, x);
cout << nums.size() << " " << best;   // 3 9`,
  }),
  slide('Pattern', 'Prefix sums: any range total in O(1)', [
    'prefix[i] = sum of the first i elements. Then the sum of a[l..r] is prefix[r + 1] − prefix[l].',
    'One O(n) pass, then every query is instant: the trick behind "subarray sum equals k" and range-total questions.',
  ], {
    javascript: `const prefix = [0];
for (const x of a) prefix.push(prefix[prefix.length - 1] + x);
const rangeSum = (l, r) => prefix[r + 1] - prefix[l];`,
    python: `prefix = [0]
for x in a:
    prefix.append(prefix[-1] + x)
range_sum = lambda l, r: prefix[r + 1] - prefix[l]`,
    java: `long[] prefix = new long[a.length + 1];
for (int i = 0; i < a.length; i++) prefix[i + 1] = prefix[i] + a[i];
// sum of a[l..r] = prefix[r + 1] - prefix[l]`,
    cpp: `vector<long long> prefix(a.size() + 1, 0);
for (size_t i = 0; i < a.size(); i++) prefix[i + 1] = prefix[i] + a[i];
// sum of a[l..r] = prefix[r + 1] - prefix[l]`,
  }),
  slide('Pattern', 'Sliding window and two pointers', [
    'Sliding window: for "best block of k in a row", add the element entering the window and remove the one leaving it. O(n) instead of O(n·k).',
    'Two pointers: on a sorted array, start at both ends; if the pair sum is too small move left, too big move right.',
    "Kadane: for the largest subarray sum, at each element either extend the current run or start again: cur = max(x, cur + x).",
  ], {
    javascript: `let sum = 0, best = -Infinity;
for (let i = 0; i < a.length; i++) {
  sum += a[i];                   // enters
  if (i >= k) sum -= a[i - k];   // leaves
  if (i >= k - 1) best = Math.max(best, sum);
}`,
    python: `total, best = 0, float('-inf')
for i, x in enumerate(a):
    total += x                   # enters
    if i >= k:
        total -= a[i - k]        # leaves
    if i >= k - 1:
        best = max(best, total)`,
    java: `long sum = 0, best = Long.MIN_VALUE;
for (int i = 0; i < a.length; i++) {
    sum += a[i];                   // enters
    if (i >= k) sum -= a[i - k];   // leaves
    if (i >= k - 1) best = Math.max(best, sum);
}`,
    cpp: `long long sum = 0, best = LLONG_MIN;
for (int i = 0; i < (int)a.size(); i++) {
    sum += a[i];                   // enters
    if (i >= k) sum -= a[i - k];   // leaves
    if (i >= k - 1) best = max(best, sum);
}`,
  }),
  slide('Common mistakes', 'What breaks array code', [
    'Reading a[i + 1] or a[i − 1] at the edges: check the first and last index.',
    'An empty array: a[0] does not exist. Decide what the answer is when there is nothing.',
    'Assuming the array is sorted, or sorting it when the original order matters.',
    'Two nested loops on 10⁵ elements is 10¹⁰ steps: a time-out. Look for a one-pass pattern.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Size → target: n ≤ 10³ allows O(n²); n ≤ 10⁵ needs O(n log n) or O(n).',
    'In-place tricks: swap with a write pointer (move zeroes), reverse parts of the array (rotate by k), Dutch national flag (three-way partition).',
    'Interview flow: say the brute force and its cost first, then improve it with one of these patterns. It shows how you think.',
    'Next step: Strings, which are arrays of characters with a few extra rules.',
  ]),
];
