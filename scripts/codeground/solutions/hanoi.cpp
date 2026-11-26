void go(int k, string a, string c, string b, vector<vector<string>>& out) {
    if (k == 0) return;
    go(k - 1, a, b, c, out);
    out.push_back({a, c});
    go(k - 1, b, c, a, out);
}

vector<vector<string>> solve(int n) {
    vector<vector<string>> out;
    go(n, "A", "C", "B", out);
    return out;
}
