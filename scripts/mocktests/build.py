"""Builds the 20-step mock test path (easy service-company patterns to hard product-company ones) as JSON files the
Preparation module accepts. Questions are original; each test follows the publicly reported pattern (sections, counts, time) of
the company it is named after. No question appears twice anywhere in the path.

    python3 build.py            # writes out/01-....json ... out/20-....json and checks them
"""
import json
import os
import random
import re

import coding
import logical
import quant
import tech
import verbal
from common import Skip

OUT = os.path.join(os.path.dirname(__file__), "out")
NOTE = "Original practice questions modelled on the publicly reported test pattern. Not an official or past paper."

# (order, slug, title, companyTag, level, minutes, about, sections) ; section = (name, [(source, count, difficulty), ...])
TESTS = [
    (1, "wipro-nlth", "Wipro NLTH style · Aptitude", "Wipro", "EASY", 48, "Logical, quantitative and verbal ability, as in Wipro's national hiring test.",
     [("Logical ability", [("logical", 14, 1)]), ("Quantitative ability", [("quant", 16, 1)]),
      ("Verbal ability", [("verbal:synonym,antonym,fill,error,idiom,ows", 22, 1)])]),
    (2, "hcltech", "HCLTech style · Aptitude + Computer basics", "HCLTech", "EASY", 60, "Four equal sections including computer fundamentals.",
     [("Quantitative", [("quant", 15, 1)]), ("Logical", [("logical", 15, 1)]), ("Verbal", [("verbal:synonym,fill,error,ows,idiom", 15, 1)]),
      ("Computer fundamentals", [("cs", 15, 1)])]),
    (3, "cognizant-genc", "Cognizant GenC style · Aptitude", "Cognizant", "EASY", 95, "Quant, logical and English, 25 each, like the GenC assessment.",
     [("Quantitative aptitude", [("quant", 25, 1)]), ("Logical reasoning", [("logical", 25, 1)]),
      ("English comprehension", [("verbal:rc,fill,error,synonym,antonym", 25, 1)])]),
    (4, "tech-mahindra", "Tech Mahindra style · Aptitude, Technical, English", "Tech Mahindra", "EASY", 75, "Three elimination-style sections.",
     [("Aptitude", [("quant", 13, 1), ("logical", 12, 1)]), ("Technical", [("cs", 12, 1), ("techgen", 13, 1)]),
      ("English", [("verbal:synonym,antonym,fill,error,improve,ows", 25, 1)])]),
    (5, "capgemini", "Capgemini style · Pseudocode, English, Quant", "Capgemini", "EASY", 75, "Pseudocode-heavy pattern with English and quant.",
     [("Pseudocode", [("pseudo", 25, 1)]), ("English communication", [("verbal:rc,fill,error,improve,jumble", 30, 1)]),
      ("Quantitative", [("quant", 15, 2)])]),
    (6, "tcs-nqt-foundation", "TCS NQT style · Foundation", "TCS", "MEDIUM", 75, "Numerical, verbal and reasoning ability, the NQT Foundation part.",
     [("Numerical ability", [("quant", 20, 2)]), ("Verbal ability", [("verbal:rc,fill,error,jumble,improve", 25, 2)]),
      ("Reasoning ability", [("logical", 20, 2)])]),
    (7, "accenture", "Accenture style · Cognitive, Technical + Coding", "Accenture", "MEDIUM", 135,
     "Cognitive and technical MCQs followed by two coding problems.",
     [("English ability", [("verbal:rc,fill,error,synonym,idiom", 20, 2)]), ("Critical reasoning", [("logical", 20, 2)]),
      ("Abstract and numerical reasoning", [("quant", 10, 2)]), ("Pseudocode", [("pseudo", 15, 2)]),
      ("Networking, security, cloud and MS Office", [("cs", 20, 2), ("techgen", 5, 2)]), ("Coding", [("coding", 2, 1)])]),
    (8, "ibm", "IBM style · Number series, English + Coding", "IBM", "MEDIUM", 95, "Number series, English and a coding round.",
     [("Number series", [("series", 12, 2)]), ("English language", [("verbal:rc,fill,error,improve", 10, 2)]),
      ("Coding", [("coding", 1, 1), ("coding", 1, 2)])]),
    (9, "ltimindtree", "LTIMindtree style · Aptitude + Coding", "LTIMindtree", "MEDIUM", 100, "Aptitude and verbal followed by coding.",
     [("Quantitative", [("quant", 15, 2)]), ("Logical", [("logical", 15, 2)]), ("Verbal", [("verbal:rc,error,jumble,synonym,antonym", 15, 2)]),
      ("Coding", [("coding", 1, 1), ("coding", 1, 2)])]),
    (10, "infosys", "Infosys style · Reasoning, Quant, Verbal, Pseudocode, Puzzles", "Infosys", "MEDIUM", 100,
     "Five sections with tough logical and quant questions, like the Infosys online test.",
     [("Logical reasoning", [("logical", 15, 2)]), ("Mathematical ability", [("quant", 10, 3)]),
      ("Verbal ability", [("verbal:rc,jumble,error,improve,fill", 20, 2)]), ("Pseudocode", [("pseudo", 5, 3)]),
      ("Puzzle solving", [("puzzle", 4, 3)])]),
    (11, "deloitte-nla", "Deloitte NLA style · MCQ + Coding", "Deloitte", "MEDIUM", 90, "Language, aptitude and technical MCQs with two coding problems.",
     [("Language skills", [("verbal:rc,fill,error,synonym,antonym,improve", 20, 2)]), ("Quantitative", [("quant", 15, 2)]),
      ("Logical", [("logical", 15, 2)]), ("Technical", [("cs", 8, 2), ("techgen", 7, 2)]), ("Coding", [("coding", 1, 1), ("coding", 1, 2)])]),
    (12, "cognizant-genc-next", "Cognizant GenC Next style · Aptitude + Coding", "Cognizant", "MEDIUM", 100, "Aptitude plus two coding problems.",
     [("Aptitude", [("quant", 15, 2), ("logical", 15, 2)]), ("Coding", [("coding", 2, 2)])]),
    (13, "tcs-nqt-advanced", "TCS NQT style · Advanced (Digital / Prime)", "TCS", "HARD", 115, "Advanced quant and reasoning, then advanced coding.",
     [("Advanced quantitative and reasoning", [("quant", 8, 3), ("logical", 7, 3)]), ("Advanced coding", [("coding", 1, 2), ("coding", 1, 3)])]),
    (14, "infosys-sp-dse", "Infosys SP / DSE style · Coding", "Infosys", "HARD", 180, "Three coding problems of rising difficulty in three hours.",
     [("Coding", [("coding", 1, 2), ("coding", 2, 3)])]),
    (15, "zoho", "Zoho style · Programming + Aptitude", "Zoho", "HARD", 120, "C output and programming logic, tough aptitude, then coding.",
     [("Programming and C output", [("pseudo", 15, 3)]), ("Aptitude", [("quant", 15, 3)]), ("Coding", [("coding", 2, 2)])]),
    (16, "oracle", "Oracle style · Aptitude, CS fundamentals + Coding", "Oracle", "HARD", 90, "Aptitude and computer science MCQs, then coding.",
     [("Aptitude", [("quant", 5, 3), ("logical", 5, 3)]), ("CS fundamentals", [("cs", 12, 3), ("techgen", 8, 3)]), ("Coding", [("coding", 2, 2)])]),
    (17, "goldman-sachs", "Goldman Sachs style · Quant, CS + Coding", "Goldman Sachs", "HARD", 120, "Quant and CS MCQs with two coding problems.",
     [("Quantitative and reasoning", [("quant", 6, 3), ("logical", 4, 3)]), ("Computer science", [("cs", 4, 3), ("techgen", 6, 3)]),
      ("Coding", [("coding", 1, 2), ("coding", 1, 3)])]),
    (18, "adobe", "Adobe style · Aptitude, Technical + Coding", "Adobe", "HARD", 90, "Short aptitude and technical sections, then two hard problems.",
     [("Aptitude", [("quant", 5, 3), ("logical", 5, 3)]), ("Technical", [("cs", 4, 3), ("techgen", 6, 3)]), ("Coding", [("coding", 2, 3)])]),
    (19, "microsoft", "Microsoft style · Coding assessment", "Microsoft", "HARD", 90, "Three coding problems.",
     [("Coding", [("coding", 1, 2), ("coding", 2, 3)])]),
    (20, "amazon", "Amazon SDE style · Debugging + Coding", "Amazon", "HARD", 105, "Code reasoning and debugging MCQs, then two hard problems.",
     [("Code reasoning and debugging", [("techgen", 10, 3)]), ("Coding", [("coding", 2, 3)])]),
]


