import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Text is everywhere', [
    'Names, messages, URLs, logs and code itself are strings. Interviewers love string questions because they test careful indexing.',
    'Palindromes, anagrams and "longest substring" questions appear in almost every company set.',
  ]),
  slide('Real-world use', 'Where string skills are used', [
    'Search boxes and autocomplete match what you type against stored text.',
    'Form validation: PAN, IFSC, e-mail and phone formats are string checks.',
    'Spell check and plagiarism tools compare texts; genome tools search patterns in DNA strings (A, C, G, T).',
    'Search result snippets highlight the shortest part of a page that contains all your words: the "minimum window" problem.',
  ]),
  slide('Core idea', 'A sequence of characters', [
    'A string works like an array of characters: s[i] is one character, length is the count.',
    'In JavaScript, Java and Python strings cannot be changed in place; build a new one (or an array / StringBuilder) instead. C++ std::string can be changed.',
    'Characters have codes: "a" is 97. Subtract "a" to map letters to 0–25 for counting.',
  ]),
  slide('Syntax', 'Length, characters, reverse', [], {
    javascript: `const s = 'campus';
const rev = s.split('').reverse().join('');
for (const ch of s) { /* one character */ }
console.log(s.length, rev, s[0]);   // 6 supmac c`,
    python: `s = 'campus'
rev = s[::-1]
for ch in s:
    pass  # one character
print(len(s), rev, s[0])   # 6 supmac c`,
    java: `String s = "campus";
String rev = new StringBuilder(s).reverse().toString();
for (char ch : s.toCharArray()) { /* one character */ }
System.out.println(s.length() + " " + rev + " " + s.charAt(0));`,
    cpp: `string s = "campus";
string rev(s.rbegin(), s.rend());
for (char ch : s) { /* one character */ }
cout << s.size() << " " << rev << " " << s[0];`,
  }),
  slide('Pattern', 'Two pointers for palindromes', [
    'Compare the first and last characters, then move both inwards. Stop at the first mismatch.',
    'O(n) time and no extra memory; clean the text first if case and punctuation should be ignored.',
  ], {
    javascript: `let l = 0, r = s.length - 1, ok = true;
while (l < r) {
  if (s[l] !== s[r]) { ok = false; break; }
  l++; r--;
}`,
    python: `l, r, ok = 0, len(s) - 1, True
while l < r:
    if s[l] != s[r]:
        ok = False
        break
    l += 1
    r -= 1`,
    java: `int l = 0, r = s.length() - 1;
boolean ok = true;
while (l < r) {
    if (s.charAt(l) != s.charAt(r)) { ok = false; break; }
    l++; r--;
}`,
    cpp: `int l = 0, r = (int)s.size() - 1;
bool ok = true;
while (l < r) {
    if (s[l] != s[r]) { ok = false; break; }
    l++; r--;
}`,
  }),
  slide('Pattern', 'Sliding window on text', [
    'Move the right edge to take a character in; move the left edge while the window breaks the rule (a repeat, a missing letter).',
    'A map of counts or last positions tells you when the window is valid. Each character enters and leaves once: O(n).',
    'Expand around the centre: every palindrome grows from a centre, so try all 2n − 1 centres for the longest one.',
  ], {
    javascript: `// longest substring without repeating characters
const last = new Map();
let l = 0, best = 0;
for (let r = 0; r < s.length; r++) {
  if (last.has(s[r]) && last.get(s[r]) >= l) l = last.get(s[r]) + 1;
  last.set(s[r], r);
  best = Math.max(best, r - l + 1);
}`,
    python: `# longest substring without repeating characters
last, l, best = {}, 0, 0
for r, ch in enumerate(s):
    if last.get(ch, -1) >= l:
        l = last[ch] + 1
    last[ch] = r
    best = max(best, r - l + 1)`,
    java: `// longest substring without repeating characters
Map<Character, Integer> last = new HashMap<>();
int l = 0, best = 0;
for (int r = 0; r < s.length(); r++) {
    char ch = s.charAt(r);
    if (last.getOrDefault(ch, -1) >= l) l = last.get(ch) + 1;
    last.put(ch, r);
    best = Math.max(best, r - l + 1);
}`,
    cpp: `// longest substring without repeating characters
unordered_map<char, int> last;
int l = 0, best = 0;
for (int r = 0; r < (int)s.size(); r++) {
    if (last.count(s[r]) && last[s[r]] >= l) l = last[s[r]] + 1;
    last[s[r]] = r;
    best = max(best, r - l + 1);
}`,
  }),
  slide('Common mistakes', 'What breaks string code', [
    'Building a string with += in a loop: O(n²) in Java. Use StringBuilder (Java) or collect pieces and join.',
    'Java: compare strings with equals(), never ==.',
    'Upper and lower case are different characters; spaces and punctuation count too.',
    'substring / slice end index is exclusive: s.substring(1, 3) has 2 characters.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Ask about the character set first: lowercase only (use a 26-size array), ASCII, or Unicode?',
    'Pattern search: KMP and the Z-function find a pattern in O(n + m); rolling hashes (Rabin-Karp) compare substrings in O(1).',
    'Longest palindromic substring in O(n) exists (Manacher), but expand-around-centre O(n²) is what interviews expect.',
    'Next step: Hashing, which makes "have I seen this?" instant.',
  ]),
];
