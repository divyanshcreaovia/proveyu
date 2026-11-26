bool solve(int n) {
    if (n < 0) return false;
    string s = to_string(n);
    return string(s.rbegin(), s.rend()) == s;
}
