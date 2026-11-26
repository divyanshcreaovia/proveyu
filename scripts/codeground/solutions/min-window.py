def solve(s, t):
    need = {}
    for c in t:
        need[c] = need.get(c, 0) + 1
    missing, l, best = len(t), 0, None
    for r, c in enumerate(s):
        if c in need:
            if need[c] > 0:
                missing -= 1
            need[c] -= 1
        while missing == 0:
            if best is None or r - l + 1 < len(best):
                best = s[l:r + 1]
            if s[l] in need:
                need[s[l]] += 1
                if need[s[l]] > 0:
                    missing += 1
            l += 1
    return 'NONE' if best is None else best
