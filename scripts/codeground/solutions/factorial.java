static long solve(int n) {
    return n <= 1 ? 1 : n * solve(n - 1);
}
