"""Coding problems. Each has a reference solution (solve: input text -> output text) and a random input generator; hidden test
outputs are produced by running the reference, so they are always consistent with the statement."""
import heapq
from bisect import bisect_left
from collections import Counter, deque

STARTER = {
    "python": "import sys\n\ndata = sys.stdin.read().split()\n# write your code here\n",
    "java": "import java.util.*;\nimport java.io.*;\n\npublic class Main {\n    public static void main(String[] args) throws IOException {\n"
            "        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\n        // write your code here\n    }\n}\n",
    "cpp": "#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n    ios::sync_with_stdio(false);\n    cin.tie(nullptr);\n"
           "    // write your code here\n    return 0;\n}\n",
    "javascript": "const data = require('fs').readFileSync(0, 'utf8').trim().split(/\\s+/);\n// write your code here\n",
}
STARTER_LINE = dict(STARTER, python="import sys\n\ntext = sys.stdin.read()\n# write your code here\n",
                    javascript="const text = require('fs').readFileSync(0, 'utf8');\n// write your code here\n")


def ints(s):
    return list(map(int, s.split()))


def arr_input(rng, n_lo, n_hi, v_lo, v_hi):
    n = rng.randint(n_lo, n_hi)
    return f"{n}\n{' '.join(str(rng.randint(v_lo, v_hi)) for _ in range(n))}\n"


WORDS = "alpha beta gamma delta river stone cloud green apple mango paper light code data cat act tac dog god odg note tone sun".split()

PROBLEMS = []


def problem(pid, level, title, text, samples, edge=(), line_input=False):
    def wrap(fn):
        PROBLEMS.append({"id": pid, "level": level, "title": title, "text": text, "samples": samples, "edge": list(edge),
                         "solve": fn, "gen": None, "line": line_input})
        return fn
    return wrap


def gen(pid):
    def wrap(fn):
        next(p for p in PROBLEMS if p["id"] == pid)["gen"] = fn
        return fn
    return wrap


# ================================================================== easy

@problem("vowels", 1, "Count the vowels",
         "Read one line of text and print how many vowels (a, e, i, o, u, upper or lower case) it contains.\n\n"
         "Input: one line, at most 10^5 characters.\nOutput: the number of vowels.", ["Hello World", "PROGRAMMING is Fun"], ["xyz", "AEIOU aeiou"], True)
def _(s):
    return str(sum(c in "aeiouAEIOU" for c in s.rstrip("\n")))


@gen("vowels")
def _(rng):
    return "".join(rng.choice("abcdefghijklmnopqrstuvwxyzAEIOU ") for _ in range(rng.randint(1, 3000)))


@problem("revwords", 1, "Reverse the words",
         "Read a sentence and print its words in reverse order, separated by single spaces.\n\n"
         "Input: one line with 1 to 10^4 words separated by spaces.\nOutput: the words in reverse order.",
         ["I love coding", "placement season starts today"], ["single"], True)
def _(s):
    return " ".join(s.split()[::-1])


@gen("revwords")
def _(rng):
    return " ".join(rng.choice(WORDS) for _ in range(rng.randint(1, 500)))


@problem("evensum", 1, "Sum of even numbers",
         "Given n integers, print the sum of the even ones.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers (−10^9 to 10^9).\n"
         "Output: the sum of the even numbers (0 if there are none).", ["5\n1 2 3 4 5", "3\n1 3 5"], ["1\n-4", "4\n1000000000 1000000000 -2 7"])
def _(s):
    a = ints(s)[1:]
    return str(sum(x for x in a if x % 2 == 0))


@gen("evensum")
def _(rng):
    return arr_input(rng, 1, 2000, -10 ** 9, 10 ** 9)


@problem("palin", 1, "Palindrome check",
         "Read a string and print YES if it reads the same forwards and backwards ignoring case and non-letter characters, otherwise NO.\n\n"
         "Input: one line (at most 10^5 characters).\nOutput: YES or NO.", ["Madam", "Never odd or even!"], ["ab", "A", "Race car?"], True)
def _(s):
    t = [c.lower() for c in s if c.isalpha()]
    return "YES" if t == t[::-1] else "NO"


@gen("palin")
def _(rng):
    half = "".join(rng.choice("abcxyzAB") for _ in range(rng.randint(1, 200)))
    return half + (half[::-1] if rng.random() < 0.5 else rng.choice("pq") + half[::-1][1:])


