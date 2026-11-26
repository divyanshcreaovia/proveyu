static String solve(String s) {
    String t = s.trim();
    if (t.isEmpty()) return "";
    List<String> w = new ArrayList<>(Arrays.asList(t.split("\\s+")));
    Collections.reverse(w);
    return String.join(" ", w);
}
