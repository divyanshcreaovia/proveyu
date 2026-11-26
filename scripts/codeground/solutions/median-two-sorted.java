static double solve(int[] a, int[] b) {
    int[] m = new int[a.length + b.length];
    System.arraycopy(a, 0, m, 0, a.length);
    System.arraycopy(b, 0, m, a.length, b.length);
    Arrays.sort(m);
    int n = m.length;
    return n % 2 == 1 ? m[n / 2] : (m[n / 2 - 1] + m[n / 2]) / 2.0;
}