@problem("primes", 1, "Count primes",
         "Print how many prime numbers are less than or equal to N.\n\nInput: one integer N (1 ≤ N ≤ 10^6).\nOutput: the count of primes ≤ N.",
         ["10", "30"], ["1", "2", "1000000"])
def _(s):
    n = int(s)
    if n < 2:
        return "0"
    sieve = bytearray([1]) * (n + 1)
    sieve[0] = sieve[1] = 0
    i = 2
    while i * i <= n:
        if sieve[i]:
            sieve[i * i::i] = bytearray(len(range(i * i, n + 1, i)))
        i += 1
    return str(sum(sieve))


@gen("primes")
def _(rng):
    return str(rng.randint(1, 10 ** 6))


@problem("gcdlcm", 1, "GCD and LCM",
         "Given two positive integers a and b, print their GCD and LCM separated by a space.\n\nInput: a and b (1 ≤ a, b ≤ 10^9).\n"
         "Output: GCD and LCM.", ["12 18", "7 5"], ["1 1", "1000000000 999999999"])
def _(s):
    from math import gcd
    a, b = ints(s)
    g = gcd(a, b)
    return f"{g} {a // g * b}"


@gen("gcdlcm")
def _(rng):
    return f"{rng.randint(1, 10 ** 9)} {rng.randint(1, 10 ** 9)}"


@problem("charfreq", 1, "Character frequency",
         "Read a word of lowercase letters and print each distinct letter with its count, in alphabetical order, one per line as ‘letter count’.\n\n"
         "Input: one word (1 to 10^5 lowercase letters).\nOutput: one line per distinct letter.", ["banana", "apple"], ["z", "aaaa"])
def _(s):
    c = Counter(s.strip())
    return "\n".join(f"{k} {c[k]}" for k in sorted(c))


@gen("charfreq")
def _(rng):
    return "".join(rng.choice("abcdefghij") for _ in range(rng.randint(1, 5000)))


@problem("missing", 1, "Find the missing number",
         "The numbers 1 to n appear in the input once each, except one which is missing. Print the missing number.\n\n"
         "Input: first line n (2 ≤ n ≤ 10^5); second line n − 1 distinct integers from 1 to n.\nOutput: the missing number.",
         ["5\n1 2 4 5", "3\n3 1"], ["2\n2", "2\n1"])
