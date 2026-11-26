def solve(n):
    out = []

    def go(k, a, c, b):
        if k == 0:
            return
        go(k - 1, a, b, c)
        out.append([a, c])
        go(k - 1, b, c, a)

    go(n, 'A', 'C', 'B')
    return out
