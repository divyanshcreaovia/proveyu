static int solve(String[] tokens) {
    Deque<Integer> st = new ArrayDeque<>();
    for (String t : tokens) {
        if (t.length() == 1 && "+-*/".contains(t)) {
            int b = st.pop(), a = st.pop();
            st.push(t.equals("+") ? a + b : t.equals("-") ? a - b : t.equals("*") ? a * b : a / b);
        } else st.push(Integer.parseInt(t));
    }
    return st.pop();
}