def _(s):
    v = ints(s)
    n = v[0]
    return str(n * (n + 1) // 2 - sum(v[1:]))


@gen("missing")
def _(rng):
    n = rng.randint(2, 20000)
    a = list(range(1, n + 1))
    a.remove(rng.randint(1, n))
    rng.shuffle(a)
    return f"{n}\n{' '.join(map(str, a))}"


@problem("dedupe", 1, "Remove duplicates",
         "Given n integers, print them with duplicates removed, keeping the first occurrence of each and the original order.\n\n"
         "Input: first line n (1 ≤ n ≤ 10^5); second line n integers.\nOutput: the remaining numbers separated by spaces.",
         ["7\n4 5 4 2 5 9 2", "3\n1 1 1"], ["1\n7"])
def _(s):
    seen, out = set(), []
    for x in ints(s)[1:]:
        if x not in seen:
            seen.add(x)
            out.append(x)
    return " ".join(map(str, out))


@gen("dedupe")
def _(rng):
    return arr_input(rng, 1, 3000, 1, 300)


# ================================================================== medium

@problem("kadane", 2, "Maximum subarray sum",
         "Given n integers, print the largest sum of a non-empty contiguous subarray.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers "
         "(−10^4 to 10^4).\nOutput: the maximum subarray sum.", ["9\n-2 1 -3 4 -1 2 1 -5 4", "3\n-3 -1 -2"], ["1\n-7", "5\n1 2 3 4 5"])
def _(s):
    a = ints(s)[1:]
    best = cur = a[0]
    for x in a[1:]:
        cur = max(x, cur + x)
        best = max(best, cur)
    return str(best)


@gen("kadane")
def _(rng):
    return arr_input(rng, 1, 30000, -10 ** 4, 10 ** 4)


@problem("brackets", 2, "Balanced brackets",
         "Print YES if the string of brackets ()[]{} is balanced, otherwise NO.\n\nInput: one line of at most 10^5 bracket characters.\nOutput: YES or NO.",
         ["{[()()]}", "([)]"], ["(", "()", "}{"], True)
def _(s):
    st, pair = [], {")": "(", "]": "[", "}": "{"}
    for c in s.strip():
        if c in "([{":
            st.append(c)
        elif not st or st.pop() != pair[c]:
            return "NO"
    return "YES" if not st else "NO"


@gen("brackets")
def _(rng):
    if rng.random() < 0.5:
        out, st = [], []
        for _ in range(rng.randint(1, 3000)):
            if st and rng.random() < 0.45:
                out.append({"(": ")", "[": "]", "{": "}"}[st.pop()])
            else:
                c = rng.choice("([{")
                st.append(c)
                out.append(c)
        out += [{"(": ")", "[": "]", "{": "}"}[c] for c in reversed(st)]
        return "".join(out)
    return "".join(rng.choice("()[]{}") for _ in range(rng.randint(1, 3000)))


@problem("pairsum", 2, "Pairs with a given sum",
         "Given n integers and a target K, print how many pairs (i < j) have a[i] + a[j] = K.\n\nInput: first line n and K (1 ≤ n ≤ 10^5, |K| ≤ 2·10^9); "
         "second line n integers (|a[i]| ≤ 10^9).\nOutput: the number of pairs.", ["5 6\n1 5 7 -1 5", "4 2\n1 1 1 1"], ["1 5\n5"])
def _(s):
    v = ints(s)
    n, k, a = v[0], v[1], v[2:]
    seen, cnt = Counter(), 0
    for x in a:
        cnt += seen[k - x]
        seen[x] += 1
    return str(cnt)


@gen("pairsum")
def _(rng):
    n = rng.randint(1, 20000)
    return f"{n} {rng.randint(-20, 40)}\n{' '.join(str(rng.randint(-10, 30)) for _ in range(n))}"


@problem("longsub", 2, "Longest substring without repeats",
         "Print the length of the longest substring of s that has no repeated characters.\n\nInput: one string of 1 to 10^5 lowercase letters.\n"
         "Output: the length.", ["abcabcbb", "bbbbb"], ["a", "pwwkew", "abcdefghijklmnopqrstuvwxyz"])
def _(s):
    s = s.strip()
    last, start, best = {}, 0, 0
    for i, c in enumerate(s):
        if last.get(c, -1) >= start:
            start = last[c] + 1
        last[c] = i
        best = max(best, i - start + 1)
    return str(best)


@gen("longsub")
def _(rng):
    return "".join(rng.choice("abcdefghijklmnop"[:rng.randint(2, 16)]) for _ in range(rng.randint(1, 20000)))


@problem("merge", 2, "Merge intervals",
         "Given n closed intervals, merge all overlapping ones (touching intervals like [1,3] and [3,5] also merge). Print the number of merged "
         "intervals, then each merged interval as ‘start end’ in increasing order of start.\n\nInput: first line n (1 ≤ n ≤ 10^5); then n lines ‘l r’ "
         "(0 ≤ l ≤ r ≤ 10^9).\nOutput: count, then one interval per line.", ["4\n1 3\n2 6\n8 10\n15 18", "2\n1 4\n4 5"], ["1\n5 5"])
def _(s):
    v = ints(s)
    iv = sorted(zip(v[1::2], v[2::2]))
    out = []
    for l, r in iv:
        if out and l <= out[-1][1]:
            out[-1][1] = max(out[-1][1], r)
        else:
            out.append([l, r])
    return "\n".join([str(len(out))] + [f"{l} {r}" for l, r in out])


@gen("merge")
def _(rng):
    n = rng.randint(1, 3000)
    rows = []
    for _ in range(n):
        l = rng.randint(0, 10000)
        rows.append(f"{l} {l + rng.randint(0, 50)}")
    return f"{n}\n" + "\n".join(rows)


@problem("kth", 2, "K-th largest element",
         "Given n integers and k, print the k-th largest element (counting duplicates).\n\nInput: first line n and k (1 ≤ k ≤ n ≤ 10^5); second line "
         "n integers.\nOutput: the k-th largest value.", ["6 2\n3 2 1 5 6 4", "9 4\n3 2 3 1 2 4 5 5 6"], ["1 1\n-5"])
def _(s):
    v = ints(s)
    n, k, a = v[0], v[1], v[2:]
    return str(heapq.nlargest(k, a)[-1])


@gen("kth")
def _(rng):
    n = rng.randint(1, 20000)
    return f"{n} {rng.randint(1, n)}\n{' '.join(str(rng.randint(-10 ** 6, 10 ** 6)) for _ in range(n))}"


@problem("rotate", 2, "Rotate an array",
         "Rotate an array of n integers to the right by k steps and print it.\n\nInput: first line n and k (1 ≤ n ≤ 10^5, 0 ≤ k ≤ 10^9); second line n "
         "integers.\nOutput: the rotated array.", ["7 3\n1 2 3 4 5 6 7", "2 5\n10 20"], ["1 100\n9", "3 0\n1 2 3"])
def _(s):
    v = ints(s)
    n, k, a = v[0], v[1], v[2:]
    k %= n
    return " ".join(map(str, a[n - k:] + a[:n - k]))


@gen("rotate")
def _(rng):
    n = rng.randint(1, 20000)
    return f"{n} {rng.randint(0, 10 ** 9)}\n{' '.join(str(rng.randint(-1000, 1000)) for _ in range(n))}"


@problem("anagrams", 2, "Anagram groups",
         "Given n words, print how many groups of anagrams there are (words with the same letters in any order are in one group).\n\n"
         "Input: first line n (1 ≤ n ≤ 10^4); then n words of lowercase letters, each up to 20 letters.\nOutput: the number of groups.",
         ["6\neat tea tan ate nat bat", "3\nabc def ghi"], ["1\nz"])
def _(s):
    words = s.split()[1:]
    return str(len({"".join(sorted(w)) for w in words}))


@gen("anagrams")
def _(rng):
    n = rng.randint(1, 3000)
    return f"{n}\n" + " ".join(rng.choice(WORDS) if rng.random() < 0.6 else "".join(rng.choice("abc") for _ in range(rng.randint(1, 5)))
                               for _ in range(n))


@problem("rangesum", 2, "Range sum queries",
         "Given an array and q queries ‘l r’ (1-based, inclusive), print the sum of a[l..r] for each query on its own line.\n\nInput: first line n and q "
         "(1 ≤ n, q ≤ 10^5); second line n integers (|a[i]| ≤ 10^4); then q lines ‘l r’ (1 ≤ l ≤ r ≤ n).\nOutput: q lines.",
         ["5 3\n1 2 3 4 5\n1 3\n2 5\n4 4", "1 1\n-7\n1 1"], [])
def _(s):
    v = ints(s)
    n, q = v[0], v[1]
    a = v[2:2 + n]
    pre = [0]
    for x in a:
        pre.append(pre[-1] + x)
    qs = v[2 + n:]
    return "\n".join(str(pre[qs[2 * i + 1]] - pre[qs[2 * i] - 1]) for i in range(q))


@gen("rangesum")
def _(rng):
    n, q = rng.randint(1, 5000), rng.randint(1, 5000)
    rows = []
    for _ in range(q):
        l = rng.randint(1, n)
        rows.append(f"{l} {rng.randint(l, n)}")
    return f"{n} {q}\n{' '.join(str(rng.randint(-10 ** 4, 10 ** 4)) for _ in range(n))}\n" + "\n".join(rows)


@problem("lis", 2, "Longest increasing subsequence",
         "Print the length of the longest strictly increasing subsequence of the array.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers.\n"
         "Output: the length.", ["8\n10 9 2 5 3 7 101 18", "4\n4 4 4 4"], ["1\n5", "5\n1 2 3 4 5"])
def _(s):
    tails = []
    for x in ints(s)[1:]:
        i = bisect_left(tails, x)
        if i == len(tails):
            tails.append(x)
        else:
            tails[i] = x
    return str(len(tails))


@gen("lis")
def _(rng):
    return arr_input(rng, 1, 30000, -10 ** 6, 10 ** 6)


@problem("platforms", 2, "Minimum platforms",
         "Trains arrive and depart at a station (times in minutes from midnight). A platform is busy from a train's arrival to its departure, "
         "inclusive. Print the minimum number of platforms needed so no train waits.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n arrival "
         "times; third line n departure times (arrival ≤ departure ≤ 2359).\nOutput: the number of platforms.",
         ["6\n900 940 950 1100 1500 1800\n910 1200 1120 1130 1900 2000", "2\n100 200\n150 250"], ["1\n0\n0", "2\n500 500\n500 500"])
def _(s):
    v = ints(s)
    n = v[0]
    arr, dep = sorted(v[1:1 + n]), sorted(v[1 + n:1 + 2 * n])
    i = j = cur = best = 0
    while i < n:
        if arr[i] <= dep[j]:
            cur += 1
            best = max(best, cur)
            i += 1
        else:
            cur -= 1
            j += 1
    return str(best)


@gen("platforms")
def _(rng):
    n = rng.randint(1, 3000)
    a = [rng.randint(0, 2300) for _ in range(n)]
    d = [min(2359, x + rng.randint(0, 120)) for x in a]
    return f"{n}\n{' '.join(map(str, a))}\n{' '.join(map(str, d))}"


@problem("spiral", 2, "Spiral order",
         "Print the elements of an r × c matrix in clockwise spiral order, starting from the top-left, separated by spaces.\n\nInput: first line r and c "
         "(1 ≤ r, c ≤ 300); then r lines of c integers.\nOutput: one line.", ["3 3\n1 2 3\n4 5 6\n7 8 9", "2 4\n1 2 3 4\n5 6 7 8"], ["1 1\n42", "3 1\n1\n2\n3"])
def _(s):
    v = ints(s)
    r, c = v[0], v[1]
    m = [v[2 + i * c:2 + (i + 1) * c] for i in range(r)]
    out, top, bot, left, right = [], 0, r - 1, 0, c - 1
    while top <= bot and left <= right:
        out += m[top][left:right + 1]
        for i in range(top + 1, bot + 1):
            out.append(m[i][right])
        if top < bot:
            out += m[bot][left:right][::-1]
        if left < right:
            for i in range(bot - 1, top, -1):
                out.append(m[i][left])
        top, bot, left, right = top + 1, bot - 1, left + 1, right - 1
    return " ".join(map(str, out))


@gen("spiral")
def _(rng):
    r, c = rng.randint(1, 60), rng.randint(1, 60)
    return f"{r} {c}\n" + "\n".join(" ".join(str(rng.randint(0, 99)) for _ in range(c)) for _ in range(r))


@problem("islands", 2, "Number of islands",
         "A grid has land (1) and water (0). Cells connect up, down, left and right. Print the number of islands.\n\nInput: first line r and c "
         "(1 ≤ r, c ≤ 500); then r lines, each a string of c characters 0/1.\nOutput: the number of islands.",
         ["4 5\n11000\n11000\n00100\n00011", "1 1\n0"], ["2 2\n11\n11", "3 3\n101\n010\n101"])
def _(s):
    lines = s.split()
    r, c = int(lines[0]), int(lines[1])
    g = [list(x) for x in lines[2:2 + r]]
    cnt = 0
    for i in range(r):
        for j in range(c):
            if g[i][j] == "1":
                cnt += 1
                g[i][j] = "0"
                dq = deque([(i, j)])
                while dq:
                    x, y = dq.popleft()
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        a, b = x + dx, y + dy
                        if 0 <= a < r and 0 <= b < c and g[a][b] == "1":
                            g[a][b] = "0"
                            dq.append((a, b))
    return str(cnt)


@gen("islands")
def _(rng):
    r, c = rng.randint(1, 120), rng.randint(1, 120)
    p = rng.uniform(0.2, 0.6)
    return f"{r} {c}\n" + "\n".join("".join("1" if rng.random() < p else "0" for _ in range(c)) for _ in range(r))


@problem("coins", 2, "Coin change ways",
         "Given coin values (unlimited supply of each) and an amount, print the number of ways to make the amount, modulo 1 000 000 007. "
         "Order of coins does not matter.\n\nInput: first line n and amount (1 ≤ n ≤ 100, 0 ≤ amount ≤ 10^4); second line n distinct coin values (1 to 10^4).\n"
         "Output: the number of ways mod 10^9+7.", ["3 5\n1 2 5", "1 3\n2"], ["2 0\n3 7"])
def _(s):
    v = ints(s)
    n, amt, coins = v[0], v[1], v[2:]
    dp = [1] + [0] * amt
    for c in coins:
        for x in range(c, amt + 1):
            dp[x] = (dp[x] + dp[x - c]) % 1_000_000_007
    return str(dp[amt])


@gen("coins")
def _(rng):
    n = rng.randint(1, 15)
    coins = rng.sample(range(1, 60), n)
    return f"{n} {rng.randint(0, 5000)}\n{' '.join(map(str, coins))}"


# ================================================================== hard

@problem("dijkstra", 3, "Shortest delivery route",
         "A city has n junctions (1..n) and m two-way roads with travel times. Print the shortest travel time from junction 1 to junction n, or −1 if n "
         "cannot be reached.\n\nInput: first line n and m (2 ≤ n ≤ 10^4, 0 ≤ m ≤ 5·10^4); then m lines ‘u v w’ (1 ≤ w ≤ 10^4).\nOutput: the shortest time or -1.",
         ["4 4\n1 2 4\n1 3 1\n3 2 2\n2 4 5", "3 1\n1 2 7"], ["2 1\n1 2 10"])
def _(s):
    v = ints(s)
    n, m = v[0], v[1]
    adj = [[] for _ in range(n + 1)]
    for i in range(m):
        a, b, w = v[2 + 3 * i:5 + 3 * i]
        adj[a].append((b, w))
        adj[b].append((a, w))
    dist = [None] * (n + 1)
    pq = [(0, 1)]
    while pq:
        d, u = heapq.heappop(pq)
        if dist[u] is not None:
            continue
        dist[u] = d
        if u == n:
            break
        for b, w in adj[u]:
            if dist[b] is None:
                heapq.heappush(pq, (d + w, b))
    return str(dist[n] if dist[n] is not None else -1)


@gen("dijkstra")
def _(rng):
    n, m = rng.randint(2, 2000), rng.randint(0, 6000)
    return f"{n} {m}\n" + "\n".join(f"{rng.randint(1, n)} {rng.randint(1, n)} {rng.randint(1, 10 ** 4)}" for _ in range(m))


@problem("editdist", 3, "Edit distance",
         "Print the minimum number of single-character insertions, deletions or replacements needed to turn word a into word b.\n\n"
         "Input: two lines, a and b (each 1 to 500 lowercase letters).\nOutput: the edit distance.", ["horse\nros", "intention\nexecution"], ["a\na", "abc\nxyz"])
def _(s):
    a, b = s.split()[:2]
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i] + [0] * len(b)
        for j, cb in enumerate(b, 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb))
        prev = cur
    return str(prev[-1])


