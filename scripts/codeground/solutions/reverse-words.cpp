string solve(string s) {
    stringstream in(s);
    vector<string> w;
    for (string x; in >> x;) w.push_back(x);
    string out;
    for (int i = (int)w.size() - 1; i >= 0; i--) out += w[i] + (i ? " " : "");
    return out;
}
