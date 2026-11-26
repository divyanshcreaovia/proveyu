import { q } from '../lib.mjs';
import guide from '../guides/basics.mjs';

export default {
  key: 'basics', title: 'Basics', summary: 'Loops, digits, number tricks and simple conversions',
  guide,
  problems: [
    q('sum-n', 'Sum of first N numbers', 'E', 'n:int', 'Return the sum 1 + 2 + ... + n. For n = 0 the sum is 0.', 'There is a formula: n(n+1)/2.',
      n => n * (n + 1) / 2, [['5', '15'], ['0', '0'], ['1', '1'], ['100', '5050'], ['1000', '500500']]),
    q('count-digits', 'Count the digits', 'E', 'n:int', 'Return how many digits the non-negative integer n has.', 'Divide by 10 until nothing is left. 0 has one digit.',
      n => String(n).length, [['4521', '4'], ['0', '1'], ['9', '1'], ['100000', '6'], ['1234567890', '10']]),
    q('reverse-number', 'Reverse a number', 'E', 'n:int', 'Return the digits of n in reverse order, without leading zeros.', 'Build the result: result = result * 10 + lastDigit.',
      n => Number(String(n).split('').reverse().join('')), [['1234', '4321'], ['100', '1'], ['7', '7'], ['9000009', '9000009'], ['120300', '3021']]),
    q('fizzbuzz', 'FizzBuzz', 'E', 'n:int', 'Return an array for the numbers 1..n: "FizzBuzz" for multiples of 15, "Fizz" for multiples of 3, "Buzz" for multiples of 5, otherwise the number itself (as text).',
      'Check 15 before 3 and 5.', n => Array.from({ length: n }, (_, i) => (i + 1) % 15 === 0 ? 'FizzBuzz' : (i + 1) % 3 === 0 ? 'Fizz' : (i + 1) % 5 === 0 ? 'Buzz' : String(i + 1)),
      [['5', '1 2 Fizz 4 Buzz'], ['1', '1'], ['3', '1 2 Fizz'], ['15', '1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz'], ['6', '1 2 Fizz 4 Buzz Fizz']]),
    q('palindrome-number', 'Palindrome number', 'E', 'n:int', 'Return true if the integer n reads the same forwards and backwards. Negative numbers are not palindromes.', 'Compare the number with its reverse.',
      n => n >= 0 && String(n) === String(n).split('').reverse().join(''), [['121', 'true'], ['-121', 'false'], ['10', 'false'], ['0', 'true'], ['12344321', 'true']]),
    q('armstrong', 'Armstrong number', 'M', 'n:int', 'A number is an Armstrong number when the sum of its digits, each raised to the number of digits, equals the number. Return true or false.',
      'First count the digits, then add each digit raised to that count.', n => String(n).split('').reduce((s, d) => s + Math.pow(Number(d), String(n).length), 0) === n,
      [['153', 'true'], ['10', 'false'], ['9474', 'true'], ['0', 'true'], ['9475', 'false']]),
    q('to-binary', 'Decimal to binary', 'M', 'n:int', 'Return the binary form of the non-negative integer n as a string, without a built-in conversion.', 'Collect n % 2 and halve n; the bits come out last first.',
      n => { let out = ''; do { out = (n % 2) + out; n = Math.floor(n / 2); } while (n > 0); return out; }, [['10', '1010'], ['0', '0'], ['1', '1'], ['255', '11111111'], ['1024', '10000000000']]),
  ],
};
