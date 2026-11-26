import { q } from '../lib.mjs';
import guide from '../guides/arrays.mjs';

export default {
  key: 'arrays', title: 'Arrays', summary: 'Scan, prefix and suffix, sliding window, two pointers, Kadane',
  guide,
  problems: [
    q('max-element', 'Largest element', 'E', 'nums:ints', 'Return the largest value in a non-empty array.', 'Keep the best seen so far.',
      nums => nums.reduce((a, b) => Math.max(a, b)), [['3 9 2 7', '9'], ['5', '5'], ['-4 -1 -9', '-1'], ['2 2 2', '2'], ['100 1 50 99', '100']]),
    q('second-largest', 'Second largest', 'E', 'nums:ints', 'Return the second largest distinct value, or -1 if there is none.', 'Track the largest and the second largest in one pass.',
      nums => { const u = Array.from(new Set(nums)).sort((a, b) => b - a); return u.length > 1 ? u[1] : -1; }, [['3 9 2 7', '7'], ['5 5 5', '-1'], ['10', '-1'], ['4 4 3', '3'], ['-2 -8 -1', '-2']]),
    q('move-zeroes', 'Move zeroes to the end', 'E', 'nums:ints', 'Move all zeroes to the end while keeping the order of the other elements. Return the array.', 'Use a write pointer for the non-zero values.',
      nums => nums.filter(x => x !== 0).concat(nums.filter(x => x === 0)), [['0 1 0 3 12', '1 3 12 0 0'], ['0', '0'], ['1 2 3', '1 2 3'], ['0 0 5', '5 0 0'], ['4 0 0 4', '4 4 0 0']]),
    q('pair-sum-sorted', 'Pair with a given sum', 'M', 'nums:ints, target:int', 'nums is sorted in increasing order. Return the 1-based positions of two different elements that add to target, or [-1] if none. If several pairs exist, return the one with the smallest first position.',
      'Two pointers from both ends.', (nums, target) => { let l = 0, r = nums.length - 1; while (l < r) { const s = nums[l] + nums[r]; if (s === target) return [l + 1, r + 1]; if (s < target) l++; else r--; } return [-1]; },
      [['2 7 11 15\n9', '1 2'], ['1 2 3 4\n8', '-1'], ['-3 0 3 6\n3', '1 4'], ['5\n10', '-1'], ['1 3 4 5 7\n9', '3 4']]),
    q('max-window-sum', 'Maximum sum of k in a row', 'M', 'nums:ints, k:int', 'Return the largest sum of k consecutive elements (1 <= k <= length).', 'Slide a window: add the new element, remove the oldest.',
      (nums, k) => { let sum = 0, best = -Infinity; for (let i = 0; i < nums.length; i++) { sum += nums[i]; if (i >= k) sum -= nums[i - k]; if (i >= k - 1) best = Math.max(best, sum); } return best; },
      [['2 1 5 1 3 2\n3', '9'], ['1 2 3\n1', '3'], ['4 4 4\n3', '12'], ['-1 -2 -3 -4\n2', '-3'], ['5 -2 7 1 -8 9\n4', '11']]),
    q('product-except-self', 'Product except self', 'M', 'nums:ints', 'Return an array where position i holds the product of all elements except nums[i]. Do not use division.', 'Multiply a prefix pass and a suffix pass.',
      nums => { const out = new Array(nums.length).fill(1); let p = 1; for (let i = 0; i < nums.length; i++) { out[i] = p; p *= nums[i]; } p = 1; for (let i = nums.length - 1; i >= 0; i--) { out[i] *= p; p *= nums[i]; } return out.map(x => x + 0); },
      [['1 2 3 4', '24 12 8 6'], ['2 3', '3 2'], ['0 4 5', '20 0 0'], ['-1 1 -1', '-1 1 -1'], ['5 5 5', '25 25 25']]),
    q('max-subarray', 'Maximum subarray sum', 'M', 'nums:ints', 'Return the largest sum of a non-empty contiguous subarray.', "Kadane: extend the current run or start a new one.",
      nums => { let cur = 0, best = -Infinity; for (const x of nums) { cur = Math.max(x, cur + x); best = Math.max(best, cur); } return best; },
      [['-2 1 -3 4 -1 2 1 -5 4', '6'], ['5', '5'], ['-3 -1 -2', '-1'], ['1 2 3', '6'], ['8 -19 5 -4 20', '21']]),
    q('trapping-rain', 'Trapping rain water', 'H', 'height:ints', 'Bars of width 1 have the given heights. Return how many units of water are held between them after rain.', 'Water at i = min(highest left, highest right) - height. Two pointers do it in one pass.',
      height => { let l = 0, r = height.length - 1, lm = 0, rm = 0, w = 0; while (l < r) { if (height[l] < height[r]) { lm = Math.max(lm, height[l]); w += lm - height[l]; l++; } else { rm = Math.max(rm, height[r]); w += rm - height[r]; r--; } } return w; },
      [['0 1 0 2 1 0 1 3 2 1 2 1', '6'], ['4 2 0 3 2 5', '9'], ['3 3 3', '0'], ['5', '0'], ['3 0 2 0 4', '7']]),
  ],
};
