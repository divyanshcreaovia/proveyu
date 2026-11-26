static String[] solve(String s) {
    char[] c = s.toCharArray();
    Arrays.sort(c);
    List<String> out = new ArrayList<>();
    go(c, new boolean[c.length], new StringBuilder(), out);
    return out.toArray(new String[0]);
}

static void go(char[] c, boolean[] used, StringBuilder p, List<String> out) {
    if (p.length() == c.length) { out.add(p.toString()); return; }
    for (int i = 0; i < c.length; i++) if (!used[i]) {
        used[i] = true; p.append(c[i]);
        go(c, used, p, out);
        used[i] = false; p.deleteCharAt(p.length() - 1);
    }
}
