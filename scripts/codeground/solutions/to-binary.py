def solve(n):
    out = ''
    while True:
        out = str(n % 2) + out
        n //= 2
        if n == 0:
            return out
