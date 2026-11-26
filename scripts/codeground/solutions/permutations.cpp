vector<string> solve(string s) {
    sort(s.begin(), s.end());
    vector<string> out;
    do out.push_back(s); while (next_permutation(s.begin(), s.end()));
    return out;
}
