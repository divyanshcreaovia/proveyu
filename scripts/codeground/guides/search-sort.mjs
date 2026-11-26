import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Order makes everything faster', [
    'Searching a sorted list of a million items takes 20 steps with binary search instead of a million.',
    'Many hard questions become easy after sorting: merge intervals, meeting rooms, two pointers, duplicates.',
    'Binary search on the answer ("smallest speed that finishes in time") is a favourite in product-company rounds.',
  ]),
  slide('Real-world use', 'Where searching and sorting run', [
    'Databases keep sorted indexes (B-trees) so a lookup by roll number or order id is instant.',
    'E-commerce sorts by price, rating and relevance; leaderboards sort by score.',
    'git bisect binary-searches your commit history to find the commit that broke the build.',
    'Calendars merge overlapping meetings; ride apps sort drivers by distance.',
  ]),
  slide('Core idea', 'Halve the search space', [
    'Binary search needs sorted data (or any yes/no condition that flips once).',
    'Look at the middle: if it is too small, the answer is on the right; otherwise on the left. Each step halves the range: O(log n).',
    'Sorting: use the built-in sort (O(n log n)); know how merge sort and quick sort work for interviews.',
  ]),
  slide('Syntax', 'Sort numbers and binary search', [
    'JavaScript sort() compares as text by default: always pass a comparator for numbers.',
  ], {
    javascript: `a.sort((x, y) => x - y);
let lo = 0, hi = a.length - 1;
while (lo <= hi) {
  const mid = (lo + hi) >> 1;
  if (a[mid] === target) return mid;
  if (a[mid] < target) lo = mid + 1; else hi = mid - 1;
}
return -1;`,
    python: `a.sort()
lo, hi = 0, len(a) - 1
while lo <= hi:
    mid = (lo + hi) // 2
    if a[mid] == target:
        return mid
    if a[mid] < target:
        lo = mid + 1
    else:
        hi = mid - 1
return -1      # or: bisect.bisect_left(a, target)`,
    java: `Arrays.sort(a);
int lo = 0, hi = a.length - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (a[mid] == target) return mid;
    if (a[mid] < target) lo = mid + 1; else hi = mid - 1;
}
return -1;     // or: Arrays.binarySearch(a, target)`,
    cpp: `sort(a.begin(), a.end());
int lo = 0, hi = (int)a.size() - 1;
while (lo <= hi) {
    int mid = lo + (hi - lo) / 2;
    if (a[mid] == target) return mid;
    if (a[mid] < target) lo = mid + 1; else hi = mid - 1;
}
return -1;     // or: lower_bound(a.begin(), a.end(), target)`,
  }),
  slide('Pattern', 'Binary search on the answer', [
    'When the question asks for the smallest (or largest) value that works, and "works" stays true once it becomes true, binary search over the values.',
    'Example: the smallest capacity that ships all packages in d days. Check a capacity in O(n); search capacities in O(log range).',
  ], {
    javascript: `let lo = minPossible, hi = maxPossible;
while (lo < hi) {
  const mid = Math.floor((lo + hi) / 2);
  if (works(mid)) hi = mid; else lo = mid + 1;
}
return lo;   // smallest value that works`,
    python: `lo, hi = min_possible, max_possible
while lo < hi:
    mid = (lo + hi) // 2
    if works(mid):
        hi = mid
    else:
        lo = mid + 1
return lo    # smallest value that works`,
    java: `long lo = minPossible, hi = maxPossible;
while (lo < hi) {
    long mid = lo + (hi - lo) / 2;
    if (works(mid)) hi = mid; else lo = mid + 1;
}
return lo;   // smallest value that works`,
    cpp: `long long lo = minPossible, hi = maxPossible;
while (lo < hi) {
    long long mid = lo + (hi - lo) / 2;
    if (works(mid)) hi = mid; else lo = mid + 1;
}
return lo;   // smallest value that works`,
  }),
  slide('Pattern', 'Sort first, then sweep', [
    'Merge intervals: sort by start; extend the last interval while the next one overlaps, otherwise start a new one.',
    'Custom order: sort with a comparator (by end time, by length, by two keys).',
    'Merge step of merge sort: two pointers on two sorted lists, take the smaller each time: O(n + m).',
  ], {
    javascript: `iv.sort((p, q) => p[0] - q[0]);
const out = [];
for (const [s, e] of iv) {
  if (out.length && s <= out[out.length - 1][1]) out[out.length - 1][1] = Math.max(out[out.length - 1][1], e);
  else out.push([s, e]);
}`,
    python: `iv.sort(key=lambda p: p[0])
out = []
for s, e in iv:
    if out and s <= out[-1][1]:
        out[-1][1] = max(out[-1][1], e)
    else:
        out.append([s, e])`,
    java: `Arrays.sort(iv, (p, q) -> Integer.compare(p[0], q[0]));
List<int[]> out = new ArrayList<>();
for (int[] cur : iv) {
    if (!out.isEmpty() && cur[0] <= out.get(out.size() - 1)[1])
        out.get(out.size() - 1)[1] = Math.max(out.get(out.size() - 1)[1], cur[1]);
    else out.add(cur);
}`,
    cpp: `sort(iv.begin(), iv.end());
vector<vector<int>> out;
for (auto& cur : iv) {
    if (!out.empty() && cur[0] <= out.back()[1]) out.back()[1] = max(out.back()[1], cur[1]);
    else out.push_back(cur);
}`,
  }),
  slide('Common mistakes', 'What breaks search and sort code', [
    'JavaScript: [10, 9, 1].sort() gives [1, 10, 9]. Always sort numbers with (a, b) => a − b.',
    'Endless loops in binary search: with lo < hi and lo = mid, the range may stop shrinking. Match the loop condition and updates.',
    '(lo + hi) can overflow int in Java and C++; use lo + (hi − lo) / 2.',
    'Binary search on unsorted data gives wrong answers silently.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Know lower bound (first ≥ x) and upper bound (first > x); most variants are one of these.',
    'Rotated sorted array: one half around mid is always sorted; decide which side the target is on.',
    'Quick select finds the k-th smallest in O(n) on average; counting sort is O(n + range) for small values.',
    'Stable sort keeps equal items in their original order (merge sort, JavaScript and Java object sorts, Python sort).',
    'Next step: Dynamic Programming.',
  ]),
];