@gen("editdist")
def _(rng):
    k = rng.randint(2, 6)
    return "".join(rng.choice("abcdef"[:k]) for _ in range(rng.randint(1, 300))) + "\n" + "".join(rng.choice("abcdef"[:k]) for _ in range(rng.randint(1, 300)))


@problem("longpal", 3, "Longest palindromic substring",
         "Print the length of the longest substring of s that is a palindrome.\n\nInput: one string of 1 to 1000 lowercase letters.\nOutput: the length.",
         ["babad", "cbbd"], ["a", "aaaa", "abcde"])
def _(s):
    s = s.strip()
    best = 1
    for c in range(len(s)):
        for l, r in ((c, c), (c, c + 1)):
            while l >= 0 and r < len(s) and s[l] == s[r]:
                l -= 1
                r += 1
            best = max(best, r - l - 1)
    return str(best)


@gen("longpal")
def _(rng):
    return "".join(rng.choice("ab" if rng.random() < 0.5 else "abcd") for _ in range(rng.randint(1, 600)))


@problem("knapsack", 3, "Project selection (0/1 knapsack)",
         "You have n projects; project i needs w[i] hours and earns v[i] points. With at most W hours, each project done at most once, print the "
         "maximum points.\n\nInput: first line n and W (1 ≤ n ≤ 100, 1 ≤ W ≤ 10^4); then n lines ‘w v’ (1 ≤ w ≤ 10^4, 1 ≤ v ≤ 10^6).\nOutput: the maximum points.",
         ["3 50\n10 60\n20 100\n30 120", "2 5\n6 10\n7 20"], ["1 1\n1 5"])
