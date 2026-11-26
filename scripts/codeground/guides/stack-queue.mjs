import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Order of work, made simple', [
    'A stack serves the most recent item first (last in, first out). A queue serves the oldest first (first in, first out).',
    'Choosing the right one makes problems like bracket matching, undo and level-by-level search almost trivial.',
  ]),
  slide('Real-world use', 'Stacks and queues around you', [
    'Undo / redo in editors and the browser back button are stacks.',
    'Every function call goes on the call stack; that is how recursion works.',
    'Code editors and compilers match brackets with a stack.',
    'Print jobs, ticket booking queues and message brokers (Kafka, RabbitMQ) are queues; breadth-first search on maps and social graphs uses a queue.',
  ]),
  slide('Core idea', 'Push, pop, peek', [
    'Stack: push adds on top, pop removes the top, peek reads it. All O(1).',
    'Queue: add at the back, remove from the front. A deque allows both ends.',
    'Ask: does the newest item need attention first (stack), or the oldest (queue)?',
  ]),
  slide('Syntax', 'Stack and queue in your language', [
    'Use the fast structure: an array index or deque, not removing from the front of a list.',
  ], {
    javascript: `const stack = [];
stack.push(1); stack.push(2);
stack.pop();                 // 2
const queue = [1, 2];
let head = 0;
const first = queue[head++]; // 1 (shift() is O(n))`,
    python: `stack = []
stack.append(1); stack.append(2)
stack.pop()                  # 2
from collections import deque
q = deque([1, 2])
q.popleft()                  # 1, O(1)`,
    java: `Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2);
stack.pop();                 // 2
Deque<Integer> queue = new ArrayDeque<>(Arrays.asList(1, 2));
queue.pollFirst();           // 1`,
    cpp: `stack<int> st;
st.push(1); st.push(2);
st.top(); st.pop();          // 2
queue<int> q;
q.push(1); q.push(2);
q.front(); q.pop();          // 1`,
  }),
  slide('Pattern', 'Matching brackets', [
    'Push every opening bracket. A closing bracket must match the top; otherwise the text is invalid.',
    'At the end the stack must be empty (nothing left open).',
  ], {
    javascript: `const pair = { ')': '(', ']': '[', '}': '{' };
const st = [];
for (const c of s) {
  if (pair[c]) { if (st.pop() !== pair[c]) return false; }
  else st.push(c);
}
return st.length === 0;`,
    python: `pair = {')': '(', ']': '[', '}': '{'}
st = []
for c in s:
    if c in pair:
        if not st or st.pop() != pair[c]:
            return False
    else:
        st.append(c)
return not st`,
    java: `Deque<Character> st = new ArrayDeque<>();
for (char c : s.toCharArray()) {
    if (c == '(' || c == '[' || c == '{') st.push(c);
    else if (st.isEmpty() || "([{".indexOf(st.pop()) != ")]}".indexOf(c)) return false;
}
return st.isEmpty();`,
    cpp: `stack<char> st;
for (char c : s) {
    if (c == '(' || c == '[' || c == '{') { st.push(c); continue; }
    char open = c == ')' ? '(' : c == ']' ? '[' : '{';
    if (st.empty() || st.top() != open) return false;
    st.pop();
}
return st.empty();`,
  }),
  slide('Pattern', 'Monotonic stack: the next greater element', [
    'Keep indices whose answer is not known yet; their values only decrease from bottom to top.',
    'When a bigger value arrives, everything smaller on top has found its "next greater". Each index is pushed and popped once: O(n).',
    'The same idea gives "days until warmer", stock span and the largest rectangle in a histogram.',
  ], {
    javascript: `const res = new Array(a.length).fill(-1), st = [];
for (let i = 0; i < a.length; i++) {
  while (st.length && a[st[st.length - 1]] < a[i]) res[st.pop()] = a[i];
  st.push(i);
}`,
    python: `res, st = [-1] * len(a), []
for i, x in enumerate(a):
    while st and a[st[-1]] < x:
        res[st.pop()] = x
    st.append(i)`,
    java: `int[] res = new int[a.length];
Arrays.fill(res, -1);
Deque<Integer> st = new ArrayDeque<>();
for (int i = 0; i < a.length; i++) {
    while (!st.isEmpty() && a[st.peek()] < a[i]) res[st.pop()] = a[i];
    st.push(i);
}`,
    cpp: `vector<int> res(a.size(), -1);
stack<int> st;
for (int i = 0; i < (int)a.size(); i++) {
    while (!st.empty() && a[st.top()] < a[i]) { res[st.top()] = a[i]; st.pop(); }
    st.push(i);
}`,
  }),
  slide('Common mistakes', 'What breaks stack and queue code', [
    'Popping or peeking an empty stack: an exception in Java and Python, undefined behaviour in C++.',
    'Using shift() (JavaScript) or list.pop(0) (Python) on a long queue: O(n) each time.',
    'Java: prefer ArrayDeque over the old Stack class.',
    'Forgetting the final check: brackets are valid only if nothing is left on the stack.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Signal words: "next greater / smaller", "previous", "span" → monotonic stack; "level by level", "minimum steps" → queue (BFS).',
    'A deque of indices gives the maximum of every sliding window in O(n).',
    'Classic designs: a min-stack with O(1) getMin, and a queue built from two stacks.',
    'Next step: Recursion, which uses the call stack for you.',
  ]),
];
