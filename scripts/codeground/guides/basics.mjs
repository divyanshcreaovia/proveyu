import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Every program is built from these pieces', [
    'Loops, conditions and arithmetic are what every other topic is made of: arrays, strings and DP are just loops with a smarter plan.',
    'The first online round of most mass recruiters (TCS NQT, Infosys, Wipro, Accenture) has questions on digits, divisibility and number conversions.',
    'Being fast and error-free here gives you time for the harder questions later in the same test.',
  ]),
  slide('Real-world use', 'Where you meet this outside an exam', [
    'Card and ID validation: payment pages check a card number with the Luhn algorithm, which walks the digits one by one.',
    'Money: apps store ₹12.50 as 1250 paise (an integer) so rounding errors never creep into a bill.',
    'Number formatting: showing 1234567 as 12,34,567 in an Indian app is digit splitting.',
    'Binary: file permissions, network masks and colour codes are numbers read in base 2 or 16.',
  ]),
  slide('Core idea', 'Repeat, test, accumulate', [
    'Keep a variable for the answer you are building (a sum, a count, a reversed number).',
    'Use a loop to look at one piece at a time: a number from 1 to n, or one digit of a number.',
    'n % 10 is the last digit; dividing by 10 (and dropping the fraction) removes it. Repeat while n > 0.',
    'Beginner check: walk through the loop on paper for a tiny input such as 4521 before typing.',
  ]),
  slide('Syntax', 'Sum of digits in your language', [
    'Same idea in every language; what changes is how integer division is written.',
    'Java and C++ drop the fraction automatically for int / int; JavaScript needs Math.floor; Python uses //.',
  ], {
    javascript: `let n = 4521, sum = 0;
while (n > 0) {
  sum += n % 10;            // last digit
  n = Math.floor(n / 10);   // drop it
}
console.log(sum);           // 12`,
    python: `n, total = 4521, 0
while n > 0:
    total += n % 10     # last digit
    n //= 10            # drop it
print(total)            # 12`,
    java: `int n = 4521, sum = 0;
while (n > 0) {
    sum += n % 10;   // last digit
    n /= 10;         // int division drops it
}
System.out.println(sum);   // 12`,
    cpp: `int n = 4521, sum = 0;
while (n > 0) {
    sum += n % 10;   // last digit
    n /= 10;         // int division drops it
}
cout << sum;         // 12`,
  }),
  slide('Pattern', 'Build a number digit by digit', [
    'To reverse 1234: take the last digit and append it to the result with result = result × 10 + digit.',
    'The same move converts text to a number ("482" → 482) and builds binary or any other base.',
  ], {
    javascript: `let n = 1234, rev = 0;
while (n > 0) {
  rev = rev * 10 + (n % 10);
  n = Math.floor(n / 10);
}
// rev = 4321`,
    python: `n, rev = 1234, 0
while n > 0:
    rev = rev * 10 + n % 10
    n //= 10
# rev = 4321`,
    java: `int n = 1234, rev = 0;
while (n > 0) {
    rev = rev * 10 + n % 10;
    n /= 10;
}
// rev = 4321`,
    cpp: `int n = 1234, rev = 0;
while (n > 0) {
    rev = rev * 10 + n % 10;
    n /= 10;
}
// rev = 4321`,
  }),
  slide('Complexity', 'How fast, how big', [
    'A digit loop runs once per digit: about log₁₀(n) steps, so even 10¹⁸ needs only 18 steps.',
    'A loop from 1 to n is O(n): fine for a million, too slow for 10¹⁸. Look for a formula, e.g. 1 + 2 + … + n = n(n + 1) / 2.',
    'Number sizes: int holds about ±2.1 × 10⁹ in Java and C++; use long / long long beyond that. JavaScript is exact up to 2⁵³; Python integers never overflow.',
  ]),
  slide('Common mistakes', 'What loses marks', [
    'Forgetting n = 0: a while (n > 0) loop never runs, so "count the digits of 0" returns 0 instead of 1.',
    'Negative numbers: -7 % 10 is -7 in Java, C++ and JavaScript but 3 in Python. Decide how to treat the sign first.',
    'Division: 5 / 2 is 2.5 in JavaScript and Python, but 2 in Java and C++ (for ints).',
    'Off-by-one: is it i < n or i <= n? Check the first and the last value the loop sees.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Before coding, say the input range aloud and choose int or long; interviewers notice.',
    'Know the shortcuts: digit count = String(n).length or ⌊log₁₀ n⌋ + 1; sum of digits of a big number given as text needs no maths at all.',
    'Try the edge cases every time: 0, 1, the largest value, a negative value, and a number with zeros inside (9000009).',
    'Next step: Arrays, where the same loop walks over many numbers at once.',
  ]),
];