def _(s):
    v = ints(s)
    n, W = v[0], v[1]
    dp = [0] * (W + 1)
    for i in range(n):
        w, val = v[2 + 2 * i], v[3 + 2 * i]
        for x in range(W, w - 1, -1):
            if dp[x - w] + val > dp[x]:
                dp[x] = dp[x - w] + val
    return str(dp[W])


@gen("knapsack")
def _(rng):
    n, W = rng.randint(1, 60), rng.randint(1, 3000)
    return f"{n} {W}\n" + "\n".join(f"{rng.randint(1, 600)} {rng.randint(1, 10 ** 6)}" for _ in range(n))


@problem("courses", 3, "Can all courses be finished?",
         "There are n courses (1..n) and m prerequisite pairs ‘a b’ meaning b must be taken before a. Print YES if all courses can be finished "
         "(no cycle of prerequisites), otherwise NO.\n\nInput: first line n and m (1 ≤ n ≤ 10^4, 0 ≤ m ≤ 5·10^4); then m lines ‘a b’.\nOutput: YES or NO.",
         ["2 1\n2 1", "2 2\n1 2\n2 1"], ["1 0", "3 3\n1 2\n2 3\n3 1"])
def _(s):
    v = ints(s)
    n, m = v[0], v[1]
    adj = [[] for _ in range(n + 1)]
    indeg = [0] * (n + 1)
    for i in range(m):
        a, b = v[2 + 2 * i], v[3 + 2 * i]
        adj[b].append(a)
        indeg[a] += 1
    dq = deque(i for i in range(1, n + 1) if indeg[i] == 0)
    seen = 0
    while dq:
        u = dq.popleft()
        seen += 1
        for x in adj[u]:
            indeg[x] -= 1
            if indeg[x] == 0:
                dq.append(x)
    return "YES" if seen == n else "NO"


