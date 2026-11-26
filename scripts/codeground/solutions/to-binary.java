static String solve(int n) {
    StringBuilder b = new StringBuilder();
    do { b.append(n % 2); n /= 2; } while (n > 0);
    return b.reverse().toString();
}
