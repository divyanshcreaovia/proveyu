vector<vector<int>> solve(vector<int> tree) {
    vector<vector<int>> out;
    if (tree.empty() || tree[0] == NONE) return out;
    vector<int> level = {tree[0]};
    size_t i = 1;
    while (!level.empty()) {
        out.push_back(level);
        vector<int> next;
        for (size_t k = 0; k < level.size(); k++)
            for (int c = 0; c < 2 && i < tree.size(); c++, i++) if (tree[i] != NONE) next.push_back(tree[i]);
        level = next;
    }
    return out;
}
