"""Shared helpers for the mock test generator: building an MCQ with shuffled, distinct options."""
import random
from fractions import Fraction


class Skip(Exception):
    """A generator drew numbers that do not make a clean question; draw again."""


def fmt(x):
    """Numbers as a student would write them: 12 not 12.0, 12.5, fractions as a/b."""
    if isinstance(x, Fraction):
        if x.denominator == 1:
            return str(x.numerator)
        return f"{x.numerator}/{x.denominator}"
    if isinstance(x, float):
        if abs(x - round(x)) < 1e-9:
            return str(int(round(x)))
        return f"{x:.2f}".rstrip("0").rstrip(".")
    return str(x)


def mcq(rng: random.Random, text, correct, wrong, explanation, unit=""):
    """4 options: the correct one plus 3 distinct wrong ones, shuffled. `wrong` may hold more than 3 candidates."""
    c = f"{fmt(correct)}{unit}"
    seen, opts = {c}, []
    for w in wrong:
        s = f"{fmt(w)}{unit}"
        if s not in seen:
            seen.add(s)
            opts.append(s)
        if len(opts) == 3:
            break
    if len(opts) < 3:
        raise Skip("not enough distinct options")
    opts.append(c)
    rng.shuffle(opts)
    return {"type": "MCQ", "text": text, "options": opts, "answer": opts.index(c), "explanation": explanation}


def near(rng, x, spread=None, count=6, integer=True):
    """Plausible wrong answers around x."""
    out = []
    spread = spread or max(2, abs(x) // 5 if integer else abs(x) / 5)
    tries = 0
    while len(out) < count and tries < 200:
        tries += 1
        d = rng.randint(1, max(1, int(spread))) * rng.choice([-1, 1]) if integer else round(rng.uniform(0.1, spread), 2) * rng.choice([-1, 1])
        v = x + d
        if v != x and v not in out and (not integer or v > 0 or x <= 0):
            out.append(v)
    return out


def fixed(text, options, answer, explanation, rng=None):
    """A hand-written MCQ: `options` with the correct one at index `answer`; shuffled when rng is given."""
    if len(set(options)) != len(options):
        raise ValueError(f"duplicate options in: {text[:60]}")
    if rng is None:
        return {"type": "MCQ", "text": text, "options": list(options), "answer": answer, "explanation": explanation}
    correct = options[answer]
    opts = list(options)
    rng.shuffle(opts)
    return {"type": "MCQ", "text": text, "options": opts, "answer": opts.index(correct), "explanation": explanation}