@gen("courses")
def _(rng):
    n = rng.randint(1, 2000)
    m = rng.randint(0, 4000)
    acyclic = rng.random() < 0.5
    rows = []
    for _ in range(m):
        a, b = rng.randint(1, n), rng.randint(1, n)
        if acyclic:
            if a == b:
                continue
            a, b = max(a, b), min(a, b)
        rows.append(f"{a} {b}")
    return f"{n} {len(rows)}\n" + "\n".join(rows)


@problem("slidemax", 3, "Sliding window maximum",
         "For every window of k consecutive elements, print its maximum, in order, separated by spaces.\n\nInput: first line n and k (1 ≤ k ≤ n ≤ 10^5); "
         "second line n integers.\nOutput: n − k + 1 numbers.", ["8 3\n1 3 -1 -3 5 3 6 7", "3 3\n2 1 2"], ["1 1\n5"])
def _(s):
    v = ints(s)
    n, k, a = v[0], v[1], v[2:]
    dq, out = deque(), []
    for i, x in enumerate(a):
        while dq and a[dq[-1]] <= x:
            dq.pop()
        dq.append(i)
        if dq[0] <= i - k:
            dq.popleft()
        if i >= k - 1:
            out.append(a[dq[0]])
    return " ".join(map(str, out))


@gen("slidemax")
def _(rng):
    n = rng.randint(1, 20000)
    return f"{n} {rng.randint(1, n)}\n{' '.join(str(rng.randint(-10 ** 5, 10 ** 5)) for _ in range(n))}"


