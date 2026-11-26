int solve(int base, int exp, int mod) {
    long long r = 1 % mod, b = base % mod;
    while (exp > 0) {
        if (exp & 1) r = r * b % mod;
        b = b * b % mod;
        exp >>= 1;
    }
    return (int)r;
}
