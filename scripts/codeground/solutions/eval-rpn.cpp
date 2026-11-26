int solve(vector<string> tokens) {
    stack<int> st;
    for (const string& t : tokens) {
        if (t == "+" || t == "-" || t == "*" || t == "/") {
            int b = st.top(); st.pop();
            int a = st.top(); st.pop();
            st.push(t == "+" ? a + b : t == "-" ? a - b : t == "*" ? a * b : a / b);
        } else st.push(stoi(t));
    }
    return st.top();
}
