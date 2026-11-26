import { q } from '../lib.mjs';
import guide from '../guides/math-bits.mjs';

export default {
  key: 'math-bits', title: 'Math and Bit tricks', summary: 'GCD, primes, modular power, counting bits',
  guide,
  problems: [
    q('gcd', 'Greatest common divisor', 'E', 'a:int, b:int', 'Return gcd(a, b) for non-negative integers, not both zero.', "Euclid's algorithm.",
      (a, b) => { while (b) { const t = a % b; a = b; b = t; } return a; }, [['48\n18', '6'], ['17\n5', '1'], ['0\n7', '7'], ['100\n10', '10'], ['270\n192', '6']]),
    q('is-prime', 'Prime check', 'E', 'n:int', 'Return true if n is a prime number.', 'Try divisors up to the square root.',
      n => { if (n < 2) return false; for (let d = 2; d * d <= n; d++) if (n % d === 0) return false; return true; }, [['11', 'true'], ['1', 'false'], ['2', 'true'], ['91', 'false'], ['1000003', 'true']]),
    q('power-of-two', 'Power of two', 'E', 'n:int', 'Return true if n is a power of two.', 'A power of two has exactly one set bit.',
      n => n > 0 && (n & (n - 1)) === 0, [['16', 'true'], ['0', 'false'], ['1', 'true'], ['18', 'false'], ['1073741824', 'true']]),
    q('count-bits', 'Count set bits', 'E', 'n:int', 'Return how many 1 bits the non-negative integer n (below 2^31) has.', 'n & (n - 1) removes one set bit.',
      n => { let c = 0; while (n) { n &= n - 1; c++; } return c; }, [['11', '3'], ['0', '0'], ['128', '1'], ['255', '8'], ['2147483647', '31']]),
    q('single-number', 'Single number', 'M', 'nums:ints', 'Every value appears twice except one. Return that one using O(1) extra space.', 'XOR all values.',
      nums => nums.reduce((a, b) => a ^ b, 0), [['4 1 2 1 2', '4'], ['2 2 1', '1'], ['7', '7'], ['-3 5 5', '-3'], ['10 14 10', '14']]),
    q('count-primes', 'Count primes below N', 'M', 'n:int', 'Return how many primes are smaller than n (n up to 1,000,000).', 'Sieve of Eratosthenes.',
      n => { if (n < 3) return 0; const comp = new Uint8Array(n); let c = 0; for (let i = 2; i < n; i++) { if (comp[i]) continue; c++; for (let j = i * i; j < n; j += i) comp[j] = 1; } return c; },
      [['10', '4'], ['0', '0'], ['2', '0'], ['100', '25'], ['1000000', '78498']]),
    q('mod-power', 'Modular power', 'H', 'base:int, exp:int, mod:int', 'Return (base^exp) % mod. exp can be up to 10^9 and mod up to 10^9+7.', 'Square and multiply; keep products in 64-bit integers (BigInt in JavaScript) so they stay exact.',
      (base, exp, mod) => { let b = BigInt(base) % BigInt(mod), e = BigInt(exp), r = BigInt(1) % BigInt(mod); const m = BigInt(mod), one = BigInt(1), zero = BigInt(0); while (e > zero) { if (e & one) r = r * b % m; b = b * b % m; e >>= one; } return Number(r); },
      [['2\n10\n1000', '24'], ['3\n0\n7', '1'], ['5\n3\n13', '8'], ['2\n1000000000\n1000000007', '140625001'], ['7\n256\n13', '9']]),
  ],
};