@problem("rain", 3, "Trapping rain water",
         "Bars of width 1 have the given heights. Print how many units of water are trapped between them after rain.\n\nInput: first line n "
         "(1 ≤ n ≤ 10^5); second line n heights (0 to 10^5).\nOutput: the trapped water.", ["12\n0 1 0 2 1 0 1 3 2 1 2 1", "6\n4 2 0 3 2 5"], ["1\n5", "3\n1 2 3"])
def _(s):
    h = ints(s)[1:]
    l, r, lm, rm, w = 0, len(h) - 1, 0, 0, 0
    while l < r:
        if h[l] < h[r]:
            lm = max(lm, h[l])
            w += lm - h[l]
            l += 1
        else:
            rm = max(rm, h[r])
            w += rm - h[r]
            r -= 1
    return str(w)


@gen("rain")
def _(rng):
    return arr_input(rng, 1, 30000, 0, 10 ** 5 if rng.random() < 0.3 else 20)


@problem("histogram", 3, "Largest rectangle in a histogram",
         "Bars of width 1 have the given heights. Print the area of the largest rectangle that fits inside the histogram.\n\nInput: first line n "
         "(1 ≤ n ≤ 10^5); second line n heights (0 to 10^4).\nOutput: the largest area.", ["6\n2 1 5 6 2 3", "2\n2 4"], ["1\n0", "5\n3 3 3 3 3"])
def _(s):
    h = ints(s)[1:] + [0]
    st, best = [], 0
    for i, x in enumerate(h):
        start = i
        while st and st[-1][1] >= x:
            j, y = st.pop()
            best = max(best, y * (i - j))
            start = j
        st.append((start, x))
    return str(best)


@gen("histogram")
def _(rng):
    return arr_input(rng, 1, 30000, 0, 10 ** 4)


@problem("inversions", 3, "Count inversions",
         "Print the number of pairs (i < j) with a[i] > a[j].\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers.\nOutput: the count.",
         ["5\n2 4 1 3 5", "3\n3 2 1"], ["1\n1", "4\n1 1 1 1"])
def _(s):
    a = ints(s)[1:]
    ranks = {x: i + 1 for i, x in enumerate(sorted(set(a)))}
    tree = [0] * (len(ranks) + 1)
    inv = 0
    for i, x in enumerate(a):
        r = ranks[x]
        j, le = r, 0
        while j > 0:
            le += tree[j]
            j -= j & -j
        inv += i - le
        j = r
        while j < len(tree):
            tree[j] += 1
            j += j & -j
    return str(inv)


@gen("inversions")
def _(rng):
    return arr_input(rng, 1, 30000, -10 ** 6, 10 ** 6)


