static boolean solve(int n) {
    if (n < 0) return false;
    String s = String.valueOf(n);
    return new StringBuilder(s).reverse().toString().equals(s);
}
