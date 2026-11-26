static String[][] solve(int n) {
    List<String[]> out = new ArrayList<>();
    go(n, "A", "C", "B", out);
    return out.toArray(new String[0][]);
}

static void go(int k, String a, String c, String b, List<String[]> out) {
    if (k == 0) return;
    go(k - 1, a, b, c, out);
    out.add(new String[] {a, c});
    go(k - 1, b, c, a, out);
}
