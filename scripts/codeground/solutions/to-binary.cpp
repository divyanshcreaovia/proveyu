string solve(int n) {
    string out;
    do { out = char('0' + n % 2) + out; n /= 2; } while (n > 0);
    return out;
}
