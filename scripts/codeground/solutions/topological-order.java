static int[] solve(int n, int[][] prerequisites) {
    List<List<Integer>> g = new ArrayList<>();
    for (int i = 0; i < n; i++) g.add(new ArrayList<>());
    int[] deg = new int[n];
    for (int[] p : prerequisites) { g.get(p[0]).add(p[1]); deg[p[1]]++; }
    PriorityQueue<Integer> ready = new PriorityQueue<>();
    for (int i = 0; i < n; i++) if (deg[i] == 0) ready.add(i);
    int[] out = new int[n];
    int k = 0;
    while (!ready.isEmpty()) {
        int u = ready.poll();
        out[k++] = u;
        for (int v : g.get(u)) if (--deg[v] == 0) ready.add(v);
    }
    return k == n ? out : new int[] {-1};
}
