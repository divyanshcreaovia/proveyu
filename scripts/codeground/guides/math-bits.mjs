import { slide } from '../lib.mjs';

export default [
  slide('Why it matters', 'Small tricks, huge speed-ups', [
    'Primes, GCD, modular arithmetic and bit tricks show up in aptitude-style coding rounds and as building blocks inside harder problems.',
    'The right formula replaces a loop of 10¹⁸ steps with one line.',
    'Bit manipulation questions (single number, count set bits, power of two) are quick wins if you know the tricks.',
  ]),
  slide('Real-world use', 'Maths and bits in software', [
    'Online security (HTTPS, UPI, RSA) depends on primes and fast modular power.',
    'Hashing and checksums (CRC, UPI and Aadhaar check digits) use modulo arithmetic.',
    'Feature flags and permissions are stored as bits: read = 1, write = 2, execute = 4; chmod 755 is bits.',
    'Graphics and networking use bit masks: IP subnets (255.255.255.0), RGB colours packed in one integer.',
  ]),
  slide('Core idea', 'Know the core tools', [
    'GCD by Euclid: gcd(a, b) = gcd(b, a % b), and LCM = a / gcd × b.',
    'Prime check up to √n; many primes at once with the Sieve of Eratosthenes.',
    'Modulo keeps numbers small: (a + b) % m and (a × b) % m can be taken at every step.',
    'Bits: & (and), | (or), ^ (xor), ~ (not), << and >> (shift). x & 1 tells odd or even.',
  ]),
  slide('Syntax', 'GCD and bit operators', [], {
    javascript: `const gcd = (a, b) => b === 0 ? a : gcd(b, a % b);
const isOdd = x => (x & 1) === 1;
const powerOfTwo = x => x > 0 && (x & (x - 1)) === 0;
// bit ops in JS work on 32-bit ints; use BigInt for larger values`,
    python: `from math import gcd
is_odd = lambda x: x & 1 == 1
power_of_two = lambda x: x > 0 and x & (x - 1) == 0
bin(10).count('1')       # set bits: 2`,
    java: `static long gcd(long a, long b) { return b == 0 ? a : gcd(b, a % b); }
boolean isOdd = (x & 1) == 1;
boolean powerOfTwo = x > 0 && (x & (x - 1)) == 0;
int setBits = Integer.bitCount(x);`,
    cpp: `long long g = __gcd(a, b);          // or std::gcd (C++17)
bool isOdd = x & 1;
bool powerOfTwo = x > 0 && (x & (x - 1)) == 0;
int setBits = __builtin_popcount(x);`,
  }),
  slide('Pattern', 'Fast power: aⁿ in O(log n)', [
    'Square the base and halve the exponent; multiply into the result when the current bit of n is 1.',
    'Take % m after every multiplication to keep values small. The answers to "mod 10⁹ + 7" questions rely on this.',
  ], {
    javascript: `function power(a, n, m) {
  let r = 1n, b = BigInt(a) % BigInt(m), M = BigInt(m), e = BigInt(n);
  while (e > 0n) {
    if (e & 1n) r = r * b % M;
    b = b * b % M; e >>= 1n;
  }
  return Number(r);   // BigInt avoids losing precision
}`,
    python: `def power(a, n, m):
    return pow(a, n, m)        # built in, already O(log n)`,
    java: `static long power(long a, long n, long m) {
    long r = 1; a %= m;
    while (n > 0) {
        if ((n & 1) == 1) r = r * a % m;
        a = a * a % m; n >>= 1;
    }
    return r;
}`,
    cpp: `long long power(long long a, long long n, long long m) {
    long long r = 1; a %= m;
    while (n > 0) {
        if (n & 1) r = r * a % m;
        a = a * a % m; n >>= 1;
    }
    return r;
}`,
  }),
  slide('Pattern', 'XOR tricks', [
    'x ^ x = 0 and x ^ 0 = x, so XOR-ing a list cancels pairs: the number that appears once is left.',
    'x & (x − 1) clears the lowest set bit: count set bits by repeating it, or test a power of two.',
    'Subsets as bits: for n items, the numbers 0 .. 2ⁿ − 1 list every subset; bit i says whether item i is taken.',
  ], {
    javascript: `let single = 0;
for (const x of nums) single ^= x;`,
    python: `single = 0
for x in nums:
    single ^= x`,
    java: `int single = 0;
for (int x : nums) single ^= x;`,
    cpp: `int single = 0;
for (int x : nums) single ^= x;`,
  }),
  slide('Common mistakes', 'What breaks maths code', [
    'Overflow: a × b with a, b near 10⁹ needs 64-bit (long / long long) before taking %. JavaScript loses precision past 2⁵³: use BigInt.',
    'Negative modulo: (a − b) % m can be negative in Java, C++ and JavaScript. Add m: ((a − b) % m + m) % m.',
    'Floating point: 0.1 + 0.2 is not 0.3. Compare with integers or a small tolerance.',
    'Operator precedence: x & 1 == 1 means x & (1 == 1) in Java, C++ and JavaScript. Use brackets.',
  ]),
  slide('Going further', 'For advanced learners and interviews', [
    'Modular inverse with Fermat: a⁻¹ = a^(m − 2) mod m for prime m; used for nCr mod p.',
    'Sieve variants: smallest prime factor sieve factorises many numbers fast.',
    'Bitmask DP solves "visit all cities" style problems for n ≤ 20.',
    'Know Gray code, the inclusion–exclusion principle and the pigeonhole principle for puzzle rounds.',
    'Next step: Lists, Trees and Graphs.',
  ]),
];