@problem("jumps", 3, "Minimum jumps",
         "From index 0 you may jump forward at most a[i] steps from index i. Print the minimum number of jumps to reach the last index, or −1 if it "
         "cannot be reached.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers (0 to 10^5).\nOutput: the minimum jumps or -1.",
         ["5\n2 3 1 1 4", "5\n3 2 1 0 4"], ["1\n0", "2\n0 1"])
def _(s):
    a = ints(s)[1:]
    n = len(a)
    if n == 1:
        return "0"
    jumps, end, far = 0, 0, 0
    for i in range(n - 1):
        if i > far:
            return "-1"
        far = max(far, i + a[i])
        if i == end:
            jumps += 1
            end = far
            if end >= n - 1:
                return str(jumps)
    return str(jumps) if end >= n - 1 else "-1"


@gen("jumps")
def _(rng):
    n = rng.randint(1, 30000)
    return f"{n}\n{' '.join(str(rng.randint(0, 4)) for _ in range(n))}"


@problem("gridpaths", 3, "Paths in a grid with blocks",
         "Count the paths from the top-left to the bottom-right of an r × c grid moving only right or down, avoiding blocked cells (#). Print the count "
         "modulo 1 000 000 007.\n\nInput: first line r and c (1 ≤ r, c ≤ 500); then r lines of c characters (. or #).\nOutput: the number of paths mod 10^9+7.",
         ["3 3\n...\n.#.\n...", "2 2\n.#\n#."], ["1 1\n.", "1 1\n#"])
def _(s):
    lines = s.split()
    r, c = int(lines[0]), int(lines[1])
    g = lines[2:2 + r]
    M = 1_000_000_007
    dp = [0] * c
    for i in range(r):
        for j in range(c):
            if g[i][j] == "#":
                dp[j] = 0
            elif i == 0 and j == 0:
                dp[j] = 1
            elif j > 0:
                dp[j] = (dp[j] + dp[j - 1]) % M
    return str(dp[-1])


@gen("gridpaths")
def _(rng):
    r, c = rng.randint(1, 200), rng.randint(1, 200)
    rows = ["".join("#" if rng.random() < 0.12 else "." for _ in range(c)) for _ in range(r)]
    return f"{r} {c}\n" + "\n".join(rows)


@problem("medians", 3, "Running median",
         "Numbers arrive one by one. After each one, print the median of all numbers so far; for an even count print the smaller of the two middle "
         "values.\n\nInput: first line n (1 ≤ n ≤ 10^5); second line n integers.\nOutput: n medians separated by spaces.", ["5\n5 15 1 3 8", "2\n4 4"], ["1\n-3"])
def _(s):
    low, high, out = [], [], []
    for x in ints(s)[1:]:
        if not low or x <= -low[0]:
            heapq.heappush(low, -x)
        else:
            heapq.heappush(high, x)
        if len(low) > len(high) + 1:
            heapq.heappush(high, -heapq.heappop(low))
        elif len(high) > len(low):
            heapq.heappush(low, -heapq.heappop(high))
        out.append(-low[0])
    return " ".join(map(str, out))


@gen("medians")
def _(rng):
    return arr_input(rng, 1, 20000, -10 ** 6, 10 ** 6)


# ================================================================== building

# The code runner keeps at most 64 KB of a program's output; expected outputs stay well under that.
MAX_EXPECTED_OUTPUT = 48_000


def build(p, rng, hidden=6, marks=10):
    """The JSON question for problem p: 2 visible samples, `hidden` random tests plus the edge cases, outputs from the reference."""
    samples = [{"input": x.strip("\n") + "\n", "output": p["solve"](x)} for x in p["samples"]]
    tests, seen = [], set()

    def add(x):
        x = x.strip("\n") + "\n"
        if x in seen:
            return True
        out = p["solve"](x)
        if len(out) > MAX_EXPECTED_OUTPUT:
            return False
        seen.add(x)
        tests.append({"input": x, "output": out})
        return True

    for e in p["edge"]:
        assert add(e), f"edge case of {p['id']} has too much output"
    for _ in range(hidden):
        for _attempt in range(30):
            if add(p["gen"](rng)):
                break
        else:
            raise RuntimeError(f"{p['id']}: could not generate a test with output under {MAX_EXPECTED_OUTPUT} bytes")
    return {"type": "CODING", "title": p["title"], "marks": marks, "text": p["text"],
            "starterCode": STARTER_LINE if p["line"] else STARTER, "samples": samples, "tests": tests,
            "explanation": f"Reference approach: see the standard technique for ‘{p['title']}’."}
