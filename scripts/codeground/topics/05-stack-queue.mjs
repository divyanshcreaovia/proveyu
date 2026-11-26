import { q } from '../lib.mjs';
import guide from '../guides/stack-queue.mjs';

export default {
  key: 'stack-queue', title: 'Stack and Queue', summary: 'Brackets, monotonic stack, reverse Polish notation, histogram',
  guide,
  problems: [
    q('valid-brackets', 'Valid brackets', 'E', 's:str', 'The string has only ()[]{} characters. Return true if every bracket is closed in the right order.', 'Push openings; each closing must match the top.',
      s => { const st = [], m = { ')': '(', ']': '[', '}': '{' }; for (const c of s) { if (m[c]) { if (st.pop() !== m[c]) return false; } else st.push(c); } return st.length === 0; },
      [['()[]{}', 'true'], ['(]', 'false'], ['([{}])', 'true'], ['(((', 'false'], ['', 'true']]),
    q('remove-adjacent', 'Remove adjacent duplicates', 'E', 's:str', 'Repeatedly remove two equal neighbouring letters until none are left. Return the result, or EMPTY.', 'Pop when the next letter equals the top.',
      s => { const st = []; for (const c of s) { if (st.length && st[st.length - 1] === c) st.pop(); else st.push(c); } return st.join('') || 'EMPTY'; },
      [['abbaca', 'ca'], ['azxxzy', 'ay'], ['aa', 'EMPTY'], ['abc', 'abc'], ['abccba', 'EMPTY']]),
    q('eval-rpn', 'Evaluate reverse Polish notation', 'M', 'tokens:words', 'Evaluate the expression. Operators are + - * / ; division truncates toward zero.', 'Push numbers; an operator takes the two on top.',
      tokens => { const st = []; for (const t of tokens) { if ('+-*/'.includes(t)) { const b = st.pop(), a = st.pop(); st.push(t === '+' ? a + b : t === '-' ? a - b : t === '*' ? a * b : Math.trunc(a / b)); } else st.push(Number(t)); } return st[0] + 0; },
      [['2 1 + 3 *', '9'], ['4 13 5 / +', '6'], ['7', '7'], ['10 6 9 3 + -11 * / * 17 + 5 +', '22'], ['3 -4 *', '-12']]),
    q('next-greater', 'Next greater element', 'M', 'nums:ints', 'For each element return the first larger element to its right, or -1.', 'Keep a stack of positions still waiting for a larger value.',
      nums => { const out = new Array(nums.length).fill(-1), st = []; for (let i = 0; i < nums.length; i++) { while (st.length && nums[st[st.length - 1]] < nums[i]) out[st.pop()] = nums[i]; st.push(i); } return out; },
      [['4 5 2 25', '5 25 25 -1'], ['13 7 6 12', '-1 12 12 -1'], ['1 2 3', '2 3 -1'], ['3 2 1', '-1 -1 -1'], ['2 1 2 4 3', '4 2 4 -1 -1']]),
    q('daily-temperatures', 'Days until warmer', 'M', 'temps:ints', 'For each day return how many days you wait for a warmer temperature, 0 if never.', 'A stack of indices with falling temperatures.',
      temps => { const out = new Array(temps.length).fill(0), st = []; for (let i = 0; i < temps.length; i++) { while (st.length && temps[st[st.length - 1]] < temps[i]) { const j = st.pop(); out[j] = i - j; } st.push(i); } return out; },
      [['73 74 75 71 69 72 76 73', '1 1 4 2 1 1 0 0'], ['30 40 50 60', '1 1 1 0'], ['30 60 90', '1 1 0'], ['90 80 70', '0 0 0'], ['55', '0']]),
    q('sliding-max', 'Sliding window maximum', 'H', 'nums:ints, k:int', 'Return the maximum of every window of k consecutive elements.', 'A deque of indices whose values only decrease.',
      (nums, k) => { const dq = [], out = []; let head = 0; for (let i = 0; i < nums.length; i++) { while (dq.length > head && nums[dq[dq.length - 1]] <= nums[i]) dq.pop(); dq.push(i); if (dq[head] <= i - k) head++; if (i >= k - 1) out.push(nums[dq[head]]); } return out; },
      [['1 3 -1 -3 5 3 6 7\n3', '3 3 5 5 6 7'], ['1\n1', '1'], ['9 8 7 6\n2', '9 8 7'], ['4 -2 5\n3', '5'], ['1 2 3 4 5\n2', '2 3 4 5']]),
    q('largest-rectangle', 'Largest rectangle in histogram', 'H', 'heights:ints', 'Bars of width 1 have the given heights. Return the area of the largest rectangle that fits inside them.', 'Increasing stack; when a lower bar arrives, close the taller ones.',
      heights => { const st = []; let best = 0; for (let i = 0; i <= heights.length; i++) { const h = i === heights.length ? 0 : heights[i]; while (st.length && heights[st[st.length - 1]] >= h) { const top = heights[st.pop()]; const left = st.length ? st[st.length - 1] : -1; best = Math.max(best, top * (i - left - 1)); } st.push(i); } return best; },
      [['2 1 5 6 2 3', '10'], ['2 4', '4'], ['5', '5'], ['1 1 1 1', '4'], ['6 2 5 4 5 1 6', '12']]),
  ],
};
