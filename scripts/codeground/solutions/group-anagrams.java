static int[] solve(String[] words) {
    Map<String, Integer> g = new HashMap<>();
    for (String w : words) {
        char[] c = w.toCharArray();
        Arrays.sort(c);
        g.merge(new String(c), 1, Integer::sum);
    }
    return new int[] {g.size(), Collections.max(g.values())};
}
