def solve(a, b):
    m = sorted(a + b)
    n = len(m)
    return m[n // 2] if n % 2 else (m[n // 2 - 1] + m[n // 2]) / 2
