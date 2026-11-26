import { q } from '../lib.mjs';
import guide from '../guides/search-sort.mjs';

export default {
  key: 'search-sort', title: 'Searching and Sorting', summary: 'Binary search, rotated arrays, intervals, search on the answer',
  guide,
  problems: [
    q('binary-search', 'Binary search', 'E', 'nums:ints, target:int', 'nums is sorted with distinct values. Return the index of target or -1.', 'Keep lo and hi and look at the middle.',
      (nums, target) => { let lo = 0, hi = nums.length - 1; while (lo <= hi) { const m = (lo + hi) >> 1; if (nums[m] === target) return m; if (nums[m] < target) lo = m + 1; else hi = m - 1; } return -1; },
      [['-1 0 3 5 9 12\n9', '4'], ['-1 0 3 5 9 12\n2', '-1'], ['5\n5', '0'], ['1 3\n3', '1'], ['2 4 6 8\n1', '-1']]),
    q('sort-squares', 'Squares in sorted order', 'E', 'nums:ints', 'nums is sorted. Return the squares of all elements, also sorted.', 'The biggest square is at one of the two ends.',
      nums => nums.map(x => x * x).sort((a, b) => a - b), [['-4 -1 0 3 10', '0 1 9 16 100'], ['-7 -3 2 3 11', '4 9 9 49 121'], ['0', '0'], ['-3 -2 -1', '1 4 9'], ['1 2 3', '1 4 9']]),
    q('first-last', 'First and last position', 'M', 'nums:ints, target:int', 'nums is sorted and may repeat values. Return the first and last index of target, or "-1 -1".', 'Binary search for the left border, and again for the right.',
      (nums, target) => { const lower = x => { let lo = 0, hi = nums.length; while (lo < hi) { const m = (lo + hi) >> 1; if (nums[m] < x) lo = m + 1; else hi = m; } return lo; }; const a = lower(target); if (nums[a] !== target) return [-1, -1]; return [a, lower(target + 1) - 1]; },
      [['5 7 7 8 8 10\n8', '3 4'], ['5 7 7 8 8 10\n6', '-1 -1'], ['\n0', '-1 -1'], ['2 2 2 2\n2', '0 3'], ['1\n1', '0 0']]),
    q('search-rotated', 'Search in a rotated array', 'M', 'nums:ints, target:int', 'A sorted array of distinct values was rotated. Return the index of target or -1 in O(log n).', 'Find which half is sorted and whether target is inside it.',
      (nums, target) => { let lo = 0, hi = nums.length - 1; while (lo <= hi) { const m = (lo + hi) >> 1; if (nums[m] === target) return m; if (nums[lo] <= nums[m]) { if (target >= nums[lo] && target < nums[m]) hi = m - 1; else lo = m + 1; } else if (target > nums[m] && target <= nums[hi]) lo = m + 1; else hi = m - 1; } return -1; },
      [['4 5 6 7 0 1 2\n0', '4'], ['4 5 6 7 0 1 2\n3', '-1'], ['1\n0', '-1'], ['3 1\n1', '1'], ['6 7 8 1 2 3 4 5\n8', '2']]),
    q('merge-intervals', 'Merge intervals', 'M', 'intervals:rows', 'Merge all overlapping intervals (touching ends overlap). Return them sorted by start, one per line.', 'Sort by start, then extend the last one or add a new one.',
      intervals => { const out = []; for (const [s, e] of intervals.slice().sort((a, b) => a[0] - b[0])) { const last = out[out.length - 1]; if (last && s <= last[1]) last[1] = Math.max(last[1], e); else out.push([s, e]); } return out; },
      [['4\n1 3\n2 6\n8 10\n15 18', '1 6\n8 10\n15 18'], ['2\n1 4\n4 5', '1 5'], ['1\n2 3', '2 3'], ['3\n5 6\n1 2\n3 4', '1 2\n3 4\n5 6'], ['3\n1 10\n2 3\n4 5', '1 10']]),
    q('ship-capacity', 'Ship packages within D days', 'H', 'weights:ints, days:int', 'Packages are loaded in order. Return the smallest ship capacity that moves all of them within the given days.', 'Binary search the capacity between the heaviest package and the total.',
      (weights, days) => { let lo = Math.max.apply(null, weights), hi = weights.reduce((a, b) => a + b, 0); while (lo < hi) { const m = (lo + hi) >> 1; let d = 1, load = 0; for (const w of weights) { if (load + w > m) { d++; load = 0; } load += w; } if (d <= days) hi = m; else lo = m + 1; } return lo; },
      [['1 2 3 4 5 6 7 8 9 10\n5', '15'], ['3 2 2 4 1 4\n3', '6'], ['1 2 3 1 1\n4', '3'], ['10\n1', '10'], ['5 5 5 5\n4', '5']]),
    q('median-two-sorted', 'Median of two sorted arrays', 'H', 'a:ints, b:ints', 'Both arrays are sorted and not both empty. Return the median of all values (a .5 result prints as .5).', 'Merging works; the O(log) way binary searches the split point of the smaller array.',
      (a, b) => { const m = a.concat(b).sort((x, y) => x - y), n = m.length; return n % 2 ? m[(n - 1) / 2] : (m[n / 2 - 1] + m[n / 2]) / 2; },
      [['1 3\n2', '2'], ['1 2\n3 4', '2.5'], ['\n1', '1'], ['1 1 1\n1 1', '1'], ['-5 3 6\n-2 8 10', '4.5']]),
  ],
};
