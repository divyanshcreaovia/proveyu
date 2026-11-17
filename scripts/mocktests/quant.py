"""Quantitative aptitude generators. Each takes (rng, level 1-3) and returns an MCQ whose answer is computed, not typed."""
from fractions import Fraction
from math import gcd, comb, perm, factorial

from common import mcq, near, Skip, fmt


def percent_change(rng, lv):
    p = rng.choice([10, 20, 25, 30, 40, 50]) if lv == 1 else rng.choice([10, 12, 15, 20, 25, 30])
    q = rng.choice([10, 20, 25, 50]) if lv == 1 else rng.choice([5, 10, 15, 20, 25])
    net = Fraction(100 + p, 100) * Fraction(100 - q, 100) * 100 - 100
    word = "increase" if net > 0 else "decrease"
    if net == 0:
        raise Skip
    text = (f"The price of an item is first increased by {p}% and then decreased by {q}%. "
            f"What is the net percentage change in its price?")
    wrong = [f"{fmt(abs(net) + d)}% {w}" for d in (1, 2, -1) for w in (word,)] + [f"{p - q}% increase" if p > q else f"{q - p}% decrease",
                                                                              f"{fmt(abs(net))}% {'decrease' if word == 'increase' else 'increase'}"]
    return mcq(rng, text, f"{fmt(abs(net))}% {word}", wrong,
               f"Net factor = (1 + {p}/100)(1 - {q}/100) = {fmt(Fraction(100 + p, 100) * Fraction(100 - q, 100))}, "
               f"so the change is {fmt(net)}%: a {fmt(abs(net))}% {word}.")


