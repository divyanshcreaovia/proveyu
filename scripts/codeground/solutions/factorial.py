def solve(n):
    return 1 if n <= 1 else n * solve(n - 1)
