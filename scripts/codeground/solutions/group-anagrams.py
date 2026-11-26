def solve(words):
    groups = {}
    for w in words:
        k = ''.join(sorted(w))
        groups[k] = groups.get(k, 0) + 1
    return [len(groups), max(groups.values())]
