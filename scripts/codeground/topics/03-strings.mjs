import { q } from '../lib.mjs';
import guide from '../guides/strings.mjs';

export default {
  key: 'strings', title: 'Strings', summary: 'Palindromes, sliding window, expand around centre, minimum window',
  guide,
  problems: [
    q('count-vowels', 'Count the vowels', 'E', 's:str', 'Return how many vowels (a, e, i, o, u in either case) the string has.', 'Check each character against "aeiou".',
      s => (s.match(/[aeiou]/gi) || []).length, [['Placement Drive', '5'], ['rhythm', '0'], ['AEIOU', '5'], ['a', '1'], ['Campus 2026', '2']]),
    q('reverse-words', 'Reverse the words', 'E', 's:str', 'Return the words of the sentence in reverse order, separated by single spaces.', 'Split on spaces and drop empty pieces.',
      s => s.split(' ').filter(Boolean).reverse().join(' '), [['learn to code', 'code to learn'], ['  hello   world  ', 'world hello'], ['one', 'one'], ['a b c d', 'd c b a'], ['we are hiring', 'hiring are we']]),
    q('valid-palindrome', 'Valid palindrome', 'E', 's:str', 'Considering only letters and digits and ignoring case, return true if the string is a palindrome.', 'Remove other characters, then use two pointers.',
      s => { const t = s.toLowerCase().replace(/[^a-z0-9]/g, ''); return t === t.split('').reverse().join(''); },
      [['A man, a plan, a canal: Panama', 'true'], ['race a car', 'false'], ['', 'true'], ['0P', 'false'], ['No lemon, no melon', 'true']]),
    q('is-anagram', 'Anagram check', 'E', 'a:str, b:str', 'Return true if b is a rearrangement of the letters of a (exact characters).', 'Count characters, or sort both strings.',
      (a, b) => a.split('').sort().join('') === b.split('').sort().join(''), [['listen\nsilent', 'true'], ['rat\ncar', 'false'], ['aab\nabb', 'false'], ['x\nx', 'true'], ['abc\nabcd', 'false']]),
    q('longest-unique', 'Longest substring without repeats', 'M', 's:str', 'Return the length of the longest substring with no repeated character.', 'Remember the last position of each character and move the left edge past it.',
      s => { const last = new Map(); let l = 0, best = 0; for (let r = 0; r < s.length; r++) { if (last.has(s[r]) && last.get(s[r]) >= l) l = last.get(s[r]) + 1; last.set(s[r], r); best = Math.max(best, r - l + 1); } return best; },
      [['abcabcbb', '3'], ['bbbbb', '1'], ['pwwkew', '3'], ['', '0'], ['abba', '2']]),
    q('longest-palindrome', 'Longest palindromic substring', 'M', 's:str', 'Return the longest substring that is a palindrome. If there are several of that length, return the one that starts first.', 'Expand around each centre.',
      s => { let best = ''; for (let c = 0; c < 2 * s.length - 1; c++) { let l = Math.floor(c / 2), r = l + (c % 2); while (l >= 0 && r < s.length && s[l] === s[r]) { l--; r++; } const p = s.slice(l + 1, r); if (p.length > best.length) best = p; } return best; },
      [['babad', 'bab'], ['cbbd', 'bb'], ['a', 'a'], ['abcde', 'a'], ['forgeeksskeegfor', 'geeksskeeg']]),
    q('count-palindromes', 'Count palindromic substrings', 'M', 's:str', 'Return how many substrings are palindromes. Substrings at different positions count separately.', 'Every successful expansion step is one more palindrome.',
      s => { let n = 0; for (let c = 0; c < 2 * s.length - 1; c++) { let l = Math.floor(c / 2), r = l + (c % 2); while (l >= 0 && r < s.length && s[l] === s[r]) { n++; l--; r++; } } return n; },
      [['abc', '3'], ['aaa', '6'], ['a', '1'], ['abba', '6'], ['racecar', '10']]),
    q('min-window', 'Minimum window substring', 'H', 's:str, t:str', 'Return the shortest substring of s that contains every character of t (with repeats). Print NONE if there is none. If several are shortest, return the leftmost.',
      'Count what is still missing; shrink the window whenever nothing is missing.',
      (s, t) => { const need = new Map(); for (const c of t) need.set(c, (need.get(c) || 0) + 1); let missing = t.length, l = 0, best = null; for (let r = 0; r < s.length; r++) { if (need.has(s[r])) { if (need.get(s[r]) > 0) missing--; need.set(s[r], need.get(s[r]) - 1); } while (missing === 0) { if (best === null || r - l + 1 < best.length) best = s.slice(l, r + 1); if (need.has(s[l])) { need.set(s[l], need.get(s[l]) + 1); if (need.get(s[l]) > 0) missing++; } l++; } } return best === null ? 'NONE' : best; },
      [['ADOBECODEBANC\nABC', 'BANC'], ['a\na', 'a'], ['a\naa', 'NONE'], ['aaflslflsldkabc\naaa', 'aaflslflsldka'], ['this is a test string\ntist', 't stri']]),
  ],
};
