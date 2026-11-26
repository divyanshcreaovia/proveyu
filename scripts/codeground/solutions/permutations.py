from itertools import permutations


def solve(s):
    return [''.join(p) for p in permutations(sorted(s))]
