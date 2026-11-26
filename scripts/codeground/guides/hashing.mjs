import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'The most useful speed-up in interviews', [
    'Many brute-force answers ask "is this value somewhere else?" with a second loop: O(n²).',
    'A hash set or map answers that in O(1) on average, so the whole solution becomes O(n).',
    'Two sum, duplicates, anagrams, frequency counts and caching are all hashing.',
  ]),
  slide('Real-world use', 'Hashing in real systems', [
    'Caches (Redis, browser cache) and database indexes find a record by key without scanning everything.',
    'Duplicate detection: cloud drives spot the same file uploaded twice; mailing tools remove repeated e-mails.',
    'Trending hashtags and top searches are frequency counts followed by "top k".',
    'Payment reconciliation matches each debit with its credit: the same idea as two sum.',
  ]),
  slide('Core idea', 'Key → bucket', [
    'A hash function turns a key into a bucket number, so lookup goes straight to the right place.',
    'Set: only keys ("have I seen it?"). Map / dictionary: key → value ("how many times?", "at which index?").',
    'Average O(1) per operation; many keys in one bucket (collisions) can make it slower.',
  ]),
  slide('Syntax', 'Count, store, look up', [], {
    javascript: `const count = new Map();
for (const w of words) count.set(w, (count.get(w) || 0) + 1);
const seen = new Set([1, 2]);
seen.has(2);        // true`,
    python: `count = {}
for w in words:
    count[w] = count.get(w, 0) + 1     # or collections.Counter(words)
seen = {1, 2}
2 in seen           # True`,
    java: `Map<String, Integer> count = new HashMap<>();
for (String w : words) count.merge(w, 1, Integer::sum);
Set<Integer> seen = new HashSet<>(Arrays.asList(1, 2));
seen.contains(2);   // true`,
    cpp: `unordered_map<string, int> count;
for (const string& w : words) count[w]++;
unordered_set<int> seen = {1, 2};
seen.count(2);      // 1`,
  }),
  slide('Pattern', 'Look for the other half', [
    'Two sum: for each x, check whether target − x was seen before; if not, remember x and its index.',
    'One pass, O(n) time, O(n) memory, instead of trying every pair.',
  ], {
    javascript: `const at = new Map();
for (let i = 0; i < nums.length; i++) {
  if (at.has(target - nums[i])) return [at.get(target - nums[i]), i];
  at.set(nums[i], i);
}`,
    python: `at = {}
for i, x in enumerate(nums):
    if target - x in at:
        return [at[target - x], i]
    at[x] = i`,
    java: `Map<Integer, Integer> at = new HashMap<>();
for (int i = 0; i < nums.length; i++) {
    Integer j = at.get(target - nums[i]);
    if (j != null) return new int[] {j, i};
    at.put(nums[i], i);
}`,
    cpp: `unordered_map<int, int> at;
for (int i = 0; i < (int)nums.size(); i++) {
    auto it = at.find(target - nums[i]);
    if (it != at.end()) return {it->second, i};
    at[nums[i]] = i;
}`,
  }),
  slide('Pattern', 'Frequency, prefix sums, top-k', [
    'Frequency: count first, then answer (first unique character, anagram check, majority element).',
    'Prefix-sum hashing: a subarray sums to k when prefix[r] − prefix[l] = k. Count earlier prefix sums equal to sum − k; start with sum 0 seen once.',
    'Top-k: count, then sort the (value, count) pairs, or keep a heap of size k for O(n log k).',
  ]),
  slide('Common mistakes', 'What goes wrong with maps', [
    'JavaScript plain objects turn keys into strings (1 and "1" collide); use Map for real keys.',
    'Arrays and objects as keys compare by reference, not by content. Join them into a string key instead.',
    'Java: Integer values above 127 must be compared with equals(), not ==.',
    'C++: map is ordered and O(log n); unordered_map is the O(1) average one. Reading count[x] also inserts x.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Hashing vs sorting: sorting is O(n log n) with no extra memory; hashing is O(n) with O(n) memory. Say the trade-off.',
    'Design classic: an LRU cache is a hash map plus a doubly linked list, giving O(1) get and put.',
    'Rolling hashes compare substrings fast; Bloom filters answer "probably seen" with very little memory.',
    'Next step: Stack and Queue, for "most recent first" problems.',
  ]),
];
