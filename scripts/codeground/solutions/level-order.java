static int[][] solve(Integer[] tree) {
    List<int[]> out = new ArrayList<>();
    if (tree.length == 0 || tree[0] == null) return new int[0][];
    List<Integer> level = new ArrayList<>(List.of(tree[0]));
    int i = 1;
    while (!level.isEmpty()) {
        out.add(level.stream().mapToInt(Integer::intValue).toArray());
        List<Integer> next = new ArrayList<>();
        for (int k = 0; k < level.size(); k++)
            for (int c = 0; c < 2 && i < tree.length; c++, i++) if (tree[i] != null) next.add(tree[i]);
        level = next;
    }
    return out.toArray(new int[0][]);
}
