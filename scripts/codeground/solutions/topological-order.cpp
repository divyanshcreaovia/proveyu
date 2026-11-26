vector<int> solve(int n, vector<vector<int>> prerequisites) {
    vector<vector<int>> g(n);
    vector<int> deg(n, 0), out;
    for (auto& p : prerequisites) { g[p[0]].push_back(p[1]); deg[p[1]]++; }
    priority_queue<int, vector<int>, greater<int>> ready;
    for (int i = 0; i < n; i++) if (deg[i] == 0) ready.push(i);
    while (!ready.empty()) {
        int u = ready.top(); ready.pop();
        out.push_back(u);
        for (int v : g[u]) if (--deg[v] == 0) ready.push(v);
    }
    if ((int)out.size() != n) return {-1};
    return out;
}
