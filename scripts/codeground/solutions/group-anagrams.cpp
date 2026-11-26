vector<int> solve(vector<string> words) {
    unordered_map<string, int> g;
    int best = 0;
    for (string w : words) { sort(w.begin(), w.end()); best = max(best, ++g[w]); }
    return {(int)g.size(), best};
}