def percent_of(rng, lv):
    p = rng.choice([12, 15, 18, 24, 35, 45, 65]) if lv > 1 else rng.choice([10, 20, 25, 50])
    n = rng.choice([200, 250, 400, 480, 600, 750, 840, 1200])
    ans = Fraction(p * n, 100)
    if ans.denominator != 1:
        raise Skip
    text = f"What is {p}% of {n}?"
    return mcq(rng, text, ans, near(rng, int(ans), max(3, int(ans) // 6)), f"{p}/100 × {n} = {fmt(ans)}.")


def profit_loss(rng, lv):
    cp = rng.choice(range(200, 2001, 50))
    p = rng.choice([10, 12, 15, 20, 25]) if lv < 3 else rng.choice([8, 12.5, 16, 18, 22])
    if lv == 1:
        sp = Fraction(cp) * Fraction(100 + Fraction(p), 100)
        if sp.denominator != 1:
            raise Skip
        text = f"A shopkeeper buys an article for Rs {cp} and sells it at a profit of {fmt(p)}%. What is the selling price?"
        return mcq(rng, text, sp, near(rng, int(sp), max(5, int(sp) // 10)), f"SP = {cp} × (100 + {fmt(p)})/100 = Rs {fmt(sp)}.", "")
    mark = rng.choice([20, 25, 30, 40, 50])
    disc = rng.choice([10, 15, 20, 25])
    sp = Fraction(100 + mark, 100) * Fraction(100 - disc, 100)
    gain = (sp - 1) * 100
    if gain.denominator > 4:
        raise Skip
    text = (f"A trader marks his goods {mark}% above the cost price and then allows a discount of {disc}%. "
            f"What is his gain or loss percent?")
    word = "gain" if gain > 0 else "loss"
    if gain == 0:
        raise Skip
    wrong = [f"{fmt(abs(gain) + d)}% {word}" for d in (2, -1, 5)] + [f"{mark - disc}% gain", f"{fmt(abs(gain))}% {'loss' if word == 'gain' else 'gain'}"]
    return mcq(rng, text, f"{fmt(abs(gain))}% {word}", wrong,
               f"SP as a fraction of CP = {fmt(Fraction(100 + mark, 100))} × {fmt(Fraction(100 - disc, 100))} = {fmt(sp)}, so {fmt(abs(gain))}% {word}.")


def simple_interest(rng, lv):
    p = rng.choice(range(2000, 20001, 500))
    r = rng.choice([4, 5, 6, 8, 10, 12])
    t = rng.choice([2, 3, 4, 5])
    si = Fraction(p * r * t, 100)
    if si.denominator != 1:
        raise Skip
    if lv == 1:
        text = f"Find the simple interest on Rs {p} at {r}% per annum for {t} years."
        return mcq(rng, text, f"Rs {fmt(si)}", [f"Rs {fmt(si + d)}" for d in near(rng, 0, max(50, int(si) // 5))],
                   f"SI = P × R × T / 100 = {p} × {r} × {t} / 100 = Rs {fmt(si)}.")
    text = f"A sum of money amounts to Rs {p + int(si)} in {t} years at {r}% per annum simple interest. What is the sum?"
    return mcq(rng, text, f"Rs {p}", [f"Rs {p + d}" for d in (-500, 500, 250, -250, 1000)],
               f"Amount = P(1 + {r}×{t}/100) = P × {fmt(Fraction(100 + r * t, 100))}, so P = {p + int(si)} / {fmt(Fraction(100 + r * t, 100))} = Rs {p}.")


def compound_interest(rng, lv):
    p = rng.choice([1000, 2000, 4000, 5000, 8000, 10000, 16000])
    r = rng.choice([5, 10, 20]) if lv < 3 else rng.choice([5, 10, 15, 20])
    t = 2 if lv < 3 else rng.choice([2, 3])
    amt = Fraction(p) * Fraction(100 + r, 100) ** t
    ci = amt - p
    si = Fraction(p * r * t, 100)
    if ci.denominator != 1:
        raise Skip
    if lv < 3:
        text = f"What is the compound interest on Rs {p} at {r}% per annum for {t} years, compounded annually?"
        return mcq(rng, text, f"Rs {fmt(ci)}", [f"Rs {fmt(si)}", f"Rs {fmt(ci + 50)}", f"Rs {fmt(ci - 20)}", f"Rs {fmt(amt)}"],
                   f"A = {p}(1 + {r}/100)^{t} = {fmt(amt)}; CI = A − P = Rs {fmt(ci)}.")
    text = (f"What is the difference between compound interest and simple interest on Rs {p} at {r}% per annum "
            f"for {t} years?")
    diff = ci - si
    return mcq(rng, text, f"Rs {fmt(diff)}", [f"Rs {fmt(diff + d)}" for d in (10, -5, 25, 40, 2)],
               f"CI = {fmt(ci)}, SI = {fmt(si)}, difference = Rs {fmt(diff)}.")


def ratio_share(rng, lv):
    a, b = rng.choice([(2, 3), (3, 5), (4, 7), (5, 3), (7, 9)])
    if lv >= 2:
        c = rng.choice([4, 5, 6, 8])
        parts = [a, b, c]
    else:
        parts = [a, b]
    total = sum(parts) * rng.choice(range(50, 801, 50))
    each = total // sum(parts)
    who = rng.randrange(len(parts))
    names = ["A", "B", "C"]
    ratio = " : ".join(map(str, parts))
    text = f"Rs {total} is divided among {', '.join(names[:len(parts) - 1])} and {names[len(parts) - 1]} in the ratio {ratio}. What is {names[who]}'s share?"
    ans = parts[who] * each
    wrong = [p * each for p in parts if p != parts[who]] + [ans + each, ans - each]
    return mcq(rng, text, f"Rs {ans}", [f"Rs {w}" for w in wrong if w > 0],
               f"One part = {total} / {sum(parts)} = {each}; {names[who]} gets {parts[who]} × {each} = Rs {ans}.")


def averages(rng, lv):
    n = rng.choice([5, 8, 10, 12, 15, 20])
    avg = rng.choice(range(20, 61))
    newp = rng.choice([1, 2, 3, 4, 5]) * rng.choice([1, -1])
    new_val = avg + newp * (n + 1)
    if new_val <= 0:
        raise Skip
    text = (f"The average age of {n} students is {avg} years. When the teacher's age is included, the average "
            f"{'increases' if newp > 0 else 'decreases'} by {abs(newp)} year{'s' if abs(newp) > 1 else ''}. What is the teacher's age?")
    if lv == 1:
        text = f"The average of {n} numbers is {avg}. If one more number, {new_val}, is added, what is the new average?"
        ans = Fraction(avg * n + new_val, n + 1)
        return mcq(rng, text, ans, near(rng, int(ans), 3), f"New sum = {avg}×{n} + {new_val} = {avg * n + new_val}; ÷ {n + 1} = {fmt(ans)}.")
    return mcq(rng, text, new_val, near(rng, new_val, max(4, n)), f"New total = {avg + newp} × {n + 1} = {(avg + newp) * (n + 1)}; old total = {avg * n}; teacher = {new_val}.")


def time_work(rng, lv):
    while True:
        a, b = rng.sample([4, 5, 6, 8, 10, 12, 15, 18, 20, 24, 30, 36, 40, 45, 60], 2)
        together = Fraction(a * b, a + b)
        if together.denominator in (1, 2, 3, 4, 5):
            break
    if lv == 1:
        text = f"A can finish a work in {a} days and B in {b} days. In how many days will they finish it working together?"
        return mcq(rng, text, together, [together + 1, together - Fraction(1, 2), Fraction(a + b, 2), Fraction(a * b, a + b + 2)],
                   f"Together per day: 1/{a} + 1/{b} = {fmt(Fraction(1, a) + Fraction(1, b))}; time = {fmt(together)} days.", " days")
    d = rng.choice([1, 2, 3])
    left = 1 - d * (Fraction(1, a) + Fraction(1, b))
    if left <= 0:
        raise Skip
    rest = left * a
    text = (f"A can do a job in {a} days and B in {b} days. They work together for {d} day{'s' if d > 1 else ''}, then B leaves. "
            f"In how many more days will A finish the job?")
    return mcq(rng, text, rest, [rest + 1, rest - Fraction(1, 2), left * b, rest + 2],
               f"In {d} days they do {fmt(1 - left)} of the work; {fmt(left)} is left, which A does in {fmt(left)} × {a} = {fmt(rest)} days.", " days")


def pipes(rng, lv):
    while True:
        f1, f2 = rng.sample([6, 8, 10, 12, 15, 20, 24, 30], 2)
        e = rng.choice([12, 15, 20, 24, 30, 40, 60])
        net = Fraction(1, f1) + Fraction(1, f2) - (Fraction(1, e) if lv > 1 else 0)
        if net > 0 and (1 / net).denominator <= 4:
            break
    t = 1 / net
    if lv > 1:
        text = (f"Pipes A and B can fill a tank in {f1} and {f2} hours. Pipe C can empty it in {e} hours. "
                f"If all three are opened together, how long will the tank take to fill?")
        expl = f"Net rate = 1/{f1} + 1/{f2} − 1/{e} = {fmt(net)} tank/hour, so {fmt(t)} hours."
    else:
        text = f"Two pipes can fill a tank in {f1} hours and {f2} hours. How long will they take to fill it together?"
        expl = f"Rate = 1/{f1} + 1/{f2} = {fmt(net)}; time = {fmt(t)} hours."
    return mcq(rng, text, t, [t + 1, t + Fraction(1, 2), t - Fraction(1, 2), Fraction(f1 + f2, 2)], expl, " hours")


def trains(rng, lv):
    if lv == 1:
        L = rng.choice([100, 120, 150, 180, 200, 240, 300])
        t = rng.choice([6, 8, 9, 10, 12, 15, 18])
        v = Fraction(L, t) * Fraction(18, 5)
        if v.denominator != 1:
            raise Skip
        text = f"A train {L} m long passes a pole in {t} seconds. What is its speed in km/h?"
        return mcq(rng, text, v, [Fraction(L, t), v + 6, v - 4, v + 12], f"{L}/{t} m/s × 18/5 = {fmt(v)} km/h.", " km/h")
    L1 = rng.choice([120, 150, 180, 200, 250])
    L2 = rng.choice([100, 150, 200, 250, 300])
    v1 = rng.choice([36, 45, 54, 60, 72])
    v2 = rng.choice([v for v in (18, 27, 36, 45) if v != v1])
    same = lv == 3 and v1 > v2 and rng.random() < 0.5
    rel = (v1 - v2) if same else (v1 + v2)
    t = Fraction(L1 + L2) / (Fraction(rel) * Fraction(5, 18))
    if t.denominator > 2:
        raise Skip
    text = (f"Two trains of lengths {L1} m and {L2} m run at {v1} km/h and {v2} km/h "
            f"{'in the same direction' if same else 'in opposite directions'}. How long do they take to cross each other completely?")
    other = Fraction(L1 + L2) / (Fraction(v1 - v2 if not same else v1 + v2) * Fraction(5, 18)) if v1 != v2 else t + 3
    return mcq(rng, text, t, [other, t + 2, t - 1, Fraction(L1, 1) / (Fraction(rel) * Fraction(5, 18))],
               f"Relative speed = {rel} km/h = {fmt(Fraction(rel) * Fraction(5, 18))} m/s; distance = {L1 + L2} m; time = {fmt(t)} s.", " s")


def boats(rng, lv):
    b = rng.choice([8, 10, 12, 15, 18, 20])
    s = rng.choice([2, 3, 4, 5])
    if s >= b:
        raise Skip
    d = (b - s) * (b + s) * rng.choice([1, 2]) // gcd(b - s, b + s)
    up, down = Fraction(d, b - s), Fraction(d, b + s)
    if lv == 1:
        text = f"A boat goes {(b + s) * 2} km downstream in 2 hours and {(b - s) * 2} km upstream in 2 hours. What is the speed of the boat in still water?"
        return mcq(rng, text, b, [s, b + s, b - s, (b + s) * 2 // 2 + 1], f"Downstream {b + s} km/h, upstream {b - s} km/h; boat = average = {b} km/h.", " km/h")
    text = (f"A boat's speed in still water is {b} km/h and the stream flows at {s} km/h. "
            f"How long will it take to go {d} km upstream and come back?")
    tot = up + down
    return mcq(rng, text, tot, [Fraction(2 * d, b), tot + 1, up, tot - Fraction(1, 2)],
               f"Upstream {fmt(up)} h at {b - s} km/h, downstream {fmt(down)} h at {b + s} km/h; total {fmt(tot)} h.", " hours")


def ages(rng, lv):
    a, b = rng.choice([(3, 2), (5, 3), (4, 3), (7, 5), (5, 4), (2, 1)])
    k = rng.choice(range(3, 10))
    y = rng.choice([4, 5, 6, 8, 10])
    pa, pb = a * k, b * k
    na, nb = pa + y, pb + y
    g = gcd(na, nb)
    text = (f"The present ages of A and B are in the ratio {a}:{b}. After {y} years, the ratio will be {na // g}:{nb // g}. "
            f"What is A's present age?")
    return mcq(rng, text, pa, [pb, pa + y, pa - 2, na, pa + 3], f"Let ages be {a}x and {b}x. ({a}x+{y})/({b}x+{y}) = {na // g}/{nb // g} gives x = {k}; A = {pa}.", " years")


def alligation(rng, lv):
    c1 = rng.choice([20, 24, 30, 32, 40])
    c2 = rng.choice([50, 54, 60, 64, 72])
    m = rng.choice(range(c1 + 2, c2 - 1))
    r = Fraction(c2 - m, m - c1)
    text = (f"In what ratio must rice at Rs {c1}/kg be mixed with rice at Rs {c2}/kg so that the mixture costs Rs {m}/kg?")
    ans = f"{r.numerator}:{r.denominator}"
    wrong = [f"{r.denominator}:{r.numerator}", f"{c1}:{c2}", f"{r.numerator + 1}:{r.denominator}", f"{r.numerator}:{r.denominator + 1}"]
    return mcq(rng, text, ans, wrong, f"Alligation: (cheaper : dearer) = ({c2} − {m}) : ({m} − {c1}) = {ans}.")


def lcm_bells(rng, lv):
    xs = rng.sample([4, 6, 8, 9, 10, 12, 15, 16, 18, 20, 24], 3 if lv < 3 else 4)
    l = 1
    for x in xs:
        l = l * x // gcd(l, x)
    if l > 360:
        raise Skip
    text = (f"{len(xs)} bells ring at intervals of {', '.join(map(str, xs[:-1]))} and {xs[-1]} seconds. "
            f"If they ring together now, after how many seconds will they next ring together?")
    return mcq(rng, text, l, [l // 2, l * 2, sum(xs), l + 12], f"LCM({', '.join(map(str, xs))}) = {l} seconds.", " seconds")


def remainders(rng, lv):
    if lv == 1:
        d = rng.choice([7, 9, 11, 13])
        n = rng.choice(range(1000, 9999))
        text = f"What is the remainder when {n} is divided by {d}?"
        return mcq(rng, text, n % d, [x % d for x in (n + 1, n + 2, n + 3, n + 5)] + [d - 1, 0],
                   f"{n} = {d} × {n // d} + {n % d}.")
    if lv == 2:
        base = rng.choice([2, 3, 7, 8, 13, 17])
        e = rng.choice(range(20, 99))
        u = pow(base, e, 10)
        text = f"What is the unit digit of {base}^{e}?"
        return mcq(rng, text, u, [pow(base, e + 1, 10), pow(base, e + 2, 10), pow(base, e + 3, 10), (u + 5) % 10, 0, 1, 9],
                   f"Unit digits of powers of {base % 10} repeat every 4; {e} mod 4 = {e % 4 or 4}, giving {u}.")
    base = rng.choice([2, 3, 5, 7])
    e = rng.choice(range(15, 60))
    d = rng.choice([7, 9, 11, 13])
    r = pow(base, e, d)
    text = f"What is the remainder when {base}^{e} is divided by {d}?"
    return mcq(rng, text, r, [x for x in range(d) if x != r][:6], f"Powers of {base} mod {d} cycle; {base}^{e} mod {d} = {r}.")


def perm_comb(rng, lv):
    if lv == 1:
        n = rng.choice([5, 6, 7, 8])
        r = rng.choice([2, 3])
        text = f"In how many ways can a committee of {r} be chosen from {n} people?"
        ans = comb(n, r)
        return mcq(rng, text, ans, [perm(n, r), ans + n, ans * 2, n * r], f"C({n},{r}) = {ans}.")
    if lv == 2:
        word = rng.choice(["APPLE", "LETTER", "BANANA", "SCHOOL", "COFFEE", "BALLOON", "SUCCESS", "COMMITTEE"[:7]])
        from collections import Counter
        c = Counter(word)
        ans = factorial(len(word))
        for v in c.values():
            ans //= factorial(v)
        text = f"In how many different ways can the letters of the word {word} be arranged?"
        return mcq(rng, text, ans, [factorial(len(word)), ans * 2, ans // 2 if ans % 2 == 0 else ans + 10, ans + len(word)],
                   f"{len(word)}! divided by the factorials of repeated letters ({', '.join(f'{k}:{v}' for k, v in c.items() if v > 1)}) = {ans}.")
    m, w = rng.choice([(5, 4), (6, 4), (6, 5), (7, 5)])
    k1, k2 = rng.choice([(2, 2), (3, 2), (2, 1), (3, 1)])
    ans = comb(m, k1) * comb(w, k2)
    text = f"From {m} men and {w} women, a team of {k1} men and {k2} women is to be formed. In how many ways can this be done?"
    return mcq(rng, text, ans, [comb(m + w, k1 + k2), comb(m, k1) + comb(w, k2), ans * 2, perm(m, k1) * comb(w, k2)],
               f"C({m},{k1}) × C({w},{k2}) = {comb(m, k1)} × {comb(w, k2)} = {ans}.")


def probability(rng, lv):
    if lv == 1:
        s = rng.choice(range(4, 11))
        ways = sum(1 for a in range(1, 7) for b in range(1, 7) if a + b == s)
        p = Fraction(ways, 36)
        text = f"Two dice are thrown together. What is the probability that the sum is {s}?"
        return mcq(rng, text, p, [Fraction(ways + 1, 36), Fraction(1, 6), Fraction(ways, 12) if ways < 12 else Fraction(1, 3), Fraction(1, 9), Fraction(5, 36)],
                   f"{ways} favourable outcomes out of 36: {fmt(p)}.")
    r, b, g = rng.choice([(3, 4, 5), (4, 5, 6), (5, 3, 2), (2, 3, 5), (6, 4, 5)])
    n = r + b + g
    if lv == 2:
        p = Fraction(comb(r, 2), comb(n, 2))
        text = f"A bag has {r} red, {b} blue and {g} green balls. Two balls are drawn at random. What is the probability that both are red?"
        return mcq(rng, text, p, [Fraction(r, n) ** 2, Fraction(r, n), Fraction(comb(r, 2) + 1, comb(n, 2)), Fraction(2 * r, n * 2 + 1)],
                   f"C({r},2)/C({n},2) = {comb(r, 2)}/{comb(n, 2)} = {fmt(p)}.")
    p = 1 - Fraction(comb(n - r, 2), comb(n, 2))
    text = f"A bag has {r} red, {b} blue and {g} green balls. Two balls are drawn at random. What is the probability that at least one is red?"
    return mcq(rng, text, p, [Fraction(comb(r, 2), comb(n, 2)), Fraction(r, n), 1 - Fraction(n - r, n) ** 2, p - Fraction(1, comb(n, 2))],
               f"1 − P(no red) = 1 − C({n - r},2)/C({n},2) = {fmt(p)}.")


def clock_angle(rng, lv):
    h = rng.choice(range(1, 13))
    m = rng.choice([0, 10, 15, 20, 25, 30, 40, 45, 50]) if lv < 3 else rng.choice(range(5, 60, 5))
    ang = abs(Fraction(60 * h - 11 * m, 2)) % 360
    if ang > 180:
        ang = 360 - ang
    text = f"What is the smaller angle between the hour and minute hands of a clock at {h}:{m:02d}?"
    return mcq(rng, text, ang, [360 - ang if ang != 180 else 90, ang + 5, abs(30 * h - 6 * m) % 360, ang + Fraction(15, 2)],
               f"Angle = |30H − 5.5M| = |{30 * h} − {fmt(Fraction(11 * m, 2))}| = {fmt(ang)}°.", "°")


def calendar(rng, lv):
    days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    d0 = rng.randrange(7)
    n = rng.choice(range(20, 400))
    ans = days[(d0 + n) % 7]
    text = f"If today is {days[d0]}, what day of the week will it be after {n} days?"
    return mcq(rng, text, ans, [x for x in days if x != ans][:5] + [days[(d0 + n + 1) % 7]],
               f"{n} mod 7 = {n % 7}; {days[d0]} + {n % 7} day{'' if n % 7 == 1 else 's'} = {ans}.")


def mensuration(rng, lv):
    if lv == 1:
        l, b = rng.choice(range(8, 40)), rng.choice(range(5, 30))
        text = f"A rectangular field is {l} m long and {b} m wide. What is the cost of fencing it at Rs 15 per metre?"
        ans = 2 * (l + b) * 15
        return mcq(rng, text, f"Rs {ans}", [f"Rs {l * b * 15}", f"Rs {(l + b) * 15}", f"Rs {ans + 150}", f"Rs {ans - 60}"],
                   f"Perimeter = 2({l}+{b}) = {2 * (l + b)} m; cost = {2 * (l + b)} × 15 = Rs {ans}.")
    r = rng.choice([7, 14, 21, 3.5, 10.5])
    h = rng.choice([10, 12, 15, 20])
    if lv == 2:
        area = Fraction(22, 7) * Fraction(r).limit_denominator() ** 2
        text = f"What is the area of a circle of radius {fmt(r)} cm? (Use π = 22/7)"
        circ = 2 * Fraction(22, 7) * Fraction(r).limit_denominator()
        return mcq(rng, text, area, [circ, area * 2, area + 11, area / 2], f"πr² = 22/7 × {fmt(r)}² = {fmt(area)} cm².", " cm²")
    vol = Fraction(22, 7) * Fraction(r).limit_denominator() ** 2 * h
    text = f"What is the volume of a cylinder with radius {fmt(r)} cm and height {h} cm? (Use π = 22/7)"
    return mcq(rng, text, vol, [vol / 3, 2 * Fraction(22, 7) * Fraction(r).limit_denominator() * h, vol + 22, vol * 2],
               f"V = πr²h = 22/7 × {fmt(r)}² × {h} = {fmt(vol)} cm³.", " cm³")


def partnership(rng, lv):
    a, b = rng.choice(range(2, 13)) * 1000, rng.choice(range(2, 13)) * 1000
    ta, tb = 12, rng.choice([4, 6, 8, 9, 10])
    profit = (a * ta + b * tb) // gcd(a * ta, b * tb) * rng.choice([100, 200, 500])
    share_b = Fraction(profit * b * tb, a * ta + b * tb)
    if share_b.denominator != 1:
        raise Skip
    text = (f"A starts a business with Rs {a}. After {12 - tb} months B joins with Rs {b}. "
            f"At the end of the year the profit is Rs {profit}. What is B's share?")
    return mcq(rng, text, f"Rs {fmt(share_b)}", [f"Rs {fmt(profit - share_b)}", f"Rs {fmt(Fraction(profit * b, a + b))}", f"Rs {fmt(share_b + 500)}", f"Rs {profit // 2}"],
               f"Ratio = {a}×12 : {b}×{tb} = {a * ta}:{b * tb}; B gets {profit} × {b * tb}/{a * ta + b * tb} = Rs {fmt(share_b)}.")


def equations(rng, lv):
    if lv == 1:
        x = rng.choice(range(2, 20))
        a, b = rng.choice(range(2, 9)), rng.choice(range(1, 30))
        text = f"If {a}x + {b} = {a * x + b}, what is x?"
        return mcq(rng, text, x, near(rng, x, 4), f"{a}x = {a * x}, so x = {x}.")
    r1, r2 = rng.sample(range(-9, 10), 2)
    if 0 in (r1, r2):
        raise Skip
    s, p = r1 + r2, r1 * r2
    text = f"What are the roots of x² {'-' if s > 0 else '+'} {abs(s)}x {'+' if p > 0 else '-'} {abs(p)} = 0?"
    if s == 0:
        text = f"What are the roots of x² {'+' if p > 0 else '-'} {abs(p)} = 0?"
    lo, hi = sorted((r1, r2))
    ans = f"{lo} and {hi}"
    wrong = []
    for x, y in ((-hi, -lo), (lo + 1, hi + 1), (lo, -hi), (-lo, hi), (lo - 1, hi - 1), (lo * 2, hi)):
        x, y = sorted((x, y))
        if {x, y} != {lo, hi}:  # a reordered copy of the right answer must never appear as a wrong option
            wrong.append(f"{x} and {y}")
    return mcq(rng, text, ans, wrong, f"Sum of roots = {s}, product = {p}: the roots are {lo} and {hi}.")


def ap_sum(rng, lv):
    a, d = rng.choice(range(1, 20)), rng.choice(range(2, 10))
    n = rng.choice(range(10, 41))
    s = n * (2 * a + (n - 1) * d) // 2
    text = f"What is the sum of the first {n} terms of the series {a}, {a + d}, {a + 2 * d}, ...?"
    return mcq(rng, text, s, [s + d * n, s - a, n * (a + (n - 1) * d), s + n], f"S = n/2 [2a + (n−1)d] = {n}/2 × [{2 * a} + {(n - 1) * d}] = {s}.")


def data_table(rng, lv):
    years = ["2021", "2022", "2023", "2024", "2025"]
    prod = {y: rng.choice(range(120, 400, 10)) for y in years}
    sold = {y: prod[y] - rng.choice(range(10, 80, 5)) for y in years}
    table = "Year | Produced (units) | Sold (units)\n" + "\n".join(f"{y} | {prod[y]} | {sold[y]}" for y in years)
    kind = rng.choice(["avg", "pct", "max"])
    if kind == "avg":
        ans = Fraction(sum(sold.values()), 5)
        q = "What is the average number of units sold per year?"
        return mcq(rng, f"Study the table and answer.\n\n{table}\n\n{q}", ans, [Fraction(sum(prod.values()), 5), ans + 10, ans - 6, ans + 3],
                   f"Total sold = {sum(sold.values())}; ÷ 5 = {fmt(ans)}.")
    if kind == "pct":
        y = rng.choice(years)
        ans = Fraction(sold[y] * 100, prod[y])
        q = f"In {y}, what percentage of the units produced was sold? (to 2 decimal places)"
        return mcq(rng, f"Study the table and answer.\n\n{table}\n\n{q}", float(round(ans, 2)),
                   [float(round(Fraction(prod[y] - sold[y], prod[y]) * 100, 2)), float(round(ans, 2)) + 2.5, float(round(ans, 2)) - 1.75, float(round(Fraction(sold[y], prod[y] + 10) * 100, 2))],
                   f"{sold[y]}/{prod[y]} × 100 = {float(round(ans, 2))}%.", "%")
    unsold = {y: prod[y] - sold[y] for y in years}
    best = max(years, key=lambda y: (unsold[y], y))
    if list(unsold.values()).count(unsold[best]) > 1:
        raise Skip
    q = "In which year was the number of unsold units the highest?"
    return mcq(rng, f"Study the table and answer.\n\n{table}\n\n{q}", best, [y for y in years if y != best], f"Unsold = produced − sold: {', '.join(f'{y}: {unsold[y]}' for y in years)}.")


GENERATORS = [percent_change, percent_of, profit_loss, simple_interest, compound_interest, ratio_share, averages, time_work, pipes,
              trains, boats, ages, alligation, lcm_bells, remainders, perm_comb, probability, clock_angle, calendar, mensuration,
              partnership, equations, ap_sum, data_table]