class Bank:
    """Hands out questions; never the same question text twice across the whole path."""

    def __init__(self, seed=2026):
        self.rng = random.Random(seed)
        self.used = set()
        self.verbal = verbal.build_pool(self.rng)
        self.cs = tech.cs_pool(self.rng)
        self.problems = {lv: [p for p in coding.PROBLEMS if p["level"] == lv] for lv in (1, 2, 3)}
        for v in self.problems.values():
            self.rng.shuffle(v)

    def _gen(self, gens, lv, weights=None):
        for _ in range(4000):
            g = self.rng.choices(gens, weights=weights)[0]
            try:
                q = g(self.rng, lv)
            except Skip:
                continue
            if q["text"] not in self.used:
                self.used.add(q["text"])
                return q
        raise RuntimeError(f"could not draw a new question from {[g.__name__ for g in gens]}")

    def take(self, source, count, lv):
        out = []
        if source.startswith("verbal:"):
            kinds = source.split(":")[1].split(",")
            i = 0
            while len(out) < count:
                k = kinds[i % len(kinds)]
                i += 1
                if i > count * 20:
                    raise RuntimeError(f"verbal pool exhausted for {kinds}")
                while self.verbal[k]:
                    q = self.verbal[k].pop()
                    if q["text"] not in self.used:
                        self.used.add(q["text"])
                        out.append(q)
                        break
            return out
        if source == "cs":
            while len(out) < count:
                q = self.cs.pop()
                if q["text"] not in self.used:
                    self.used.add(q["text"])
                    out.append(q)
            return out
        if source == "coding":
            for _ in range(count):
                p = self.problems[lv].pop()
                out.append(coding.build(p, self.rng))
            return out
        gens = {"quant": quant.GENERATORS, "logical": logical.GENERATORS, "techgen": tech.GENERATORS, "pseudo": tech.PSEUDO,
                "series": [logical.number_series, logical.letter_series],
                "puzzle": [logical.height_order, logical.blood_relation, logical.cube_paint, logical.ranking]}[source]
        return [self._gen(gens, lv) for _ in range(count)]


