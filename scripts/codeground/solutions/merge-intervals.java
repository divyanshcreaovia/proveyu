static int[][] solve(int[][] intervals) {
    Arrays.sort(intervals, (p, q) -> Integer.compare(p[0], q[0]));
    List<int[]> out = new ArrayList<>();
    for (int[] cur : intervals) {
        if (!out.isEmpty() && cur[0] <= out.get(out.size() - 1)[1])
            out.get(out.size() - 1)[1] = Math.max(out.get(out.size() - 1)[1], cur[1]);
        else out.add(new int[] {cur[0], cur[1]});
    }
    return out.toArray(new int[0][]);
}
