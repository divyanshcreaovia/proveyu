static String solve(String s, String t) {
    int[] need = new int[256];
    boolean[] used = new boolean[256];
    for (char c : t.toCharArray()) { need[c]++; used[c] = true; }
    int missing = t.length(), l = 0, bestL = -1, bestLen = Integer.MAX_VALUE;
    for (int r = 0; r < s.length(); r++) {
        char c = s.charAt(r);
        if (used[c]) { if (need[c] > 0) missing--; need[c]--; }
        while (missing == 0) {
            if (r - l + 1 < bestLen) { bestLen = r - l + 1; bestL = l; }
            char d = s.charAt(l);
            if (used[d]) { need[d]++; if (need[d] > 0) missing++; }
            l++;
        }
    }
    return bestL < 0 ? "NONE" : s.substring(bestL, bestL + bestLen);
}
