vector<vector<int>> solve(vector<vector<int>> intervals) {
    sort(intervals.begin(), intervals.end());
    vector<vector<int>> out;
    for (auto& cur : intervals) {
        if (!out.empty() && cur[0] <= out.back()[1]) out.back()[1] = max(out.back()[1], cur[1]);
        else out.push_back(cur);
    }
    return out;
}