def build_all():
    bank = Bank()
    os.makedirs(OUT, exist_ok=True)
    files = []
    for order, slug, title, tag, level, minutes, about, sections in TESTS:
        secs = []
        for name, parts in sections:
            qs = []
            for source, count, lv in parts:
                qs += bank.take(source, count, lv)
            secs.append({"name": name, "questions": qs})
        mcqs = sum(1 for s in secs for q in s["questions"] if q["type"] == "MCQ")
        codes = sum(1 for s in secs for q in s["questions"] if q["type"] == "CODING")
        parts = [f"{mcqs} MCQs" if mcqs else "", f"{codes} coding problem{'s' if codes > 1 else ''}" if codes else ""]
        desc = f"{about} {' + '.join(p for p in parts if p)} in {minutes} minutes. {NOTE}"
        test = {"title": title, "description": desc, "companyTag": tag, "level": level, "order": order, "durationMinutes": minutes, "sections": secs}
        check(test)
        path = os.path.join(OUT, f"{order:02d}-{slug}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(test, f, ensure_ascii=False, indent=1)
        files.append((path, mcqs, codes, minutes))
    return files


def check(t):
    """The same rules the server applies, plus content checks."""
    assert 5 <= t["durationMinutes"] <= 300, t["title"]
    texts = set()
    for s in t["sections"]:
        assert s["name"] and s["questions"], (t["title"], s["name"])
        for q in s["questions"]:
            assert q["text"] and q["text"] not in texts, ("duplicate in test", q["text"][:80])
            texts.add(q["text"])
            if q["type"] == "MCQ":
                o = q["options"]
                assert 2 <= len(o) <= 6 and len(set(o)) == len(o), q
                assert isinstance(q["answer"], int) and 0 <= q["answer"] < len(o), q
                assert not re.search(r"\{[A-Z]\}|\bNone\b|\bnan\b", q["text"] + " ".join(o)), ("template leak", q["text"][:80], o)
            else:
                assert q["tests"] and q["title"] and q["samples"], q["title"]
                assert all(c["output"] != "" for c in q["tests"] + q["samples"]), q["title"]


if __name__ == "__main__":
    total_m = total_c = 0
    for path, m, c, mins in build_all():
        total_m += m
        total_c += c
        print(f"{os.path.basename(path):34s} {m:3d} MCQ  {c} coding  {mins:3d} min  {os.path.getsize(path) // 1024:4d} KB")
    print(f"total {total_m} MCQs, {total_c} coding problems")
