def solve(tree):
    if not tree or tree[0] is None:
        return []
    out, level, i = [], [tree[0]], 1
    while level:
        out.append(level)
        nxt = []
        for _ in level:
            for _ in range(2):
                if i < len(tree):
                    if tree[i] is not None:
                        nxt.append(tree[i])
                    i += 1
        level = nxt
    return out
