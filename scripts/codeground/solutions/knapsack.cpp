int solve(vector<int> weights, vector<int> values, int capacity) {
    vector<int> dp(capacity + 1, 0);
    for (size_t i = 0; i < weights.size(); i++)
        for (int c = capacity; c >= weights[i]; c--) dp[c] = max(dp[c], dp[c - weights[i]] + values[i]);
    return dp[capacity];
}
