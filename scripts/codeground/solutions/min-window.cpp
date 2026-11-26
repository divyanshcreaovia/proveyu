string solve(string s, string t) {
    map<char, int> need;
    for (char c : t) need[c]++;
    int missing = t.size(), l = 0, bestL = -1, bestLen = INT_MAX;
    for (int r = 0; r < (int)s.size(); r++) {
        if (need.count(s[r])) { if (need[s[r]] > 0) missing--; need[s[r]]--; }
        while (missing == 0) {
            if (r - l + 1 < bestLen) { bestLen = r - l + 1; bestL = l; }
            if (need.count(s[l])) { need[s[l]]++; if (need[s[l]] > 0) missing++; }
            l++;
        }
    }
    return bestL < 0 ? "NONE" : s.substr(bestL, bestLen);
}
