import { q } from '../lib.mjs';
import guide from '../guides/hashing.mjs';

export default {
  key: 'hashing', title: 'Hashing', summary: 'Map and Set, frequency counts, prefix-sum hashing, top-k',
  guide,
  problems: [
    q('has-duplicate', 'Contains duplicate', 'E', 'nums:ints', 'Return true if any value appears at least twice.', 'A hash set is smaller than the array when there are repeats.',
      nums => new Set(nums).size !== nums.length, [['1 2 3 1', 'true'], ['1 2 3 4', 'false'], ['7', 'false'], ['2 2', 'true'], ['', 'false']]),
    q('two-sum', 'Two sum', 'E', 'nums:ints, target:int', 'Return the 0-based indices (smaller first) of two elements that add to target. Exactly one answer exists.', 'Store value -> index; look for target - value first.',
      (nums, target) => { const at = new Map(); for (let i = 0; i < nums.length; i++) { if (at.has(target - nums[i])) return [at.get(target - nums[i]), i]; at.set(nums[i], i); } return -1; },
      [['2 7 11 15\n9', '0 1'], ['3 2 4\n6', '1 2'], ['3 3\n6', '0 1'], ['-1 5 8 2\n7', '0 2'], ['10 1 9 4\n13', '2 3']]),
    q('first-unique', 'First unique character', 'E', 's:str', 'Return the 0-based index of the first character that appears only once, or -1.', 'Count every character, then scan again.',
      s => { const c = new Map(); for (const ch of s) c.set(ch, (c.get(ch) || 0) + 1); for (let i = 0; i < s.length; i++) if (c.get(s[i]) === 1) return i; return -1; },
      [['leetcode', '0'], ['loveleetcode', '2'], ['aabb', '-1'], ['z', '0'], ['aabbc', '4']]),
    q('group-anagrams', 'Count anagram groups', 'M', 'words:words', 'Words that are anagrams belong to one group. Return the number of groups and the size of the largest group.', 'The sorted letters of a word are a good key.',
      words => { const g = new Map(); for (const w of words) { const k = w.split('').sort().join(''); g.set(k, (g.get(k) || 0) + 1); } return [g.size, Math.max.apply(null, Array.from(g.values()))]; },
      [['eat tea tan ate nat bat', '3 3'], ['abc', '1 1'], ['ab ba ab', '1 3'], ['dog cat', '2 1'], ['listen silent enlist google', '2 3']]),
    q('longest-consecutive', 'Longest consecutive run', 'M', 'nums:ints', 'Return the length of the longest run of consecutive integers present in the array (any order). Aim for O(n).', 'Start counting only at values whose predecessor is missing.',
      nums => { const s = new Set(nums); let best = 0; for (const x of s) if (!s.has(x - 1)) { let y = x; while (s.has(y + 1)) y++; best = Math.max(best, y - x + 1); } return best; },
      [['100 4 200 1 3 2', '4'], ['0 3 7 2 5 8 4 6 0 1', '9'], ['', '0'], ['5', '1'], ['9 1 4 7 3 -1 0 5 8 -1 6', '7']]),
    q('top-k-frequent', 'Top k frequent', 'M', 'nums:ints, k:int', 'Return the k most frequent values, most frequent first. For equal counts, the smaller value comes first.', 'Count, then sort by count and value.',
      (nums, k) => { const c = new Map(); for (const x of nums) c.set(x, (c.get(x) || 0) + 1); return Array.from(c).sort((a, b) => b[1] - a[1] || a[0] - b[0]).slice(0, k).map(e => e[0]); },
      [['1 1 1 2 2 3\n2', '1 2'], ['1\n1', '1'], ['4 4 5 5 6\n2', '4 5'], ['7 8 9\n3', '7 8 9'], ['-1 -1 2 2 2 3\n1', '2']]),
    q('subarray-sum-k', 'Subarrays with sum k', 'M', 'nums:ints, k:int', 'Return how many contiguous subarrays have a sum equal to k. Values can be negative.', 'Count earlier prefix sums equal to sum - k.',
      (nums, k) => { const seen = new Map([[0, 1]]); let sum = 0, n = 0; for (const x of nums) { sum += x; n += seen.get(sum - k) || 0; seen.set(sum, (seen.get(sum) || 0) + 1); } return n; },
      [['1 1 1\n2', '2'], ['1 2 3\n3', '2'], ['1 -1 0\n0', '3'], ['5\n5', '1'], ['3 4 7 2 -3 1 4 2\n7', '4']]),
  ],
};
