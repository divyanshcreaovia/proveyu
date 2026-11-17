"""Logical reasoning generators. Answers are computed from the generated puzzle, never typed by hand."""
import string

from common import mcq, near, Skip, fixed

def ordinal(n):
    return f"{n}{'th' if 10 <= n % 100 <= 20 else {1: 'st', 2: 'nd', 3: 'rd'}.get(n % 10, 'th')}"


NAMES = ["Asha", "Bharat", "Chitra", "Dev", "Esha", "Farhan", "Gita", "Harsh", "Isha", "Jatin", "Kavya", "Lokesh", "Meera", "Nikhil",
         "Ojas", "Pooja", "Rahul", "Sneha", "Tarun", "Uma", "Varun", "Yash", "Zoya"]


def number_series(rng, lv):
    kind = rng.choice(["ap", "gp", "sq", "diff2", "alt", "mulplus"] if lv > 1 else ["ap", "gp", "sq", "mulplus"])
    if kind == "ap":
        a, d = rng.randint(2, 30), rng.randint(3, 15)
        seq = [a + i * d for i in range(6)]
        rule = f"add {d} each time"
    elif kind == "gp":
        a, r = rng.randint(1, 6), rng.choice([2, 3])
        seq = [a * r ** i for i in range(6)]
        rule = f"multiply by {r} each time"
    elif kind == "sq":
        s, off = rng.randint(2, 6), rng.choice([0, 1, -1, 2])
        seq = [(s + i) ** 2 + off for i in range(6)]
        rule = f"squares of {s}, {s + 1}, ... {'plus ' + str(off) if off > 0 else 'minus ' + str(-off) if off < 0 else ''}".strip()
    elif kind == "diff2":
        a, d, dd = rng.randint(1, 20), rng.randint(1, 6), rng.randint(1, 4)
        seq = [a]
        for i in range(5):
            seq.append(seq[-1] + d + i * dd)
        rule = f"differences increase by {dd} ({d}, {d + dd}, {d + 2 * dd}, ...)"
    elif kind == "alt":
        a, b, d1, d2 = rng.randint(2, 20), rng.randint(30, 60), rng.randint(2, 6), rng.randint(2, 7)
        seq = [a, b, a + d1, b - d2, a + 2 * d1, b - 2 * d2]
        rule = f"two series alternate: +{d1} on odd positions, −{d2} on even positions"
    else:
        a, m, c = rng.randint(1, 5), rng.choice([2, 3]), rng.randint(1, 4)
        seq = [a]
        for _ in range(5):
            seq.append(seq[-1] * m + c)
        rule = f"multiply by {m} and add {c}"
    shown, ans = seq[:5], seq[5]
    text = f"What comes next in the series: {', '.join(map(str, shown))}, ?"
    return mcq(rng, text, ans, near(rng, ans, max(3, abs(ans) // 8)), f"Rule: {rule}. Next term = {ans}.")


def letter_series(rng, lv):
    k = rng.randint(2, 5) if lv == 1 else rng.randint(2, 7)
    start = rng.randint(0, 25 - 5 * k) if 25 - 5 * k > 0 else 0
    if lv == 1:
        letters = [string.ascii_uppercase[start + i * k] for i in range(5)]
        if start + 5 * k > 25:
            raise Skip
        ans = string.ascii_uppercase[start + 5 * k]
        text = f"What comes next: {', '.join(letters)}, ?"
        wrong = [string.ascii_uppercase[(start + 5 * k + d) % 26] for d in (1, -1, 2, -2)]
        return mcq(rng, text, ans, wrong, f"Each letter moves {k} places forward.")
    a = rng.randint(0, 10)
    pairs = [string.ascii_uppercase[(a + i * k) % 26] + string.ascii_uppercase[(25 - a - i * k) % 26] for i in range(5)]
    ans = pairs[4]
    text = f"What comes next: {', '.join(pairs[:4])}, ?"
    wrong = [ans[::-1], string.ascii_uppercase[(a + 4 * k + 1) % 26] + ans[1], ans[0] + string.ascii_uppercase[(25 - a - 4 * k + 1) % 26],
             string.ascii_uppercase[(a + 5 * k) % 26] + ans[1]]
    return mcq(rng, text, ans, wrong, f"First letters move +{k}; second letters move −{k} from the other end of the alphabet.")


WORDS = ["CAMERA", "PLANET", "GARDEN", "BRIDGE", "MARKET", "SILVER", "WINTER", "FOREST", "ORANGE", "PENCIL", "BOTTLE", "CASTLE",
         "DINNER", "FLOWER", "HAMMER", "JACKET", "LADDER", "MIRROR", "NATURE", "PARROT", "ROCKET", "SCHOOL", "TICKET", "WALNUT"]


def coding_decoding(rng, lv):
    w1, w2 = rng.sample(WORDS, 2)
    shift = rng.choice([1, 2, 3, -1, -2]) if lv < 3 else rng.choice([2, 3, 4])
    enc = lambda w: "".join(string.ascii_uppercase[(ord(c) - 65 + shift) % 26] for c in w)
    if lv == 3:
        enc2 = lambda w: enc(w)[::-1]
        text = f"In a code language, {w1} is written as {enc2(w1)}. How is {w2} written in that code?"
        ans = enc2(w2)
        wrong = [enc(w2), ans[::-1][1:] + ans[0], "".join(string.ascii_uppercase[(ord(c) - 65 + shift + 1) % 26] for c in w2)[::-1], w2[::-1]]
        return mcq(rng, text, ans, wrong, f"Each letter is shifted {shift:+d} and the word is then reversed.")
    text = f"In a certain code, {w1} is written as {enc(w1)}. How is {w2} written in that code?"
    ans = enc(w2)
    wrong = ["".join(string.ascii_uppercase[(ord(c) - 65 + shift + d) % 26] for c in w2) for d in (1, -1, 2)] + [ans[::-1]]
    return mcq(rng, text, ans, wrong, f"Each letter is shifted {shift:+d} place{'s' if abs(shift) > 1 else ''} in the alphabet.")


DIRS = ["North", "East", "South", "West"]


def directions(rng, lv):
    name = rng.choice(NAMES)
    x = y = 0
    facing = rng.randrange(4)
    steps = []
    moves = rng.randint(3, 4) if lv < 3 else rng.randint(4, 5)
    for i in range(moves):
        d = rng.choice([2, 3, 4, 5, 6, 8, 10, 12])
        if i == 0:
            steps.append(f"walks {d} m towards the {DIRS[facing]}")
        else:
            turn = rng.choice(["left", "right"])
            facing = (facing + (1 if turn == "right" else -1)) % 4
            steps.append(f"turns {turn} and walks {d} m")
        dx, dy = [(0, 1), (1, 0), (0, -1), (-1, 0)][facing]
        x, y = x + dx * d, y + dy * d
    dist2 = x * x + y * y
    dist = int(round(dist2 ** 0.5))
    if dist * dist != dist2 or dist == 0:
        raise Skip
    text = f"{name} {', then '.join(steps)}. How far is {name} from the starting point?"
    return mcq(rng, text, dist, near(rng, dist, 4) + [abs(x) + abs(y)], f"Net displacement: {abs(x)} m {'east' if x >= 0 else 'west'} and {abs(y)} m {'north' if y >= 0 else 'south'}; distance = √({abs(x)}² + {abs(y)}²) = {dist} m.", " m")


def direction_final(rng, lv):
    facing = rng.randrange(4)
    turns = [rng.choice(["left", "right", "about"]) for _ in range(rng.randint(2, 4))]
    f = facing
    for t in turns:
        f = (f + {"left": -1, "right": 1, "about": 2}[t]) % 4
    desc = ", then ".join("turns about" if t == "about" else f"turns {t}" for t in turns)
    text = f"A man is facing {DIRS[facing]}. He {desc}. Which direction is he facing now?"
    return mcq(rng, text, DIRS[f], [d for d in DIRS if d != DIRS[f]], f"Right = 90° clockwise, left = 90° anticlockwise, about = 180°. Final direction: {DIRS[f]}.")


def ranking(rng, lv):
    l, r = rng.randint(5, 25), rng.randint(5, 25)
    n = l + r - 1
    name = rng.choice(NAMES)
    if lv == 1:
        text = f"{name} is {ordinal(l)} from the left and {ordinal(r)} from the right in a row. How many people are in the row?"
        return mcq(rng, text, n, [n + 1, n - 1, l + r, n + 2], f"Total = {l} + {r} − 1 = {n}.")
    other = rng.choice([x for x in NAMES if x != name])
    k = rng.randint(2, min(l, n - l) + 1) if min(l, n - l) > 2 else 2
    text = (f"In a row of {n} students, {name} is {ordinal(l)} from the left. {other} is {k} places to the right of {name}. "
            f"What is {other}'s position from the right end?")
    pos_left = l + k
    if pos_left > n:
        raise Skip
    ans = n - pos_left + 1
    return mcq(rng, text, ans, [ans + 1, ans - 1, pos_left, n - l, ans + 2], f"{other} is {ordinal(pos_left)} from the left, so {n} − {pos_left} + 1 = {ordinal(ans)} from the right.")


def height_order(rng, lv):
    people = rng.sample(NAMES, 5 if lv < 3 else 6)
    order = people[:]  # tallest first
    clues = [f"{order[i]} is taller than {order[i + 1]}" for i in range(len(order) - 1)]
    rng.shuffle(clues)
    q = rng.choice(["tallest", "shortest", "second tallest", "third tallest"] if lv > 1 else ["tallest", "shortest"])
    idx = {"tallest": 0, "shortest": -1, "second tallest": 1, "third tallest": 2}[q]
    ans = order[idx]
    text = f"{'. '.join(clues)}. Who is the {q}?"
    return mcq(rng, text, ans, [p for p in people if p != ans], f"Chaining the clues: {' > '.join(order)}. The {q} is {ans}.")


def blood_relation(rng, lv):
    a, b, c, d = rng.sample(NAMES, 4)
    templates = [
        (f"{a} is the father of {b}. {b} is the sister of {c}. How is {a} related to {c}?", "Father",
         ["Uncle", "Brother", "Grandfather", "Son"], f"{c} is {b}'s sibling, so {a} is {c}'s father too."),
        (f"{a} is the mother of {b}. {c} is the son of {b}. How is {a} related to {c}?", "Grandmother",
         ["Mother", "Aunt", "Sister", "Niece"], f"{a} is the mother of {c}'s parent."),
        (f"{a} is the brother of {b}. {b} is the daughter of {c}. {c} is the husband of {d}. How is {a} related to {d}?", "Son",
         ["Brother", "Nephew", "Father", "Husband"], f"{a} is a child of {c} and {d}, and male."),
        (f"Pointing to a man, {a} says, \"His mother is the only daughter of my mother.\" How is {a} related to the man?", "Mother",
         ["Sister", "Aunt", "Grandmother", "Daughter"], f"The only daughter of {a}'s mother is {a} herself, so {a} is his mother."),
        (f"{a}'s father is the only son of {b}'s father. {b} is {a}'s ____ if {b} is female.", "Aunt",
         ["Mother", "Sister", "Cousin", "Grandmother"], f"{b} is a daughter of {a}'s grandfather and not {a}'s mother (his father is the only son), so she is {a}'s aunt."),
        (f"{a} is the husband of {b}. {c} is the only grandchild of {d}, who is {a}'s father. {c} is the daughter of {b}. How is {d} related to {b}?", "Father-in-law",
         ["Father", "Grandfather", "Uncle", "Brother-in-law"], f"{d} is the father of {b}'s husband."),
        (f"Introducing a woman, {a} says, \"She is the wife of the only son of my father.\" How is the woman related to {a}?", "Wife",
         ["Sister", "Sister-in-law", "Mother", "Daughter"], f"The only son of {a}'s father is {a}, so she is {a}'s wife."),
        (f"{a} is the son of {b}. {b} is the sister of {c}. {c} is the father of {d}. How is {a} related to {d}?", "Cousin",
         ["Brother", "Uncle", "Nephew", "Son"], f"{a}'s mother and {d}'s father are siblings, so they are cousins."),
    ]
    text, ans, wrong, expl = rng.choice(templates if lv > 1 else templates[:3])
    return fixed(text, [ans] + wrong[:3], 0, expl, rng)


def odd_one_out(rng, lv):
    kind = rng.choice(["prime", "square", "mult", "cube"])
    if kind == "prime":
        good = rng.sample([11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71], 3)
        bad = rng.choice([21, 27, 33, 39, 49, 51, 57, 63, 69, 77, 87, 91])
        rule = "all others are prime"
    elif kind == "square":
        good = [x * x for x in rng.sample(range(4, 20), 3)]
        bad = rng.choice(good) + rng.choice([1, 2, 3])
        rule = "all others are perfect squares"
    elif kind == "cube":
        good = [x ** 3 for x in rng.sample(range(2, 11), 3)]
        bad = rng.choice(good) + rng.choice([1, 2, 4])
        rule = "all others are perfect cubes"
    else:
        m = rng.choice([6, 7, 8, 9, 11, 13])
        good = [m * x for x in rng.sample(range(3, 15), 3)]
        bad = rng.choice(good) + rng.choice([1, 2, 3, 4])
        rule = f"all others are multiples of {m}"
    if bad in good:
        raise Skip
    shown = good + [bad]
    rng.shuffle(shown)
    return mcq(rng, f"Which number is the odd one out: {', '.join(map(str, shown))}?", bad, good, f"{bad} — {rule}.")


def analogy(rng, lv):
    f = rng.choice(["sq", "cube", "sqplus", "double3"])
    a, b = rng.sample(range(2, 13), 2)
    fn = {"sq": lambda x: x * x, "cube": lambda x: x ** 3, "sqplus": lambda x: x * x + x, "double3": lambda x: 2 * x + 3}[f]
    desc = {"sq": "n → n²", "cube": "n → n³", "sqplus": "n → n² + n", "double3": "n → 2n + 3"}[f]
    text = f"{a} : {fn(a)} :: {b} : ?"
    ans = fn(b)
    return mcq(rng, text, ans, near(rng, ans, max(3, ans // 6)), f"The rule is {desc}; for {b} it gives {ans}.")


def cube_paint(rng, lv):
    n = rng.randint(3, 6)
    kinds = {"three": 8, "two": 12 * (n - 2), "one": 6 * (n - 2) ** 2, "no": (n - 2) ** 3}
    k = rng.choice(list(kinds))
    text = (f"A cube painted on all faces is cut into {n ** 3} identical small cubes. "
            f"How many small cubes have {'exactly ' + k if k != 'no' else 'no'} face{'s' if k != 'one' else ''} painted?")
    ans = kinds[k]
    return mcq(rng, text, ans, [v for kk, v in kinds.items() if kk != k] + [ans + n, ans + 6, ans + 4],
               f"For an n×n×n cube (n = {n}): 3 faces: 8, 2 faces: 12(n−2), 1 face: 6(n−2)², none: (n−2)³ → {ans}.")


def venn(rng, lv):
    a, b = rng.randint(20, 60), rng.randint(15, 50)
    both = rng.randint(5, min(a, b) - 2)
    neither = rng.randint(0, 15)
    total = a + b - both + neither
    if lv == 1:
        text = (f"In a class of {total} students, {a} like cricket, {b} like football and {both} like both. "
                f"How many like neither?")
        return mcq(rng, text, neither, [neither + both, total - a - b if total - a - b > 0 else neither + 3, neither + 2, both], f"{total} − ({a} + {b} − {both}) = {neither}.")
    text = (f"In a survey, {a} people read newspaper A, {b} read B, {both} read both and {neither} read neither. "
            f"How many people read only one newspaper?")
    ans = a + b - 2 * both
    return mcq(rng, text, ans, [a + b - both, ans + both, a - both, ans - neither if ans - neither > 0 else ans + 5], f"Only A = {a - both}, only B = {b - both}; total = {ans}.")


def operator_puzzle(rng, lv):
    f = rng.choice(["sqsum", "prodplus", "diffsq"])
    fn = {"sqsum": lambda x, y: x * x + y * y, "prodplus": lambda x, y: x * y + x + y, "diffsq": lambda x, y: (x - y) ** 2 + x}[f]
    desc = {"sqsum": "a² + b²", "prodplus": "ab + a + b", "diffsq": "(a − b)² + a"}[f]
    pairs = [tuple(rng.sample(range(2, 10), 2)) for _ in range(3)]
    lines = "; ".join(f"{x} ⊕ {y} = {fn(x, y)}" for x, y in pairs[:2])
    x, y = pairs[2]
    ans = fn(x, y)
    return mcq(rng, f"If {lines}, then {x} ⊕ {y} = ?", ans, near(rng, ans, max(4, ans // 6)), f"a ⊕ b = {desc}; so {x} ⊕ {y} = {ans}.")


SYLLOGISMS = [
    ("All {A} are {B}. All {B} are {C}.", "All {A} are {C}", True), ("All {A} are {B}. All {B} are {C}.", "All {C} are {A}", False),
    ("Some {A} are {B}. All {B} are {C}.", "Some {A} are {C}", True), ("Some {A} are {B}. All {B} are {C}.", "All {A} are {C}", False),
    ("No {A} is {B}. All {C} are {B}.", "No {C} is {A}", True), ("No {A} is {B}. All {C} are {B}.", "Some {A} are {C}", False),
    ("All {A} are {B}. Some {B} are {C}.", "Some {A} are {C}", False), ("All {A} are {B}. No {B} is {C}.", "No {A} is {C}", True),
    ("Some {A} are {B}. No {B} is {C}.", "Some {A} are not {C}", True), ("Some {A} are {B}. No {B} is {C}.", "No {A} is {C}", False),
    ("All {A} are {B}. All {A} are {C}.", "Some {B} are {C}", True), ("No {A} is {B}. No {B} is {C}.", "No {A} is {C}", False),
]


def syllogism(rng, lv):
    words = rng.sample(["cats", "pens", "books", "trees", "doctors", "birds", "chairs", "cars", "teachers", "flowers", "rivers", "phones"], 3)
    prem, concl, follows = rng.choice(SYLLOGISMS)
    sub = lambda s: s.format(A=words[0], B=words[1], C=words[2])
    text = f"Statements: {sub(prem)}\nConclusion: {sub(concl)}.\nDoes the conclusion follow?"
    opts = ["The conclusion definitely follows", "The conclusion does not follow", "The data is contradictory", "The conclusion is partly true"]
    return fixed(text, opts, 0 if follows else 1, "Draw the Venn diagram for the statements: the conclusion " + ("must hold in every possible case." if follows else "fails in at least one possible case."))


GENERATORS = [number_series, letter_series, coding_decoding, directions, direction_final, ranking, height_order, blood_relation,
              odd_one_out, analogy, cube_paint, venn, operator_puzzle, syllogism]
