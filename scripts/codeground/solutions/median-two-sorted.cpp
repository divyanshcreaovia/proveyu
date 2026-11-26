double solve(vector<int> a, vector<int> b) {
    vector<int> m(a);
    m.insert(m.end(), b.begin(), b.end());
    sort(m.begin(), m.end());
    int n = m.size();
    return n % 2 ? m[n / 2] : (m[n / 2 - 1] + m[n / 2]) / 2.0;
}
